#ifndef SEARCH_H
#define SEARCH_H

#include <queue>

#include "Config.h"
#include "Graph.h"
#include "FactDB.h"

/**
 * A state in the search space.
 * This represents a path from the query fact, to the intermediate
 * state represented by this object.
 * 
 * Backpointers are provided to make this "path element" into a full path.
 */
class Path {
 public:
  const vector<word> fact;
  const edge_type edgeType;

  Path(const uint64_t, const vector<word>&, const edge_type&);
  Path(const Path& source, const vector<word>&, const edge_type&);
  Path(const vector<word>&);
  Path(const Path&);
  ~Path();

  bool operator==(const Path& other) const;

  /**
   * Returns the actual pointer to the source path.
   * The implementation of this function depends on the backend being used
   * (e.g., RamCloud).
   */
  Path* source() const;
 
 private:
  const uint64_t id;
  const uint64_t sourceId;
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
  
  BreadthFirstSearch();
  ~BreadthFirstSearch();

 private:
  queue<Path>* impl;
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
vector<Path*> Search(const Graph*, const FactDB*,
                     const vector<word>&, SearchType*,
                     CacheStrategy*, const uint64_t);



#endif
