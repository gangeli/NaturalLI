#include <cstdio>
#include <cstdlib>
#include <set>
#include <cstring>
#include <ctime>
#include <assert.h>

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
  return (bitmask[bucket] & mask) != 0;
}

/** Set element |index| in the bitmask */
inline void setBit(uint64_t bitmask[], const uint8_t& index) {
  uint8_t bucket = index >> 6;
  uint8_t offset = index % 64;
  uint64_t mask = 0x1 << offset;
  bitmask[bucket] = bitmask[bucket] | mask;
}

//
// Class Path
//
Path::Path(const Path* parentOrNull, 
           const tagged_word* fact, 
           const uint8_t& factLength,
           const edge_type& edgeType,
           const uint8_t& lastMutatedIndex,
           const inference_state& inferState,
           const uint8_t& monotoneBoundary )
    : parent(parentOrNull),
      fact(fact),
      factLength(factLength),
      lastMutationIndex(lastMutatedIndex),
      edgeType(edgeType),
      inferState(inferState),
      monotoneBoundary(monotoneBoundary) {
  assert (monotoneBoundary <= factLength);
}

Path::Path(const tagged_word* fact, const uint8_t& factLength,
           const uint8_t& monotoneBoundary)
    : parent(NULL),
      fact(fact),
      factLength(factLength),
      lastMutationIndex(0),
      edgeType(NULL_EDGE_TYPE),
      inferState(INFER_EQUIVALENT),
      monotoneBoundary(monotoneBoundary) {
  assert (monotoneBoundary <= factLength);
}

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
//  printf("de-allocating BFS; %lu MB freed\n",
//    ((fringeCapacity << POOL_BUCKET_SHIFT)*sizeof(Path) + (poolCapacity << POOL_BUCKET_SHIFT) * sizeof(word)) >> 20);
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

inline const inference_state compose(const inference_state& current,
                                     const inference_state& transition) {
  // TODO(gabor) work out transitions
  return current;
}

