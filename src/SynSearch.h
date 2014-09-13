#ifndef SEARCH_H
#define SEARCH_H

#include <limits>

#include "config.h"
#include "Types.h"
#include "Graph.h"
#include "knheap/knheap.h"

#ifndef TWO_PASS_HASH
  #define TWO_PASS_HASH 0
#endif
#if TWO_PASS_HASH!=0
  #include "fnv/fnv.h"
#endif


// ----------------------------------------------
// UTILITIES
// ----------------------------------------------
#define TREE_ROOT 63
#define TREE_IS_DELETED(mask, index) (((0x1 << index) & mask) != 0)
#define TREE_DELETE(mask, index) (mask | (0x1 << index))


// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

/**
 * The relevant information on a node of the dependency graph.
 */
#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(6) {
#endif
  tagged_word word;
  uint8_t     governor:6,
              relation:6;
              /* <--4 bits to spare--> */
#ifdef __GNUG__
} __attribute__((packed)) dep_tree_word;
#else
} dep_tree_word;
#endif

/**
 * A packed representation of a dependency edge.
 */
#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(8) {
#endif
  uint32_t governor:25,
           dependent:25;
  uint8_t  relation:6;
#ifdef __GNUG__
} __attribute__((packed)) dependency_edge;
#else
} dependency_edge;
#endif

/**
 * A dependency tree.
 */
class Tree {
 public:
  /**
   * Construct a Tree from an explicit specification
   */
  Tree(const uint8_t& length, 
       const tagged_word* words,
       const uint8_t* parents,
       const uint8_t* relations);
  
  /**
   * Construct a Tree from a stripped-down CoNLL format.
   * This is particularly useful for testing.
   */
  Tree(const std::string& conll);

  /**
   * Find the children of a particular node in the tree.
   * 
   * @param index The index to find children for; zero indexed.
   * @param childrenIndices The buffer to output the children to.
   * @param childRelations The buffer to output the outgoing relations to.
   * @param childrenLength The output to store the number of children in.
   */
  void dependents(const uint8_t& index,
      const uint8_t& maxCount,
      uint8_t* childrenIndices, 
      uint8_t* childRelations,
      uint8_t* childrenLength) const;
  
  /** @see children() above */
  inline void dependents(const uint8_t& index,
      uint8_t* childrenIndices, 
      uint8_t* childRelations,
      uint8_t* childrenLength) const {
    dependents(index, 255, childrenIndices, childRelations, childrenLength);
  }
  
  /**
   * The index of the root of the dependency tree.
   */
  uint8_t root() const;

  /**
   * The tagged word at the given index (zero indexed).
   */
  inline tagged_word word(const uint8_t& index) const {
    return data[index].word;
  }

  /**
   * The index of the parent of a given word (both zero indexed).
   */
  inline uint8_t governor(const uint8_t& index) const {
    return data[index].governor;
  }

  /**
   * The incoming relation to a given index (zero indexed).
   */
  inline uint8_t relation(const uint8_t& index) const {
    return data[index].relation;
  }

  /**
   * Create a mask for the deletions caused by deleting the given
   * word (zero indexed).
   */
  uint32_t createDeleteMask(const uint8_t& root) const;

  /**
   * Checks if this tree is equal to another tree
   */
  bool operator==(const Tree& rhs) const;
  
  /**
   * Checks if this tree is not equal to another tree
   */
  bool operator!=(const Tree& rhs) const { return !(*this == rhs); }

  /**
   * This class is constructed to line up to a cache line, but
   * will never use all of that memory. This is a pointer to the
   * (relatively small) space at the end of this class.
   */
  inline void* cacheSpace() {
    const uint64_t base = (uint64_t) this;
    const uint64_t end  = base + sizeof(*this);
    const uint64_t cache = end - availableCacheLength;
    return (void*) cache;
  }

  /**
   * Compute the hash of the tree.
   * This hash should be word order _independent_,
   * and include not only the dependency arcs but also their
   * labels.
   *
   * In practice, we hash all of the edge triples in the tree:
   * <governor_word, relation, dependent_word>
   */
  uint64_t hash() const;
  
  /**
   * Mutate the given word of the dependency tree, returning the
   * new hash as computed from the old hash and the deletion mask.
   *
   * IMPORTANT NOTE!!!
   *   The tree must be mutated top-to-bottom, or else this function
   *   will not maintain validity.
   *   Specifically, as soon as we are mutating a node whose child has
   *   also mutated, we can no longer accurately "subtract" that edge
   *   from the hash code.
   *
   * @param index The index of the word being mutated
   * @param oldHash The hash of the tree, as it stood before the mutation.
   * @param deletionMask The deletions already performed on this tree.
   * @param oldWord The old word being mutated from.
   * @param newWord The new word we have mutated to.
   *
   * @return The new hash of the Tree, as computed off of oldHash.
   */
  uint64_t updateHashFromMutation(
                                  const uint64_t& oldHash,
                                  const uint32_t& deletionMask,
                                  const uint8_t& index, 
                                  const ::word& oldWord,
                                  const ::word& governor,
                                  const ::word& newWord) const;
  
