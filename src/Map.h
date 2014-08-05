#ifndef MAP_H
#define MAP_H

#include <stdint.h>
#include <limits>
#include <assert.h>

#define __MAP_CHECKSUM_LEFTSHIFT 7

#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(8) {
#endif
  uint64_t checksum:25,
           pointer:38;
  bool     occupied:1;
#ifdef __GNUG__
} __attribute__ ((__packed__)) map_entry;
#else
} map_entry;
#endif

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
           const uint64_t& value);
  
  void increment(const uint32_t& hash,
                 const uint32_t& secondHash,
                 const uint64_t& incr,
                 const uint64_t& limit);
  
  inline void increment(const uint32_t& hash,
                        const uint32_t& secondHash,
                        const uint64_t& incr) {
    increment(hash, secondHash, incr, std::numeric_limits<uint32_t>::max());
  }
  
  bool get(const uint32_t& hash,
           const uint32_t& secondHash,
           uint64_t* toSet) const;



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
  map_entry* data;
  uint64_t  dataLength;

  inline uint32_t occupied(const map_entry& cell) const {
    return cell.occupied;
  }
  
  inline uint32_t getChecksum(const map_entry& cell) const {
    return cell.checksum;
  }
  inline void setChecksum(map_entry* cell, const uint32_t& checksum) {
    cell->checksum = checksum;
  }
  
  inline uint64_t getDatum(const map_entry& cell) const {
    return cell.pointer;
  }
  inline void setDatum(map_entry* cell, const uint64_t& datum) {
    assert( ((datum << 25) >> 25) == datum );
    cell->pointer = datum;
  }
  
  inline uint32_t canOccupy(const map_entry& cell, const uint32_t& checksum) const {
    if (!cell.occupied) { return true; }
    uint32_t empiricalChecksum = cell.checksum;
    uint32_t knownChecksum = (checksum << __MAP_CHECKSUM_LEFTSHIFT) >> __MAP_CHECKSUM_LEFTSHIFT;
    return empiricalChecksum == knownChecksum;
  }
  
  inline void occupy(map_entry* cell,
                     const uint32_t& checksum,
                     const uint64_t& datum) {
    assert( canOccupy(*cell, checksum) );
    setChecksum(cell, checksum);
    setDatum(cell, datum);
    cell->occupied = true;
  }
};


#endif
