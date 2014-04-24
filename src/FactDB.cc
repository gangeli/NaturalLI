#include <cstdlib>
#include <set>
#include <sstream>

#include "Postgres.h"
#include "FactDB.h"


using namespace std;

/**
 * A simple fact database, which simply stores [the hash of]
 * all the facts in memory.
 * Note that this database does not support insertions!
 */
class InMemoryFactDB : public FactDB {
 private:
  const set<int64_t> contents;

 public:
  InMemoryFactDB(set<int64_t>& contents) : contents(contents) { }
  
  virtual const bool contains(const tagged_word* query, 
                              const uint8_t& queryLength,
                              const int16_t& mutationIndex,
                              edge* insertions) const {
    insertions[0].source = 0;
    // Variables for hashing
    int64_t hash = 0;
    uint32_t shiftIncr = (64 / queryLength);
    uint32_t shift = 0;
    // Hash fact
    for (int i = 0; i < queryLength; ++i) {
      hash = hash ^ query[i].word << shift;
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
  snprintf(factQuery, 127, "SELECT gloss, weight FROM %s;", PG_TABLE_FACT);
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
  
  virtual const bool contains(const tagged_word* query, 
                              const uint8_t& queryLength,
                              const int16_t& mutationIndex,
                              edge* insertions) const {
    insertions[0].source = 0;
    return queryLength == 3 && 
      query[0].word == CAT.word &&
      query[1].word == HAVE.word &&
      query[2].word == TAIL.word;
  }
};

FactDB* ReadMockFactDB() {
  return new MockFactDB();
}

//
// ReadLiteralFacts
//
vector<vector<word>> ReadLiteralFacts(uint64_t count) {
  vector<vector<word>> facts;
  char query[128];
  snprintf(query, 127,
           "SELECT gloss, weight FROM %s ORDER BY weight DESC LIMIT %lu;",
           PG_TABLE_FACT, count);
  PGIterator iter = PGIterator(query);
  
  while (iter.hasNext()) {
    PGRow row = iter.next();
    const char* gloss = row[0];
    stringstream stream (&gloss[1]);
    vector<word> fact;
    while( stream.good() ) {
      string substr;
      getline( stream, substr, ',' );
      word w = atoi(substr.c_str());
      fact.push_back(w);
    }
    facts.push_back(fact);
  }
  return facts;
}



