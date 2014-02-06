#include <cstdio>
#include <cstdlib>
#include <set>
#include <cstring>
#include <ctime>

#include "Search.h"
#include "Utils.h"

using namespace std;

//
// Utilities
//

/** Check if element |index| is set in the bitmask */
inline bool isSetBit(const uint64_t bitmask[], const uint8_t& index) {
  uint8_t bucket = index >> 6;
  uint8_t offset = index % 64;
  uint64_t mask = 0x1 << offset;
  return bitmask[bucket] & mask != 0;
}

/** Set element |index| in the bitmask */
inline bool setBit(uint64_t bitmask[], const uint8_t& index) {
  uint8_t bucket = index >> 6;
  uint8_t offset = index % 64;
  uint64_t mask = 0x1 << offset;
  bitmask[bucket] = bitmask[bucket] | mask;
}

//
// Class Path
//
Path::Path(const Path* parentOrNull, const word* fact, uint8_t factLength, edge_type edgeType,
           const uint64_t fixedBitmask[], uint8_t lastMutatedIndex) 
    : parent(parentOrNull),
      fact(fact),
      factLength(factLength),
      lastMutationIndex(lastMutatedIndex),
      edgeType(edgeType),
      fixedBitmask{fixedBitmask[0], fixedBitmask[1], fixedBitmask[2], fixedBitmask[3]} {
}

Path::Path(const word* fact, uint8_t factLength)
    : parent(NULL),
      fact(fact),
      factLength(factLength),
      lastMutationIndex(255),
      edgeType(255),
      fixedBitmask{0,0,0,0} { }

Path::~Path() {
};

bool Path::operator==(const Path& other) const {
  // Check metadata
  if ( !( edgeType == other.edgeType && factLength == other.factLength ) ) {
    return false;
  }
  // Check fact content
  for (int i = 0; i < factLength; ++i) {
    if (other.fact[i] != fact[i]) {
      return false;
    }
  }
  // Return true
  return true;
}

//
// Class SearchType
//

//
// Class BreadthFirstSearch
//

// -- constructor --
BreadthFirstSearch::BreadthFirstSearch()
    : fringeLength(0), fringeCapacity(1), fringeI(0),
      poolCapacity(1), poolLength(0), poppedRoot(false) {
  this->fringe = (Path**) malloc(fringeCapacity * sizeof(Path*));
  for (int i = 0; i < fringeCapacity; ++i) {
    this->fringe[i] = (Path*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(Path) );
  }
  this->currentFringe = this->fringe[0];
  this->memoryPool = (word**) malloc(poolCapacity * sizeof(word*));
  for (int i = 0; i < poolCapacity; ++i) {
    this->memoryPool[i] = (word*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(word) );
  }
  this->currentMemoryPool = this->memoryPool[0];
}

// -- destructor --
BreadthFirstSearch::~BreadthFirstSearch() {
  printf("de-allocating search; %lu MB freed\n",
    ((fringeCapacity << POOL_BUCKET_SHIFT) + (poolCapacity << POOL_BUCKET_SHIFT)) >> 20);
  for (int i = 0; i < fringeCapacity; ++i) {
    free(this->fringe[i]);
  }
  free(this->fringe);
  for (int i = 0; i < poolCapacity; ++i) {
    free(this->memoryPool[i]);
  }
  free(this->memoryPool);
  delete this->root;
}

// -- allocate space for word --
inline word* BreadthFirstSearch::allocateWord(uint8_t toAllocateLength) {
  const uint64_t startOffset = poolLength % (0x1 << POOL_BUCKET_SHIFT);
  const uint64_t endOffset = startOffset + toAllocateLength;
  const uint64_t newBucket = (poolLength + toAllocateLength) >> POOL_BUCKET_SHIFT;
  if ((startOffset >> POOL_BUCKET_SHIFT) != (endOffset >> POOL_BUCKET_SHIFT)) {  // if we've overflowed the bucket
    // Case: overflowed buffer
    if (newBucket >= poolCapacity) {
      // Case: need more space
      const uint64_t newCapacity = (poolCapacity + 1 + poolCapacity / 3);
      word** newPointer = (word**) malloc( newCapacity * sizeof(word*) );
      if (newPointer == NULL) {
        return NULL;
      }
      memcpy(newPointer, memoryPool, poolCapacity * sizeof(word*));
      free (memoryPool);
      memoryPool = newPointer;
      for (int i = poolCapacity; i < newCapacity; ++i) {
        this->memoryPool[i] = (word*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(word) );
      }
      poolCapacity = newCapacity;
    }
    // Allocate space at the beginning of the next bucket
    poolLength = (newBucket << POOL_BUCKET_SHIFT) + toAllocateLength;
    currentMemoryPool = memoryPool[newBucket];
    return &currentMemoryPool[0];
  } else {
    poolLength += toAllocateLength;
    return &currentMemoryPool[startOffset];
  }
}

