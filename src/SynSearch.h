#ifndef SEARCH_H
#define SEARCH_H

#include <limits>

#include "Types.h"
#include "knheap/knheap.h"

/*
 * TODO(gabor)
 *   - test children and createDeleteMask for Tree
 */

// ----------------------------------------------
// UTILITIES
// ----------------------------------------------
#define TREE_ROOT 63
#define TREE_IS_DELETED(mask, index) (((0x1 << index) & mask) != 0)
#define TREE_DELETE(mask, index) (mask | (0x1 << index))



// ----------------------------------------------
// PATH ELEMENT (SEARCH NODE)
// ----------------------------------------------

/**
 * A packed structure with the information relevant to
 * a path element.
 *
 * This struct should take up 16 bytes.
 */
#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(16) {
#endif
  uint64_t    factHash:64;
  uint8_t     index:5;
  bool        validity:1;
  uint32_t    deleteMask:MAX_QUERY_LENGTH;  // 26
  tagged_word currentToken;
#ifdef __GNUG__
} __attribute__((packed)) syn_path_data;
#else
} syn_path_data;
#endif

/**
 * A state in the search space, making use of the syntactic information
 * in the state.
 *
 * Assuming a Dependency Tree is defined somewhere else, this class
 * keeps track of the deletions made to the tree, the hash of the
 * current tree (for fact lookup), the deletions made to the tree,
 * the value of the current token being mutated, and the validity state
 * of the inference so far. In addition, it keeps track of a
 * backpointer, as well as costs for if this fact ends up being in the
 * "true" state and "false" state.
 *
 * This class fits into 32 bytes; it should not exceed the size of
 * a cache line.
 */
class SynPath {
 public:
  void mutations(SynPath* output, uint64_t* index);
  void deletions(SynPath* output, uint64_t* index);

  SynPath();
  SynPath(const SynPath& from);

  inline bool operator<=(const SynPath& rhs) const {
    return getPriorityKey() <= rhs.getPriorityKey();
  }
  inline bool operator<(const SynPath& rhs) const {
    return getPriorityKey() < rhs.getPriorityKey();
  }
  inline bool operator>(const SynPath& rhs) const {
    return !(*this <= rhs);
  }
  inline bool operator>=(const SynPath& rhs) const {
    return !(*this < rhs);
  }
  
  inline void operator=(const SynPath& from) {
    this->costIfTrue = from.costIfTrue;
    this->costIfFalse = from.costIfFalse;
    this->data = from.data;
    this->backpointer = from.backpointer;
  }

  inline float getPriorityKey() const { 
    return costIfFalse < costIfTrue ? costIfFalse : costIfTrue;
  }


  float costIfTrue,
        costIfFalse;
 private:
  syn_path_data data;
  SynPath* backpointer;
};



// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------
#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(6) {
#endif
  tagged_word word;
  uint8_t     parent:6,
              relation:6;
              /* <--4 bits to spare--> */
#ifdef __GNUG__
} __attribute__((packed)) dep_tree_word;
#else
} dep_tree_word;
#endif

class Tree {
 public:
  Tree(const uint8_t& length, 
       const tagged_word* words,
       const uint8_t* parents,
       const uint8_t* relations);
  
  Tree(const std::string& conll);

  void children(const uint8_t& index,
      uint8_t* childrenIndices, 
      uint8_t* childRelations,
      uint8_t* childrenLength) const;
  
  uint8_t root() const;

  inline tagged_word word(const uint8_t& index) const {
    return data[index].word;
  }

  inline uint8_t parent(const uint8_t& index) const {
    return data[index].parent;
  }

  inline uint8_t relation(const uint8_t& index) const {
    return data[index].relation;
  }

  uint32_t createDeleteMask(const uint8_t& root) const;

  bool operator==(const Tree& rhs) const;
  
  bool operator!=(const Tree& rhs) const { return !(*this == rhs); }

  inline void* cacheSpace() {
    const uint64_t base = (uint64_t) this;
    const uint64_t end  = base + sizeof(*this);
    const uint64_t cache = end - availableCacheLength;
    return (void*) cache;
  }

  const uint8_t length;
  const uint8_t availableCacheLength;
 private:
  dep_tree_word data[MAX_QUERY_LENGTH];
  uint8_t cache[34];  // if changed, also update availableCacheLength computation
};

#endif
