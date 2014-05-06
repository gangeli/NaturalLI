#include <limits.h>

#include "gtest/gtest.h"
#include "Trie.h"

inline tagged_word getTaggedWord(word w) { return getTaggedWord(w, 0, 0); }

class TrieTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    trie = new TrieRoot();
  }

  virtual void TearDown() {
    delete trie;
  }
  
  Trie* trie;
  tagged_word buffer[32];
  word outBuffer[256];
  edge_type edgeBuffer[256];
  uint32_t savedMinFactCount;
  uint32_t savedMinCompletionW;

  void testCompletion(
      uint8_t fact1Length, uint32_t fact11, uint32_t fact12, uint32_t fact13,
      uint8_t fact2Length, uint32_t fact21, uint32_t fact22, uint32_t fact23,
      uint8_t queryLength, uint32_t factq1, uint32_t factq2,
      int16_t index, uint32_t insert1, uint32_t insert2, uint32_t insert3) {

    edge edges[MAX_COMPLETIONS];

    // Add fact 2
    if (fact1Length > 0) { buffer[0] = getTaggedWord(fact11); }
    if (fact1Length > 1) { buffer[1] = getTaggedWord(fact12); }
    if (fact1Length > 2) { buffer[2] = getTaggedWord(fact13); }
    trie->add(buffer, fact1Length);
    EXPECT_TRUE(trie->contains(buffer, fact1Length));
    
    // Add fact 2
    if (fact2Length > 0) { buffer[0] = getTaggedWord(fact21); }
    if (fact2Length > 1) { buffer[1] = getTaggedWord(fact22); }
    if (fact2Length > 2) { buffer[2] = getTaggedWord(fact23); }
    trie->add(buffer, fact2Length);
    if (fact2Length > 0) {
      EXPECT_TRUE(trie->contains(buffer, fact2Length));
    }

    // Issue children query
    if (queryLength > 0) { buffer[0] = getTaggedWord(factq1); }
    if (queryLength > 1) { buffer[1] = getTaggedWord(factq2); }
    edges[0].source = 0;
    edges[1].source = 0;
    edges[2].source = 0;
    EXPECT_FALSE(trie->contains(buffer, queryLength, index, edges));

    // Run assertions
    EXPECT_EQ(insert1, edges[0].source);
    EXPECT_EQ(insert2, edges[1].source);
    EXPECT_EQ(insert3, edges[2].source);
  }
};

// Make sure we can add to the Trie
TEST_F(TrieTest, CanAdd) {
  buffer[0] = getTaggedWord(42);
  trie->add(buffer, 1);
}

// Make sure we can add/contains depth 1
TEST_F(TrieTest, CanAddContainsDepth1) {
  buffer[0] = getTaggedWord(42);
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 1));
}

// Make sure we can add/contains depth 2
TEST_F(TrieTest, CanAddContainsDepth2) {
  buffer[0] = getTaggedWord(42);
  buffer[1] = getTaggedWord(43);
  // Full string
  trie->add(buffer, 2);
  EXPECT_TRUE(trie->contains(buffer, 2));
  EXPECT_FALSE(trie->contains(buffer, 1));
  // Add substring
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 2));
  EXPECT_TRUE(trie->contains(buffer, 1));
}

// A silly toy trie
TEST_F(TrieTest, ToyExample) {
  buffer[0] = getTaggedWord(42);
  buffer[1] = getTaggedWord(43);
  trie->add(buffer, 2);
  buffer[1] = getTaggedWord(44);
  trie->add(buffer, 2);
  buffer[0] = getTaggedWord(7);
  trie->add(buffer, 2);
  // Some tests
  buffer[0] = getTaggedWord(42); buffer[1] = getTaggedWord(43);
  EXPECT_TRUE(trie->contains(buffer, 2));
  buffer[0] = getTaggedWord(7); buffer[1] = getTaggedWord(44);
  EXPECT_TRUE(trie->contains(buffer, 2));
  buffer[0] = getTaggedWord(42); buffer[1] = getTaggedWord(44);
  EXPECT_TRUE(trie->contains(buffer, 2));
  // Some false tests
  buffer[0] = getTaggedWord(7); buffer[1] = getTaggedWord(42);
  EXPECT_FALSE(trie->contains(buffer, 2));
  buffer[0] = getTaggedWord(42); buffer[1] = getTaggedWord(7);
  EXPECT_FALSE(trie->contains(buffer, 2));
  // Some tricks
  buffer[0] = getTaggedWord(42); buffer[1] = getTaggedWord(43); buffer[2] = getTaggedWord(43);
  EXPECT_FALSE(trie->contains(buffer, 3));
  EXPECT_FALSE(trie->contains(buffer, 0));
  EXPECT_FALSE(trie->contains(buffer, 1));
}

// Make sure we can add to the Trie
TEST_F(TrieTest, FactDBCanAdd) {
  buffer[0] = getTaggedWord(42);
  trie->add(buffer, 1);
}

