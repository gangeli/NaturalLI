#ifndef GRAPH_H
#define GRAPH_H

#include <config.h>
#include "Types.h"
#include <vector>

/**
 * Represents the mutation graph, along with the word indexer.
 * That is, for any given query word, it returns the set of valid edges
 * along which that word can mutate (hypernyms, lemma, similar words, etc.).
 */
class Graph {
 public:
  virtual ~Graph() { }

  /** Get all incoming edges from the given word */
  virtual const edge* incomingEdgesFast(const tagged_word& sink, uint32_t* outputLength) const = 0;
  /** For debugging, get the string form of the given word */
  virtual const char* gloss(const tagged_word&) const = 0;
  /** The set of all words in the graph, created as a vector */
  virtual const std::vector<word> keys() const = 0;
  /** Returns whether this edge is a valid deletion */
  virtual const bool containsDeletion(const edge& deletion) const = 0;

  /** A helper to get the outgoing edges in a more reasonable form */
  virtual const std::vector<edge> incomingEdges(const tagged_word& sink) {
    std::vector<edge> rtn;
    uint32_t length = 0;
    const edge* edges = incomingEdgesFast(sink, &length);
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
