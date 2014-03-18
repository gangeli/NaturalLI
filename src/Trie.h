#ifndef TRIE_H
#define TRIE_H

#include <map>

#include "Config.h"
#include "Bloom.h"
#include "FactDB.h"

class Trie;
class Trie : public FactDB {
 public:
  /** {@inheritDoc} */
  virtual void add(word* elements, uint8_t length);

  /** {@inheritDoc} */
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, uint8_t* canInsertLength);
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, &length);
  }

 private:
  bool leaf;
  std::map<word,Trie> children;

  inline void setIsLeaf(bool leaf) {
    this->leaf = leaf;
  }
  inline bool isLeaf() { return leaf; }
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
                              tagged_word* canInsert, uint8_t* canInsertLength);
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, &length);
  }

 private:
  Trie facts;
  Trie completions;
};

/**
 * Read in the known database of facts as a Trie.
 */
FactDB* ReadFactTrie();

#endif
