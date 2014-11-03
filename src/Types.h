#ifndef TYPES_H
#define TYPES_H

#include <vector>
#include <stdint.h>

#include <config.h>

//
// Definitions / Enums
//

// Monotonicities
#define MONOTONE_UP      0
#define MONOTONE_DOWN    1
#define MONOTONE_FLAT    2
#define MONOTONE_INVALID 3
#define MONOTONE_DEFAULT MONOTONE_UP

// Graph Edges
// READ BEFORE YOU CHANGE:
//  - These must match the edge types in Utils.scala
//  - These should also be reflected in the toString in Utils.cc
//  - Remember to update EDGE_ADDS_BEGIN, MONOTONE_INDEPENDENT_BEGIN and NUM_EDGE_TYPES
//  - Pay attention to inline notes!
#define WORDNET_UP                   0
#define WORDNET_DOWN                 1
#define WORDNET_NOUN_ANTONYM         2
#define WORDNET_NOUN_SYNONYM         3
#define WORDNET_VERB_ANTONYM         4
#define WORDNET_ADJECTIVE_ANTONYM    5
#define WORDNET_ADVERB_ANTONYM       6
#define WORDNET_ADJECTIVE_PERTAINYM  7
#define WORDNET_ADVERB_PERTAINYM     8
#define WORDNET_ADJECTIVE_RELATED    9
#define ANGLE_NN                     10
#define FREEBASE_UP                  11
#define FREEBASE_DOWN                12

#define ADD_NOUN                     13
#define ADD_VERB                     14
#define ADD_ADJ                      15
#define ADD_NEGATION                 16
#define ADD_EXISTENTIAL              17
#define ADD_QUANTIFIER_OTHER         18
#define ADD_UNIVERSAL                19
#define ADD_OTHER                    20

#define EDGE_DELS_BEGIN              21  // should be the same as the first DEL_X item
#define DEL_NOUN                     21
#define DEL_VERB                     22
#define DEL_ADJ                      23
#define DEL_NEGATION                 24
#define DEL_EXISTENTIAL              25
#define DEL_QUANTIFIER_OTHER         26
#define DEL_UNIVERSAL                27
#define DEL_OTHER                    28  // NOTE: never have more than 8 insertion types. @see Trie.h

#define QUANTIFIER_UP                29
#define QUANTIFIER_DOWN              30
#define MONOTONE_INDEPENDENT_BEGIN   31  // should be the same as the first index after which monotonicity doesn't matter
#define QUANTIFIER_NEGATE            31
#define QUANTIFIER_REWORD            32

#define MORPH_FUDGE_NUMBER           33
#define SENSE_REMOVE                 34
#define SENSE_ADD                    35

#define NUM_EDGE_TYPES               36


// Inference States
#define INFER_FALSE   0
#define INFER_TRUE    1
#define INFER_UNKNOWN 2

// Inference functions
#define FUNCTION_EQUIVALENT         0
#define FUNCTION_FORWARD_ENTAILMENT 1
#define FUNCTION_REVERSE_ENTAILMENT 2
#define FUNCTION_NEGATION           3
#define FUNCTION_ALTERNATION        4
#define FUNCTION_COVER              5
#define FUNCTION_INDEPENDENCE       6

// Dependency edges
typedef uint8_t dep_label;
#define DEP_ACOMP 0
#define DEP_ADVCL 1
#define DEP_ADVMOD 2
#define DEP_AMOD 3
#define DEP_APPOS 4
#define DEP_AUX 5
#define DEP_AUXPASS 6
#define DEP_CC 7
#define DEP_CCOMP 8
#define DEP_CONJ 9
#define DEP_COP 10
#define DEP_CSUBJ 11
#define DEP_CSUBJPASS 12
#define DEP_DEP 13
#define DEP_DET 14
#define DEP_DISCOURSE 15
#define DEP_DOBJ 16
#define DEP_EXPL 17
#define DEP_GOESWITH 18
#define DEP_IOBJ 19
#define DEP_MARK 20
#define DEP_MWE 21
#define DEP_NEG 22
#define DEP_NN 23
#define DEP_NPADVMOD 24
#define DEP_NSUBJ 25
#define DEP_NSUBJPASS 26
#define DEP_NUM 27
#define DEP_NUMBER 28
#define DEP_PARATAXIS 29
#define DEP_PCOMP 30
#define DEP_POBJ 31
#define DEP_POSS 32
#define DEP_POSSEIVE 33
#define DEP_PRECONJ 34
#define DEP_PREDET 35
#define DEP_PREP 36
#define DEP_PRT 37
#define DEP_PUNCT 38
#define DEP_QUANTMOD 39
#define DEP_RCMOD 40
#define DEP_ROOT 41
#define DEP_TMOD 42
#define DEP_VMOD 43
#define DEP_XCOMP 44

