#ifndef TRIE_H
#define TRIE_H

#include <map>

#include "Config.h"
#include "FactDB.h"

class Trie;
class Trie : public FactDB {
 public:
  /** {@inheritDoc} */
  void add(word* elements, uint8_t length);

  /** {@inheritDoc} */
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, uint8_t* canInsertLength);
  
  /** {@inheritDoc} */
  bool contains(const tagged_word* words, const uint8_t wordLength) {
    uint8_t length = 0;
    return contains(words, wordLength, NULL, &length);
  }

 private:
  uint32_t data;
  std::map<word,Trie> children;

  inline void setIsLeaf(bool isLeaf) {
    data = ((isLeaf ? 0x1 : 0x0) << 31) | data;
  }
  inline word getWord() { return (data << 1) >> 1; }
  inline bool isLeaf() { return (data >> 31) != 0; }
};

#endif
