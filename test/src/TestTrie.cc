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

// Do some basic validation on my awful hacks
TEST_F(TrieTest, TryToValidateHacks) {
  ASSERT_EQ(sizeof(Trie), sizeof(trie_placeholder));
}

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
  EXPECT_EQ(436, usage);
  EXPECT_EQ(10 * sizeof(tagged_word), onFacts);
  EXPECT_EQ(352, onStructure);
  EXPECT_EQ(44, onCompletionCaching);
#else
  EXPECT_EQ(336, usage);
  EXPECT_EQ(10 * sizeof(tagged_word), onFacts);  // 40 bytes
  EXPECT_EQ(264, onStructure);
  EXPECT_EQ(32, onCompletionCaching);
#endif
}


//
// ----------------------------------------------------------------------------
//


// 1, 2
// 1, 3
// 5, 6
class LossyTrieTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    HashIntMap map(10);
    uint32_t mainHash, auxHash;
    factA[0] = 1;
    factA[1] = 2;
    mainHash = fnv_32a_buf((uint8_t*) factA, 2 * sizeof(uint32_t),  FNV1_32_INIT);
    auxHash = fnv_32a_buf((uint8_t*) factA, 2 * sizeof(uint32_t),  1154);
    map.put(mainHash, auxHash, 0);
    factB[0] = 1;
    factB[1] = 3;
    mainHash = fnv_32a_buf((uint8_t*) factB, 2 * sizeof(uint32_t),  FNV1_32_INIT);
    auxHash = fnv_32a_buf((uint8_t*) factB, 2 * sizeof(uint32_t),  1154);
    map.put(mainHash, auxHash, 0);
    factC[0] = 5;
    factC[1] = 6;
    mainHash = fnv_32a_buf((uint8_t*) factC, 2 * sizeof(uint32_t),  FNV1_32_INIT);
    auxHash = fnv_32a_buf((uint8_t*) factC, 2 * sizeof(uint32_t),  1154);
    map.put(mainHash, auxHash, 0);
    // 2 completions from [1, ...]
    mainHash = fnv_32a_buf((uint8_t*) factA, 1 * sizeof(uint32_t),  FNV1_32_INIT);
    auxHash = fnv_32a_buf((uint8_t*) factA, 1 * sizeof(uint32_t),  1154);
    map.put(mainHash, auxHash, 2);
    // 1 completion from [5, ...]
    mainHash = fnv_32a_buf((uint8_t*) factC, 1 * sizeof(uint32_t),  FNV1_32_INIT);
    auxHash = fnv_32a_buf((uint8_t*) factC, 1 * sizeof(uint32_t),  1154);
    map.put(mainHash, auxHash, 1);
    trie = new LossyTrie(map);

    // Some bad facts
    factBadA[0] = 1;
    factBadA[1] = 4;
    factPrefixA[0] = 2;
    factPrefixB[0] = 3;
    factPrefixC[0] = 6;
    
    // Populate Trie
    // (facts)
    trie->addFact(factA, 2);
    trie->addFact(factB, 2);
    trie->addFact(factC, 2);
    // (completions)
    trie->addCompletion(factA, 1, factA[1], 31, 7);
    trie->addCompletion(factB, 1, factB[1], 31, 7);
    trie->addCompletion(factC, 1, factC[1], 31, 7);
    // (prefix completions)
    trie->addBeginInsertion(1, 31, 7, 2);
    trie->addBeginInsertion(1, 31, 7, 3);
    trie->addBeginInsertion(5, 31, 7, 6);
  }

  virtual void TearDown() {
    delete trie;
  }
  
  LossyTrie* trie;
  word factA[2];
  word factB[2];
  word factC[2];
  word factBadA[2];
  word factPrefixA[1];
  word factPrefixB[1];
  word factPrefixC[1];
};

/**
 *
 */
TEST_F(LossyTrieTest, LossyTrieDataGetSet) { 
  lossy_trie_data t;
  EXPECT_EQ(1, sizeof(lossy_trie_data));
  // Get/Retrieve
  t.magicBits = 0x1F;
  t.isFact = true;
  t.hasCompletions = true;
  t.isFull = true;
  EXPECT_EQ(0x1F, t.magicBits);
  EXPECT_EQ(true, t.isFact);
  EXPECT_EQ(true, t.hasCompletions);
  EXPECT_EQ(true, t.isFull);
  // Change bits
  t.isFact = false;
  t.hasCompletions = false;
  t.isFull = false;
  EXPECT_EQ(false, t.isFact);
  EXPECT_EQ(false, t.hasCompletions);
  EXPECT_EQ(false, t.isFull);
}

