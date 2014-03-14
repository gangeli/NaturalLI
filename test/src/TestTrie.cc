#include <limits.h>

#include "gtest/gtest.h"
#include "Trie.h"

class TrieTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    trie = new Trie();
    db = new TrieFactDB();
  }

  virtual void TearDown() {
    delete trie;
  }
  
  Trie* trie;
  TrieFactDB* db;
  uint32_t buffer[32];
  word outBuffer[256];

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

// Test children return
TEST_F(TrieTest, ReturnsChildren) {
  uint8_t outLength = 5;
  buffer[0] = 42;
  buffer[1] = 43;
  // Full string
  trie->add(buffer, 2);
  EXPECT_TRUE(trie->contains(buffer, 2));
  // Get output
  outBuffer[0] = 0;
  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, &outLength));
  EXPECT_EQ(1, outLength);
  EXPECT_EQ(43, outBuffer[0]);

  // Add another element
  buffer[1] = 44;
  trie->add(buffer, 2);
  // Check that the output changed
  outBuffer[0] = 0;
  outBuffer[1] = 0;
  EXPECT_FALSE(trie->contains(buffer, 1, outBuffer, &outLength));
  EXPECT_EQ(2, outLength);
  EXPECT_EQ(43, outBuffer[0]);
  EXPECT_EQ(44, outBuffer[1]);
}

// Make sure we can add to the Trie
TEST_F(TrieTest, FactDBCanAdd) {
  buffer[0] = 42;
  db->add(buffer, 1);
}

// Make sure we can add/contains depth 1
TEST_F(TrieTest, FactDBCanAddContainsDepth1) {
  buffer[0] = 42;
  db->add(buffer, 1);
  EXPECT_TRUE(db->contains(buffer, 1));
}

// Make sure we can add/contains depth 2
TEST_F(TrieTest, FactDBCanAddContainsDepth2) {
  buffer[0] = 42;
  buffer[1] = 43;
  // Full string
  db->add(buffer, 2);
  EXPECT_TRUE(db->contains(buffer, 2));
  EXPECT_FALSE(db->contains(buffer, 1));
  // Add substring
  db->add(buffer, 1);
  EXPECT_TRUE(db->contains(buffer, 2));
  EXPECT_TRUE(db->contains(buffer, 1));
}

TEST_F(TrieTest, FactDBCompletion) {
  // Add {2, 3, 4, 5} to co-occur with 1
  buffer[0] = 1; buffer[1] = 2;
  db->add(buffer, 2);
  buffer[0] = 1; buffer[1] = 3;
  db->add(buffer, 2);
  buffer[0] = 4; buffer[1] = 1;
  db->add(buffer, 2);
  buffer[0] = 5; buffer[1] = 2; buffer[2] = 1;
  db->add(buffer, 3);
  
  // Test that {2, 3, 4, 5} are proposed from 1
  uint8_t outLength = 255;
  outBuffer[0] = 0;
  outBuffer[1] = 0;
  outBuffer[2] = 0;
  outBuffer[3] = 0;
  buffer[0] = 1;
  EXPECT_FALSE(db->contains(buffer, 1, outBuffer, &outLength));
  EXPECT_EQ(3, outLength);
  EXPECT_EQ(2, outBuffer[0]);
  EXPECT_EQ(3, outBuffer[1]);
  EXPECT_EQ(4, outBuffer[2]);
}
