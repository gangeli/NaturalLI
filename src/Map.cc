#include <cstring>
#include <stdlib.h>
#include <config.h>

#include "Map.h"


//
// HashIntMap::HashIntMap
//
HashIntMap::HashIntMap(const uint64_t& size) : dataLength(size) {
  this->data = (uint64_t*) malloc(size * sizeof(uint64_t));
  memset(this->data, 0, size * sizeof(uint64_t));
}
  
//
// HashIntMap::HashIntMap
//
HashIntMap::HashIntMap(const HashIntMap& other) : dataLength(other.buckets()) {
  this->data = (uint64_t*) malloc(dataLength * sizeof(uint64_t));
  memcpy(this->data, other.data, dataLength * sizeof(uint64_t));
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
                     const uint32_t& value) {
  uint64_t index = hash % this->dataLength;
  while (!checkChecksum(this->data[index], secondHash)) {
    index = (index + 1) % this->dataLength;
  }
  setChecksum(&(this->data[index]), secondHash);
  setDatum(&(this->data[index]), value);
}

//
// HashIntMap::increment
//
void HashIntMap::increment(const uint32_t& hash,
                           const uint32_t& secondHash) {
  uint64_t index = hash % this->dataLength;
  while (!checkChecksum(this->data[index], secondHash)) {
    index = (index + 1) % this->dataLength;
  }
  setChecksum(&(this->data[index]), secondHash);
  setDatum(&(this->data[index]), getDatum(this->data[index] + 1));
}
 
//
// HashIntMap::get
//
bool HashIntMap::get(const uint32_t& hash,
                     const uint32_t& secondHash,
                     uint32_t* toSet) const {
  uint64_t index = hash % this->dataLength;
  uint64_t checksum = 0;
  do {
    checksum = getChecksum(this->data[index]);
    if (checksum == secondHash) {
      *toSet = getDatum(this->data[index]);
      return true;
    }
    index += 1;
  } while (checksum != 0);
  return false;
}


