#include <limits.h>
#include <vector>

#include "gtest/gtest.h"
#include "Config.h"
#include "Graph.h"
#include "FactDB.h"
#include "Trie.h"
#include "Utils.h"

using namespace std;

/**
 * Iterate over the entire graph, to make sure that we are
 * not going to either segfault or return an invalid edge.
 */
TEST(TrieFactDB, CompletionsValid) {
  FactDB* db   = ReadFactTrie(1000000);
  Graph* graph = ReadGraph();
  uint64_t numWords = graph->keys().size();

  tagged_word canInsert[256];
  edge_type   canInsertType[256];
  uint8_t     canInsertLength;

  // Unigrams
  uint32_t numCompletions = 0;
  uint32_t numWordsToConsider = 100000;
  printf("Considering %u start words\n", numWordsToConsider);
  if (numWords < numWordsToConsider) { numWordsToConsider = numWords; }
  // (loop starts here)
  for (word w = 0; w < numWordsToConsider; ++w) {  // for each word...
    bool wordHasCompletion = false;
    for (uint32_t s = 0; s < 8; ++s) {  // for each word sense...
      for (monotonicity m = 0; m < 3; ++m) {  // for each monotonicity mark (shouldn't matter)...
        // Set up the query
        tagged_word word = getTaggedWord(w, s, m);
        canInsertLength = 255;
        // Issue the query
        db->contains(&word, 1, canInsert, canInsertType, &canInsertLength);
        // Make sure the response [insertable facts] is valid
        for (uint32_t i = 0; i < canInsertLength; ++i) {
          EXPECT_LT(canInsertType[i], NUM_EDGE_TYPES);
          EXPECT_LT(canInsert[i], numWords);
          EXPECT_EQ(0, getSense(canInsert[i]));
          EXPECT_EQ(MONOTONE_FLAT, getMonotonicity(canInsert[i]));
        }
        // Update whether any edges were found
        if (canInsertLength > 0) { wordHasCompletion = true; }
      }
    }
    if (wordHasCompletion) {
      numCompletions += 1;
    }
  }
  printf("%u inputs have some completion\n", numCompletions);
  EXPECT_GT(numCompletions, 0);

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
    const edge* edges = graph->outgoingEdgesFast(w, &numEdges);
    for (uint32_t i = 0; i < numEdges; ++i) {
      ASSERT_LT(edges[i].sink, keys.size());
      ASSERT_GE(edges[i].cost, 0.0);
    }
  }
  delete graph;
}

