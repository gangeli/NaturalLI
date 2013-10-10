#ifndef GRAPH_H
#define GRAPH_H

#include "Config.h"
#include <vector>

/**
 * A simple representation of an Edge in the graph -- that is,
 * a potential mutation of a word.
 */
struct edge {
  word  source;
  word  sink;
  char  type;
  float cost;
};

/**
 * Represents the mutation graph, along with the word indexer.
 * That is, for any given query word, it returns the set of valid edges
 * along which that word can mutate (hypernyms, lemma, similar words, etc.).
 */
class Graph {
 public:
  virtual const vector<edge>& outgoingEdges(word) const = 0;
  virtual const string gloss(word) const = 0;
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