// -- push a new element onto the fringe --
inline const Path* BreadthFirstSearch::push(
    const Path* parent, const uint8_t& mutationIndex,
    const uint8_t& replaceLength, const tagged_word& sink, const tagged_word& toInsert,
    const edge_type& edge, const float& cost, const inference_state localInference,
    const CacheStrategy* cache, bool& outOfMemory) {
  outOfMemory = false;

  // Allocate new fact
  // (prototype fact)
  const uint8_t mutatedLength = parent->factLength - 1 + replaceLength;
  assert (mutatedLength > 0);
  tagged_word localMutated[mutatedLength];
  // (mutate fact)
  // Care should be taken in this code.
  // 1. Copy everything before mutation index
  memcpy(localMutated, parent->fact, mutationIndex * sizeof(tagged_word));
  // 2. Do replacement
  switch (replaceLength) {
    case 0:
      // we deleted something; do nothing
      break;
    case 1:
      localMutated[mutationIndex] = sink;
      break;
    case 2:
      if (mutationIndex == parent->factLength) {
        // special case where we are inserting to the end of the fact
        localMutated[mutatedLength - 2] = sink;
        localMutated[mutatedLength - 1] = toInsert;
      } else {
        // standard case
        localMutated[mutationIndex]     = sink;
        localMutated[mutationIndex + 1] = toInsert;
      }
      break;
    default:
      printf("Can only have a replace length of 0, 1, or 2; replace length is %u", replaceLength);
      std::exit(1);
      break;
  }

  // 3. Calculate the new mutation index / monotone boundary
  uint8_t newMutationIndex;
  uint8_t newMonotoneBoundary = parent->monotoneBoundary;
  switch (replaceLength) {
    case 2:
      // on inserts, shift focus to new word
      if (sink.word != parent->fact[mutationIndex].word) {
        newMutationIndex = mutationIndex;
      } else {
        newMutationIndex = mutationIndex + 1;
      }
      // Update monotone boundary
      if (parent->monotoneBoundary > mutationIndex) {
        newMonotoneBoundary = parent->monotoneBoundary + 1;
      }
      break;
    case 0:
      if (mutationIndex >= mutatedLength) {
        // make sure we don't overflow the end of the fact
        newMutationIndex = mutatedLength - 1;
      } else {
        newMutationIndex = mutationIndex;
      }
      // Update monotone boundary
      if (parent->monotoneBoundary > mutationIndex) {
        newMonotoneBoundary = parent->monotoneBoundary - 1;
      }
      break;
    default:
      // by default, don't change anything
      newMutationIndex = mutationIndex;
      break;
  }
  assert (mutatedLength > newMutationIndex);

  // 4. Copy everything after the mutation zone
  if (mutationIndex < parent->factLength - 1) {
    memcpy(&(localMutated[mutationIndex + replaceLength]),
           &(parent->fact[mutationIndex + 1]),
           (parent->factLength - mutationIndex - 1) * sizeof(tagged_word));
  }
  // (check cache)
  if (cache->isSeen(localMutated, mutatedLength, newMutationIndex)) { return NULL; }
  // (allocate fact -- we do this here to avoid allocating cache hits)
  tagged_word* mutated = allocateWord(mutatedLength);
  if (mutated == NULL) { outOfMemory = true; return NULL; }
  // (copy local mutated fact to queue)
  memcpy(mutated, localMutated, mutatedLength * sizeof(tagged_word));

  // 5. Tweak monotonicity
  const uint8_t until = newMonotoneBoundary >= newMutationIndex ? newMonotoneBoundary : mutatedLength;
  switch (edge) {
    case QUANTIFIER_WEAKEN:
    case QUANTIFIER_STRENGTHEN:
    case QUANTIFIER_NEGATE:
      // Flip monotonicity
      for (uint8_t k = newMutationIndex; k < until; ++k) {
        switch (mutated[k].monotonicity) {
          case MONOTONE_UP:
            mutated[k].monotonicity = MONOTONE_DOWN;
            break;
          case MONOTONE_DOWN:
            mutated[k].monotonicity = MONOTONE_UP;
            break;
          default:
            break;
        }
      }
      break;
    default:
      break;
  }

  // Allocate new path
  Path* newPath = allocatePath();
  if (newPath == NULL) { outOfMemory = true; return NULL; }
  assert (mutatedLength > newMutationIndex);
  return new(newPath) Path(parent, mutated, mutatedLength,
                           replaceLength == 1 ? edge : NULL_EDGE_TYPE, 
                           newMutationIndex,
                           compose(parent->inferState, localInference),
                           newMonotoneBoundary);
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
//  printf("de-allocating UCS; %lu MB freed\n", (heapSize * (sizeof(Path*) + sizeof(float))) >> 20);
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
                    const edge_type& edge, const float& cost, 
                    const inference_state localInference,
                    const CacheStrategy* cache, bool& outOfMemory) {
  // Allocate the node
  const Path* node = BreadthFirstSearch::push(parent, mutationIndex, replaceLength, replace1, replace2, edge, cost, localInference, cache, outOfMemory);
  if (node == NULL) { return NULL; }
  // Ensure we have space on the heap
  if (heapSize >= heapCapacity) {
    uint64_t newCapacity = heapCapacity << 1;
    const Path** newHeap = (const Path**) malloc(newCapacity * sizeof(Path*));
    if (newHeap == NULL) { outOfMemory = true; return NULL; }
    float* newCosts = (float*) malloc(newCapacity * sizeof(float*));
    if (newCosts == NULL) { outOfMemory = true; return NULL; }
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
bool CacheStrategyNone::isSeen(const tagged_word* fact, const uint8_t& factLength,
                               const uint32_t& additionalFlags) const {
  return false;
}

void CacheStrategyNone::add(const tagged_word* fact, const uint8_t& factLength,
                            const uint32_t& additionalFlags) { }

//
// Class CacheStrategyBloom
//
bool CacheStrategyBloom::isSeen(const tagged_word* fact, const uint8_t& factLength,
                                const uint32_t& additionalFlags) const {
  word words[factLength + 1];
  words[0] = additionalFlags;
  for (uint32_t i = 0; i < factLength; ++i) {
    words[i + 1] = fact[i].word;
  }
  return filter.contains(words, 4 * ((uint32_t) factLength + 1));
}

void CacheStrategyBloom::add(const tagged_word* fact, const uint8_t& factLength,
                             const uint32_t& additionalFlags) {
  word words[factLength + 1];
  words[0] = additionalFlags;
  for (uint32_t i = 0; i < factLength; ++i) {
    words[i + 1] = fact[i].word;
  }
  filter.add(words, 4 * ((uint32_t) factLength + 1));
}

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
  const float pathCost = path.cost == 0.0 ? 1e-10 : path.cost;
  if (path.type >= MONOTONE_INDEPENDENT_BEGIN) {  // lemma morphs
    const float anyCost = unigramWeightsAny[path.type] * pathCost;
    return lastEdgeType == NULL_EDGE_TYPE ? anyCost : anyCost + 
        (changingSameWord ? bigramWeightsAny[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
  }
  // Case: care about monotonicity
  float upCost, downCost, flatCost;
  switch (monotonicity) {
    case MONOTONE_UP:
      upCost = unigramWeightsUp[path.type] * pathCost;
      return lastEdgeType == NULL_EDGE_TYPE ? upCost : upCost + 
          (changingSameWord ? bigramWeightsUp[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
    case MONOTONE_DOWN:
      downCost = unigramWeightsDown[path.type] * pathCost;
      return lastEdgeType == NULL_EDGE_TYPE ? downCost : downCost +
          (changingSameWord ? bigramWeightsDown[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
    case MONOTONE_FLAT:
      flatCost = unigramWeightsFlat[path.type] * pathCost;
      return lastEdgeType == NULL_EDGE_TYPE ? flatCost : flatCost +
          (changingSameWord ? bigramWeightsFlat[((uint64_t) lastEdgeType) * NUM_EDGE_TYPES + path.type] : 0.0f);
    default:
      printf("Unknown monotonicity: %d\n", monotonicity);
      std::exit(1);
      return 0.0f;
  }
}

/**
 * A simple little function to create a new search response.
 */
inline search_response mkResponse(const vector<scored_path>& paths, 
                          const uint64_t& numTicks) {
  search_response rtn;
  rtn.paths = paths;
  rtn.totalTicks = numTicks;
  return rtn;
}

//
// Function Search()
//
search_response Search(Graph* graph, FactDB* knownFacts,
                       const tagged_word* queryFact, const uint8_t& queryFactLength,
                       const uint8_t& monotoneBoundary,
                       SearchType* fringe, CacheStrategy* cache,
                       const WeightVector* weights,
                       const uint64_t& timeout) {
  //
  // Setup
  //
  // -- Initialize state --
  // Create a vector for the return value to occupy
  vector<scored_path> responses;
  // Initialize timer (number of elements popped from the fringe)
  uint64_t time = 0;
  const uint32_t tickTime = 10000;
  std::clock_t startTime = std::clock();
  bool oom = false;
  // Initialize add-able words
  edge inserts[MAX_COMPLETIONS];
  for (int i = 0; i < MAX_COMPLETIONS; ++i) { inserts[i].sink = 0; }
  
  // -- Initialize fringe--
  // Create the start state
  fringe->root = new Path(queryFact, queryFactLength, monotoneBoundary);  // I need the memory to not go away

  //
  // Search
  //
  while (!fringe->isEmpty() && time < timeout) {
    // -- Get the next element from the fringe --
    const Path* parent;
    const float costSoFar = fringe->pop(&parent);
    if (cache->isSeen(parent->fact, parent->factLength,
        parent->lastMutationIndex)) {
      continue;
    }
    cache->add(parent->fact, parent->factLength, 
               parent->lastMutationIndex);

    // -- Debug Output --
//    printf("%lu [%f] %s [index:%d]\n", time, costSoFar, toString(*graph, parent->fact, parent->factLength).c_str(), parent->lastMutationIndex);
    // Update time
    time += 1;
    if (time % tickTime == 0) {
      const uint32_t tickOOM
        = tickTime < 1000 ? 1 : (tickTime < 1000000 ? 1000 : (tickTime  < 1000000000 ? 1000000 : 1) );
      printf("[%lu%s / %lu%s search tick]; %lu paths found (%lu ms elapsed; cost@%f)\n",
             time / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             timeout / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 999999 ? "k" : (tickTime  < 999999999 ? "m" : "") ),
             responses.size(),
             (uint64_t) (1000.0 * ( std::clock() - startTime ) / ((double) CLOCKS_PER_SEC)),
             costSoFar);
    }

    // -- Check If Valid --
    bool isCorrectFact = false;
    assert (parent->factLength > parent->lastMutationIndex);
    if (knownFacts->contains(parent->fact, parent->factLength,
                             parent->lastMutationIndex, inserts)) {
      responses.push_back(scored_path());
      responses[responses.size()-1].path     = parent;
      responses[responses.size()-1].cost     = costSoFar;
      responses[responses.size()-1].numTicks = time;
      isCorrectFact = true;
    }

    // -- Push Children --
    // (variables)
    const uint8_t& parentLength = parent->factLength;
    const tagged_word* parentFact = parent->fact;
    const uint8_t& indexToMutate = parent->lastMutationIndex;
    const tagged_word& parentWord = parentFact[indexToMutate == parentLength ? parentLength - 1 : indexToMutate];
    const monotonicity& parentMonotonicity = parentWord.monotonicity;

    // Do post-insertions
    if (parentLength < MAX_FACT_LENGTH) {
      for (uint8_t insertI = 0; insertI < MAX_COMPLETIONS; ++insertI) {
        // Make sure the insert exists
        if (inserts[insertI].sink == 0) { break; }
        const edge& insertion = inserts[insertI];
        // Add the state to the fringe
        const float insertionCost = weights->computeCost(
            NULL_EDGE_TYPE, insertion, false, parentMonotonicity);
        if (insertionCost < 1e10) {
          // push insertion
          fringe->push(parent, indexToMutate,
            2, parentWord, getTaggedWord(insertion.sink, insertion.sense, parentMonotonicity),
            insertion.type, costSoFar + insertionCost, INFER_FORWARD_ENTAILMENT, cache, oom);
          if (oom) { printf("Error pushing to stack; returning\n"); return mkResponse(responses, time); }
        }
      }
    }

    // Do pre-insertions (for index=0 only)
    if (parent->lastMutationIndex == 0 && parentLength < MAX_FACT_LENGTH) {
      // Re-check insertions (though only to depth 1, for efficiency)
      // NOTE: This must be after post-insertions, else inserts gets overwritten!
      knownFacts->contains(parent->fact, 1, -1, inserts);
      // Actually execute insertions
      for (uint8_t insertI = 0; insertI < MAX_COMPLETIONS; ++insertI) {
        // Make sure the insert exists
        if (inserts[insertI].sink == 0) { break; }
        const edge& insertion = inserts[insertI];
        // Add the state to the fringe
        const float insertionCost = weights->computeCost(
            NULL_EDGE_TYPE, insertion, false, parentMonotonicity);
        if (insertionCost < 1e10) {
          // push insertion
          fringe->push(parent, 0,
            2, getTaggedWord(insertion.sink, insertion.sense, parentMonotonicity), parentWord,
            insertion.type, costSoFar + insertionCost, INFER_FORWARD_ENTAILMENT, cache, oom);
          if (oom) { printf("Error pushing to stack; returning\n"); return mkResponse(responses, time); }
        }
      }
    }

    // Do mutations
    uint32_t numMutations = 0;
    const edge* mutations = graph->outgoingEdgesFast(parentWord, &numMutations);
    const uint8_t& parentSense = parentWord.sense;
    for (int i = 0; i < numMutations; ++i) {
      const edge& mutation = mutations[i];
      if (mutation.sense != parentSense) {
        continue;
      }
      // Add the state to the fringe
      const float mutationCost = weights->computeCost(
          parent->edgeType, mutation,
          parent->parent == NULL || parent->edgeType == NULL_EDGE_TYPE,
          parentMonotonicity);
//      printf("  mutate %u -> %u at %u cost %f  type=(%u,%u), unigramUp=%f, unigramDown=%f\n",
//             parentFact[indexToMutate].word, mutation.sink, indexToMutate, mutationCost,
//             parent->edgeType, mutation.type, weights->unigramWeightsUp[mutation.type], weights->unigramWeightsDown[mutation.type] );
      if (mutationCost < 1e10 && (parentLength > 1 || mutation.sink != 0)) {
        // push mutation[/deletion]
        fringe->push(parent, indexToMutate,
          mutation.sink == 0 ? 0 : 1,
          getTaggedWord(mutation.sink, mutation.sense, parentMonotonicity), NULL_WORD,
          mutation.type, costSoFar + mutationCost, INFER_FORWARD_ENTAILMENT, cache, oom);
        if (oom) { printf("Error pushing to stack; returning\n"); return mkResponse(responses, time); }
      }
    }
        
    // Do index shift
    if (parent->lastMutationIndex < parentLength - 1 && !isCorrectFact) {
      fringe->push(parent, indexToMutate + 1, 1,
        parent->fact[parent->lastMutationIndex + 1], NULL_WORD,
        NULL_EDGE_TYPE, costSoFar, INFER_FORWARD_ENTAILMENT, cache, oom);
      if (oom) { printf("Error pushing to stack; returning\n"); return mkResponse(responses, time); }
    }
  }

  //
  // Return
  //
  const uint64_t searchTimeMS = (uint64_t) (1000.0 * ( std::clock() - startTime ) / ((double) CLOCKS_PER_SEC));
  if (time > 100) {  // my OCD trying to get the tests to not print anything
    printf("end search (%lu ms); %lu ticks yielded %lu results\n", searchTimeMS, time, responses.size());
  }
  return mkResponse(responses, time);
}




