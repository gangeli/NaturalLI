#ifndef SYN_SEARCH_H
#define SYN_SEARCH_H

#include <limits>
#include <bitset>

#include "config.h"
#include "Types.h"
#include "Graph.h"
#include "knheap/knheap.h"
#include "btree_set.h"
#include "Models.h"

// Ensure definitions
#ifndef TWO_PASS_HASH
  #define TWO_PASS_HASH 0
#endif
#ifndef SEARCH_CYCLE_MEMORY
  #define SEARCH_CYCLE_MEMORY 0
#endif
#ifndef SEARCH_FULL_MEMORY
  #define SEARCH_FULL_MEMORY 0
#endif

// Conditional includes
#if TWO_PASS_HASH!=0
  #include "fnv/fnv.h"
#endif

class Tree;
class SearchNode;

// ----------------------------------------------
// NATURAL LOGIC
// ----------------------------------------------

/**
 * Translate from edge type to the Natural Logic lexical function
 * that edge type maps to.
 *
 * @param edge The edge type being traversed.

 * @return The Natural Logic relation corresponding to that edge.
 */
inline natlog_relation edgeToLexicalFunction(const natlog_relation& edge) {
  switch (edge) {
    // WordNet
    case HYPERNYM:             return FUNCTION_FORWARD_ENTAILMENT;
    case HYPONYM:              return FUNCTION_REVERSE_ENTAILMENT;
    case ANTONYM:              return FUNCTION_ALTERNATION;
    case SYNONYM:              return FUNCTION_EQUIVALENT;
    // Freebase
    case HOLONYM:              return FUNCTION_FORWARD_ENTAILMENT;
    case MERONYM:              return FUNCTION_REVERSE_ENTAILMENT;
    // Nearest Neighbors
    case ANGLE_NN:             return FUNCTION_EQUIVALENT;
    // WordNet Similar
    case SIMILAR:              return FUNCTION_EQUIVALENT;
    // Quantifier Morphs
    case QUANTIFIER_UP:        return FUNCTION_FORWARD_ENTAILMENT;
    case QUANTIFIER_DOWN:      return FUNCTION_REVERSE_ENTAILMENT;
    case QUANTIFIER_NEGATE:    return FUNCTION_NEGATION;
    case QUANTIFIER_REWORD:    return FUNCTION_EQUIVALENT;
    // Sense shifts
    case SENSE_ADD:            return FUNCTION_EQUIVALENT;
    case SENSE_REMOVE:         return FUNCTION_EQUIVALENT;
    // Verb entailment
    case VERB_ENTAIL:          return FUNCTION_FORWARD_ENTAILMENT;
    default:
      fprintf(stderr, "No such edge: %u\n", edge);
      std::exit(1);
      return 255;
  }
}

/**
 * Translate inserting a given Stanford Dependency to the Natural Logic
 * relation introduced.
 *
 * For example, inserting the relation 'amod' onto the term 'meat'
 * to yield 'red <--amod-- meat' yields the relation:
 *
 *    'meat' \reverse 'red meat' ('meat' > 'red meat')
 * 
 * and therefore should return FUNCTION_REVERSE_ENTAILMENT.
 *
 * @param dep The stanford dependency being inserted.
 *
 * @return The Natural Logic relation expressed.
 */
natlog_relation dependencyInsertToLexicalFunction(const dep_label& dep,
                                                  const ::word& dependent);

/**
 * The inverse of dependencyInsertToLexicalFunction()
 */
inline natlog_relation dependencyDeleteToLexicalFunction(const dep_label& dep,
                                                         const ::word& dependent) {
  natlog_relation insertRelation = dependencyInsertToLexicalFunction(dep, dependent);
  switch (insertRelation) {
    case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
    case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
    case FUNCTION_EQUIVALENT:         return FUNCTION_EQUIVALENT;
    case FUNCTION_NEGATION:           return FUNCTION_NEGATION;
    case FUNCTION_ALTERNATION:        return FUNCTION_COVER;
    case FUNCTION_COVER:              return FUNCTION_ALTERNATION;
    case FUNCTION_INDEPENDENCE:       return FUNCTION_INDEPENDENCE;
    default:
      fprintf(stderr, "No such natlog relation: %u\n", insertRelation);
      std::exit(1);
      return 255;
  }
}

