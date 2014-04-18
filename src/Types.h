#ifndef TYPES_H
#define TYPES_H

#include <vector>
#include <stdint.h>

#include <config.h>

//
// Definitions / Enums
//

// Monotonicities
#define MONOTONE_FLAT 0
#define MONOTONE_UP   1
#define MONOTONE_DOWN 2

// Graph Edges
#define WORDNET_UP                   0
#define WORDNET_DOWN                 1
#define WORDNET_NOUN_ANTONYM         2
#define WORDNET_VERB_ANTONYM         3
#define WORDNET_ADJECTIVE_ANTONYM    4
#define WORDNET_ADVERB_ANTONYM       5
#define WORDNET_ADJECTIVE_PERTAINYM  6
#define WORDNET_ADVERB_PERTAINYM     7
#define WORDNET_ADJECTIVE_RELATED    8
#define ANGLE_NN                     9
#define FREEBASE_UP                  10
#define FREEBASE_DOWN                11

#define EDGE_ADDS_BEGIN              12  // should be the same as the first ADD_X item
#define ADD_NOUN                     12
#define ADD_VERB                     13
#define ADD_ADJ                      14
#define ADD_ADV                      15
#define ADD_OTHER                    16  // NOTE: never have more than 8 insertion types. @see Trie.h

#define DEL_NOUN                     17
#define DEL_VERB                     18
#define DEL_ADJ                      19
#define DEL_ADV                      20
#define DEL_OTHER                    21

#define MORPH_TO_LEMMA               22
#define MORPH_FROM_LEMMA             23
#define MORPH_FUDGE_NUMBER           24
#define SENSE_REMOVE                 25
#define SENSE_ADD                    26

#define QUANTIFIER_WEAKEN            27
#define QUANTIFIER_NEGATE            28
#define QUANTIFIER_STRENGTHEN        29
#define QUANTIFIER_REWORD            30

#define NUM_EDGE_TYPES               31


// Inference States
#define INFER_EQUIVALENT         0
#define INFER_FORWARD_ENTAILMENT 1
#define INFER_REVERSE_ENTAILMENT 2
#define INFER_NEGATION           3
#define INFER_ALTERNATION        4
#define INFER_COVER              5
#define INFER_INDEPENDENCE       6

// Static Data
#define NULL_WORD getTaggedWord(0, 0, 0)
#define LEMUR     getTaggedWord(2480367, 0, 0)
#define ANIMAL    getTaggedWord(2730, 0, 0)
#define TIMONE    getTaggedWord(18828894, 0, 0)
#define CAT       getTaggedWord(2432, 0, 0)
#define HAVE      getTaggedWord(3830, 0, 0)
#define TAIL      getTaggedWord(23480, 0, 0)
// (static data as strings)
#define LEMUR_STR  "2480367"
#define ANIMAL_STR "2730"
#define TIMONE_STR "18828894"
#define CAT_STR    "2432"
#define HAVE_STR   "3830"
#define TAIL_STR   "23480"

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

/**
 * A simple representation of an Edge in the graph -- that is,
 * a potential mutation of a word.
 */
struct edge {
  word      sink;
  uint8_t   sense;
  edge_type type;
  float     cost;
};

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
