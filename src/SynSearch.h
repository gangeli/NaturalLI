#ifndef SYN_SEARCH_H
#define SYN_SEARCH_H

#include <limits>

#include "config.h"
#include "Types.h"
#include "Graph.h"
#include "knheap/knheap.h"
#include "btree_set.h"

// Ensure definitions
#ifndef TWO_PASS_HASH
  #define TWO_PASS_HASH 0
#endif
#ifndef SEARCH_CYCLE_MEMORY
  #define SEARCH_CYCLE_MEMORY 0
#endif

// Conditional includes
#if TWO_PASS_HASH!=0
  #include "fnv/fnv.h"
#endif

class Tree;

// ----------------------------------------------
// NATURAL LOGIC
// ----------------------------------------------

typedef uint8_t natlog_relation;
#define FUNCTION_WORDNET_UP                   FUNCTION_FORWARD_ENTAILMENT
#define FUNCTION_WORDNET_DOWN                 FUNCTION_REVERSE_ENTAILMENT
#define FUNCTION_WORDNET_NOUN_ANTONYM         FUNCTION_ALTERNATION
#define FUNCTION_WORDNET_NOUN_SYNONYM         FUNCTION_EQUIVALENT
#define FUNCTION_WORDNET_VERB_ANTONYM         FUNCTION_ALTERNATION
#define FUNCTION_WORDNET_ADJECTIVE_ANTONYM    FUNCTION_NEGATION
#define FUNCTION_WORDNET_ADVERB_ANTONYM       FUNCTION_ALTERNATION
#define FUNCTION_WORDNET_ADJECTIVE_PERTAINYM  FUNCTION_EQUIVALENT
#define FUNCTION_WORDNET_ADVERB_PERTAINYM     FUNCTION_EQUIVALENT
#define FUNCTION_WORDNET_ADJECTIVE_RELATED    FUNCTION_EQUIVALENT
#define FUNCTION_ANGLE_NN                     FUNCTION_EQUIVALENT
#define FUNCTION_FREEBASE_UP                  FUNCTION_FORWARD_ENTAILMENT
#define FUNCTION_FREEBASE_DOWN                FUNCTION_REVERSE_ENTAILMENT

typedef uint8_t quantifier_type;
#define QUANTIFIER_TYPE_NONE 0x0
#define QUANTIFIER_TYPE_ADDITIVE 0x1
#define QUANTIFIER_TYPE_MULTIPLICATIVE 0x2
#define QUANTIFIER_TYPE_BOTH (QUANTIFIER_TYPE_ADDITIVE | QUANTIFIER_TYPE_MULTIPLICATIVE)


/**
 * Translate from edge type to the Natural Logic lexical function
 * that edge type maps to.
 *
 * @param edge The edge type being traversed.

 * @return The Natural Logic relation corresponding to that edge.
 */
