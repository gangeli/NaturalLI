#ifndef UTILS_H
#define UTILS_H

#include <ctime>
#include <vector>
#include <string>
#include <stdio.h>
#include <stdlib.h>
#include <execinfo.h>
#include <cxxabi.h>
#include <assert.h>

#include <config.h>
#include "Types.h"
#include "Graph.h"
#include "Search.h"
#include "SynSearch.h"

class SearchType;  // C++ doesn't like combiling without these here?
class Path;

/**
 * Print the string gloss for the given fact.
 */
std::string toString(const Graph& graph, const tagged_word* fact, const uint8_t factSize);

/**
 * Print the string gloss for the given fact.
 */
std::string toString(const Graph& graph, const Tree& tree);

/**
 * Print a human readable dump of a search path.
 */
std::string toString(const Graph& graph, SearchType& searchType, const Path* path);

/**
 * Print a human readable dump of a search path.
 */
std::string toString(const Graph& graph, const Tree& tree, const SearchNode& path);

/**
 * Print out the edge type being taken.
 */
std::string toString(const edge_type& edge);

/**
 * Print out elapsed time in days+hours+minutes+seconds.
 */
std::string toString(const time_t& elapsedTime);

/**
 * Index a dependency arc to an integer.
 */
uint8_t indexDependency(const std::string& dependencyAsString);


/**
 * Get the String gloss for an indexed dependency arc.
 */
std::string dependencyGloss(const uint8_t& indexed);

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

/**
 *  The fact (some, dogs, chase, cats)
 */
const std::vector<tagged_word> someDogsChaseCats();

/**
 * stacktrace.h (c) 2008, Timo Bingmann from http://idlebox.net/
 * published under the WTFPL v2.0
 */
void print_stacktrace(FILE *out = stderr, unsigned int max_frames = 63);

/**
 * Convert an edge type to the MacCartney style projection function.
 */
inference_function edge2function(const edge_type& type);

/**
 * atoi() is actually quite slow, and we don't need all its special cases.
 */
inline uint32_t fast_atoi( const char * str ) {
  assert (str[0] > '0' || str[1] == '\0');  // note: no leading zeroes
  uint32_t val = 0;
  char c = *str;
  while( c != '\0' ) {
    assert (str[0] >= '0');
    assert (str[0] <= '9');
    val = val*10 + (c - '0');
    str += 1;
    c = *str;
    if (c == '}') { return val; }
  }
  return val;
}

//
// Print the current time
//
inline void printTime(const char* format) {
  char s[128];
  time_t t = time(NULL);
  struct tm* p = localtime(&t);
  strftime(s, 1000, format, p);
  fprintf(stderr, "%s", s);
}

#define HASH_MAIN_32(data, length) fnv_32a_buf(data, length, FNV1_32_INIT);
#define HASH_AUX_32(data, length) fnv_32a_buf(data, length, 1154);
#define HASH_MAIN_64(data, length) fnv_64a_buf(data, length, FNV1_64_INIT);
#define HASH_AUX_64(data, length) fnv_64a_buf(data, length, 1154);

#endif
