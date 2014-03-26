#ifndef TRIE_H
#define TRIE_H

#include <map>
#include <limits>
#include <unordered_map>

#include "Config.h"
#include "Bloom.h"
#include "FactDB.h"

class Trie;
class Trie : public FactDB {
 public:
  /** {@inheritDoc} */
  virtual void add(word* elements, uint8_t length);
  
  /**
   * Like the vanilla add() method, but also keep track of the edge
   * type that would correspond to each element being inserted.
   */
  virtual void add(word* elements, edge_type* edges, uint8_t length);

  /** {@inheritDoc} */
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, edge_type* canInsertEdge,
                              uint8_t* canInsertLength);
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, NULL, &length);
  }

 private:
  uint8_t edgeType;
  std::map<word,Trie> children;

  inline void setIsLeaf(bool leaf) { edgeType = (edgeType | 0x80); }
  inline bool isLeaf() { return (edgeType & 0x80) != 0; }
  inline void setEdgeType(const edge_type& e) { edgeType = ((edgeType & 0x80) | e); }
  inline edge_type getEdgeType() { return edgeType & 0x7F; }
};

class TrieFactDB : public Trie {
 public:
  /** Add a fact with a given weight */
  virtual void add(word* elements, uint8_t length, uint32_t weight);

  /** {@inheritDoc} */
  virtual void add(word* elements, uint8_t length) {
    add(elements, length, MIN_COMPLETION_W);
  }

  /** {@inheritDoc} */
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, edge_type* canInsertEdge,
                              uint8_t* canInsertLength);
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, NULL, &length);
  }

  /** Add a word as a valid insertion */
  void addValidInsertion(const word& word, const edge_type& type);

 private:
  /**
   * Filter valid words from the buffer, and sort the result.
   * @param elements The input elements (query or fact).
   * @param length   The length of the input buffer.
   * @param buffer   The buffer to write the filtered+sorted elements to.
   * @param edges    The output edge types if each of these words were to be inserted.
   * @return         The length of the written buffer.
   */
  const uint8_t filterAndSort(const tagged_word* elements, 
                              uint8_t length, word* buffer,
                              edge_type* edges);

  Trie facts;
  Trie completions;
  std::unordered_map<word,edge_type> validInsertions;
};

/**
 * Read in the known database of facts as a Trie.
 * @param maxFactsToRead Cap the number of facts read to this amount, 
 *        ordered by the fact's weight in the database
 */
FactDB* ReadFactTrie(const uint64_t& maxFactsToRead);

/** Read all facts in the database; @see ReadFactTrie(uint64_t) */
inline FactDB* ReadFactTrie() { return ReadFactTrie(std::numeric_limits<uint64_t>::max()); }

#endif