  /**
   * Update the hash from deleting a particular sub-tree.
   * 
   * IMPORTANT NOTE!!!
   *   As above, the tree must be deleted top-to-bottom, for the
   *   same reasons
   *
   * @param oldHash The old hash code of the tree.
   * @param deletionIndex The index at which we are deleting a subtree.
   * @param deletionWord The word we are deleting at the given index.
   * @param governor The governor of the word at the deletion index.
   * @param newDeletions The deletion mask of new words being deleted.
   */
  uint64_t updateHashFromDeletions(
                                   const uint64_t& oldHash,
                                   const uint8_t& deletionIndex, 
                                   const ::word& deletionWord,
                                   const ::word& governor,
                                   const uint32_t& newDeletions) const;

  /**
   * The number of words in this dependency graph
   */
  const uint8_t length;

  /**
   * The length of the cache at the end of this class
   * 
   * @see Tree::cacheSpace()
   */
  const uint8_t availableCacheLength;

 private:
  /** The actual data for this tree */
  dep_tree_word data[MAX_QUERY_LENGTH];
  
  /** The buffer at the end of the class to fill a cache line */
  uint8_t cache[34];  // if changed, also update availableCacheLength computation

  /** Get the incoming edge at the given index as a struct */
  inline dependency_edge edgeInto(const uint8_t& index,
                                  const ::word& wordAtIndex,
                                  const ::word& governor) const {
    dependency_edge edge;
    edge.governor = governor;
    edge.dependent = wordAtIndex;
    edge.relation = data[index].relation;
    return edge;
  }
  
  /** See edgeInto(index, word, word), but using the tree's known governor */
  inline dependency_edge edgeInto(const uint8_t& index, 
                                  const ::word& wordAtIndex) const {
    const uint8_t governorIndex = data[index].governor;
    if (governorIndex == TREE_ROOT) {
      return edgeInto(index, wordAtIndex, 0x0);
    } else {
      return edgeInto(index, wordAtIndex, data[governorIndex].word.word);
    }
  }

  /** See edgeInto(index, word), but using the tree's known word */
  inline dependency_edge edgeInto(const uint8_t& index) const {
    return edgeInto(index, data[index].word.word);
  }
};



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

  /** A dummy constructor. Don't use this */
  SynPath();
  /** The copy constructor */
  SynPath(const SynPath& from);
  /** The initial node constructor */
  SynPath(const Tree& init);

  /** Compares the priority keys of two path states */
  inline bool operator<=(const SynPath& rhs) const {
    return getPriorityKey() <= rhs.getPriorityKey();
  }
  /** Compares the priority keys of two path states */
  inline bool operator<(const SynPath& rhs) const {
    return getPriorityKey() < rhs.getPriorityKey();
  }
  /** Compares the priority keys of two path states */
  inline bool operator>(const SynPath& rhs) const {
    return !(*this <= rhs);
  }
  /** Compares the priority keys of two path states */
  inline bool operator>=(const SynPath& rhs) const {
    return !(*this < rhs);
  }
  
  /** The assignment operator */
  inline void operator=(const SynPath& from) {
    this->costIfTrue = from.costIfTrue;
    this->costIfFalse = from.costIfFalse;
    this->data = from.data;
    this->backpointer = from.backpointer;
  }

  /**
   * Gets the priority key for this path. That is, the
   * minimum of the true and false costs
   */
  inline float getPriorityKey() const { 
    return costIfFalse < costIfTrue ? costIfFalse : costIfTrue;
  }

  void pushChildren(const Tree& tree, const SynPath& parent,
                    const uint16_t& maxCountToPush, SynPath* buffer) const;

  /** The costs so far of this path */
  float costIfTrue,
        costIfFalse;
 private:
  /** The data stored in this path */
  syn_path_data data;
  /** A backpointer to the path this came from */
  SynPath* backpointer;
};

// ----------------------------------------------
// SEARCH INSTANCE
// ----------------------------------------------

struct syn_search_response {
  std::vector<SynPath> path;
  float cost;
  uint64_t totalTicks;
};

syn_search_response SynSearch(Graph* mutationGraph, Tree* input);


#endif