// Make sure we can add/contains depth 1
TEST_F(TrieTest, FactDBCanAddContainsDepth1) {
  buffer[0] = getTaggedWord(42);
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 1));
}

// Make sure we can add/contains depth 2
TEST_F(TrieTest, FactDBCanAddContainsDepth2) {
  buffer[0] = getTaggedWord(42);
  buffer[1] = getTaggedWord(43);
  // Full string
  trie->add(buffer, 2);
  EXPECT_TRUE(trie->contains(buffer, 2));
  EXPECT_FALSE(trie->contains(buffer, 1));
  // Add substring
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 2));
  EXPECT_TRUE(trie->contains(buffer, 1));
}


// Test children return
TEST_F(TrieTest, CompletionSimpleEdge) {
  testCompletion(
    // Entries
    2,     1,   2,   255,  // fact 1
    0,     255, 255, 255,  // fact 2
    // Query
    1,     1,   255,       // query fact
    0,                     // completion index
    // Checks
    2, 0, 0);              // valid sinks
}

// Test insert at end of fact
TEST_F(TrieTest, CompletionAtEndOfFact) {
  testCompletion(
    // Entries
    3,     1,   2,   3,  // fact 1
    3,     1,   2,   4,  // fact 2
    // Query                                 
    2,     1,   2,       // query fact
    1,                   // completion index                    
    // Checks            
    3, 4, 0);            // valid sinks
}

// Test duplicate children
TEST_F(TrieTest, CompletionDuplicateLexicalEntry) {
  testCompletion(
    // Entries
    3,     1,   2,   1,    // fact 1
    0,     255, 255, 255,  // fact 2
    // Query                                    
    2,     1,   2,         // query fact
    1,                     // completion index                     
    // Checks              
    1, 0, 0);              // valid sinks
}

// Test missing begin
TEST_F(TrieTest, CompletionFromStart) {
  testCompletion(
    // Entries
    3,     2,   2,   1,    // fact 1
    1,     3,   255, 255,  // fact 2
    // Query                                    
    1,     2,   255,       // query fact
    -1,                    // completion index                     
    // Checks              
    2, 0, 0);              // valid sinks
}

// Test missing begin (empty query)
TEST_F(TrieTest, CompletionFromEmptyQuery) {
  testCompletion(
    // Entries
    3,     2,   2,   1,    // fact 1
    1,     3,   255, 255,  // fact 2
    // Query                                    
    0,   255,   255,       // query fact
    -1,                    // completion index                     
    // Checks              
    3, 0, 0);              // valid sinks
}

// Test missing begin (no skips)
TEST_F(TrieTest, CompletionFromNoSkipGrams) {
  testCompletion(
    // Entries
    3,     2,   2,   1,    // fact 1
    1,     3,   255, 255,  // fact 2
    // Query                                    
    2,   100,   101,       // query fact
    -1,                    // completion index                     
    // Checks              
    2, 3, 0);              // valid sinks
}

TEST_F(TrieTest, EnsureTrieStructSizes) {
  EXPECT_EQ(1, sizeof(packed_edge));
  EXPECT_EQ(4, sizeof(trie_data));
}

TEST_F(TrieTest, EnsureTrieSize) {
  EXPECT_EQ(8, sizeof(uint64_t));
#if HIGH_MEMORY
  EXPECT_EQ(32, sizeof(*trie));
#else
  EXPECT_EQ(24, sizeof(*trie));
#endif
  // Add some facts
  buffer[0] = getTaggedWord(1);
  buffer[1] = getTaggedWord(2);
  buffer[2] = getTaggedWord(3);
  buffer[3] = getTaggedWord(4);
  buffer[4] = getTaggedWord(5);
  buffer[5] = getTaggedWord(6);
  buffer[6] = getTaggedWord(7);
  buffer[7] = getTaggedWord(8);
  buffer[8] = getTaggedWord(9);
  buffer[9] = getTaggedWord(10);
  trie->add(buffer, 10);
  // Check size @ 1 elem
  uint64_t onFacts = 0; uint64_t onStructure = 0; uint64_t onCompletionCaching = 0;
  uint64_t usage = trie->memoryUsage(&onFacts, &onStructure, &onCompletionCaching);
  ASSERT_EQ(onFacts + onStructure + onCompletionCaching, usage);
  EXPECT_EQ(usage, trie->memoryUsage(NULL, NULL, NULL));
#if HIGH_MEMORY
  EXPECT_EQ(516, usage);
  EXPECT_EQ(10 * sizeof(tagged_word), onFacts);
  EXPECT_EQ(432, onStructure);
  EXPECT_EQ(44, onCompletionCaching);
#else
  EXPECT_EQ(416, usage);
  EXPECT_EQ(10 * sizeof(tagged_word), onFacts);  // 40 bytes
  EXPECT_EQ(344, onStructure);
  EXPECT_EQ(32, onCompletionCaching);
#endif
  buffer[9] = getTaggedWord(11);
  trie->add(buffer, 10);
}
