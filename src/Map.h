#ifndef MAP_H
#define MAP_H

#include <stdint.h>
#include <limits>

/**
 * A really funky open-address hash map that's intended to be used by
 * the LossyTrie in Trie.h.
 */
class HashIntMap {
 public:
  HashIntMap(const uint64_t& size);
  HashIntMap(const HashIntMap& other);
  ~HashIntMap();

  void put(const uint32_t& hash,
           const uint32_t& secondHash,
           const uint32_t& value);
  
  void increment(const uint32_t& hash,
                 const uint32_t& secondHash,
                 const uint32_t& incr,
                 const uint32_t& limit);
  
  inline void increment(const uint32_t& hash,
                        const uint32_t& secondHash,
                        const uint32_t& incr) {
    increment(hash, secondHash, incr, std::numeric_limits<uint32_t>::max());
  }
  
  bool get(const uint32_t& hash,
           const uint32_t& secondHash,
           uint32_t* toSet) const;



  inline uint64_t buckets() const { return dataLength; }
  
  inline uint64_t size() const { 
    uint64_t size = 0;
    for (uint64_t i = 0; i < dataLength; ++i) {
      if (getChecksum(data[i]) != 0) { size += 1; }
    }
    return size;
  }
  
  inline uint64_t sum() const { 
    uint64_t sum = 0;
    for (uint64_t i = 0; i < dataLength; ++i) {
      sum += getDatum(data[i]);
    }
    return sum;
  }

  template<typename Functor> inline void mapValues(Functor fn) {
    for (uint64_t i = 0; i < dataLength; ++i) {
      if (getChecksum(data[i]) != 0) { 
        uint32_t newCount = fn(getDatum(data[i]));
        setDatum(&(data[i]), newCount);
      }
    }
  }

 private:
  uint64_t* data;
  uint64_t  dataLength;

  inline uint32_t getChecksum(const uint64_t& cell) const {
    return cell >> 32;
  }
  inline uint32_t checkChecksum(const uint64_t& cell, const uint32_t& checksum) const {
    uint32_t empiricalChecksum = cell >> 32;
    return empiricalChecksum == 0 || empiricalChecksum == checksum;
  }
  inline void setChecksum(uint64_t* cell, const uint32_t& checksum) {
    uint64_t checksumLong = checksum;
    *cell = (*cell) | (checksumLong << 32);
  }
  
  inline uint32_t getDatum(const uint64_t& cell) const {
    return (cell << 32) >> 32;
  }
  inline void setDatum(uint64_t* cell, const uint32_t& datum) {
    uint64_t datumLong = datum;
    *cell = (((*cell) >> 32) << 32) | datumLong;
  }
};


#endif
