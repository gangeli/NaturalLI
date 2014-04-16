#ifndef FACT_DB_H
#define FACT_DB_H

#include "Config.h"
#include "Graph.h"
#include <vector>

/**
 * A representation of a database of facts.
 *
 * This class has a few responsibilities:
 *   1. Determine if a fact is in the database. This is the straightforward use case
 *      for the class.
 *   2. Determine where and what words can be inserted to complete a fact in the
 *      database. This is essential, as otherwise any word would be a possible candidate
 *      insertion; or alternately no word would be. The fact database is the entity which
 *      knows which insertions have any chance of eventually being in the database.
 */
class FactDB {
 public:
  virtual ~FactDB() {}

  /**
   * Check if the fact database contains this fact.
   * If it contains the fact, a list of possible insertable facts is
   * returned.
   *
   * @param words         The fact to look up.
   * @param wordLength    The length of the word array to look up.
   * @param mutationIndex The index we are mutating; this is primarly for populating the insertions.
   * @param insertions    [Output] The edges we can insert after the mutation index.
   *
   * @return True if the fact is in the database.
   */
  virtual const bool contains(const tagged_word* words, 
                              const uint8_t& wordLength,
                              const int16_t& mutationIndex,
                              edge* insertions) const = 0;

  /**
   * A helper for checking containment, ignoring the result of
   * the next words which could be inserted.
   *
   * @param words      The query to check.
   * @param wordLength The size of the query
   *
   * @return True if the given query exists in the database.
   */
  bool contains(const tagged_word* words, const uint8_t wordLength) const {
    edge edges[MAX_COMPLETIONS];
    return contains(words, wordLength, wordLength - 1, edges);
  }
};


/**
 * Read in a mock database of facts.
 * As of writing this comment, only the fact "cats have tails" is included.
 * Also note that this database does not support insertions.
 */
FactDB* ReadMockFactDB();

/**
 * No funny business -- just read facts from the database
 */
std::vector<std::vector<word>> ReadLiteralFacts(uint64_t count);

#endif
