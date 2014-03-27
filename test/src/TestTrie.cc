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
      uint8_t insert2Length, uint32_t insert21, uint32_t insert22, uint32_t insert23) {

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
    EXPECT_EQ(insert2Length, edges[1].size());
    if (insert2Length > 0) { EXPECT_EQ(insert21, edges[1][0].sink); }
    if (insert2Length > 1) { EXPECT_EQ(insert22, edges[1][1].sink); }
    if (insert2Length > 2) { EXPECT_EQ(insert23, edges[1][2].sink); }
  }
};

// Test children return
TEST_F(TrieTest, SimpleEdgeCompletion) {
  // insert [1, 2]; complete 1 with [2]
  testCompletion(
    // Entries
    2,     1,   2,   255,
    0,     255, 255, 255,
    // Query
    1,     1,   255,
    // Checks
    0,     255, 255, 255,
    1,     2,   255, 255);
}

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



//// Make sure we can complete facts in the DB
//TEST_F(TrieTest, FactDBCompletion) {
//  trie->addValidInsertion(1, DEL_NOUN);
//  trie->addValidInsertion(2, DEL_NOUN);
//  trie->addValidInsertion(3, DEL_NOUN);
//  trie->addValidInsertion(4, DEL_NOUN);
//  trie->addValidInsertion(5, DEL_NOUN);
//  // Add {2, 3, 4, 5} to co-occur with 1
//  buffer[0] = 1; buffer[1] = 2;
//  trie->add(buffer, 2);
//  buffer[0] = 1; buffer[1] = 3;
//  trie->add(buffer, 2);
//  buffer[0] = 4; buffer[1] = 1;
//  trie->add(buffer, 2);
//  buffer[0] = 5; buffer[1] = 2; buffer[2] = 1;
//  trie->add(buffer, 3);
//  
//  // Test that {2, 3, 4, 5} are proposed from 1
//  uint8_t outLength = 255;
//  outBuffer[0] = 0;
//  outBuffer[1] = 0;
//  outBuffer[2] = 0;
//  outBuffer[3] = 0;
//  buffer[0] = 1;
//  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(3, outLength);
//  EXPECT_EQ(2, outBuffer[0]);
//  EXPECT_EQ(3, outBuffer[1]);
//  EXPECT_EQ(4, outBuffer[2]);
//}
//
//// Make sure we can complete facts in the DB
//TEST_F(TrieTest, FactDBCompletionNoMatch) {
//  // Add {2, 3, 4, 5} to co-occur with 1
//  buffer[0] = 1; buffer[1] = 2;
//  trie->add(buffer, 2);
//  buffer[0] = 1; buffer[1] = 3;
//  trie->add(buffer, 2);
//  buffer[0] = 4; buffer[1] = 1;
//  trie->add(buffer, 2);
//  buffer[0] = 5; buffer[1] = 2; buffer[2] = 1;
//  trie->add(buffer, 3);
//  
//  // Test that nothing is proposed from 6
//  uint8_t outLength = 255;
//  outBuffer[0] = 0;
//  outBuffer[1] = 0;
//  outBuffer[2] = 0;
//  outBuffer[3] = 0;
//  buffer[0] = 6;
//  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(0, outLength);
//  
//  buffer[0] = 6;
//  buffer[1] = 3;
//  outLength = 255;
//  EXPECT_FALSE(trie->contains(buffer, 2, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(0, outLength);
//}
//
//// Make sure we can get the correct edges from the DB
//TEST_F(TrieTest, FactDBEdgeCompletion) {
//  trie->addValidInsertion(1, 0);
//  trie->addValidInsertion(2, 1);
//  trie->addValidInsertion(3, 2);
//  trie->addValidInsertion(4, 3);
//  trie->addValidInsertion(5, 4);
//  void addValidInsertion(const word& word, const edge_type& type);
//  // Add {2, 3, 4, 5} to co-occur with 1
//  buffer[0] = 1; buffer[1] = 2;
//  trie->add(buffer, 2);
//  buffer[0] = 1; buffer[1] = 3;
//  trie->add(buffer, 2);
//  buffer[0] = 4; buffer[1] = 1;
//  trie->add(buffer, 2);
//  buffer[0] = 5; buffer[1] = 2; buffer[2] = 1;
//  trie->add(buffer, 3);
//  
//  // Test that {2, 3, 4, 5} are proposed from 1
//  uint8_t outLength = 255;
//  outBuffer[0] = 0;
//  outBuffer[1] = 0;
//  outBuffer[2] = 0;
//  outBuffer[3] = 0;
//  edgeBuffer[0] = 0;
//  edgeBuffer[1] = 0;
//  edgeBuffer[2] = 0;
//  edgeBuffer[3] = 0;
//  buffer[0] = 1;
//  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(3, outLength);
//  EXPECT_EQ(2, outBuffer[0]);
//  EXPECT_EQ(3, outBuffer[1]);
//  EXPECT_EQ(4, outBuffer[2]);
//  EXPECT_EQ(1, edgeBuffer[0]);
//  EXPECT_EQ(2, edgeBuffer[1]);
//  EXPECT_EQ(3, edgeBuffer[2]);
//}
//
//// Make sure we can complete facts with duplicate words
//TEST_F(TrieTest, FactDBCompletionDuplicateWord) {
//  trie->addValidInsertion(1, DEL_NOUN);
//  trie->addValidInsertion(2, DEL_NOUN);
//  trie->addValidInsertion(3, DEL_NOUN);
//  buffer[0] = 1; buffer[1] = 2; buffer[2] = 1;
//  trie->add(buffer, 2);
//  trie->add(buffer, 3);
//  
//  // Test that {1, 2} are proposed from 1
//  uint8_t outLength = 255;
//  outBuffer[0] = 0; outBuffer[1] = 0; outBuffer[2] = 0; outBuffer[3] = 0;
//  buffer[0] = 1;
//  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(1, outLength);
//  EXPECT_EQ(2, outBuffer[0]);
//}
//
//// Some smart dog is smart -> some dog is smart
//TEST_F(TrieTest, FactDBCompletionDuplicateWordRegression) {
//  trie->addValidInsertion(1, DEL_NOUN); // some
//  trie->addValidInsertion(2, DEL_NOUN); // smart
//  trie->addValidInsertion(3, DEL_NOUN); // dog
//  trie->addValidInsertion(4, DEL_NOUN); // is
//  buffer[0] = 1; buffer[1] = 2; buffer[2] = 3; buffer[3] = 4; buffer[4] = 2;
//  trie->add(buffer, 5);
//  
//  uint8_t outLength = 255;
//  outBuffer[0] = 0; outBuffer[1] = 0; outBuffer[2] = 0; outBuffer[3] = 0;
//  buffer[0] = 1;
//  buffer[1] = 3;
//  buffer[2] = 4;
//  buffer[3] = 2;
//  EXPECT_FALSE(trie->contains(buffer, 4, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(1, outLength);
//  EXPECT_EQ(2, outBuffer[0]);
//}
//
//// Make sure we can complete a fact that's one away from being correct
//TEST_F(TrieTest, FactDBCompletionOffByOne) {
//  trie->addValidInsertion(1, DEL_NOUN);
//  trie->addValidInsertion(2, DEL_NOUN);
//  trie->addValidInsertion(3, DEL_NOUN);
//  buffer[0] = 1; buffer[1] = 2; buffer[2] = 3;
//  trie->add(buffer, 3);
//  
//  // Test that {1, 2} are proposed from 1
//  uint8_t outLength = 255;
//  outBuffer[0] = 0; outBuffer[1] = 0; outBuffer[2] = 0; outBuffer[3] = 0;
//  buffer[0] = 1;
//  buffer[1] = 3;
//  EXPECT_FALSE(trie->contains(buffer, 2, outBuffer, edgeBuffer, &outLength));
//  EXPECT_EQ(1, outLength);
//  EXPECT_EQ(2, outBuffer[0]);
//}
