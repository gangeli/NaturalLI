#include <cstring>
#include <stdlib.h>
#include <stdio.h>
#include <config.h>

#include "Map.h"


//
// HashIntMap::HashIntMap
//
HashIntMap::HashIntMap(const uint64_t& size) : dataLength(size) {
  this->data = (map_entry*) malloc(size * sizeof(map_entry));
  memset(this->data, 0, size * sizeof(map_entry));
}
  
//
// HashIntMap::HashIntMap
//
HashIntMap::HashIntMap(const HashIntMap& other) : dataLength(other.buckets()) {
  this->data = (map_entry*) malloc(dataLength * sizeof(map_entry));
  memcpy(this->data, other.data, dataLength * sizeof(map_entry));
}

//
// HashIntMap::~HashIntMap
//
HashIntMap::~HashIntMap() {
  free(this->data);
}
  
//
// HashIntMap::put
//
void HashIntMap::put(const uint32_t& hash,
                     const uint32_t& secondHash,
                     const uint64_t& value) {
  uint64_t index = hash % this->dataLength;
  uint64_t originalIndex = index;
  // Find a free bucket
  while (!canOccupy(this->data[index], secondHash)) {
    index = (index + 1) % this->dataLength;
    if (index == originalIndex) {
      printf("Overflowed allocated HashIntMap buckets [1]!\n");
      exit(1);
    }
  }
  // Occupy it
  occupy(&(this->data[index]), secondHash, value);
}

//
// HashIntMap::increment
//
void HashIntMap::increment(const uint32_t& hash,
                           const uint32_t& secondHash,
                           const uint64_t& incr,
                           const uint64_t& limit) {
  uint64_t index = hash % this->dataLength;
  uint64_t originalIndex = index;
  // Find a free bucket
  while (!canOccupy(this->data[index], secondHash)) {
    index = (index + 1) % this->dataLength;
    if (index == originalIndex) {
      printf("Overflowed allocated HashIntMap buckets [2]!\n");
      exit(1);
    }
  }
  // Increment the value
  uint64_t value = getDatum(this->data[index]) + incr;
  if (value > limit) { value = limit; }
  // Occupy the bucket
  occupy(&(this->data[index]), secondHash, value);
}
 
//
// HashIntMap::get
//
bool HashIntMap::get(const uint32_t& hash,
                     const uint32_t& secondHash,
                     uint64_t* toSet) const {
  uint64_t index = hash % this->dataLength;
  uint64_t originalIndex = index;
  const uint32_t checksum = (secondHash << __MAP_CHECKSUM_LEFTSHIFT) >> __MAP_CHECKSUM_LEFTSHIFT;

  // If this cell isn't occupied, we're out of luck
  if (!occupied(this->data[index])) { return false; }
  // If it is, let's see if it's the right checksum
  do {
    // We're at index i; check the checksum
    if (getChecksum(this->data[index]) == checksum) {
      // Hooray, it matches!
      *toSet = getDatum(this->data[index]);
      return true;
    }
    // Doesn't match -- continue looking
    index = (index + 1) % this->dataLength;
  } while (occupied(this->data[index]));
  return false;
}