/**
 * Project a lexical relation through a quantifier type
 * (multiplicative, additive, etc.), given the monotonicity of that
 * position in the tree with respect to the quantifier being projected
 * through.
 *
 * @param monotonicity The monotonicity of the argument with respect to
 *        the quantifier being projected through.
 * @param quantifierType The type of quantifier being projected through,
 *                       as per Icard's scheme.
 * @param lexicalFunction The lexical function we are projecting.
 *
 * @return The projected function.
 */
 // don't inline! Gets called nested a lot
natlog_relation project(const monotonicity& monotonicity,
                        const quantifier_type& quantifierType,
                        const natlog_relation& lexicalFunction);

/**
 * The hard state assignment from the reverse traversal of the
 * NatLog FSA. For instance, if the end state is 'true' and the
 * projected relation is 'cover', then the only way to get to 'true'
 * through cover is from the 'false' state.
 *
 * @param endState The end of the FSA transition; this is the current
 *                  state from the perspective of search.
 * @param projectedRelation The projected relation over the transition.
 *
 * @return The hard state assignment we have transitioned to.
 */
bool reverseTransition(const bool& endState,
                       const natlog_relation projectedRelation);

/**
 * A class encapsulating the costs incurred during a search instance.
 */
class SynSearchCosts {
 public:
  /** The cost of a mutation */
  float mutationCost(const Tree& tree,
                     const SearchNode& currentNode,
                     const uint8_t& edgeType,
                     const bool& endTruthValue,
                     bool* beginTruthValue) const;
  
  /** The cost of an insertion (deletion in search) */
  float insertionCost(const Tree& tree,
                      const SearchNode& governor,
                      const dep_label& dependencyLabel,
                      const ::word& dependent,
                      const bool& endTruthValue,
                      bool* beginTruthValue) const;
  
  /** The cost of a deletion (insertion in search) */
  float deletionCost(const Tree& tree,
                     const SearchNode& governor,
                     const dep_label& dependencyLabel,
                     const ::word& dependent,
                     const bool& endTruthValue,
                     bool* beginTruthValue) const;

  float mutationLexicalCost[NUM_MUTATION_TYPES + 1];  // + 1 to allow for dumping parse errors into the null cost
  float insertionLexicalCost[NUM_DEPENDENCY_LABELS + 1];
  float transitionCostFromTrue[8 + 1];
  float transitionCostFromFalse[8 + 1];
};

/**
 * Create a standard set of NatLog weights.
 * This is useful as an initialization point for learning.
 *
 * @param smallConstantCost A small constant cost for all transitions.
 * @param okCost A cost to be added to the smallConstantCost for an OK
 *               transition.
 * @param badCost A cost to be added to the smallConstantCost for a bad
 *                transition.
 *
 * @return A search cost vector representing these weights.
 */
SynSearchCosts* createStrictCosts(const float& smallConstantCost,
                                  const float& okCost,
                                  const float& badCost);

/** Create a version of the weights encoding strict natural logic inference */
inline SynSearchCosts* strictNaturalLogicCosts() {
  return createStrictCosts(0.01f, 0.0f, std::numeric_limits<float>::infinity());
}

/** Create a version of the weights encoding soft natural logic inference */
inline SynSearchCosts* softNaturalLogicCosts() {
  return createStrictCosts(0.01f, 0.1f, 0.25);
}

// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

#define TREE_ROOT 63
#define TREE_ROOT_WORD 0x0
#define TREE_IS_DELETED(mask, index) (((0x1 << index) & mask) != 0)
#define TREE_DELETE(mask, index) (mask | (0x1 << index))

#define MONOPAIR_UP_UP        0
#define MONOPAIR_UP_DOWN      1
#define MONOPAIR_UP_FLAT      2
#define MONOPAIR_DOWN_UP      3
#define MONOPAIR_DOWN_DOWN    4
#define MONOPAIR_DOWN_FLAT    5
#define MONOPAIR_FLAT_UP      6
#define MONOPAIR_FLAT_DOWN    7


