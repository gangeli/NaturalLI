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
Path::Path(const Path* parentOrNull, const tagged_word* fact, uint8_t factLength, edge_type edgeType,
           const uint64_t fixedBitmask[], uint8_t lastMutatedIndex) 
    : parent(parentOrNull),
      fact(fact),
      factLength(factLength),
      lastMutationIndex(lastMutatedIndex),
      edgeType(edgeType),
      fixedBitmask{fixedBitmask[0], fixedBitmask[1], fixedBitmask[2], fixedBitmask[3]} {
}

Path::Path(const tagged_word* fact, uint8_t factLength)
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
    : fringeLength(0), fringeCapacity(16), fringeI(0),
      poolCapacity(16), poolLength(0), poppedRoot(false) {
  this->fringe = (Path**) malloc(fringeCapacity * sizeof(Path*));
  for (int i = 0; i < fringeCapacity; ++i) {
    this->fringe[i] = (Path*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(Path) );
  }
  this->currentFringe = this->fringe[0];
  this->memoryPool = (tagged_word**) malloc(poolCapacity * sizeof(tagged_word*));
  for (int i = 0; i < poolCapacity; ++i) {
    this->memoryPool[i] = (tagged_word*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(tagged_word) );
  }
  this->currentMemoryPool = this->memoryPool[0];
}