/**
 * The magic bits should be the first 5 bits in the metadata.
 * This works in conjunction with the Packed Insertion assumption
 * to verify that we're not overwriting another bucket.
 */
TEST_F(LossyTrieTest, LossyTrieDataStorageAssumptions) { 
  lossy_trie_data t;
  t.magicBits = 0x1F;
  t.isFact = false;
  t.hasCompletions = false;
  t.isFull = false;
  uint8_t asByte = *((uint8_t*) &t);
  printf("%x\n", asByte);
  EXPECT_EQ(0x1F, asByte >> 3);
}

/**
 * The source should be the first 4 bytes in the packed_insertion
 * This works in conjunction with the Lossy Trie Data assumption
 * to verify that we're not overwriting another bucket.
 */
TEST_F(LossyTrieTest, PackedInsertionStorageAssumptions) { 
  packed_insertion p;
  EXPECT_EQ(6, sizeof(packed_insertion));
  p.source = 42;
  p.sense = 0;
  p.type = 0;
  p.endOfList = 0;
  // (check source)
  uint8_t asInt = *(((uint8_t*) &p) + 2);
  EXPECT_EQ(42, asInt);
  // (check edge type)
  p.type = 248;
  asInt = *(((uint8_t*) &p));
  EXPECT_EQ(248, asInt);
}

/**
 *
 */
TEST_F(LossyTrieTest, AddFactCrashTest) { }

/**
 *
 */
TEST_F(LossyTrieTest, ContainsFact) {
  EXPECT_TRUE(trie->contains(factA, 2));
  EXPECT_TRUE(trie->contains(factB, 2));
  EXPECT_TRUE(trie->contains(factC, 2));
  EXPECT_FALSE(trie->contains(factBadA, 2));
  EXPECT_FALSE(trie->contains(factA, 1));
  EXPECT_FALSE(trie->contains(factB, 1));
  EXPECT_FALSE(trie->contains(factC, 1));
}

/**
 *
 */
TEST_F(LossyTrieTest, CompletionsSimple) {
  // Populate Trie
  edge insertions[MAX_COMPLETIONS];
  // Completions from [1]
  EXPECT_TRUE(trie->contains(factA, 2, 0, insertions));
  ASSERT_EQ(2, insertions[0].source);
  ASSERT_EQ(3, insertions[1].source);
  ASSERT_EQ(0, insertions[2].source);
  // Completions from [5]
  EXPECT_TRUE(trie->contains(factC, 2, 0, insertions));
  ASSERT_EQ(6, insertions[0].source);
  ASSERT_EQ(0, insertions[1].source);
  // Completions from [1, 2]
  EXPECT_TRUE(trie->contains(factA, 2, 1, insertions));
  ASSERT_EQ(0, insertions[0].source);
}

/**
 *
 */
TEST_F(LossyTrieTest, CompletionsOtherFields) {
  // Populate Trie
  edge insertions[MAX_COMPLETIONS];
  // Completions from [1]
  EXPECT_TRUE(trie->contains(factA, 2, 0, insertions));
  ASSERT_EQ(2, insertions[0].source);
  ASSERT_EQ(31, insertions[0].source_sense);
  ASSERT_EQ(7, insertions[0].type);
}

/**
 *
 */
TEST_F(LossyTrieTest, CompletionsPrefix) {
  // Populate Trie
  edge insertions[MAX_COMPLETIONS];
  // Prefix completions from [2]
  insertions[0].source = 999;
  EXPECT_FALSE(trie->contains(factPrefixA, 2, -1, insertions));
  ASSERT_EQ(1, insertions[0].source);
  ASSERT_EQ(0, insertions[1].source);
  // Prefix completions from [3]
  insertions[0].source = 999;
  EXPECT_FALSE(trie->contains(factPrefixB, 3, -1, insertions));
  ASSERT_EQ(1, insertions[0].source);
  ASSERT_EQ(0, insertions[1].source);
  // Prefix completions from [6]
  insertions[0].source = 999;
  EXPECT_FALSE(trie->contains(factPrefixC, 6, -1, insertions));
  ASSERT_EQ(5, insertions[0].source);
  ASSERT_EQ(0, insertions[1].source);
}


