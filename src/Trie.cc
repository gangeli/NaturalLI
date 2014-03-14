#include "Trie.h"

void Trie::add(word* elements, uint8_t length) {
  if (length == 0) { 
    setIsLeaf(true);
  } else {
    children[elements[0]].add( &elements[1], length - 1);
  }
}

const bool Trie::contains(const tagged_word* query, const uint8_t queryLength, 
                          tagged_word* canInsert, uint8_t* canInsertLength) {
  if (queryLength == 0) {
    if (*canInsertLength > 0) {
      uint8_t i = 0;
      for (std::map<word,Trie>::iterator iter = children.begin(); 
           iter != children.end();
           ++iter) {
        canInsert[i] = iter->first;
        i += 1;
        if (i > *canInsertLength) { break; }
      }
      *canInsertLength = i;
    }
    return isLeaf();
  } else {
    std::map<word,Trie>::iterator iter = children.find( query[0] );
    if (iter == children.end()) {
      return false;
    } else {
      Trie& child = iter->second;
      return child.contains(&query[1], queryLength - 1, canInsert, canInsertLength);
    }
  }
}
