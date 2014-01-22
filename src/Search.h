#ifndef SEARCH_H
#define SEARCH_H

#include <queue>

#include "Config.h"
#include "Graph.h"
#include "FactDB.h"

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
  uint64_t id;
  uint64_t sourceId;
  word fact[256];
  uint8_t factLength;
  edge_type edgeType;

  Path(const uint64_t, const uint64_t, const word*, const uint8_t,
       const edge_type&);
  Path(const uint64_t, const word*, const uint8_t, const edge_type&);
  Path(const Path& source, const word*, const uint8_t, const edge_type&);
  Path(const word*, const uint8_t);
  Path(const Path&);
  ~Path();

  void operator=(const Path& copy);
  bool operator==(const Path& other) const;

  /**
   * Returns the actual pointer to the source path.
   * The implementation of this function depends on the backend being used
   * (e.g., RamCloud).
   */
  Path* source(SearchType& queue) const;
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
  virtual void push(const Path&) = 0;
  virtual const Path pop() = 0;
  virtual bool isEmpty() = 0;
  virtual Path* findPathById(uint64_t id) = 0;
};

/**
 * Represents a Breadth First Search strategy. That is, a uniform cost search
 * with cost 1 for every edge taken, which allows for performance improvements.
 */
class BreadthFirstSearch : public SearchType {
 public:
  virtual void push(const Path&);
  virtual const Path pop();
  virtual bool isEmpty();
  virtual Path* findPathById(uint64_t id);
  
  BreadthFirstSearch();
  ~BreadthFirstSearch();

 private:
  queue<Path>* impl;
  vector<Path> id2path;
};

/**
 * Represents a uniform cost search (Djkstra's).
 */
class UCSSearch : public SearchType {
 public:
  virtual void push(const Path&);
  virtual const Path pop();
  virtual bool isEmpty();
  virtual Path* findPathById(uint64_t id);
  
  UCSSearch();
  ~UCSSearch();

 private:
  priority_queue<Path, vector<Path>, PathCompare>* impl;
  vector<Path> id2path;
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
 */
vector<Path*> Search(Graph*, FactDB*,
                     const word*, const uint8_t,
                     SearchType*,
                     CacheStrategy*, const uint64_t);



#endif