/**
 * Information about a quantifier.
 */
#ifdef __GNUG__
struct quantifier_monotonicity {
#else
struct alignas(1) quantifier_monotonicity {
#endif
  uint8_t subj_mono:2,
          subj_type:2,
          obj_mono:2,
          obj_type:2;

  inline bool operator==(const quantifier_monotonicity& a) const{
    return subj_mono == a.subj_mono &&
      subj_type == a.subj_type &&
      obj_mono == a.obj_mono &&
      obj_type == a.obj_type;
  }
  inline bool operator!=(const quantifier_monotonicity& a) const{
    return subj_mono != a.subj_mono ||
      subj_type != a.subj_type ||
      obj_mono != a.obj_mono ||
      obj_type != a.obj_type;
  }
  inline void clear() {
    subj_mono = MONOTONE_UP;
    subj_type = QUANTIFIER_TYPE_BOTH;
    obj_mono = MONOTONE_UP;
    obj_type = QUANTIFIER_TYPE_BOTH;
  }
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif

#ifdef __GNUG__
struct quantifier_span {
#else
struct alignas(4) quantifier_span {
#endif
  uint8_t subj_begin:5,
          subj_end:5,
          obj_begin:5,
          obj_end:5,
          quantifier_index;
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif


/**
 * The relevant information on a node of the dependency graph.
 */
#ifdef __GNUG__
struct dep_tree_word {
#else
struct alignas(6) dep_tree_word {
#endif
  ::word        word:VOCABULARY_ENTROPY;  // 24
  uint8_t       sense:SENSE_ENTROPY,      // 5
                governor:6,
                relation:DEPENDENCY_ENTROPY;
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif

/**
 * A packed representation of a dependency edge.
 */
#ifdef __GNUG__
struct dependency_edge {
#else
struct dependency_edge alignas(8) {
#endif
  uint32_t   governor:VOCABULARY_ENTROPY,
             dependent:VOCABULARY_ENTROPY;
  dep_label  relation:DEPENDENCY_ENTROPY;
  uint8_t    placeholder:8;  // <-- should be zero

  dependency_edge(const uint32_t& governor,
                  const uint32_t& dependent,
                  const dep_label& relation
                  ) : placeholder(0), governor(governor), dependent(dependent), relation(relation) { }
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif

/**
 * A dependency tree.
 */
class Tree {
 friend class SearchNode;
 public:
  /**
   * Construct a Tree from a stripped-down CoNLL format.
   * The CoNLL format, in particular, is:
   * 
   * <table>
   * <tr> <td> Column </td> <td> Description </td> </tr>
   * <tr> <td>   1    </td> <td> The word at this token index, as an integer. </td> </tr>
   * <tr> <td>   2    </td> <td> The governor of this word, 1 indexed. </td> </tr>
   * <tr> <td>   3    </td> <td> The dependency label of the incoming arc, which must match an arc in Models.h </td> </tr>
   * <tr> <td>   4    </td> <td> The word sense of this word, with 0 denoting the general (or unknown) sense. </td> </tr>
   * <tr> <td>   5    </td> <td> The monotonicity spec of the subject (e.g., anti-additive), if this is a quantifier. Otherwise, '-' denotes that this is not a quantifier. </td> </tr>
   * <tr> <td>   6    </td> <td> The span of the subject, if this is a quantifier. Otherwise, '-'. </td> </tr>
   * <tr> <td>   7    </td> <td> The monotonicity spec of the object (e.g., anti-additive), if this is a quantifier. Otherwise, '-' denotes that this is not a quantifier. </td> </tr>
   * <tr> <td>   8    </td> <td> The span of the object, if this is a quantifier. Otherwise, '-'. </td> </tr>
   * <tr> <td>   9    </td> <td> Optional additional flags, as a string of (case-insensitive) characters. </td> </tr>
   * </table>
   *
   * <p>
   *   A valid tree should either have colums 1-4, 1-8, or 1-9.
   *   Any other colum combinations are invalid.
   * </p>
   *
   * <p>
   *  The set of valid optional flags are:
   * </p>
   * <ul>
   *   <li>'l': This is a location that can partake in meronymy edges.</li>
   * <ul>
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
      dep_label* childRelations,
      uint8_t* childrenLength) const;
  
  /** @see dependents() above */
  inline void dependents(const uint8_t& index,
      uint8_t* childrenIndices, 
      dep_label* childRelations,
      uint8_t* childrenLength) const {
    dependents(index, 255, childrenIndices, childRelations, childrenLength);
  }

