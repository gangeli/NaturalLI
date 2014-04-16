#ifndef CONFIG_H
#define CONFIG_H

#include <string>
#include <stdint.h>
#include <vector>
#include <assert.h>

//
// Configuration (TODO(gabor) should really be set in ./configure script)
//
static std::string   PG_HOST          = "john0.stanford.edu";
static uint16_t      PG_PORT          = 4243;
static std::string   PG_DATABASE      = "truth";
static std::string   PG_USER          = "gabor";
static std::string   PG_PASSWORD      = "gabor";

static bool          RAMCLOUD_LOADED  = true;
static std::string   RAMCLOUD_HOST    = "0.0.0.0";
static uint16_t      RAMCLOUD_PORT    = 12246;
 
static std::string   PG_TABLE_WORD    = "word";
static std::string   PG_TABLE_EDGE    = "edge";
static std::string   PG_TABLE_FACT    = "fact";

#define MAX_FACT_LENGTH                 255
#define MAX_FACT_LENGTH_IN_LONG_WORDS   4
#define MAX_COMPLETIONS                 25
#define MAX_COMPLETION_SCAN             100

//
// Typedefs
//
typedef uint32_t word;
typedef uint32_t tagged_word;
typedef uint8_t edge_type;
typedef uint8_t monotonicity;
typedef uint8_t inference_state;

//
// Static Defines
//
/** The maximum number of elements to pop off the queue for a search */
#define SEARCH_TIMEOUT   100000
/** The minimum count for a fact to be seen to be added to the KB */
#define MIN_FACT_COUNT   1

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

#define NUM_EDGE_TYPES               27


// Inference States
#define INFER_EQUIVALENT         0
#define INFER_FORWARD_ENTAILMENT 1
#define INFER_REVERSE_ENTAILMENT 2
#define INFER_NEGATION           3
#define INFER_ALTERNATION        4
#define INFER_COVER              5
#define INFER_INDEPENDENCE       6

// Static Data
#define LEMUR  2480367
#define ANIMAL 2730
#define TIMONE 18828894
#define CAT    2432
#define HAVE   3830
#define TAIL   23480
// (static data as strings)
#define LEMUR_STR  "2480367"
#define ANIMAL_STR "2730"
#define TIMONE_STR "18828894"
#define CAT_STR    "2432"
#define HAVE_STR   "3830"
#define TAIL_STR   "23480"

#endif