inline natlog_relation edgeToLexicalFunction(const natlog_relation& edge) {
  switch (edge) {
    case WORDNET_UP:                  return FUNCTION_FORWARD_ENTAILMENT;
    case WORDNET_DOWN:                return FUNCTION_REVERSE_ENTAILMENT;
    case WORDNET_NOUN_ANTONYM:        return FUNCTION_ALTERNATION;
    case WORDNET_NOUN_SYNONYM:        return FUNCTION_EQUIVALENT;
    case WORDNET_VERB_ANTONYM:        return FUNCTION_ALTERNATION;
    case WORDNET_ADJECTIVE_ANTONYM:   return FUNCTION_NEGATION;
    case WORDNET_ADVERB_ANTONYM:      return FUNCTION_ALTERNATION;
    case WORDNET_ADJECTIVE_PERTAINYM: return FUNCTION_EQUIVALENT;
    case WORDNET_ADVERB_PERTAINYM:    return FUNCTION_EQUIVALENT;
    case WORDNET_ADJECTIVE_RELATED:   return FUNCTION_EQUIVALENT;
    case ANGLE_NN:                    return FUNCTION_EQUIVALENT;
    case FREEBASE_UP:                 return FUNCTION_FORWARD_ENTAILMENT;
    case FREEBASE_DOWN:               return FUNCTION_REVERSE_ENTAILMENT;
    default:
      printf("No such edge: %u\n", edge);
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
inline natlog_relation dependencyInsertToLexicalFunction(const dep_label& dep,
                                                         const ::word& dependent) {
  switch (dep) {
    case DEP_ACOMP: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_ADVCL: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_ADVMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_AMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_APPOS: return FUNCTION_EQUIVALENT;
    case DEP_AUX: return FUNCTION_FORWARD_ENTAILMENT;      // he left -> he should leave
    case DEP_AUXPASS: return FUNCTION_FORWARD_ENTAILMENT;  // as above
    case DEP_CC: return FUNCTION_REVERSE_ENTAILMENT;       // match DEP_CONJ
    case DEP_CCOMP: return FUNCTION_INDEPENDENCE;           // interesting project here... "he said x" -> "x"?
    case DEP_CONJ: return FUNCTION_REVERSE_ENTAILMENT;     // match DEP_CC
    case DEP_COP: return FUNCTION_EQUIVALENT;
    case DEP_CSUBJ: return FUNCTION_INDEPENDENCE;           // don't drop subjects.
    case DEP_CSUBJPASS: return FUNCTION_INDEPENDENCE;       // as above
    case DEP_DEP: return FUNCTION_INDEPENDENCE;
    case DEP_DET:
      // TODO(gabor) quantifiers go here!
      //             The relation here will depend on the quantifier type.
      return FUNCTION_INDEPENDENCE;
    case DEP_DISCOURSE: return FUNCTION_EQUIVALENT;
    case DEP_DOBJ: return FUNCTION_INDEPENDENCE;            // don't drop objects.
    case DEP_EXPL: return FUNCTION_EQUIVALENT;             // though we shouldn't see this...
    case DEP_GOESWITH: return FUNCTION_EQUIVALENT;         // also shouldn't see this
    case DEP_IOBJ: return FUNCTION_REVERSE_ENTAILMENT;     // she gave me a rais -> she gave a raise
    case DEP_MARK: return FUNCTION_INDEPENDENCE;
    case DEP_MWE: return FUNCTION_INDEPENDENCE;             // shouldn't see this
    case DEP_NEG: return FUNCTION_NEGATION;
    case DEP_NN: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_NPADVMOD: return FUNCTION_INDEPENDENCE;        // not sure about this one
    case DEP_NSUBJ: return FUNCTION_INDEPENDENCE;
    case DEP_NSUBJPASS: return FUNCTION_INDEPENDENCE;
    case DEP_NUM: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_NUMBER: return FUNCTION_INDEPENDENCE;
    case DEP_PARATAXIS: return FUNCTION_INDEPENDENCE;        // or, reverse?
    case DEP_PCOMP: return FUNCTION_INDEPENDENCE;            // though, not so in collapsed dependencies
    case DEP_POBJ: return FUNCTION_INDEPENDENCE;             // must delete whole preposition
    case DEP_POSS: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_POSSEIVE: return FUNCTION_INDEPENDENCE;         // see DEP_POSS
    case DEP_PRECONJ: return FUNCTION_INDEPENDENCE;          // FORBIDDEN to see this
    case DEP_PREDET: return FUNCTION_INDEPENDENCE;           // FORBIDDEN to see this
    case DEP_PREP: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_PRT: return FUNCTION_INDEPENDENCE;
    case DEP_PUNCT: return FUNCTION_EQUIVALENT;
    case DEP_QUANTMOD: return FUNCTION_FORWARD_ENTAILMENT;
    case DEP_RCMOD: return FUNCTION_INDEPENDENCE;            // no documentation?
    case DEP_ROOT: return FUNCTION_INDEPENDENCE;             // err.. never delete
    case DEP_TMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_VMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_XCOMP: return FUNCTION_INDEPENDENCE;
    default:
      printf("No such dependency label: %u\n", dep);
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
                     const uint8_t& index,
                     const uint8_t& edgeType,
                     const bool& endTruthValue,
                     bool* beginTruthValue) const;
  
  /** The cost of an insertion (deletion in search) */
  float insertionCost(const Tree& tree,
                      const uint8_t& governorIndex,
                      const dep_label& dependencyLabel,
                      const ::word& dependent,
                      const bool& endTruthValue,
                      bool* beginTruthValue) const;

  float mutationLexicalCost[NUM_MUTATION_TYPES];
  float insertionLexicalCost[NUM_DEPENDENCY_LABELS];
  float deletionLexicalCost[NUM_DEPENDENCY_LABELS];
  float transitionCostFromTrue[8];
  float transitionCostFromFalse[8];
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
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif

#ifdef __GNUG__
struct quantifier_span {
#else
struct alignas(3) quantifier_span {
#endif
  uint8_t subj_begin:5,
          subj_end:5,
          obj_begin:5,
          obj_end:5;
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
  ::word        word:25;
  uint8_t       sense:5,
                governor:6,
                relation:6;
#ifdef __GNUG__
} __attribute__((packed));
#else
};
#endif

/**
 * A packed representation of a dependency edge.
 */
#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(8) {
#endif
  uint32_t   governor:25,
             dependent:25;
  dep_label  relation:6;
  uint8_t    placeholder:8;  // <-- should be zero
#ifdef __GNUG__
} __attribute__((packed)) dependency_edge;
#else
} dependency_edge;
#endif

/**
 * A dependency tree.
 */
class Tree {
 friend class SearchNode;
 public:
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
      dep_label* childRelations,
      uint8_t* childrenLength) const;
  
  /** @see children() above */
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

  /** If true, the word at the given index is a quantifier. */
  inline bool isQuantifier(const uint8_t& index) const {
    for (uint8_t i = 0; i < numQuantifiers; ++i) {
      if (quantifierSpans[i].subj_begin == index) { return true; }
    }
    return false;
  }

  /**
   * Iterates over the quantifiers of the sentence. For each
   * quantifier, call the visitor function with that quantifier's type
   * and monotonicity.
   * These are guaranteed to be called in order of narrowest to broadest
   * scope outwards from the word.
   *
   * @param index The index of the word to query.
   * @param visitor A function to call for each quantifier scoping the word
   *                at index.
   */
  void foreachQuantifier(
      const uint8_t& index,
      std::function<void(quantifier_type,monotonicity)> visitor) const ;
  
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
   * @param index The index of the token being mutated or projected through.
   * @param lexicalRelation The root lexical relation at the given index.
   *
   * @return The lexical relation projected up to the root of the tree.
   */
  natlog_relation projectLexicalRelation( const uint8_t& index,
                                          const natlog_relation& lexicalRelation) const;
  
  /** Returns the number of quantifiers in the sentence. */
  inline uint8_t getNumQuantifiers() const { return numQuantifiers; }

  /**
   * The number of words in this dependency graph
   */
  const uint8_t length;

 protected:
  /** Information about the monotonicities of the quantifiers in the sentence */
  quantifier_monotonicity quantifierMonotonicities[MAX_QUANTIFIER_COUNT];

 private:
  /** The actual data for this tree */
  dep_tree_word data[MAX_QUERY_LENGTH];

  /** Information about the spans of the quantifiers in the sentence */
  quantifier_span quantifierSpans[MAX_QUANTIFIER_COUNT];

  /** The number of quantifiers in the tree. */
  uint8_t numQuantifiers;

  /** Fill up to 3 cache lines */
#if MAX_QUANTIFIER_COUNT < 10
  uint8_t nullBuffer[26 - MAX_QUANTIFIER_COUNT*3];
#else
  uint8_t nullBuffer[CACHE_LINE_SIZE + 26 - MAX_QUANTIFIER_COUNT*3];
#endif

  /** Get the incoming edge at the given index as a struct */
  inline dependency_edge edgeInto(const uint8_t& index,
                                  const ::word& wordAtIndex,
                                  const ::word& governor) const {
    dependency_edge edge;
    edge.placeholder = 0x0;
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
struct alignas(16) syn_path_data {
#endif
  uint64_t    factHash:64;
  uint8_t     index:5;
  bool        truth:1;
  uint32_t    deleteMask:MAX_QUERY_LENGTH;  // 26
  tagged_word currentToken;
  word        governor;

  bool operator==(const syn_path_data& rhs) const {
    return factHash == rhs.factHash &&
           deleteMask == rhs.deleteMask &&
           index == rhs.index &&
           truth == rhs.truth &&
           currentToken == rhs.currentToken &&
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
 public:
  void mutations(SearchNode* output, uint64_t* index);
  void deletions(SearchNode* output, uint64_t* index);

  /** A dummy constructor. Don't use this */
  SearchNode();
  /** The copy constructor */
  SearchNode(const SearchNode& from);
  /** The initial node constructor */
  SearchNode(const Tree& init);
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

//  /** Compares the priority keys of two path states */
//  inline bool operator<=(const SearchNode& rhs) const {
//    return getPriorityKey() <= rhs.getPriorityKey();
//  }
//  /** Compares the priority keys of two path states */
//  inline bool operator<(const SearchNode& rhs) const {
//    return getPriorityKey() < rhs.getPriorityKey();
//  }
//  /** Compares the priority keys of two path states */
//  inline bool operator>(const SearchNode& rhs) const {
//    return !(*this <= rhs);
//  }
//  /** Compares the priority keys of two path states */
//  inline bool operator>=(const SearchNode& rhs) const {
//    return !(*this < rhs);
//  }
  
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
    this->backpointer = from.backpointer;
    memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
      MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
  }

  /** Returns the hash of the current fact. */
  inline uint64_t factHash() const { return data.factHash; }
  
  /** Returns the current word being mutated. */
  inline tagged_word token() const { return data.currentToken; }
  
  /** Returns index of the current token being mutated. */
  inline uint8_t tokenIndex() const { return data.index; }
  
  /** Returns governor of the current token. */
  inline ::word governor() const { return data.governor; }
  
  /** Returns whether a given word has been deleted*/
  inline bool isDeleted(const uint8_t& index) const { 
    return TREE_IS_DELETED(data.deleteMask, index);
  }
  
  /** Returns a backpointer to the parent of this fact. */
  inline uint32_t getBackpointer() const { return backpointer; }
  
  /** Returns the truth state of this node. */
  inline bool truthState() const { return data.truth; }

 private:
  /** The data stored in this path */
  quantifier_monotonicity quantifierMonotonicities[MAX_QUANTIFIER_COUNT];  // TODO(gabor) set me in various places
  /** The data stored in this path */
  syn_path_data data;
  /** A backpointer to the path this came from */
  uint32_t backpointer;
};

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
// CHANNEL
// ----------------------------------------------

#define CHANNEL_BUFFER_LENGTH ((1280 - 2*CACHE_LINE_SIZE) / sizeof(ScoredSearchNode))

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

#ifdef __GNUG__
typedef struct {
#else
typedef struct alignas(1280) {
#endif
  // Push thread's memory
  uint16_t    pushPointer:16;
  uint8_t     __filler1[CACHE_LINE_SIZE - 2];  // pushPointer gets its own cache line
  // Buffer
  ScoredSearchNode     buffer[CHANNEL_BUFFER_LENGTH];
  // Poll thread's memory
  uint8_t     __filler2[CACHE_LINE_SIZE - 2];  // pollPointer gets its own cache line
  uint16_t    pollPointer:16;
#ifdef __GNUG__
} __attribute__((packed)) channel_data;
#else
} channel_data;
#endif


/**
 * A rudimentary thread-safe lockless channel.
 * Note that only two threads can actually communicate across a Channel.
 * One must always be writing, and one must always be reading.
 * Otherwise, all hell breaks loose.
 */
class Channel {
 public:
  /** Create a new channel. threadsafeChannel() is more recommended */
  Channel() {
    data.pushPointer = 0;
    data.pollPointer = 0;
  }

  /** Push an element onto the channel. Returns false if there is no space */
  bool push(const ScoredSearchNode& value);
  /** Poll an element from the channel. Returns false if there is nothing in the channel */
  bool poll(ScoredSearchNode* output);

  /** Public for debugging and testing only. Do not access this directly */
  channel_data data;
};

inline Channel* threadsafeChannel() {
  void* ptr;
  int rc = posix_memalign(&ptr, CACHE_LINE_SIZE, sizeof(Channel));
  return new(ptr) Channel();
}

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
typedef struct {
  uint32_t maxTicks;
  float costThreshold;
  bool stopWhenResultFound;
  bool silent;
} syn_search_options;

/**
 * A convenient struct to store the output of the search algorithm.
 */
struct syn_search_response {
  std::vector<std::vector<SearchNode>> paths;
  uint64_t totalTicks;
};

/**
 * Create the input options for a Search.
 *
 * @param maxTicks The max number of search ticks (e.g., nodes viewed)
 *                 to explore before timing out.
 * @param costThreshold The threshold above which nodes should not be
 *                      visited.
 * @param stopWhenResultFound If true, do not continue searching past
 *                            the first result found.
 */
syn_search_options SynSearchOptions(
    const uint32_t& maxTicks,
    const float& costThreshold,
    const bool& stopWhenResultFound,
    const bool& silent);

/**
 * The entry method for starting a new search.
 */
syn_search_response SynSearch(
    const Graph* mutationGraph,
    const btree::btree_set<uint64_t>& database,
    const Tree* input,
    const SynSearchCosts* costs,
    const syn_search_options& opts);

#endif