  /**
   * Register a quantifier in the tree.
   *
   * @return True if the quantifier was registered, or false if there is no
   *         remaining space for it.
   */
  inline bool registerQuantifier(
      const quantifier_span& span,
      const quantifier_type& subjType,
      const quantifier_type& objType,
      const monotonicity& subjMono,
      const monotonicity& objMono) {
    if (numQuantifiers >= MAX_QUANTIFIER_COUNT) {
      return false;
    } else {
      quantifierSpans[numQuantifiers] = span;
      quantifierMonotonicities[numQuantifiers].subj_mono = subjMono;
      quantifierMonotonicities[numQuantifiers].obj_mono = objMono;
      quantifierMonotonicities[numQuantifiers].subj_type = subjType;
      quantifierMonotonicities[numQuantifiers].obj_type = objType;
      numQuantifiers += 1;
      return true;
    }
  }

  /** Gives the quantifier index of the given quantifier. */
  inline int8_t quantifierIndex(const uint8_t& tokenIndex) const {
    for (uint8_t i = 0; i < numQuantifiers; ++i) {
      if (quantifierSpans[i].quantifier_index == tokenIndex) { return i; }
    }
    return -1;
  }
  
  /** Gives the token index of the given quantifier. */
  inline int8_t quantifierTokenIndex(const uint8_t& quantifierIndex) const {
    return quantifierSpans[quantifierIndex].quantifier_index;
  }
  
  /** If true, the word at the given index is a quantifier. */
  inline bool isQuantifier(const uint8_t& index) const {
    return quantifierIndex(index) >= 0;
  }
  
  /**
   * Iterates over the quantifiers of the sentence. For each
   * quantifier, call the visitor function with that quantifier's type
   * and monotonicity.
   * These are guaranteed to be called in order of narrowest to broadest
   * scope outwards from the word.
   *
   * Note that this is slow if you call it repeatedly, due to the memory allocation
   * and de-allocation from the visitor function.
   *
   * @param index The index of the word to query.
   * @param currentSearchState The search state to use for overriding the quantifier.
   * @param visitor A function to call for each quantifier scoping the word
   *                at index.
   */
  void foreachQuantifier(
        const uint8_t& index,
        const SearchNode& currentSearchState,
        std::function<void(const quantifier_type,const monotonicity)> visitor) const;

  /**
   * @see foreachQuantifier(uint8_t, SearchNode, function), but without overriding
   * the quantifier definitions from the search node.
   */
  void foreachQuantifier(
        const uint8_t& index,
        std::function<void(quantifier_type,monotonicity)> visitor) const;
  
  /**
   * The index of the root of the dependency tree.
   */
  uint8_t root() const;

  /**
   * The tagged word at the given index (zero indexed).
   */
  inline tagged_word token(const uint8_t& index) const {
    return getTaggedWord(data[index].word, data[index].sense, MONOTONE_UP);
  }
  
