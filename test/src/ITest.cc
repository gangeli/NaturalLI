#include <limits.h>
#include <vector>
#include <stdint.h>

#include <config.h>
#include "gtest/gtest.h"
#include "Graph.h"
#include "FactDB.h"
#include "Trie.h"
#include "Utils.h"

#ifdef _WIN32
#include <intrin.h>
uint64_t rdtsc(){
    return __rdtsc();
}
#else
uint64_t rdtsc(){
    unsigned int lo,hi;
    __asm__ __volatile__ ("rdtsc" : "=a" (lo), "=d" (hi));
    return ((uint64_t)hi << 32) | lo;
}
#endif

using namespace std;
#define NUM_FACTS_TO_CHECK    1000000
#define NUM_WORDS_TO_CONSIDER 100000

/**
 * Make sure that we are reading the fact database in a repeatable order.
 * This is primarily important for TrieITest::FactsAppearInRealFactDB to
 * ensure that the two means of reading from the database should agree.
 */
TEST(PostgresITest, RepeatableFactOrdering) {
  vector<vector<word>> facts1 = ReadLiteralFacts( NUM_FACTS_TO_CHECK );
  vector<vector<word>> facts2 = ReadLiteralFacts( NUM_FACTS_TO_CHECK );
  ASSERT_GE(facts1.size(), NUM_FACTS_TO_CHECK);

  for (int i = 0; i < NUM_FACTS_TO_CHECK; ++i) {
    EXPECT_EQ(facts1[i], facts2[i]);
  }
}

/**
 * Check to make sure all facts that are loaded are actually in the database.
 */
TEST(TrieITest, FactsAppearInRealFactDB) {
  vector<vector<word>> facts = ReadLiteralFacts( NUM_FACTS_TO_CHECK );
  printf("Read facts as vector\n");
  FactDB*              db    = ReadFactTrie(     NUM_FACTS_TO_CHECK, NULL );
  printf("Read facts as Trie\n");

  printf("Checking containment...\n");
  uint32_t numContains = 0;
  for (vector<vector<word>>::iterator it = facts.begin(); it != facts.end(); ++it) {
    tagged_word fact[it->size()];
    for (int i = 0; i < it->size(); ++i) {
      fact[i] = getTaggedWord((*it)[i], 0, 0);
    }
    bool contains = db->contains(fact, it->size());
    EXPECT_TRUE(contains);
    if (contains) { numContains += 1; }
  }
  EXPECT_EQ(NUM_FACTS_TO_CHECK, numContains);
  printf("  done.\n");

  delete db;
}



/**
 * Iterate over the entire graph, to make sure that we are
 * not going to either segfault or return an invalid edge.
 */
TEST(TrieITest, CompletionsValid) {
  edge edges[MAX_COMPLETIONS];

  FactDB* db   = ReadFactTrie(NUM_FACTS_TO_CHECK, NULL);
  Graph* graph = ReadGraph();
  uint64_t numWords = graph->keys().size();

  tagged_word canInsert[256];
  edge_type   canInsertType[256];
  uint8_t     canInsertLength;

  // Unigrams
  uint32_t numCompletions = 0;
  uint32_t numNonzeroSense = 0;
  uint32_t numWordsToConsider = NUM_WORDS_TO_CONSIDER;
  if (numWords < NUM_WORDS_TO_CONSIDER) { numWordsToConsider = numWords; }
  printf("Considering %u start words\n", numWordsToConsider);
  uint64_t beginTime = rdtsc();
  // (loop starts here)
  for (word w = 0; w < numWordsToConsider; ++w) {  // for each word...
    bool wordHasCompletion = false;
    bool wordHasNonzeroSense = false;
    for (uint32_t s = 0; s < 8; ++s) {  // for each word sense...
      for (monotonicity m = 0; m < 3; ++m) {  // for each monotonicity mark (shouldn't matter)...
        // Set up the query
        tagged_word word = getTaggedWord(w, s, m);
        // Issue the query
        db->contains(&word, 1, 0, edges);
        // Make sure the response [insertable facts] is valid
        for (uint32_t i = 0; i < MAX_COMPLETIONS; ++i) {
          if (edges[i].source == 0) { break; }
          EXPECT_LT(edges[i].type, NUM_EDGE_TYPES);
          EXPECT_LT(edges[i].source, numWords);
          EXPECT_LT(edges[i].source_sense, 32);
          EXPECT_EQ(0, edges[i].sink);
          EXPECT_EQ(0, edges[i].sink_sense);
          if (edges[i].source_sense > 0) {
            wordHasNonzeroSense = true;
          }
          wordHasCompletion = true;
        }
      }
    }
    if (wordHasCompletion) {
      numCompletions += 1;
    }
    if (wordHasNonzeroSense) {
      numNonzeroSense += 1;
    }
  }
  uint64_t endTime = rdtsc();
  printf("%u inputs have some completion (%u with non-null sense)\n", numCompletions, numNonzeroSense);
  printf("  [took %lu CPU ticks; %2.2fx the time for the control]\n",
            (endTime-beginTime), ((double) (endTime - beginTime)) / ((double) (185l * 8l * 3l * 100000l)));

  // Make sure we have some completions
  EXPECT_GT(numCompletions, 0);
  EXPECT_GT(numNonzeroSense, 0);

//  // Make sure we're not taking too long
//  // 1,500,000 ticks per containment check
//  EXPECT_LT(endTime-beginTime, 7000l * numWordsToConsider * 8l * 3l);
//  // ...or too short (else we should update the test)
//  EXPECT_GT(endTime-beginTime, 5000l * numWordsToConsider * 8l * 3l);

  delete db;
  delete graph;
}



/**
 * Iterate over the entire graph, to make sure that we are
 * not going to either segfault or return an invalid edge.
 */
TEST(GraphITest, AllEdgesValid) {
  Graph* graph = ReadGraph();
  vector<word> keys = graph->keys();
  for (int w = 0; w < keys.size(); ++w) {
    uint32_t numEdges = 0;
    const edge* edges = graph->incomingEdgesFast(getTaggedWord(w, 0, 0), &numEdges);
    for (uint32_t i = 0; i < numEdges; ++i) {
      ASSERT_LT(edges[i].sink, keys.size());
      ASSERT_LT(edges[i].source, keys.size());
      ASSERT_GE(edges[i].cost, 0.0);
    }
  }
  delete graph;
}
