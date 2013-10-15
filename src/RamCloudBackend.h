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