  /**
   * The word at the given index (zero indexed).
   */
  inline ::word word(const uint8_t& index) const {
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
  inline dep_label relation(const uint8_t& index) const {
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
   * Project a given lexical relation, at the given index of the tree,
   * up through the quantifiers of the tree.
   * 
   * @param currentNode The node being projected over, at the given index.
   *                    This is to get the most recent quantifier markings,
   *                    and to get the token index.
   * @param lexicalRelation The root lexical relation at the given index.
   * @param index The index to project through. In most cases, this should be
   *              currentNode.tokenIndex()
   *
   * @return The lexical relation projected up to the root of the tree.
   */
  natlog_relation projectLexicalRelation( const SearchNode& currentNode,
                                          const natlog_relation& lexicalRelation,
                                          const uint8_t& index) const;

  /**
   * @see projectLexicalRelation(SearchNode, natlog_relation), but
   *      applied to the index of the current search state node.
   */
  natlog_relation projectLexicalRelation( const SearchNode& currentNode, 
                                          const natlog_relation& lexicalRelation) const;

  /** Returns the polarity of the token at the given index. */
  inline monotonicity polarityAt(const SearchNode& currentNode,
                                 const uint8_t& index) const {
    switch( projectLexicalRelation(currentNode, FUNCTION_FORWARD_ENTAILMENT, index)) {
      case FUNCTION_FORWARD_ENTAILMENT:
        return MONOTONE_UP;
      case FUNCTION_REVERSE_ENTAILMENT:
        return MONOTONE_DOWN;
      case FUNCTION_INDEPENDENCE:
        return MONOTONE_FLAT;
      default:
        return MONOTONE_INVALID;
    }
  }
  
  /** Returns the number of quantifiers in the sentence. */
  inline uint8_t getNumQuantifiers() const { return numQuantifiers; }
  
  inline bool isLocation(const uint8_t& tokenIndex) const { return isLocationMask[tokenIndex]; }
  
  /**
   * Get the quantifier information for the given quantifier index.
   */
  inline const quantifier_monotonicity& quantifier(const uint8_t& quantifierIndex) const {
    return quantifierMonotonicities[quantifierIndex];
  }
  
  /**
   * Sort the indices in a tree topologically, from the root to the leaf
   * nodes. The hard guarantee of this method is that the parent
   * of a node will be earlier in the resulting buffer than the node
   *
   * @param tree The tree to topologically sort.
   * @param buffer The output buffer to put the resulting indices into.
   *               The value 255 is reserved for the "end of stream" signal.
   *               If a sentence has 256 words in it, then tough luck; index
   *               255 will never be visited.
   */
  void topologicalSort(uint8_t* buffer, const bool& ignoreQuantifiers) const;

  /**
   * Sort the vertices of this tree, ignoring quantifiers in the sorted list.
   * @see topologicalSort(uint8_t*, true)
   */
  inline void topologicalSortIgnoreQuantifiers(uint8_t* buffer) const {
    topologicalSort(buffer, true);
  }
  
  /**
   * Sort the vertices of this tree, including quantifiers in the sorted list.
   * @see topologicalSort(uint8_t*, false)
   */
  inline void topologicalSort(uint8_t* buffer) const {
    topologicalSort(buffer, false);
  }

  /**
   * The number of words in this dependency graph
   */
  const uint8_t length;

 protected:
  /** Information about the monotonicities of the quantifiers in the sentence. */
  quantifier_monotonicity quantifierMonotonicities[MAX_QUANTIFIER_COUNT];

 private:
  /** The actual data for this tree. */
  dep_tree_word data[MAX_QUERY_LENGTH];

  /** Information about the spans of the quantifiers in the sentence. */
  quantifier_span quantifierSpans[MAX_QUANTIFIER_COUNT];
  
  /** A cached data structure for which quantifiers are in scope at a given index. */
  uint8_t quantifiersInScope[MAX_QUERY_LENGTH][MAX_QUANTIFIER_COUNT];

  /** The number of quantifiers in the tree. */
  uint8_t numQuantifiers;
  
  /** The number of quantifiers in the tree. */
  std::bitset<MAX_QUERY_LENGTH> isLocationMask;

  // End variables

  /** Populate the quantifiers in scope at a particular index */
  void populateQuantifiersInScope(const uint8_t index);

  /** Get the incoming edge at the given index as a struct */
  inline dependency_edge edgeInto(const uint8_t& index,
                                  const ::word& wordAtIndex,
                                  const ::word& governor) const {
    return dependency_edge(governor, wordAtIndex, data[index].relation);
  }
  
  /** See edgeInto(index, word, word), but using the tree's known governor */
  inline dependency_edge edgeInto(const uint8_t& index, 
                                  const ::word& wordAtIndex) const {
    const uint8_t governorIndex = data[index].governor;
    if (governorIndex == TREE_ROOT) {
      return edgeInto(index, wordAtIndex, TREE_ROOT_WORD);
    } else {
      return edgeInto(index, wordAtIndex, data[governorIndex].word);
    }
  }

  /** See edgeInto(index, word), but using the tree's known word */
  inline dependency_edge edgeInto(const uint8_t& index) const {
    return edgeInto(index, data[index].word);
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
struct syn_path_data {
#else
struct alignas(24) syn_path_data {
#endif
  uint64_t    factHash:64,
              currentWord:VOCABULARY_ENTROPY,  // 24
              currentSense:SENSE_ENTROPY,      // 5
              deleteMask:MAX_QUERY_LENGTH,     // 39
              backpointer:25;
  word        governor:VOCABULARY_ENTROPY;     // 24
  uint8_t     index:6;
  bool        truth:1,
              allQuantifiersSeen:1;

  bool operator==(const syn_path_data& rhs) const {
    return factHash == rhs.factHash &&
           deleteMask == rhs.deleteMask &&
           index == rhs.index &&
           truth == rhs.truth &&
           currentWord == rhs.currentWord &&
           currentSense == rhs.currentSense &&
           governor == rhs.governor;
  }
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif


/**
 * A state in the search space, making use of the syntactic information
 * in the state.
 *
 * Assuming a Dependency Tree is defined somewhere else, this class
 * keeps track of the deletions made to the tree, the hash of the
 * current tree (for fact lookup), the deletions made to the tree,
 * the value of the current token being mutated, and the truth state
 * of the inference so far. In addition, it keeps track of a
 * backpointer, as well as costs for if this fact ends up being in the
 * "true" state and "false" state.
 *
 * This class fits into 32 bytes; it should not exceed the size of
 * a cache line.
 */
class SearchNode {
 friend class Tree;
 public:
  void mutations(SearchNode* output, uint64_t* index);
  void deletions(SearchNode* output, uint64_t* index);

  /** A dummy constructor. Don't use this. */
  SearchNode();
  /** The copy constructor. */
  SearchNode(const SearchNode& from);
  /** The initial node constructor. */
  SearchNode(const Tree& init);
  /** The initial node constructor, with an assumed truth value. */
  SearchNode(const Tree& init, const bool& assumedTruthValue);
  /** The initial node constructor, but starting from a specific index. */
  SearchNode(const Tree& init, const uint8_t& index);
  /** 
   * The initial node constructor, 
   * but starting from a specific index, and with an assumed
   * truth value
   */
  SearchNode(const Tree& init, const bool& assumedTruthValue, const uint8_t& index);
  /** The mutate constructor */
  SearchNode(const SearchNode& from, const uint64_t& newHash,
             const tagged_word& newToken,
             const bool& newTruthValue, const uint32_t& backpointer);
  /**
   * The delete branch constructor. the new deletions are
   * <b>added to</b> the deletions already in |from|
   */
  SearchNode(const SearchNode& from, const uint64_t& newHash,
          const bool& newTruthValue, const uint32_t& newDeletions, 
          const uint32_t& backpointer);
  /** The move index constructor */
  SearchNode(const SearchNode& from, const Tree& tree,
          const uint8_t& newIndex, const uint32_t& backpointer);
  
  /**
   * Checks if the two paths are the same, <i>including</i> the
   * mutation index but <i>NOT including</i> the costs or backpointer.
   * To check if two paths are the same for just the fact, use the
   * fact hash.
   */
  inline bool operator==(const SearchNode& rhs) const {
    return this->data == rhs.data;
  }
  
  /** @see operator==(const SearchNode&) */
  inline bool operator!=(const SearchNode& rhs) const {
    return !((*this) == rhs);
  }
  
  /** The assignment operator */
  inline void operator=(const SearchNode& from) {
    this->data = from.data;
    memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
      MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
  }

  /** Returns the hash of the current fact. */
  inline uint64_t factHash() const { return data.factHash; }
  
  // TODO(gabor) refactor this so it's clear it has no monotonicity info
  /** Returns the current word being mutated. */
  inline tagged_word token() const { 
    return getTaggedWord(data.currentWord, data.currentSense, 0); 
  }
  
  /** Returns the current word being mutated. */
  inline ::word word() const { return data.currentWord; }
  
  /** Returns index of the current token being mutated. */
  inline uint8_t tokenIndex() const { return data.index; }
  
  /** Returns governor of the current token. */
  inline ::word governor() const { return data.governor; }
  
  /** Returns whether a given word has been deleted*/
  inline bool isDeleted(const uint8_t& index) const { 
    return TREE_IS_DELETED(data.deleteMask, index);
  }
  
  /** Returns a backpointer to the parent of this fact. */
  inline uint32_t getBackpointer() const { return data.backpointer; }
  
  /** Returns the truth state of this node. */
  inline bool truthState() const { return data.truth; }
  
  /** Project the lexical relation through this node's quantifiers */
  natlog_relation projectLexicalRelation( const SearchNode& currentNode,
                                          const natlog_relation& lexicalRelation) const;
  
  /**
   * Mutate this quantifier to have different monotonicities
   */
  void mutateQuantifier(
      const uint8_t& quantifierIndex,
      const monotonicity& subjMono,
      const quantifier_type& subjType,
      const monotonicity& objMono,
      const quantifier_type& objType
      );

  /**
   * Compute a deletion from a given SearchNode
   */
  SearchNode deletion(const uint32_t& myIndexInHistory,
                      const bool& newTruthValue,
                      const Tree& tree,
                      const uint8_t& dependentIndex) const;
  /**
   * Compute a mutation from a given SearchNode
   */
  inline SearchNode mutation(const edge& edge,
                             const uint32_t& myIndexInHistory,
                             const bool& newTruthValue,
                             const Tree& tree,
                             const Graph* graph) const {
    // Get the word we're mutating
    const tagged_word nodeToken = this->token();
    assert(nodeToken.word == edge.sink);
    assert(nodeToken.sense == edge.sink_sense);
    // Compute the new hash
    const uint64_t newHash = tree.updateHashFromMutation(
        this->factHash(), this->tokenIndex(), nodeToken.word,
        this->governor(), edge.source
      );
    const tagged_word newToken = getTaggedWord(
        edge.source,
        edge.source_sense,
        nodeToken.monotonicity);
    // Ensure the mutation is valid
    assert(graph == NULL || newToken.word < graph->vocabSize());
    assert(newToken.sense < (1 << SENSE_ENTROPY));
    assert(newToken.monotonicity < 4);
    // (create child)
    return SearchNode(*this, newHash, newToken,
                      newTruthValue, myIndexInHistory);
  }

  /**
   * Get the quantifier information for the given quantifier index.
   */
  inline const quantifier_monotonicity& quantifier(const uint8_t& quantifierIndex) const {
    return quantifierMonotonicities[quantifierIndex];
  }

  /** Returns whether we have traversed all the quantifiers in this path */
  inline const bool allQuantifiersSeen() const { return data.allQuantifiersSeen; }

  /** Sets a flag for having traversed all the quantifiers (thus we shouldn't re-traverse them) */
  inline void setAllQuantifiersSeen() {
    data.allQuantifiersSeen = true;
  }


 protected:
  /** The data stored in this path */
  quantifier_monotonicity quantifierMonotonicities[MAX_QUANTIFIER_COUNT];  // TODO(gabor) set me in various places

 private:
  /** The data stored in this path */
  syn_path_data data;
};

/**
 * A search node with an associated score.
 */
struct ScoredSearchNode {
  SearchNode node;
  /** The score for this Search Node */
  float cost;
  /** Create a new scored search node */
  ScoredSearchNode(const SearchNode& node, const float& cost)
    : node(node), cost(cost) { }
  ScoredSearchNode() : cost(0.0f) {}

  void operator=(const ScoredSearchNode& rhs) {
    this->node = rhs.node;
    this->cost = rhs.cost;
  }
};


// ----------------------------------------------
// Threadsafe Int
// ----------------------------------------------

#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(CACHE_LINE_SIZE) {
#endif
  uint64_t value;
  uint8_t     __filler1[CACHE_LINE_SIZE - 8];
#ifdef __GNUG__
} __attribute__((packed)) uint64_threadsafe_t;
#else
} uint64_threadsafe_t;
#endif

inline uint64_threadsafe_t* malloc_uint64_threadsafe_t() {
  void* ptr;
  int rc = posix_memalign(&ptr, CACHE_LINE_SIZE, sizeof(uint64_threadsafe_t));
  uint64_threadsafe_t* p = (uint64_threadsafe_t*) ptr;
  p->value = 0l;
  return p;
}

// ----------------------------------------------
// SEARCH INSTANCE
// ----------------------------------------------

/**
 * The structure representing the parameterization of the search
 * we are intended to perform.
 */
struct syn_search_options {
  /** The search ticks to run for (the number of elements popped) */
  uint32_t maxTicks;
  /** The cost above which to no longer add things to the fringe */
  float costThreshold;
  /** If true, stop when the first result is found */
  bool stopWhenResultFound;
  /** If true, check the fringe after the search is done */
  bool checkFringe;
  /** If true, do not print anything during the search (for tests, primarily) */
  bool silent;

  /**
   * Create the input options for a Search.
   *
   * @param maxTicks The max number of search ticks (e.g., nodes viewed)
   *                 to explore before timing out.
   * @param costThreshold The threshold above which nodes should not be
   *                      visited.
   * @param checkFringe If true, check the fringe for known facts. This is
   *                    particularly useful when using the system for
   *                    entailment rather than truth checking.
   * @param stopWhenResultFound If true, do not continue searching past
   *                            the first result found.
   *
   * @param silent If true, do not print anything during search.
   */
  syn_search_options( const uint32_t& maxTicks,
                      const float& costThreshold,
                      const bool& stopWhenResultFound,
                      const bool& checkFringe,
                      const bool& silent) {
    this->maxTicks = maxTicks;
    this->costThreshold = costThreshold;
    this->stopWhenResultFound = stopWhenResultFound;
    this->checkFringe = checkFringe;
    this->silent = silent;
  }
};

/**
 * A single search path.
 */
struct syn_search_path {
  std::vector<SearchNode> nodeSequence;
  float cost;

  syn_search_path(const std::vector<SearchNode>& nodeSequence,
                  const float& cost)
                  : nodeSequence(nodeSequence), cost(cost) { }
       
  inline SearchNode operator[](const uint64_t& i) { return nodeSequence[i]; }
  inline SearchNode front() { return nodeSequence.front(); }
  inline SearchNode back() { return nodeSequence.back(); }
  inline uint64_t size() const { return nodeSequence.size(); }
};

/**
 * A convenient struct to store the output of the search algorithm.
 */
struct syn_search_response {
  std::vector<syn_search_path> paths;
  uint64_t totalTicks;

  inline uint64_t size() const { return paths.size(); }
};

///**
// * Run a partial search from a known fact, primarily to resolve
// * deletions (which would be insertions in the reverse search).
// */
//void ForwardPartialSearch(
//    const BidirectionalGraph* mutationGraph,
//    const Tree* input,
//    std::function<void(const SearchNode&)> callback);

/**
 * The entry method for starting a new search.
 */
syn_search_response SynSearch(
    const Graph* mutationGraph,
    const btree::btree_set<uint64_t>* mainKB,
    const btree::btree_set<uint64_t>& auxKB,
    const Tree* input,
    const SynSearchCosts* costs,
    const bool& assumedInitialTruth,
    const syn_search_options& opts);

/** @see SynSearch(), but with only one knowledge base */
inline syn_search_response SynSearch(
    const Graph* mutationGraph,
    const btree::btree_set<uint64_t>* mainKB,
    const Tree* input,
    const SynSearchCosts* costs,
    const bool& assumedInitialTruth,
    const syn_search_options& opts) {
  return SynSearch(mutationGraph, mainKB, btree::btree_set<uint64_t>(), 
      input, costs, assumedInitialTruth, opts);
}

#endif
