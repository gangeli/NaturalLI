#include <limits.h>

#include "gtest/gtest.h"
#include "Trie.h"

class TrieTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    trie = new Trie();
  }

  virtual void TearDown() {
    delete trie;
  }
  
  Trie* trie;
  uint32_t buffer[32];
  word outBuffer[256];
  edge_type edgeBuffer[256];
  uint32_t savedMinFactCount;
  uint32_t savedMinCompletionW;

  void testCompletion(
      uint8_t fact1Length, uint32_t fact11, uint32_t fact12, uint32_t fact13,
      uint8_t fact2Length, uint32_t fact21, uint32_t fact22, uint32_t fact23,
      uint8_t queryLength, uint32_t factq1, uint32_t factq2,
      uint8_t insert1Length, uint32_t insert11, uint32_t insert12, uint32_t insert13,
      uint8_t insert2Length, uint32_t insert21, uint32_t insert22, uint32_t insert23,
      uint8_t insert3Length, uint32_t insert31, uint32_t insert32, uint32_t insert33) {

    std::vector<edge> edges[MAX_FACT_LENGTH + 1];

    // Add fact 2
    if (fact1Length > 0) { buffer[0] = fact11; }
    if (fact1Length > 1) { buffer[1] = fact12; }
    if (fact1Length > 2) { buffer[2] = fact13; }
    trie->add(buffer, fact1Length);
    EXPECT_TRUE(trie->contains(buffer, fact1Length));
    
    // Add fact 2
    if (fact2Length > 0) { buffer[0] = fact21; }
    if (fact2Length > 1) { buffer[1] = fact22; }
    if (fact2Length > 2) { buffer[2] = fact23; }
    trie->add(buffer, fact2Length);
    if (fact2Length > 0) {
      EXPECT_TRUE(trie->contains(buffer, fact2Length));
    }

    // Issue children query
    if (queryLength > 0) { buffer[0] = factq1; }
    if (queryLength > 1) { buffer[1] = factq2; }
    EXPECT_FALSE(trie->contains(buffer, queryLength, edges));

    // Run assertions
    EXPECT_EQ(insert1Length, edges[0].size());
    if (insert1Length > 0) { EXPECT_EQ(insert11, edges[0][0].sink); }
    if (insert1Length > 1) { EXPECT_EQ(insert12, edges[0][1].sink); }
    if (insert1Length > 2) { EXPECT_EQ(insert13, edges[0][2].sink); }
    for (uint32_t i = 0; i < insert1Length; ++i) {
      EXPECT_EQ(0, edges[0][i].sense);
      EXPECT_EQ(ADD_OTHER, edges[0][i].type);
    }
    EXPECT_EQ(insert2Length, edges[1].size());
    if (insert2Length > 0) { EXPECT_EQ(insert21, edges[1][0].sink); }
    if (insert2Length > 1) { EXPECT_EQ(insert22, edges[1][1].sink); }
    if (insert2Length > 2) { EXPECT_EQ(insert23, edges[1][2].sink); }
    for (uint32_t i = 0; i < insert2Length; ++i) {
      EXPECT_EQ(0, edges[1][i].sense);
      EXPECT_EQ(ADD_OTHER, edges[1][i].type);
    }
    EXPECT_EQ(insert3Length, edges[2].size());
    if (insert3Length > 0) { EXPECT_EQ(insert31, edges[2][0].sink); }
    if (insert3Length > 1) { EXPECT_EQ(insert32, edges[2][1].sink); }
    if (insert3Length > 2) { EXPECT_EQ(insert33, edges[2][2].sink); }
    for (uint32_t i = 0; i < insert3Length; ++i) {
      EXPECT_EQ(0, edges[2][i].sense);
      EXPECT_EQ(ADD_OTHER, edges[2][i].type);
    }
  }
};

// Make sure we can add to the Trie
TEST_F(TrieTest, CanAdd) {
  buffer[0] = 42;
  trie->add(buffer, 1);
}

// Make sure we can add/contains depth 1
TEST_F(TrieTest, CanAddContainsDepth1) {
  buffer[0] = 42;
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 1));
}

// Make sure we can add/contains depth 2
TEST_F(TrieTest, CanAddContainsDepth2) {
  buffer[0] = 42;
  buffer[1] = 43;
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
  buffer[0] = 42;
  buffer[1] = 43;
  trie->add(buffer, 2);
  buffer[1] = 44;
  trie->add(buffer, 2);
  buffer[0] = 7;
  trie->add(buffer, 2);
  // Some tests
  buffer[0] = 42; buffer[1] = 43;
  EXPECT_TRUE(trie->contains(buffer, 2));
  buffer[0] = 7; buffer[1] = 44;
  EXPECT_TRUE(trie->contains(buffer, 2));
  buffer[0] = 42; buffer[1] = 44;
  EXPECT_TRUE(trie->contains(buffer, 2));
  // Some false tests
  buffer[0] = 7; buffer[1] = 42;
  EXPECT_FALSE(trie->contains(buffer, 2));
  buffer[0] = 42; buffer[1] = 7;
  EXPECT_FALSE(trie->contains(buffer, 2));
  // Some tricks
  buffer[0] = 42; buffer[1] = 43; buffer[2] = 43;
  EXPECT_FALSE(trie->contains(buffer, 3));
  EXPECT_FALSE(trie->contains(buffer, 0));
  EXPECT_FALSE(trie->contains(buffer, 1));
}

// Make sure we can add to the Trie
TEST_F(TrieTest, FactDBCanAdd) {
  buffer[0] = 42;
  trie->add(buffer, 1);
}

// Make sure we can add/contains depth 1
TEST_F(TrieTest, FactDBCanAddContainsDepth1) {
  buffer[0] = 42;
  trie->add(buffer, 1);
  EXPECT_TRUE(trie->contains(buffer, 1));
}

// Make sure we can add/contains depth 2
TEST_F(TrieTest, FactDBCanAddContainsDepth2) {
  buffer[0] = 42;
  buffer[1] = 43;
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
    2,     1,   2,   255,
    0,     255, 255, 255,
    // Query
    1,     1,   255,
    // Checks
    0,     255, 255, 255,
    1,     2,   255, 255,
    0,     255, 255, 255);
}

// Test insert at end of fact
TEST_F(TrieTest, CompletionAtEndOfFact) {
  testCompletion(
    // Entries
    3,     1,   2,   3,
    3,     1,   2,   4,
    // Query
    2,     1,   2,
    // Checks
    0,     255, 255, 255,
    0,     255, 255, 255,
    2,     3,   4,   255);
}

// Test duplicate children
TEST_F(TrieTest, CompletionDuplicateLexicalEntry) {
  testCompletion(
    // Entries
    3,     1,   2,   1,
    0,     255, 255, 255,
    // Query
    2,     1,   2,
    // Checks
    0,     255, 255, 255,
    0,     255, 255, 255,
    1,     1,   255, 255);
}

// Test missing begin
TEST_F(TrieTest, CompletionFromStart) {
  testCompletion(
    // Entries
    3,     2,   2,   1,
    1,     3,   255, 255,
    // Query
    0,     255, 255,
    // Checks
    2,     2,   3,   255,
    0,     255, 255, 255,
    0,     255, 255, 255);
}
