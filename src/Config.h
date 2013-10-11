#ifndef CONFIG_H
#define CONFIG_H

#include <string>
#include <stdint.h>
#include <vector>

using namespace std;

//
// Configuration
//
static string   PG_HOST        = "john0.stanford.edu";
static uint16_t PG_PORT        = 4243;
static string   PG_DATABASE    = "truth";
static string   PG_USER        = "gabor";
static string   PG_PASSWORD    = "gabor";

static string   RAMCLOUD_HOST  = "127.0.0.1";
static uint16_t RAMCLOUD_PORT  = 12242;
 
static string   PG_TABLE_WORD  = "word_indexer";
static string   PG_TABLE_EDGE  = "edge";
static string   PG_TABLE_FACT  = "fact_partial";

static bool     USE_RAMCLOUD   = false;

static uint64_t SEARCH_TIMEOUT = 1000000;

//
// Typedefs
//
typedef uint32_t word;
typedef uint8_t edge_type;

#endif
