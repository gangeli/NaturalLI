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
// DO NOT EDIT: generated with `source rc && edgeTypes`
#define ANGLE_NN     0
#define ANTONYM     1
#define HOLONYM     2
#define HYPERNYM     3
#define HYPONYM     4
#define MERONYM     5
#define QUANTIFIER_DOWN     6
#define QUANTIFIER_NEGATE     7
#define QUANTIFIER_REWORD     8
#define QUANTIFIER_UP     9
#define SENSE_ADD     10
#define SENSE_REMOVE     11
#define SYNONYM     12
#define VERB_ENTAIL     13


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
// DO NOT EDIT: generated with `source rc && dummyVocab`
#define ALL  getTaggedWord(3004, 0, 0)
#define ALL_STR  "3004"
#define LEMUR  getTaggedWord(65392, 0, 0)
#define LEMUR_STR  "65392"
#define ANIMAL  getTaggedWord(4663, 0, 0)
#define ANIMAL_STR  "4663"
#define POTTO  getTaggedWord(88397, 0, 0)
#define POTTO_STR  "88397"
#define CAT  getTaggedWord(18116, 0, 0)
#define CAT_STR  "18116"
#define FURRY  getTaggedWord(0, 0, 0)
#define FURRY_STR  "0"
#define HAVE  getTaggedWord(53880, 0, 0)
#define HAVE_STR  "53880"
#define TAIL  getTaggedWord(111051, 0, 0)
#define TAIL_STR  "111051"
#define SOME  getTaggedWord(104804, 0, 0)
#define SOME_STR  "104804"
#define DOG  getTaggedWord(32628, 0, 0)
#define DOG_STR  "32628"
#define CHASE  getTaggedWord(19615, 0, 0)
#define CHASE_STR  "19615"
#define NO  getTaggedWord(77169, 0, 0)
#define NO_STR  "77169"

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
