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
std::string toString(const Graph& graph, const word* fact, const uint8_t factSize);

/**
 * Print a human readable dump of a search path.
 */
std::string toString(const Graph& graph, SearchType& searchType, const Path* path);

/**
 * Print out the edge type being taken.
 */
std::string toString(edge_type& edge);

/**
 * Get the word part of a tagged word.
 */
inline word getWord(const tagged_word& w) { return (w << 2) >> 2; }

/**
 * Get the monotonicity part of a tagged word.
 */
inline monotonicity getMonotonicity(const tagged_word& w) { return w >> 30; }

#endif
