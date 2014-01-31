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
inline bool isSetBit(const uint64_t bitmask[], const uint8_t index) {
  uint8_t bucket = index / 64;
  uint8_t offset = index % 64;
  uint64_t mask = 0x1 << offset;
  return bitmask[bucket] & mask != 0;
}

/** Set element |index| in the bitmask */
inline bool setBit(uint64_t bitmask[], const uint8_t index) {
  uint8_t bucket = index / 64;
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
      fixedBitmask{fixedBitmask[0], fixedBitmask[1], fixedBitmask[2], fixedBitmask[3]} { }

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
BreadthFirstSearch::BreadthFirstSearch()
    : fringeLength(0), fringeCapacity(1), fringeI(0),
      poolCapacity(1), poolLength(0), poppedRoot(false),
      sumOffset(0){
  this->fringe = (Path*) malloc(fringeCapacity * sizeof(Path));
  this->memoryPool = (word*) malloc(poolCapacity * sizeof(word*));
}

BreadthFirstSearch::~BreadthFirstSearch() {
  free(this->fringe);
  free(this->memoryPool);
  delete this->root;
}
 
const Path* BreadthFirstSearch::push(
    const Path* parent, uint8_t mutationIndex,
    uint8_t replaceLength, word replace1, word replace2, edge_type edge) {

  // Allocate new fact
  uint8_t mutatedLength = parent->factLength - 1 + replaceLength;
  // (ensure space)
  while (poolLength + mutatedLength >= poolCapacity) {
    // (re-allocate array)
    word* newPool = (word*) malloc(2 * poolCapacity * sizeof(word*));
    uint64_t startOffset = ((uint64_t) newPool) - ((uint64_t) memoryPool);
    memcpy(newPool, memoryPool, poolCapacity * sizeof(word*));
    free(memoryPool);
    memoryPool = newPool;
    poolCapacity = 2 * poolCapacity;
    // (fix pointers -- and God help me for pointer arithmetic)
    for (int i = 0; i <fringeLength; ++i) {
      fringe[i].fact = (word*) (startOffset + ((uint64_t) fringe[i].fact));
    }
  }
  // (allocate fact)
  word* mutated = &memoryPool[poolLength];
  poolLength += mutatedLength;
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
  // (ensure space)
  while (fringeLength >= fringeCapacity) {
    // (re-allocate array)
    Path* newFringe = (Path*) malloc(2 * fringeCapacity * sizeof(Path));
    uint64_t startOffset = ((uint64_t) newFringe) - ((uint64_t) fringe);
    memcpy(newFringe, fringe, fringeCapacity * sizeof(Path));
    free(fringe);
    fringe = newFringe;
    fringeCapacity = 2 * fringeCapacity;
    // (fix pointers -- and God help me for pointer arithmetic)
    for (int i = 0; i < fringeLength; ++i) {
      if (fringe[i].parent != root) {
        fringe[i].parent = (Path*) (startOffset + ((uint64_t) fringe[i].parent));
      }
    }
    if (parent != root) {
      parent = (Path*) (startOffset + ((uint64_t) parent));
    }
  }
  // (allocate path)
  fringeLength += 1;
  new(&fringe[fringeLength-1]) Path(parent, mutated, mutatedLength, edge, fixedBitmask, mutationIndex);
  return parent;
}

const Path* BreadthFirstSearch::peek() {
  if (!poppedRoot && this->fringeI == 0) {
    poppedRoot = true;
    return root;
  }
  return &this->fringe[this->fringeI];
}

const Path* BreadthFirstSearch::pop() {
  if (this->fringeI == 0 && !poppedRoot) {
    poppedRoot = true;
    return root;
  }
  this->fringeI += 1;
  return &this->fringe[this->fringeI - 1];
}

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
  const uint32_t tickTime = 100;
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
      printf("[%lu%s / %lu%s search tick]; %lu paths found (%f ms elapsed)\n",
             time / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             timeout / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             responses.size(),
             1000.0 * ( std::clock() - startTime ) / ((double) CLOCKS_PER_SEC));
    }

    // -- Check If Valid --
    if (knownFacts->contains(parent->fact, parent->factLength)) {
      responses.push_back(parent);
    }

    // -- Mutations --
    for (int indexToMutate = 0;
         indexToMutate < parent->factLength;
         ++indexToMutate) {  // for each index to mutate...
      if (isSetBit(parent->fixedBitmask, indexToMutate)) { continue; }
      uint32_t numMutations = 0;
      const edge* mutations = graph->outgoingEdgesFast(parent->fact[indexToMutate], &numMutations);
      for (int i = 0; i < numMutations; ++i) {
        if (mutations[i].type > 1) { continue; } // TODO(gabor) don't only do WordNet up
        // Add the state to the fringe
        parent = fringe->push(parent, indexToMutate, 1, mutations[i].sink, 0, mutations[i].type);
      }
    }
  }

  return responses;
}




