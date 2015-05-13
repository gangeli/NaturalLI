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
#include "SynSearch.h"

/**
 * Escapes a string, so that it is valid to pass into JSON.
 */
std::string escapeQuote(const std::string& input);

/**
 * Print the string gloss for the given fact.
 */
std::string toString(const Graph& graph, const tagged_word* fact, const uint8_t factSize);

/**
 * Print the string gloss of a tree; this is just the tokens.
 */
std::string toString(const Tree& tree, const Graph& graph);

/**
 * Print the string gloss for the given fact.
 */
inline std::string toString(const Graph& graph, 
                            const std::vector<tagged_word>& fact) {
  tagged_word arr[fact.size()];
  for (uint32_t i = 0; i < fact.size(); ++i) {
    arr[i] = fact[i];
  }
  return toString(graph, arr, fact.size());
}

/**
 * Print the string gloss for the given fact.
 */
std::string toString(const Graph& graph, const Tree& tree);

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
 * Print out the path taken, as a list of user-specified toString
 * entries.
 *
 * @param tree The tree at the END of the path. That is, the query tree.
 * @param path The path from the known fact to the query, in order.
 * @param toString The function to call to convert a path element to a string.
 *
 * @return A vector of string elements corresponding to the path.
 *
 * @see toJSONList
 */
std::vector<std::string> toStringList(
    const Tree& tree,
    const std::vector<SearchNode>& path,
    std::function<std::string(const SearchNode, const std::vector<tagged_word>)> toString);

/**
 * Print out the path taken, as a list of JSON entries.
 */
std::vector<std::string> toJSONList(const Graph& graph,
                                    const Tree& tree,
                                    const std::vector<SearchNode>& path);

/**
 * Print out the path taken, as a JSON list of states.
 *
 * @see toJSONList
 */
std::string toJSON(const Graph& graph,
                   const Tree& tree,
                   const std::vector<SearchNode>& path);

/**
 * Print the gloss of the knowledge base entry corresponding to
 * this path.
 */
std::string kbGloss(const Graph& graph,
                    const Tree& tree,
                    const std::vector<SearchNode>& path);

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
