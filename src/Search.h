#ifndef SEARCH_H
#define SEARCH_H

#include <queue>

#include "Config.h"
#include "Graph.h"
#include "FactDB.h"

#define POOL_BUCKET_SHIFT 20

class SearchType;

/**
 * A state in the search space.
 * This represents a path from the query fact, to the intermediate
 * state represented by this object.
 * 
 * Backpointers are provided to make this "path element" into a full path.
 */
class Path {
 public:
  /** A pointer to the "parent" of this node -- where the search came from */
  const Path* parent;
  /** The actual fact expressed by the node */
  const word* fact;
  /** The length of the fact expressed by the node */
  const uint8_t factLength;
  /** The index that was last mutated, or 255 if this is the first element*/
  const uint8_t lastMutationIndex;
  /**
   * A bitmask for which words can be modified in the fact.
   * An element which is 'fixed' can still be deleted, but cannot be modified
   * any more.
   */
  const uint64_t fixedBitmask[4];  // 256 bits
  /** The type of edge this path was created from */
  edge_type edgeType;

  /** The canonical constructor -- all fields are specified */
  Path(const Path* parentOrNull, const word* fact, uint8_t factLength, edge_type edgeType,
       const uint64_t fixedBitmask[], const uint8_t lastMutationIndex);
  /** A constructor for a root node -- there is no parent, nor edge type */
  Path(const word* fact, uint8_t factLength);
  /** The deconstructor -- this should be largely empty */
  ~Path();

  /** Check if this fact is equal to another fact */
  bool operator==(const Path& other) const;
};

struct PathCompare {
  bool operator()(const Path& lhs, const Path& rhs) {
    return 1 > 2;  // TODO(gabor) implement me!
  }
};

/**
 * An interface for the data structure to store the states on the work queue
 * for the search.
 *
 * The name SearchType comes from the observation that the only relevant
 * difference between various search algorithms is the implementation of this
 * data structure.
 *
 */
class SearchType {
 public:
  /**
   * Push a mutation onto the queue; the address of this element is
   * guaranteed to never change.
   * The returned path is new pushed element.
   */
  virtual const Path* push(const Path* parent, uint8_t mutationIndex,
                    uint8_t replaceLength, word replace1, word replace2,
                    edge_type edge, float cost) = 0;
  virtual float pop(const Path** poppedElement) = 0;
  virtual const Path* peek() = 0;
  virtual bool isEmpty() = 0;

  /** The root of the search */
  const Path* root;
  /** Set the root of the search. This class now owns this pointer. */
  void start(const Path* startState) { root = startState; }
  
  virtual ~SearchType() { }
  
  /**
   * A simple utility to pop an element if you don't care about the score
   */
  const Path* popWithoutScore() {
    const Path* rtn;
    pop(&rtn);
    return rtn;
  }

 protected:
  SearchType() : root(NULL) { } 
};

/**
 * Represents a Breadth First Search strategy. That is, a uniform cost search
 * with cost 1 for every edge taken, which allows for performance improvements.
 */
class BreadthFirstSearch : public SearchType {
 public:
  virtual const Path* push(const Path* parent, uint8_t mutationIndex,
                    uint8_t replaceLength, word replace1, word replace2,
                    edge_type edge, float cost);
  virtual float pop(const Path** poppedElement);
  virtual const Path* peek();
  virtual bool isEmpty();

  /**
   * A debug function to get the ith element of the queue.
   */
  const Path* debugGet(uint64_t i) {
    const uint64_t bucket = i >> POOL_BUCKET_SHIFT;
    const uint64_t offset = i % (0x1 << POOL_BUCKET_SHIFT);
    return &fringe[bucket][offset];
  }
  
  BreadthFirstSearch();
  virtual ~BreadthFirstSearch();

 protected:
  // manage the queue
  Path** fringe;
  Path* currentFringe;
  Path* currentPopFringe;
  uint64_t fringeCapacity;
  uint64_t fringeLength;
  uint64_t fringeI;
  
  // manage the fact memory pool
  uint64_t poolCapacity;
  uint64_t poolLength;
  word** memoryPool;
  word* currentMemoryPool;
  
  // manage 0 element corner case
  bool poppedRoot;

  /**
   * Allocate space for another word in the pool
   */
   inline word* allocateWord(uint8_t toAllocateLength);
   inline Path* allocatePath();
};

/**
 * Represents a Uniform Cost search, making use of the fundamental storage
 * mechanism of BFS, but putting a heap on top of it.
 */
class UniformCostSearch : public BreadthFirstSearch {
 public:
  virtual const Path* push(const Path* parent, uint8_t mutationIndex,
                    uint8_t replaceLength, word replace1, word replace2,
                    edge_type edge, float cost);
  virtual float pop(const Path** poppedElement);
  virtual const Path* peek();
  virtual bool isEmpty();

  UniformCostSearch();
  virtual ~UniformCostSearch();
 
 protected:
  // Implementation of a min-heap
  uint64_t heapSize;
  uint64_t heapCapacity;
  const Path** heap;
  float* costs;

  // Functions for min-heap
  void bubbleUp(const uint64_t index);
  void bubbleDown(const uint64_t index);
};

/**
 * An interface for the structure used to cache already seen paths.
 * 
 * Naively, this amounts to a set of seen facts; however, this can be stored
 * in varous places, and fact equality can be computed in a number of ways.
 * 
 */
class CacheStrategy {
 public:
  virtual bool isSeen(const Path&) = 0;
  virtual void add(const Path&) = 0;
};

/**
 * The simplest cache strategy -- do not cache any seen states.
 * This takes no CPU overhead (beyond the function call), and does not require
 * any memory.
 */
class CacheStrategyNone : public CacheStrategy {
 public:
  virtual bool isSeen(const Path&);
  virtual void add(const Path&);
};

/**
 * Perform a search from the query fact, to any antecedents that can be
 * found by searching through valid edits, insertions, or deletions.
 *
 * Note that the Path* elements in the output will only last as long as the
 * passed-in SearchType, as they are allocated in memory which the SearchType
 * controls.
 * A related note is that the caller of this function should (a) not re-use
 * a SearchType across multiple searches, and (b) delete the SearchType to free
 * the associated memory.
 */
std::vector<const Path*> Search(Graph*, FactDB*,
                     const word* query, const uint8_t queryLength,
                     SearchType*,
                     CacheStrategy*, const uint64_t timeout);



#endif
