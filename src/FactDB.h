#ifndef FACT_DB_H
#define FACT_DB_H

#include "config.h"
#include "btree_set.h"

/**
 * Appends the given facts to the fact stream.
 *
 * @param factStream The chunk of facts to append.
 * @param count The length of the chunk we are appending.
 * @param kb [output] The knowledge base we are populating.
 */
void appendToKB(const uint64_t* factStream,
                const uint64_t& count,
                btree::btree_set<uint64_t>* kb);



/**
 * Creates a knowledge base from a fixed stream of hashes.
 *
 * @param factStream The facts to add, as an array of hashed facts.
 * @param count The length of the fact stream.
 *
 * @return A set representing the facts in the knowledge base.
 */
inline const btree::btree_set<uint64_t> readKB(
    const uint64_t* factStream, const uint64_t& count) {
  btree::btree_set<uint64_t> kb;
  appendToKB(factStream, count, &kb);
  return kb;
}

/**
 * Reads a knowledge base from a given serialized file. This file should be
 * a simple sequence of hashed values; each 8 bytes represents a fact, followed
 * immediately by the next fact.
 *
 * @param path The path to the file.
 * 
 * @return A set representing the facts in the knowledge base.
 */
const btree::btree_set<uint64_t> readKB(std::string path);

#endif
