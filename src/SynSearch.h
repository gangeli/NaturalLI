#ifndef SEARCH_H
#define SEARCH_H

#include <limits>

#include "Types.h"
#include "knheap/knheap.h"

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
  uint32_t    deleteMask:MAX_QUERY_LENGTH;  // 25
  /* <--1 bit to spare here-->*/
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
  SynPath(const float& costIfTrue, const float& costIfFalse);

  inline bool operator<=(const SynPath& rhs) {
    const float minA = costIfTrue < costIfFalse ? costIfTrue : costIfFalse;
    const float minB = rhs.costIfTrue < rhs.costIfFalse ? rhs.costIfTrue : rhs.costIfFalse;
    return minA <= minB;
  }
  
  inline void operator=(const SynPath& from) {
    this->costIfTrue = from.costIfTrue;
    this->costIfFalse = from.costIfFalse;
    this->data = from.data;
    this->backpointer = from.backpointer;
  }

 private:
    syn_path_data data;
    float costIfTrue,
          costIfFalse;
    SynPath* backpointer;
};

const SynPath MIN_SCORE_PATH(0.0, 0.0);
const SynPath MAX_SCORE_PATH(
  std::numeric_limits<float>::infinity(),
  std::numeric_limits<float>::infinity());


class Tree {
 public:

};

#endif
