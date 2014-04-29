#ifndef TYPES_H
#define TYPES_H

#include <vector>
#include <stdint.h>

#include <config.h>

//
// Definitions / Enums
//

// Monotonicities
#define MONOTONE_UP   0
#define MONOTONE_DOWN 1
#define MONOTONE_FLAT 2
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

#define MONOTONE_INDEPENDENT_BEGIN   29  // should be the same as the first index after which monotonicity doesn't matter
#define QUANTIFIER_WEAKEN            29
#define QUANTIFIER_NEGATE            30
#define QUANTIFIER_STRENGTHEN        31
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

// Static Data
#define NULL_WORD getTaggedWord(0, 0, 0)
#define LEMUR     getTaggedWord(11863, 0, 0)
#define ANIMAL    getTaggedWord(3605, 0, 0)
#define POTTO     getTaggedWord(112136, 0, 0)
#define CAT       getTaggedWord(3263, 0, 0)
#define HAVE      getTaggedWord(1265, 0, 0)
#define TAIL      getTaggedWord(26812, 0, 0)
#define SOME      getTaggedWord(8489, 0, 0)
#define DOG       getTaggedWord(20841, 0, 0)
#define CHASE     getTaggedWord(37234, 0, 0)
// (static data as strings)
#define LEMUR_STR  "11863"
#define ANIMAL_STR "3605"
#define POTTO_STR  "112136"
#define CAT_STR    "3263"
#define HAVE_STR   "1265"
#define TAIL_STR   "26812"

//
// Typedefs
//

/** 
 * A representation of a word, tagged with various bits of
 * metadata
 */
typedef struct {
  uint32_t monotonicity:2,
           sense:5,
           word:25;
}__attribute__((packed)) tagged_word;

/** The == operator for two tagged words */
inline bool operator==(const tagged_word& lhs, const tagged_word& rhs) {
  return lhs.word == rhs.word && lhs.sense == rhs.sense && lhs.monotonicity == rhs.monotonicity;
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
