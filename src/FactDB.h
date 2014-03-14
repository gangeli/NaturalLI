#ifndef FACT_DB_H
#define FACT_DB_H

#include "Config.h"

/**
 * A representation of a database of facts.
 * Importantly, this is used to check if a given fact is already known.
 */
class FactDB {
 public:
  /**
   * Check if the fact database contains this fact.
   * If it contains the fact, a list of possible insertable facts is
   * returned.
   *
   * @param words            The fact to look up.
   * @param wordLength       The length of the word array to look up
   * @output canInsert       The words which can be inserted to plausibly form a fact.
   * @output canInsertLength The length of the returned canInsert array
   *                         NOTE: the value passed in will be the maximum length
   *                               populated.
   * @return True if the fact is in the database.
   */
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, uint8_t* canInsertLength) = 0;

  /**
   * A helper for checking containment, ignoring the result of
   * the next words which could be inserted.
   */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, &length);
  }
};


/**
 * Read in a mock database of facts.
 * As of writing this comment, only the fact "cats have tails" is included.
 */
FactDB* ReadMockFactDB();

#endif