// -- allocate space for path --
inline Path* BreadthFirstSearch::allocatePath() {
  const uint64_t bucket = fringeLength >> POOL_BUCKET_SHIFT;
  const uint64_t offset = fringeLength % (0x1 << POOL_BUCKET_SHIFT);
  if (bucket >= fringeCapacity) {
    // Case: need more space
    const uint64_t newCapacity = (fringeCapacity + 1 + fringeCapacity / 3);
    Path** newPointer = (Path**) malloc( newCapacity * sizeof(Path*) );
    if (newPointer == NULL) {
      return NULL;
    }
    memcpy(newPointer, fringe, fringeCapacity * sizeof(Path*));
    free(fringe);
    fringe = newPointer;
    for (int i = fringeCapacity; i < newCapacity; ++i) {
      this->fringe[i] = (Path*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(Path) );
    }
    fringeCapacity = newCapacity;
  }
  if (offset == 0) { currentFringe = fringe[bucket]; }
  fringeLength += 1;
  return &currentFringe[offset];
}

// -- push a new element onto the fringe --
inline const Path* BreadthFirstSearch::push(
    const Path* parent, uint8_t mutationIndex,
    uint8_t replaceLength, word replace1, word replace2, edge_type edge) {

  // Allocate new fact
  const uint8_t mutatedLength = parent->factLength - 1 + replaceLength;
  // (allocate fact)
  word* mutated = allocateWord(mutatedLength);
  if (mutated == NULL) { return NULL; }
  // (mutate fact)
  memcpy(mutated, parent->fact, mutationIndex * sizeof(word));
  if (replaceLength > 0) { mutated[mutationIndex] = replace1; }
  if (replaceLength > 1) { mutated[mutationIndex + 1] = replace2; }
  if (mutationIndex < parent->factLength - 1) {
    memcpy(&(mutated[mutationIndex + replaceLength]),
           &(parent->fact[mutationIndex + 1]),
           (parent->factLength - mutationIndex - 1) * sizeof(word));
  }

  // Compute fixed bitmap
  uint64_t fixedBitmask[4];
  memcpy(fixedBitmask, parent->fixedBitmask, 4 * sizeof(uint64_t));
  if (mutationIndex != parent->lastMutationIndex) {
    setBit(fixedBitmask, parent->lastMutationIndex);
  }

  // Allocate new path
  Path* newPath = allocatePath();
  if (newPath == NULL) { return NULL; }
  new(newPath) Path(parent, mutated, mutatedLength, edge, fixedBitmask, mutationIndex);
  return parent;
}

// -- peek at the next element --
const Path* BreadthFirstSearch::peek() {
  if (!poppedRoot && this->fringeI == 0) {
    poppedRoot = true;
    return root;
  }
  const uint64_t offset = this->fringeI % (0x1 << POOL_BUCKET_SHIFT);
  return &this->currentFringe[offset];
}

// -- pop the next element --
inline const Path* BreadthFirstSearch::pop() {
  if (this->fringeI == 0 && !poppedRoot) {
    poppedRoot = true;
    return root;
  }
  const uint64_t offset = this->fringeI % (0x1 << POOL_BUCKET_SHIFT);
  if (offset == 0) {
    const uint64_t bucket = this->fringeI >> POOL_BUCKET_SHIFT;
    currentPopFringe = fringe[bucket];
  }
  this->fringeI += 1;
  return &currentPopFringe[offset];
}

// -- check if the fringe is empty --
inline bool BreadthFirstSearch::isEmpty() {
  return (fringeI >= fringeLength && poppedRoot) || root == NULL;
}

//
// Class CacheStrategy
//

//
// Class CacheStrategyNone
//
bool CacheStrategyNone::isSeen(const Path&) {
  return false;
}

void CacheStrategyNone::add(const Path&) { }

//
// Function search()
//

