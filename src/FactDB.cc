#include <cstdlib>
#include <set>

#include "Postgres.h"
#include "FactDB.h"
#include "Utils.h"


using namespace std;

/**
 * A simple fact database, which simply stores [the hash of]
 * all the facts in memory.
 */
class InMemoryFactDB : public FactDB {
 private:
  const set<int64_t> contents;

 public:
  InMemoryFactDB(set<int64_t>& contents) : contents(contents) { }
  
  virtual const bool contains(const tagged_word* query, const uint8_t queryLength,
                              tagged_word* output, edge_type* canInsertEdge,
                              uint8_t* outputLength) {
    *outputLength = 0;
    // Variables for hashing
    int64_t hash = 0;
    uint32_t shiftIncr = (64 / queryLength);
    uint32_t shift = 0;
    // Hash fact
    for (int i = 0; i < queryLength; ++i) {
      hash = hash ^ getWord(query[i]) << shift;
      shift += shiftIncr;
    }
    // Compute if the fact is contained in the set
    return contents.find(hash) != contents.end();
  }
};


FactDB* ReadFactDB() {
  printf("Reading facts...\n");
  // Read facts
  set<int64_t> facts;
  // (query)
  char factQuery[127];
  snprintf(factQuery, 127, "SELECT gloss, weight FROM %s;", PG_TABLE_FACT.c_str());
  PGIterator iter = PGIterator(factQuery);
  uint64_t i = 0;
  while (iter.hasNext()) {
    facts.insert( atol(iter.next()[0]) );
    i += 1;
    if (i % 10000000 == 0) {
      printf("loaded %luM facts\n", i / 1000000);
    }
  }

  // Return
  printf("%s\n", "Done reading the fact database.");
  return new InMemoryFactDB(facts);
}


/**
 * A simple fact database, which simply stores [the hash of]
 * all the facts in memory.
 */
class MockFactDB : public FactDB {
 public:
  
  virtual const bool contains(const tagged_word* query, const uint8_t queryLength,
                              tagged_word* output, edge_type* canInsertEdge,
                              uint8_t* outputLength) {
    *outputLength = 0;
    return queryLength == 3 && 
      getWord(query[0]) == CAT &&
      getWord(query[1]) == HAVE &&
      getWord(query[2]) == TAIL;
  }
};

FactDB* ReadMockFactDB() {
  return new MockFactDB();
}
