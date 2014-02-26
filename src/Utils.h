#ifndef UTILS_H
#define UTILS_H

#include <vector>

#include "Config.h"
#include "Graph.h"
#include "Search.h"

/**
 *  The fact (lemur, have, tail)
 */
const std::vector<word> lemursHaveTails();

/**
 *  The fact (animal, have, tail)
 */
const std::vector<word> animalsHaveTails();

/**
 *  The fact (cat, have, tail)
 */
const std::vector<word> catsHaveTails();

/**
 * Print the string gloss for the given fact.
 */
std::string toString(const Graph& graph, const tagged_word* fact, const uint8_t factSize);

/**
 * Print a human readable dump of a search path.
 */
std::string toString(const Graph& graph, SearchType& searchType, const Path* path);

/**
 * Print out the edge type being taken.
 */
std::string toString(const edge_type& edge);

/**
 * Get the word part of a tagged word.
 */
inline word getWord(const tagged_word& w) { return (w << 7) >> 7; }

/**
 * Get the word+sense part of a tagged word.
 */
inline word getWordAndSense(const tagged_word& w) { return (w << 2) >> 2; }

/**
 * Get the monotonicity part of a tagged word.
 */
inline monotonicity getMonotonicity(const tagged_word& w) { return w >> 30; }

/**
 * Get the synset sense of a tagged word.
 */
inline monotonicity getSense(const tagged_word& w) { return (w << 2) >> 25; }

/**
 * Create a tagged word from a word and monotonicity
 */
inline tagged_word getTaggedWord(const word& w, const monotonicity& m) { return (m << 30) | ((w << 7) >> 7); }

/**
 * Create a tagged word from a word, sense, and monotonicity
 */
inline tagged_word getTaggedWord(const word& w, const uint8_t sense, const monotonicity& m) {
  return (m << 30) | ((sense & 0x20) << 25) | ((w << 7) >> 7);
}

#endif