// A helper to push elements to the queue en bulk
inline const Path* flushQueue(SearchType* fringe,
                              const Path* parent,
                              const uint8_t* indexToMutateArr,
                              const word* sinkArr,
                              const uint8_t* typeArr,
                              const uint8_t queueLength) {
  uint8_t i = 0;
  while (parent != NULL && i < queueLength) {
    parent = fringe->push(parent, indexToMutateArr[i], 1, sinkArr[i], 0, typeArr[i]);
    i += 1;
  }
  return parent;
}

// The main search() function
vector<const Path*> Search(Graph* graph, FactDB* knownFacts,
                     const word* queryFact, const uint8_t queryFactLength,
                     SearchType* fringe, CacheStrategy* cache,
                     const uint64_t timeout) {
  //
  // Setup
  //
  // Create a vector for the return value to occupy
  vector<const Path*> responses;
  // Create the start state
  fringe->root = new Path(queryFact, queryFactLength);  // I need the memory to not go away
  // Initialize timer (number of elements popped from the fringe)
  uint64_t time = 0;
  const uint32_t tickTime = 100000;
  std::clock_t startTime = std::clock();

  //
  // Search
  //
  while (!fringe->isEmpty() && time < timeout) {
    // Get the next element from the fringe
    if (fringe->isEmpty()) {
      // (fringe is empty -- this should basically never be called)
      printf("IMPOSSIBLE: the search fringe is empty. This means (a) there are leaf nodes in the graph, and (b) this would have caused a memory leak.\n");
      std::exit(1);
    }
    const Path* parent = fringe->pop();
    // Update time
    time += 1;
    if (time % tickTime == 0) {
      const uint32_t tickOOM
        = tickTime < 1000 ? 1 : (tickTime < 1000000 ? 1000 : (tickTime  < 1000000000 ? 1000000 : 1) );
      printf("[%lu%s / %lu%s search tick]; %lu paths found (%lu ms elapsed)\n",
             time / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             timeout / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             responses.size(),
             (uint64_t) (1000.0 * ( std::clock() - startTime ) / ((double) CLOCKS_PER_SEC)));
    }

    // -- Check If Valid --
//    printf("Checking %s\n", toString(*graph, parent->fact, parent->factLength).c_str());
    if (knownFacts->contains(parent->fact, parent->factLength)) {
      responses.push_back(parent);
    }

    // -- Mutations --
    // (variables)
    uint8_t parentLength = parent->factLength;
    const uint64_t fixedBitmask[4] = { parent->fixedBitmask[0], parent->fixedBitmask[1], parent->fixedBitmask[2], parent->fixedBitmask[3] };
    const word* parentFact = parent->fact;  // note: this can change over the course of the search
    // (mutation queue)
    uint8_t indexToMutateArr[256];
    word sinkArr[256];
    uint8_t typeArr[256];
    uint8_t queueLength = 0;
    // (algorithm)
    for (uint8_t indexToMutate = 0;
         indexToMutate < parentLength;
         ++indexToMutate) {  // for each index to mutate...
      if (isSetBit(fixedBitmask, indexToMutate)) { continue; }
      uint32_t numMutations = 0;
      const edge* mutations = graph->outgoingEdgesFast(parentFact[indexToMutate], &numMutations);
      for (int i = 0; i < numMutations; ++i) {
        // Prune edges to add
        if (mutations[i].type > 1) { continue; } // TODO(gabor) don't only do WordNet down
        // Flush if necessary (save memory)
        if (queueLength >= 255) {
          parent = flushQueue(fringe, parent, indexToMutateArr, sinkArr, typeArr, queueLength);
          if (parent == NULL) {
            printf("Error pushing to stack; returning\n");
            return responses;
          }
          parentFact = parent->fact;
          queueLength = 0;
        }
        // Add the state to the fringe
        // These are queued up in order to try to protect the cache; the push() call is
        // fairly expensive memory-wise.
        indexToMutateArr[queueLength] = indexToMutate;
        sinkArr[queueLength] = mutations[i].sink;
        typeArr[queueLength] = mutations[i].type;
//        printf("\tmutation [%d] %s -> %s  (type %s)\n", indexToMutateArr[queueLength],
//               graph->gloss(parent->fact[indexToMutateArr[queueLength]]),
//               graph->gloss(sinkArr[queueLength]),
//               toString(typeArr[queueLength]).c_str());
        queueLength += 1;
//        parent = fringe->push(parent, indexToMutate, 1, mutations[i].sink, 0, mutations[i].type);
      }
    }
    if (flushQueue(fringe, parent, indexToMutateArr, sinkArr, typeArr, queueLength) == NULL) {
      printf("Error pushing to stack; returning\n");
      return responses;
    }
  }

  return responses;
}




