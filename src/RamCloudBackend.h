#ifndef RAM_CLOUD_H
#define RAM_CLOUD_H

#include "RamCloud.h"

#include "Graph.h"
#include "FactDB.h"
#include "Search.h"

/**
 * A class representing the set of nodes already visited.
 * This is to ensure that we don't return duplicate nodes,
 * and don't do more work in the search than we should.
 * In this implementation, the backend makes use of RamCloud to store
 * seen facts, and a path is identified entirely by its most
 * recent fact.
 */
class RamCloudCacheStrategyFactSeen : public CacheStrategy {
 public:
  RamCloudCacheStrategyFactSeen();
  ~RamCloudCacheStrategyFactSeen();

  /**
   * Return true if the latest fact in the path has been visited already.
   */
  virtual bool isSeen(const Path&);
  /**
   * Add this path to the cache.
   */
  virtual void add(const Path&);
 
 private:
  RAMCloud::RamCloud ramcloud;
  char cacheTableName[64];
  uint64_t cacheTableId;
  bool* TRUE;
};

/**
 * A Queue, implemented in RamCloud
 */
class RamCloudBreadthFirstSearch : public SearchType {
 public:
  RamCloudBreadthFirstSearch();
  ~RamCloudBreadthFirstSearch();

  virtual const Path* push(const Path* parent, uint8_t mutationIndex,
                    uint8_t replaceLength, word replace1, word replace2,
                    edge_type edge);
  virtual const Path* pop();
  virtual bool isEmpty();
 
 private:
  RAMCloud::RamCloud ramcloud;
  char queueTableName[64];
  uint64_t queueTableId;
  uint64_t head;
  uint64_t tail;
};

/**
 * A set containing our known facts, implemented as a RamCloud
 * table.
 */
class RamCloudFactDB : public FactDB {
 public:
  RamCloudFactDB();
  ~RamCloudFactDB();
  virtual const bool contains(const word*, const uint8_t wordLength);

 private:
  const char* factTableName;
  uint64_t factTableId;
  RAMCloud::RamCloud ramcloud;

};

/**
 * Read in the known database of facts to RamCloud
 */
FactDB* ReadRamCloudFactDB();


#endif
