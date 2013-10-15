#ifndef FACT_DB_H
#define FACT_DB_H

#include "Config.h"

/**
 * A representation of a database of facts.
 * Importantly, this is used to check if a given fact is already known.
 */
class FactDB {
 public:
  virtual const bool contains(const word*, const uint8_t wordLength) = 0;
};

/**
 * Read in the known database of facts.
 * Depending on various flags, the particular implementation of a FactDB
 * may vary (e.g., in memory, RamCloud, etc).
 */
FactDB* ReadFactDB();

/**
 * Read in a mock database of facts.
 * As of writing this comment, only the fact "cats have tails" is included.
 */
FactDB* ReadMockFactDB();

#endif
