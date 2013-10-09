#include <set>
#include <cstdlib>

#include "Postgres.h"
#include "FactDB.h"


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
  
  virtual const bool contains(const vector<word>& query) const {
    // Variables for hashing
    int64_t hash = 0;
    uint32_t shiftIncr = (64 / query.size());
    uint32_t shift = 0;
    // Hash fact
    for (int i = 0; i < query.size(); ++i) {
      hash = hash ^ query[i] << shift;
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
  snprintf(factQuery, 127, "SELECT id FROM %s LIMIT 10000000;", PG_TABLE_FACT.c_str());
  session factSession = query(factQuery);
  // (process)
  for (int i = 0; i < factSession.numResults; ++i) {
    if (i % 10000000 == 0) { printf("loaded %iM facts\n", i / 1000000); fflush(stdout); }
    if (PQgetisnull(factSession.result, i, 0)) {
      printf("null row in fact indexer; query=%s\n", factQuery);
      return NULL;
    }
    int64_t key = atoi(PQgetvalue(factSession.result, i, 0));
    facts.insert(key);
  }
  // (clean up)
  release(factSession);

  // Return
  printf("%s\n", "Done reading the fact database.");
  return new InMemoryFactDB(facts);
}