// Static Data
#define NULL_WORD getTaggedWord(0, 0, 0)
#define ALL       getTaggedWord(3593, 0, 0)
#define LEMUR     getTaggedWord(73918, 0, 0)
#define ANIMAL    getTaggedWord(5532, 0, 0)
#define POTTO     getTaggedWord(99965, 0, 0)
#define CAT       getTaggedWord(20852, 0, 0)
#define FURRY     getTaggedWord(50015, 0, 0)
#define HAVE      getTaggedWord(60042, 0, 0)
#define TAIL      getTaggedWord(125248, 0, 0)
#define SOME      getTaggedWord(118441, 0, 0)
#define DOG       getTaggedWord(36557, 0, 0)
#define CHASE     getTaggedWord(22520, 0, 0)
// (static data as strings)
#define ALL_STR    "3593"
#define NO_STR     "87288"
#define LEMUR_STR  "73918"
#define ANIMAL_STR "5532"
#define POTTO_STR  "99965"
#define CAT_STR    "20852"
#define FURRY_STR  "50015"
#define HAVE_STR   "60042"
#define TAIL_STR   "125248"

//
// Typedefs
//

/** 
 * A representation of a word, tagged with various bits of
 * metadata
 */
#ifdef __GNUG__
struct tagged_word {
#else
struct alignas(4) tagged_word {
#endif
  uint32_t monotonicity:2,
           sense:SENSE_ENTROPY,
           word:VOCABULARY_ENTROPY;
#ifdef __GNUG__
};
#else
} __attribute__ ((__packed__));
#endif

/** The == operator for two tagged words */
inline bool operator==(const tagged_word& lhs, const tagged_word& rhs) {
  return lhs.word == rhs.word && lhs.sense == rhs.sense && lhs.monotonicity == rhs.monotonicity;
}

/** The < operator for two tagged words */
inline bool operator<(const tagged_word& lhs, const tagged_word& rhs) {
  if (lhs.word != rhs.word) { return lhs.word < rhs.word; }
  if (lhs.sense != rhs.sense) { return lhs.sense < rhs.sense; }
  return lhs.monotonicity < rhs.monotonicity;
}

/** The != operator for two tagged words */
inline bool operator!=(const tagged_word& lhs, const tagged_word& rhs) {
  return !(lhs == rhs);
}

/** A simple helper for a word, untagged with type information */
typedef uint32_t word;

/** A simple helper for a monotonicity */
typedef uint8_t monotonicity;

/** An edge type -- for example, WORDNET_UP */
typedef uint8_t edge_type;

/** The current inference state */
typedef uint8_t inference_state;

/** An inference function (e.g., forward entailment) */
typedef uint8_t inference_function;

/**
 * A simple representation of an Edge in the graph -- that is,
 * a potential mutation of a word.
 */
struct edge {
  word      source;
  uint8_t   source_sense;
  word      sink;
  uint8_t   sink_sense;
  edge_type type;
  float     cost;
};

//
// Some Utilities
//

/**
 * Create a tagged word from a word and monotonicity
 */
inline tagged_word getTaggedWord(const word& w, const uint32_t& monotonicity) {
  tagged_word out;
  out.word = w;
  out.sense = 0;
  out.monotonicity = monotonicity;
  return out;
}

/**
 * Create a tagged word from a word, sense, and monotonicity
 */
inline tagged_word getTaggedWord(const word& w, const uint32_t sense, const uint32_t& monotonicity) {
  tagged_word out;
  out.word = w;
  out.sense = sense;
  out.monotonicity = monotonicity;
  return out;
}

#endif
