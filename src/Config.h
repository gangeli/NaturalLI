#ifndef CONFIG_H
#define CONFIG_H

#include <string>
#include <stdint.h>
#include <vector>

using namespace std;

//
// Configuration
//
//static string   PG_HOST          = "john0.stanford.edu";
static string   PG_HOST          = "localhost";
static uint16_t PG_PORT          = 4243;
static string   PG_DATABASE      = "truth";
static string   PG_USER          = "gabor";
static string   PG_PASSWORD      = "gabor";

static bool     RAMCLOUD_LOADED  = true;
static string   RAMCLOUD_HOST    = "0.0.0.0";
static uint16_t RAMCLOUD_PORT    = 12246;
 
static string   PG_TABLE_WORD    = "word_indexer";
static string   PG_TABLE_EDGE    = "edge";
static string   PG_TABLE_FACT    = "fact_partial";

static uint64_t SEARCH_TIMEOUT   = 100000;

//
// Typedefs
//
typedef uint32_t word;
typedef uint8_t edge_type;
typedef uint8_t monotonicity;

#define MONOTONE_FLAT 0
#define MONOTONE_UP   1
#define MONOTONE_DOWN 2

#define NUM_EDGE_TYPES 15
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
#define MORPH_TO_LEMMA               12
#define MORPH_FROM_LEMMA             13
#define MORPH_FUDGE_NUMBER           14

#endif
