#ifndef UTILS_H
#define UTILS_H

#include <vector>
#include <string>

#include <config.h>
#include "Types.h"
#include "Graph.h"
#include "Search.h"

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
 *  The fact (lemur, have, tail)
 */
const std::vector<tagged_word> lemursHaveTails();

/**
 *  The fact (animal, have, tail)
 */
const std::vector<tagged_word> animalsHaveTails();

/**
 *  The fact (cat, have, tail)
 */
const std::vector<tagged_word> catsHaveTails();

#endif
