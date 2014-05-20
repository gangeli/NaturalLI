#include "fnv/fnv.h"

#include <cstring>
#include <stdlib.h>
#include <config.h>
#include <stdint.h>

#include "Bloom.h"

BloomFilter::BloomFilter() {
  const uint64_t size = 0x1l << 32 >> 6;
  this->bits = (uint64_t*) malloc( size * sizeof(uint64_t) );
  if (this->bits != NULL) {
    memset(this->bits, 0x0, size * sizeof(uint64_t));
  }
}

BloomFilter::~BloomFilter() {
  if (this->bits != NULL) {
    free(this->bits);
  }
}

inline void BloomFilter::setBit(const uint32_t& bit) {
  const uint32_t bucket = bit >> 6;
  const uint32_t offset = (bit << 26) >> 26;
  const uint64_t bitmask = 0x1 << offset;
  if (this->bits != NULL) {
    this->bits[bucket] |= bitmask;
  }
}

inline bool BloomFilter::getBit(const uint32_t& bit) const {
  const uint32_t bucket = bit >> 6;
  const uint32_t offset = (bit << 26) >> 26;
  const uint64_t bitmask = 0x1 << offset;
  if (this->bits != NULL) {
    return this->bits[bucket] & bitmask;
  } else {
    return false;
  }
}

bool BloomFilter::contains(const uint32_t* data, const uint32_t& length) const {
  return getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, FNV1_32_INIT)) &&  // the recommended hash
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 1154)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 2181)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 4230)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 8331)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 16532)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 32933)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 65734)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 131335)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 262536)) &&
         getBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 523937));
}


void BloomFilter::add(const uint32_t* data, const uint32_t& length) {
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, FNV1_32_INIT));  // the recommended hash
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 1154));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 2181));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 4230));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 8331));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 16532));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 32933));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 65734));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 131335));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 262536));
  setBit(fnv_32a_buf(const_cast<uint32_t*>(data), length, 523937));
}
