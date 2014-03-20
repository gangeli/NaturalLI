#ifndef GRAPH_H
#define GRAPH_H

#include "Config.h"
#include <vector>

/**
 * A simple representation of an Edge in the graph -- that is,
 * a potential mutation of a word.
 */
struct edge {
  word      sink;
  uint8_t   sense;
  edge_type type;
  float     cost;
};

/**
 * Represents the mutation graph, along with the word indexer.
 * That is, for any given query word, it returns the set of valid edges
 * along which that word can mutate (hypernyms, lemma, similar words, etc.).
 */
class Graph {
 public:
  virtual ~Graph() { }

  /** Get all outgoing edges from the given word */
  virtual const edge* outgoingEdgesFast(const tagged_word& source, uint32_t* outputLength) const = 0;
  /** For debugging, get the string form of the given word */
  virtual const char* gloss(const tagged_word&) const = 0;
  /** The set of all words in the graph, created as a vector */
  virtual const std::vector<word> keys() const = 0;

  /** A helper to get the outgoing edges in a more reasonable form */
  virtual const std::vector<edge> outgoingEdges(const tagged_word& source) {
    std::vector<edge> rtn;
    uint32_t length = 0;
    const edge* edges = outgoingEdgesFast(source, &length);
    for (int i = 0; i < length; ++i) {
      rtn.push_back(edges[i]);
    }
    return rtn;
  }
};

/**
 * Read the mutation graph. The actual Graph object returns depends on
 * various flags, optionally storing it in memory, RamCloud, etc.
 */
Graph* ReadGraph();


/**
 * Create a simple, fake graph to use for debugging and testing.
 * At the time of writing this comment, it handled the facts
 * "lemur have tail", "animal have tail", and "cat have tail",
 * with appropriate edges defined
 */
Graph* ReadMockGraph();

#endif
