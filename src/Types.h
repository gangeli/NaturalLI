#ifndef TYPES_H
#define TYPES_H

#include <vector>
#include <stdint.h>

#include <config.h>

//
// Definitions / Enums
//

// Monotonicities
/** A simple helper for a monotonicity */
typedef uint8_t monotonicity;
#define MONOTONE_UP      0
#define MONOTONE_DOWN    1
#define MONOTONE_FLAT    2
#define MONOTONE_INVALID 3
#define MONOTONE_DEFAULT MONOTONE_UP

/** A typedef for a quantifier type (e.g., multiplicative) */
typedef uint8_t quantifier_type;
#define QUANTIFIER_TYPE_NONE 0x0
#define QUANTIFIER_TYPE_ADDITIVE 0x1
#define QUANTIFIER_TYPE_MULTIPLICATIVE 0x2
#define QUANTIFIER_TYPE_BOTH (QUANTIFIER_TYPE_ADDITIVE | QUANTIFIER_TYPE_MULTIPLICATIVE)

// Inference States
/** The current inference state */
typedef uint8_t inference_state;
#define INFER_FALSE   0
#define INFER_TRUE    1
#define INFER_UNKNOWN 2

// Inference functions
/** A typedef for a Natlog relation (e.g., forward entailment) */
typedef uint8_t natlog_relation;
#define FUNCTION_EQUIVALENT         0
#define FUNCTION_FORWARD_ENTAILMENT 1
#define FUNCTION_REVERSE_ENTAILMENT 2
#define FUNCTION_NEGATION           3
#define FUNCTION_ALTERNATION        4
#define FUNCTION_COVER              5
#define FUNCTION_INDEPENDENCE       6

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

/** An edge type -- for example, WORDNET_UP */
typedef uint8_t edge_type;


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
