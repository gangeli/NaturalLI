#ifndef TRIE_H
#define TRIE_H

#include <limits>
#include "btree_map.h"
#include "btree_set.h"
#include <vector>

#include "Config.h"
#include "Bloom.h"
#include "FactDB.h"

class Trie;

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
  Trie() : data(0), isLeaf(false) { }
  virtual ~Trie();

  /**
   * Like the vanilla add() method, but also keep track of the edge
   * type and words sense that would correspond to each element being inserted.
   */
  virtual void add(edge* elements, uint8_t length);
  
  /** {@inheritDoc} */
  virtual void add(word* elements, uint8_t length) {
    edge edges[length];
    for (int i = 0; i < length; ++i) {
      edge e;
      e.sink  = elements[i];
      e.sense = 0;
      e.type  = ADD_OTHER;
      e.cost  = 1.0f;
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
  uint32_t data;

  /** A marker for whether this node is a leaf node */
  bool     isLeaf;

  /** Register a new edge type to insert */
  inline void registerEdge(edge e) {
    uint8_t type = e.type - EDGE_ADDS_BEGIN;
    const uint32_t bitmask = 0xFF;
    for (uint8_t shift = 0; shift < 32; (shift += 8)) {  
      if ((data & (bitmask << shift)) == 0) {
        data = data | ( (e.sense | (type << 5)) << shift);
        return;
      }
    }
    // Don't register more than 4 senses. This seems like a reasonable limitation...
  }

  /** Get the edge types we can insert */
  inline uint8_t getEdges(edge* buffer) const {
    const uint32_t bitmask = 0xFF;
    uint8_t size = 0;
    for (uint8_t shift = 0; shift < 32; (shift += 8)) {  
      if ((data & (bitmask << shift)) == 0) {
        return size;
      } else {
        edge e;
        uint32_t localData = ((data >> shift) & 0xFF);
        e.sense = (localData & 0x1F);
        e.type  = (localData >> 5) + EDGE_ADDS_BEGIN;
        e.cost  = 1.0f;
        buffer[size] = e;
        size += 1;
      }
    }
    return size;
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
