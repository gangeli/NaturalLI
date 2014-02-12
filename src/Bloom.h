#ifndef BLOOM_H
#define BLOOM_H

#include "Config.h"

/**
 * A fixed 4 giga-bit bloom filter
 */
class BloomFilter {
 public:
  bool contains(const uint32_t* data, const uint32_t& length) const;
  void add(const uint32_t* data, const uint32_t& length);
  BloomFilter();
  ~BloomFilter();
 
 private:
  inline void setBit(const uint32_t& bit);
  inline bool getBit(const uint32_t& bit) const;
  uint64_t* bits;
};

#endif
