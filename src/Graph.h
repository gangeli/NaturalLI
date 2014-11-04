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

  /** 
   * Get all incoming edges from the given word.
   * This function ignores the word sense of the sink word; that must be
   * checked by the caller of the function.
   * TODO(gabor) this should not take a tagged word
   * 
   */
  virtual const edge* incomingEdgesFast(const word& sink, uint32_t* outputLength) const = 0;
  /** For debugging, get the string form of the given word */
  virtual const char* gloss(const tagged_word&) const = 0;
  /** The set of all words in the graph, created as a vector */
  virtual const std::vector<word> keys() const = 0;
  /** Returns whether this edge is a valid deletion */
  virtual const bool containsDeletion(const edge& deletion) const = 0;
  /** Returns the vocabulary size */
  virtual const uint64_t vocabSize() const = 0;

  /** A helper to get the outgoing edges in a more reasonable form */
  virtual const std::vector<edge> incomingEdges(const tagged_word& sink) {
    std::vector<edge> rtn;
    uint32_t length = 0;
    const edge* edges = incomingEdgesFast(sink.word, &length);
    for (int i = 0; i < length; ++i) {
      if (edges[i].sink_sense == sink.sense) {
        rtn.push_back(edges[i]);
      }
    }
    return rtn;
  }
};

/**
 * A Graph that also allows for looking up outgoing edges.
 * This class is far less optimized than its single directional 
 * counterpart
 */
class BidirectionalGraph : Graph {
 public:
  BidirectionalGraph(const Graph* impl);
  ~BidirectionalGraph() { 
    delete impl;
  }

  /** Get all outgoing edges from a source. */
  const std::vector<edge> outgoingEdges(const tagged_word& source) const {
    std::vector<edge> rtn;
    for (auto iter = outgoingEdgeData[source.word].begin(); iter != outgoingEdgeData[source.word].end(); ++iter) {
      if (iter->source_sense == source.sense) {
        rtn.push_back(*iter);
      }
    }
    return rtn;
  }
  
  /** {@inheritDoc} */
  virtual const edge* incomingEdgesFast(const word& sink, uint32_t* outputLength) const {
    return impl->incomingEdgesFast(sink, outputLength);
  }
  /** {@inheritDoc} */
  virtual const char* gloss(const tagged_word& token) const {
    return impl->gloss(token);
  }
  /** {@inheritDoc} */
  virtual const std::vector<word> keys() const { return impl->keys(); }
  /** {@inheritDoc} */
  virtual const bool containsDeletion(const edge& deletion) const {
    return impl->containsDeletion(deletion);
  }
  /** {@inheritDoc} */
  virtual const uint64_t vocabSize() const {
    return size;
  }

 public:
  const Graph* impl;

 private:
  const uint64_t size;
  std::vector<std::vector<edge>> outgoingEdgeData;
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
Graph* ReadMockGraph(const bool& allowCycles);

/** @see ReadMockGraph(false) */
inline Graph* ReadMockGraph() { return ReadMockGraph(false); }

#endif