// -- destructor --
BreadthFirstSearch::~BreadthFirstSearch() {
  printf("de-allocating BFS; %lu MB freed\n",
    ((fringeCapacity << POOL_BUCKET_SHIFT)*sizeof(Path) + (poolCapacity << POOL_BUCKET_SHIFT) * sizeof(word)) >> 20);
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
inline tagged_word* BreadthFirstSearch::allocateWord(uint8_t toAllocateLength) {
  const uint64_t startOffset = poolLength % (0x1 << POOL_BUCKET_SHIFT);
  const uint64_t endOffset = startOffset + toAllocateLength;
  const uint64_t newBucket = (poolLength + toAllocateLength) >> POOL_BUCKET_SHIFT;
  if ((startOffset >> POOL_BUCKET_SHIFT) != (endOffset >> POOL_BUCKET_SHIFT)) {  // if we've overflowed the bucket
    // Case: overflowed buffer
    if (newBucket >= poolCapacity) {
      // Case: need more space
      const uint64_t newCapacity = poolCapacity << 1;
      tagged_word** newPointer = (tagged_word**) malloc( newCapacity * sizeof(tagged_word*) );
      if (newPointer == NULL) {
        return NULL;
      }
      memcpy(newPointer, memoryPool, poolCapacity * sizeof(tagged_word*));
      free (memoryPool);
      memoryPool = newPointer;
      for (int i = poolCapacity; i < newCapacity; ++i) {
        this->memoryPool[i] = (tagged_word*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(word) );
        if (this->memoryPool[i] == NULL) {
          return NULL;
        }
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
    const uint64_t newCapacity = fringeCapacity << 1;
    Path** newPointer = (Path**) malloc( newCapacity * sizeof(Path*) );
    if (newPointer == NULL) {
      return NULL;
    }
    memcpy(newPointer, fringe, fringeCapacity * sizeof(Path*));
    free(fringe);
    fringe = newPointer;
    for (int i = fringeCapacity; i < newCapacity; ++i) {
      this->fringe[i] = (Path*) malloc( (0x1 << POOL_BUCKET_SHIFT) * sizeof(Path) );
      if (this->fringe[i] == NULL) {
        return NULL;
      }
    }
    fringeCapacity = newCapacity;
  }
  if (offset == 0) { currentFringe = fringe[bucket]; }
  fringeLength += 1;
  return &currentFringe[offset];
}

// -- push a new element onto the fringe --
inline const Path* BreadthFirstSearch::push(
    const Path* parent, const uint8_t& mutationIndex,
    const uint8_t& replaceLength, const tagged_word& replace1, const tagged_word& replace2,
    const edge_type& edge, const float& cost) {

  // Allocate new fact
  const uint8_t mutatedLength = parent->factLength - 1 + replaceLength;
  // (allocate fact)
  tagged_word* mutated = allocateWord(mutatedLength);
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
  return new(newPath) Path(parent, mutated, mutatedLength, edge, fixedBitmask, mutationIndex);
}

// -- peek at the next element --
const Path* BreadthFirstSearch::peek() {
  if (!poppedRoot && this->fringeI == 0) {
    return root;
  }
  const uint64_t offset = this->fringeI % (0x1 << POOL_BUCKET_SHIFT);
  return &this->currentFringe[offset];
}

// -- pop the next element --
inline float BreadthFirstSearch::pop(const Path** poppedElement) {
  if (this->fringeI == 0 && !poppedRoot) {
    poppedRoot = true;
    *poppedElement = root;
    return 0.0;
  }
  const uint64_t offset = this->fringeI % (0x1 << POOL_BUCKET_SHIFT);
  if (offset == 0) {
    const uint64_t bucket = this->fringeI >> POOL_BUCKET_SHIFT;
    currentPopFringe = fringe[bucket];
  }
  this->fringeI += 1;
  *poppedElement = &currentPopFringe[offset];
  return 0.0f;
}

// -- check if the fringe is empty --
inline bool BreadthFirstSearch::isEmpty() {
  return (fringeI >= fringeLength && poppedRoot) || root == NULL;
}

//
// Class UniformCostSearch
//

// -- constructor --
UniformCostSearch::UniformCostSearch() :
    heapSize(0), heapCapacity(1024){
  heap = (const Path**) malloc(heapCapacity * sizeof(Path*));
  costs = (float*) malloc(heapCapacity * sizeof(float));
}

// -- destructor --
UniformCostSearch::~UniformCostSearch() {
  printf("de-allocating UCS; %lu MB freed\n", (heapSize * (sizeof(Path*) + sizeof(float))) >> 20);
  free(heap);
  free(costs);
}

// -- swap() --
inline void swap(const Path** heap, float* costs, const uint64_t i, const uint64_t j) {
  const Path* tmpPath = heap[i];
  float tmpCost = costs[i];
  heap[i]  = heap[j];
  costs[i] = costs[j];
  heap[j]  = tmpPath;
  costs[j] = tmpCost;
}

// -- bubbleDown() --
inline void UniformCostSearch::bubbleDown(const uint64_t index) {
  const uint64_t leftChild = index << 1;
  const uint64_t rightChild = (index << 1) + 1;
  // Default: stay where we are
  float min = costs[index];
  uint64_t argmin = index;
  // Check left child
  if (leftChild < heapSize) {
    const float leftMin = costs[leftChild];
    if (leftMin < min) {
      min = leftMin;
      argmin = leftChild;
    }
  }
  // Check right child
  if (rightChild < heapSize) {
    const float rightMin = costs[rightChild];
    if (rightMin < min) {
      min = rightMin;
      argmin = rightChild;
    }
  }
  // Bubble
  if (argmin != index) {
    swap(heap, costs, argmin, index);
    bubbleDown(argmin);
  }
}

// -- bubbleUp() --
inline void UniformCostSearch::bubbleUp(const uint64_t index) {
  if (index == 0) { return; }
  const uint64_t parent = index >> 1;
  if (costs[parent] > costs[index]) {
    swap(heap, costs, parent, index);
    bubbleUp(parent);
  }
}

// -- push() --
inline const Path* UniformCostSearch::push(const Path* parent, const uint8_t& mutationIndex,
                    const uint8_t& replaceLength, const tagged_word& replace1, const tagged_word& replace2,
                    const edge_type& edge, const float& cost) {
  // Allocate the node
  const Path* node = BreadthFirstSearch::push(parent, mutationIndex, replaceLength, replace1, replace2, edge, cost);
  // Ensure we have space on the heap
  if (heapSize >= heapCapacity) {
    uint64_t newCapacity = heapCapacity << 1;
    const Path** newHeap = (const Path**) malloc(newCapacity * sizeof(Path*));
    if (newHeap == NULL) { return NULL; }
    float* newCosts = (float*) malloc(newCapacity * sizeof(float*));
    if (newCosts == NULL) { return NULL; }
    memcpy(newHeap, heap, heapCapacity * sizeof(Path*));
    memcpy(newCosts, costs, heapCapacity * sizeof(float));
    free(heap);
    free(costs);
    heap = newHeap;
    costs = newCosts;
    heapCapacity = newCapacity;
  }
  // Add the node to the heap
  heap[heapSize]  = node;
  costs[heapSize] = cost;
  // Fix heap
  bubbleUp(heapSize);
  heapSize += 1;
  // Return
  return node;
}

// -- pop() --
inline float UniformCostSearch::pop(const Path** poppedElement) {
  if (!poppedRoot) {
    poppedRoot = true;
    *poppedElement = root;
    return 0.0;
  }
  // Pop element
  *poppedElement = heap[0];
  float cost = costs[0];
  // Fix heap
  heapSize -= 1;
  if (heapSize > 0) {
    heap[0] = heap[heapSize];
    costs[0] = costs[heapSize];
    bubbleDown(0);
  }
  // Return
  return cost;
}

// -- peek() --
const Path* UniformCostSearch::peek() {
  if (!poppedRoot) {
    return root;
  } else {
    return heap[0];
  }
}

// -- isEmpty() --
bool UniformCostSearch::isEmpty() {
  return (heapSize == 0 && poppedRoot) || root == NULL;
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
// Class WeightVector
//
WeightVector::WeightVector() :
  available(false),
  unigramWeightsUp(NULL), bigramWeightsUp(NULL),
  unigramWeightsDown(NULL), bigramWeightsDown(NULL),
  unigramWeightsFlat(NULL), bigramWeightsFlat(NULL),
  unigramWeightsAny(NULL), bigramWeightsAny(NULL) { }

WeightVector::WeightVector(
  float* unigramWeightsUp, float* bigramWeightsUp,
  float* unigramWeightsDown, float* bigramWeightsDown,
  float* unigramWeightsFlat, float* bigramWeightsFlat,
  float* unigramWeightsAny, float* bigramWeightsAny ) :
  available(true),
  unigramWeightsUp(unigramWeightsUp), bigramWeightsUp(bigramWeightsUp),
  unigramWeightsDown(unigramWeightsDown), bigramWeightsDown(bigramWeightsDown),
  unigramWeightsFlat(unigramWeightsFlat), bigramWeightsFlat(bigramWeightsFlat),
  unigramWeightsAny(unigramWeightsAny), bigramWeightsAny(bigramWeightsAny) { }
  
WeightVector::~WeightVector() {
  if (available) {
    free(unigramWeightsUp);
    free(bigramWeightsUp);
    free(unigramWeightsDown);
    free(bigramWeightsDown);
    free(unigramWeightsFlat);
    free(bigramWeightsFlat);
    free(unigramWeightsAny);
    free(bigramWeightsAny);
  }
}

inline float WeightVector::computeCost(const edge_type& lastEdgeType, const edge& path,
                                       const bool& changingSameWord,
                                       const monotonicity& monotonicity) const {
  if (!available) { return 0.0; }
  // Case: don't care about monotonicity
  if (path.type > FREEBASE_DOWN) {  // lemma morphs
    return unigramWeightsAny[path.type] * path.cost + (changingSameWord ? bigramWeightsAny[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
  }
  // Case: care about monotonicity
  switch (monotonicity) {
    case MONOTONE_UP:
      return unigramWeightsUp[path.type] * path.cost + (changingSameWord ? bigramWeightsUp[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
    case MONOTONE_DOWN:
      return unigramWeightsDown[path.type] * path.cost + (changingSameWord ? bigramWeightsDown[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
      break;
    case MONOTONE_FLAT:
      return unigramWeightsFlat[path.type] * path.cost + (changingSameWord ? bigramWeightsFlat[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
      break;
    default:
      printf("Unknown monotonicity: %d\n", monotonicity);
      std::exit(1);
      return 0.0f;
  }
}

//
// Function search()
//

// A helper to push elements to the queue en bulk
inline bool flushQueue(SearchType* fringe,
                       const Path* parent,
                       const uint8_t* indexToMutateArr,
                       const tagged_word* sinkArr,
                       const uint8_t* typeArr,
                       const float* costArr,
                       const uint8_t queueLength) {
  uint8_t i = 0;
  while (parent != NULL && i < queueLength) {
    if (fringe->push(parent, indexToMutateArr[i], 1, sinkArr[i], 0, typeArr[i], costArr[i]) == NULL) {
      return false;
    }
    i += 1;
  }
  return true;
}

// The main search() function
vector<scored_path> Search(Graph* graph, FactDB* knownFacts,
                     const tagged_word* queryFact, const uint8_t queryFactLength,
                     SearchType* fringe, CacheStrategy* cache,
                     const WeightVector* weights,
                     const uint64_t timeout) {
  //
  // Setup
  //
  // Create a vector for the return value to occupy
  vector<scored_path> responses;
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
    const Path* parent;
    float costSoFar = fringe->pop(&parent);
    printf("%lu [%f] %s\n", time, costSoFar, toString(*graph, parent->fact, parent->factLength).c_str());
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
    if (knownFacts->contains(parent->fact, parent->factLength)) {
      responses.push_back(scored_path());
      responses[responses.size()-1].path = parent;
      responses[responses.size()-1].cost = costSoFar;
    }

    // -- Mutations --
    // (variables)
    uint8_t parentLength = parent->factLength;
    const uint64_t fixedBitmask[4] = { parent->fixedBitmask[0], parent->fixedBitmask[1], parent->fixedBitmask[2], parent->fixedBitmask[3] };
    const tagged_word* parentFact = parent->fact;  // note: this can change over the course of the search
    // (mutation queue)
    uint8_t indexToMutateArr[256];
    tagged_word sinkArr[256];
    uint8_t typeArr[256];
    float costArr[256];
    uint8_t queueLength = 0;
    // (algorithm)
    for (uint8_t indexToMutate = 0;
         indexToMutate < parentLength;
         ++indexToMutate) {  // for each index to mutate...
      if (isSetBit(fixedBitmask, indexToMutate)) { continue; }
      uint32_t numMutations = 0;
      const edge* mutations = graph->outgoingEdgesFast(parentFact[indexToMutate], &numMutations);
      const monotonicity parentMonotonicity = getMonotonicity(parentFact[indexToMutate]);
      for (int i = 0; i < numMutations; ++i) {
        // Flush if necessary (save memory)
        if (queueLength >= 255) {
          if (!flushQueue(fringe, parent, indexToMutateArr, sinkArr, typeArr, costArr, queueLength)) {
            printf("Error pushing to stack; returning\n");
            return responses;
          }
          queueLength = 0;
        }
        // Add the state to the fringe
        // These are queued up in order to try to protect the cache; the push() call is
        // fairly expensive memory-wise.
        const float mutationCost = weights->computeCost(
            parent->edgeType, mutations[i],
            parent->parent == NULL || parent->lastMutationIndex == indexToMutate,
            parentMonotonicity);
//        printf("  %s --[%s]--> %s (cost %f)\n",
//          graph->gloss(mutations[i].source),
//          toString(mutations[i].type).c_str(),
//          graph->gloss(mutations[i].sink),
//          mutationCost);
        if (mutationCost < 1e10) {
          indexToMutateArr[queueLength] = indexToMutate;
          sinkArr[queueLength] = getTaggedWord(mutations[i].sink, parentMonotonicity);
          typeArr[queueLength] = mutations[i].type;
          costArr[queueLength] = costSoFar + mutationCost;
          queueLength += 1;
        }
      }
    }
    if (!flushQueue(fringe, parent, indexToMutateArr, sinkArr, typeArr, costArr, queueLength)) {
      printf("Error pushing to stack; returning\n");
      return responses;
    }
  }
  return responses;
}




