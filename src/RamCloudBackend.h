#ifndef RAM_CLOUD_H
#define RAM_CLOUD_H

#include "RamCloud.h"

#include "Graph.h"
#include "FactDB.h"
#include "Search.h"

/**
 * TODO(gabor)
 */
class RamCloudCacheStrategyFactSeen : public CacheStrategy {
 public:
  /**
   * Return true if the latest fact in the path has been visited already.
   */
  virtual bool isSeen(const Path&);
  /**
   * Add this path to the cache.
   */
  virtual void add(const Path&);
};

/**
 * TODO(gabor)
 */
class RamCloudBreadthFirstSearch : public SearchType {
 public:
  virtual void push(const Path&);
  virtual const Path pop();
  virtual bool isEmpty();
};

/**
 * A graph object backed by RamCloud tables.
 */
class RamCloudGraph : public Graph {
 public:
  RamCloudGraph();
  RamCloudGraph(const Graph&);
  ~RamCloudGraph();

  virtual const vector<edge>& outgoingEdges(word) const;
  virtual const string gloss(word) const;
  virtual const vector<word> keys() const;
 
 private:
  RAMCloud::RamCloud ramcloud;
  const uint64_t wordIndexerTable;
  const uint64_t edgesTable;
};

Graph* ReadRamCloudGraph();

/**
 * TODO(gabor)
 */
class RamCloudFactDB : public FactDB {
 public:
  virtual const bool contains(const vector<word>&) const = 0;
};


#endif
