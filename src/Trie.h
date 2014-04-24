#ifndef TRIE_H
#define TRIE_H

#include <limits>
#include "btree_map.h"
#include "btree_set.h"
#include <vector>

#include <config.h>
#include "Bloom.h"
#include "FactDB.h"

class Trie;

typedef struct {
  uint8_t sense:5,
          type:3;
}__attribute__((packed)) packed_edge;

typedef struct {
  packed_edge validInsertions[4];
  uint8_t numValidInsertions;
} trie_data;

/**
 * A Trie implementation of a fact database.
 * This is primarily useful as it provides a means of determining possible
 * completions of a candidate element so that it could be a member of the database
 * based on the prefix found so far.
 *
 * Each instance of this class is really a "node" in the Trie.
 * A node stores the children of the node -- that is, what completions exist -- as
 * well as a bit of information on the node itself. This is:
 *   1. Whether this path is a complete path. That is, whether this path exists
 *      in the DB as a fact.
 *   2. A bitmask for the possible insertion edge types for the LAST word in the path.
 *      For example, in the Trie *-the->*-blue->*-cat->[*], the node [*] would have
 *      information on which senses of "cat" could have been inserted.
 */
class Trie : public FactDB {
 public:
  Trie() : isLeaf(false) { 
    data.numValidInsertions = 0;
  }
  virtual ~Trie();

  /**
   * Like the vanilla add() method, but also keep track of the edge
   * type and words sense that would correspond to each element 
   * begin <b>DELETED</b>, which corresponds to an insertion in the
   * reverse search.
   */
  virtual void add(edge* elements, uint8_t length);
  
  /** Insert a given fact. */
  virtual void add(tagged_word* elements, uint8_t length) {
    edge edges[length];
    for (int i = 0; i < length; ++i) {
      edge e;
      e.source       = elements[i].word;
      e.source_sense = elements[i].sense;
      e.sink         = 0;
      e.sink_sense   = 0;
      e.type         = ADD_OTHER;
      e.cost         = 1.0f;
      edges[i] = e;
    }
    add(edges, length);
  }


  /** {@inheritDoc} */
  virtual const bool contains(const tagged_word* words,
                              const uint8_t& wordLength,
                              const int16_t& mutationIndex,
                              edge* insertions) const;
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) const {
    edge edges[MAX_COMPLETIONS];
    return contains(words, wordLength, wordLength - 1, edges);
  }

 private:
  inline void addCompletion(const Trie* child, const word& sink,
                            edge* insertion, uint32_t& index) const;
  
  const bool containsImpl(const tagged_word* words,
                          const uint8_t& wordLength,
                          const int16_t& mutationIndex,
                          edge* insertions,
                          uint32_t& mutableIndex) const;

  /** The core of the Trie. Check if a sequence of [untagged] words is in the Trie. */
  btree::btree_map<word,Trie*> children;

  /** A utility structure for words from here which would complete a fact */
  btree::btree_map<word,Trie*> completions;

  /** A map from grandchild to valid child values. */
  btree::btree_map<word,std::vector<word>> skipGrams;

  /** A compact representation of the data to be stored at this node of the Trie. */
  trie_data data;

  /** A marker for whether this node is a leaf node */
  bool     isLeaf;

  /** Register a new edge type to insert */
  inline void registerEdge(edge e) {
    // Don't register more than 4 senses. This seems like a reasonable limitation...
    assert (data.numValidInsertions <= 4);
    if (data.numValidInsertions == 4) { return; }
    // Set the new sense
    data.validInsertions[data.numValidInsertions].type = e.type - EDGE_ADDS_BEGIN;
    data.validInsertions[data.numValidInsertions].sense = e.source_sense;
    assert (data.validInsertions[data.numValidInsertions].type  < 8);
    assert (data.validInsertions[data.numValidInsertions].sense < 32);
    data.numValidInsertions += 1;
  }

  /** Get the edge types we can insert */
  inline uint8_t getEdges(edge* buffer) const {
    for (uint8_t i = 0; i < data.numValidInsertions; ++i) {
      buffer[i].type         = data.validInsertions[i].type + EDGE_ADDS_BEGIN;
      buffer[i].source_sense = data.validInsertions[i].sense;
      buffer[i].cost         = 1.0f;
      assert (buffer[i].type  >= EDGE_ADDS_BEGIN);
      assert (buffer[i].type  <  EDGE_ADDS_BEGIN + 8);
      assert (buffer[i].source_sense <  32);
    }
    return data.numValidInsertions;
  }
};


/**
 * Read in the known database of facts as a Trie.
 * @param maxFactsToRead Cap the number of facts read to this amount, 
 *        ordered by the fact's weight in the database
 */
FactDB* ReadFactTrie(const uint64_t maxFactsToRead);

/** Read all facts in the database; @see ReadFactTrie(uint64_t) */
inline FactDB* ReadFactTrie() { return ReadFactTrie(std::numeric_limits<uint64_t>::max()); }

#endif
