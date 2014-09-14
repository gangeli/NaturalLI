#include <limits.h>
#include <config.h>

#include "gtest/gtest.h"
#include "SynSearch.h"
#include "Utils.h"

using namespace std;

// ----------------------------------------------
// SynPath (Search State)
// ----------------------------------------------
class SynPathTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
  }
  
  virtual void TearDown() {
  }
};

TEST_F(SynPathTest, HasExpectedSizes) {
  EXPECT_EQ(4, sizeof(tagged_word));
  EXPECT_EQ(26, MAX_QUERY_LENGTH);
  EXPECT_EQ(16, sizeof(syn_path_data));
  EXPECT_EQ(32, sizeof(SynPath));
  EXPECT_LE(sizeof(SynPath), CACHE_LINE_SIZE);
}

// ----------------------------------------------
// Tree (Dependency Tree)
// ----------------------------------------------
class TreeTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    tree = new Tree(string("42\t2\tnsubj\n") +
                    string("43\t0\troot\n") +
                    string("44\t2\tdobj"));
    bigTree = new Tree(
                       string("42\t2\tamod\n") +
                       string("43\t3\tnsubj\n") +
                       string("44\t0\troot\n") +
                       string("45\t5\tamod\n") +
                       string("46\t3\tdobj"));
  }
  
  virtual void TearDown() {
    delete tree;
    delete bigTree;
  }

  const Tree* tree;
  const Tree* bigTree;

};

TEST_F(TreeTest, HasExpectedSizes) {
  EXPECT_EQ(192, sizeof(Tree));  // 3 cache lines
}

TEST_F(TreeTest, DefinitionsOk) {
  EXPECT_LT(MAX_QUERY_LENGTH, TREE_ROOT);
}

/**
 * Make sure that TREE_DELETE and TREE_IS_DELETED works as
 * intended.
 */
TEST_F(TreeTest, BitShifts) {
  uint32_t globalBitmask = 0;
  for (uint32_t i = 0; i < 32; ++i) {
    // Fine grained check
    uint32_t bitmask = 0x0;
    EXPECT_TRUE(TREE_IS_DELETED(
      TREE_DELETE(bitmask, i),
      i));
    // Chain marks
    uint32_t newGlobalBitmask = TREE_DELETE(globalBitmask, i);
    EXPECT_NE(globalBitmask, newGlobalBitmask);
    globalBitmask = newGlobalBitmask;
  }
  // Check to make sure we've deleted everything
  EXPECT_EQ(0x0 - 1, globalBitmask);
}

//
// CoNLL Format
//
TEST_F(TreeTest, CoNLLFormatConstructor) {
  EXPECT_EQ(getTaggedWord(42, 0, 0), tree->word(0));
  EXPECT_EQ(getTaggedWord(43, 0, 0), tree->word(1));
  EXPECT_EQ(getTaggedWord(44, 0, 0), tree->word(2));
  
  EXPECT_EQ(1, tree->governor(0));
  EXPECT_EQ(TREE_ROOT, tree->governor(1));
  EXPECT_EQ(1, tree->governor(2));
  
  EXPECT_EQ("nsubj", string(dependencyGloss(tree->relation(0))));
  EXPECT_EQ("root",  string(dependencyGloss(tree->relation(1))));
  EXPECT_EQ("dobj",  string(dependencyGloss(tree->relation(2))));
}

//
// Equality
//
TEST_F(TreeTest, Equality) {
  Tree localTree(string("42\t2\tnsubj\n") +
                 string("43\t0\troot\n") +
                 string("44\t2\tdobj"));
  EXPECT_EQ(localTree, localTree);
  EXPECT_EQ(*tree, localTree);
  
  Tree diff1(string("43\t2\tnsubj\n") +
             string("43\t0\troot\n") +
             string("44\t2\tdobj"));
  EXPECT_NE(*tree, diff1);
  Tree diff2(string("42\t2\tnsubj\n") +
             string("43\t0\troot\n") +
             string("45\t2\tdobj"));
  EXPECT_NE(*tree, diff2);
  Tree diff3(string("42\t3\tnsubj\n") +
             string("43\t0\troot\n") +
             string("44\t2\tdobj"));
  EXPECT_NE(*tree, diff3);
  Tree diff4(string("42\t2\tnsubj\n") +
             string("43\t0\troot\n") +
             string("44\t2\tnsubj"));
  EXPECT_NE(*tree, diff4);
  Tree diff5(string("42\t2\tnsubj\n") +
             string("43\t0\troot\n") +
             string("44\t2\tdobj\n") +
             string("45\t3\tamod"));
  EXPECT_NE(*tree, diff5);
  
}

//
// Cache is where it should be
//
TEST_F(TreeTest, CacheWellFormed) {
  Tree localTree(string("42\t2\tnsubj\n") +
                 string("43\t0\troot\n") +
                 string("44\t2\tdobj"));
  EXPECT_EQ(*tree, localTree);
  uint8_t len = localTree.availableCacheLength;
  // Statically check cache length
  EXPECT_EQ(
    sizeof(Tree) - 3 * sizeof(dep_tree_word) - sizeof(uint8_t)
      - sizeof(uint8_t),
    len);
  uint8_t* cache = (uint8_t*) localTree.cacheSpace();
  // Repeatable calls
  EXPECT_EQ(cache, localTree.cacheSpace());
  // Is zeroed initially
  for (uint8_t i = 0; i < len; ++i) {
    EXPECT_EQ(42, cache[i]);
  }
  // Change cache
  for (uint8_t i = 0; i < len; ++i) {
    cache[i] = i;
  }
  // Check tree unchanged
  EXPECT_EQ(*tree, localTree);
}

//
// Root
//
TEST_F(TreeTest, Root) {
  EXPECT_EQ(1, tree->root());
}

//
// Delete Mask (small tree)
//
TEST_F(TreeTest, DeleteMaskSmall) {
  EXPECT_EQ(TREE_DELETE(TREE_DELETE(TREE_DELETE(0x0, 0), 1), 2), 
    tree->createDeleteMask(1));
  EXPECT_EQ(TREE_DELETE(0x0, 0),
    tree->createDeleteMask(0));
  EXPECT_EQ(TREE_DELETE(0x0, 2),
    tree->createDeleteMask(2));
}

//
// Dependents (small tree)
//
TEST_F(TreeTest, DependentsSmall) {
  uint8_t indices[5];
  uint8_t relations[5];
  uint8_t length;
  // From root
  tree->dependents(1, indices, relations, &length);
  ASSERT_EQ(2, length);
  EXPECT_EQ(0, indices[0]);
  EXPECT_EQ("nsubj", dependencyGloss(relations[0]));
  EXPECT_EQ(2, indices[1]);
  EXPECT_EQ("dobj", dependencyGloss(relations[1]));
  // From dependents
  tree->dependents(0, indices, relations, &length);
  EXPECT_EQ(0, length);
  tree->dependents(2, indices, relations, &length);
  EXPECT_EQ(0, length);
}

//
// Sanity Check Big Tree
//
TEST_F(TreeTest, SanityCheckBigTree) {
  EXPECT_EQ(2, bigTree->root());
  EXPECT_EQ(1, bigTree->governor(0));
  EXPECT_EQ(2, bigTree->governor(1));
  EXPECT_EQ(TREE_ROOT, bigTree->governor(2));
  EXPECT_EQ(4, bigTree->governor(3));
  EXPECT_EQ(2, bigTree->governor(4));
}

//
// Delete Mask (big tree)
//
TEST_F(TreeTest, DeleteMaskBig) {
  EXPECT_EQ(
    TREE_DELETE(TREE_DELETE(
      TREE_DELETE(TREE_DELETE(TREE_DELETE(0x0, 0), 1), 2), 3), 4), 
    bigTree->createDeleteMask(2));
  EXPECT_EQ(TREE_DELETE(0x0, 0),
    bigTree->createDeleteMask(0));
  EXPECT_EQ(TREE_DELETE(TREE_DELETE(0x0, 0), 1),
    bigTree->createDeleteMask(1));
  EXPECT_EQ(TREE_DELETE(0x0, 3),
    bigTree->createDeleteMask(3));
  EXPECT_EQ(TREE_DELETE(TREE_DELETE(0x0, 3), 4),
    bigTree->createDeleteMask(4));
}

//
// Dependents (big tree)
//
TEST_F(TreeTest, DependentsBig) {
  uint8_t indices[5];
  uint8_t relations[5];
  uint8_t length;
  // From root
  bigTree->dependents(2, indices, relations, &length);
  ASSERT_EQ(2, length);
  EXPECT_EQ(1, indices[0]);
  EXPECT_EQ("nsubj", dependencyGloss(relations[0]));
  EXPECT_EQ(4, indices[1]);
  EXPECT_EQ("dobj", dependencyGloss(relations[1]));
  // From dependents
  bigTree->dependents(1, indices, relations, &length);
  ASSERT_EQ(1, length);
  EXPECT_EQ(0, indices[0]);
  EXPECT_EQ("amod", dependencyGloss(relations[0]));
  bigTree->dependents(4, indices, relations, &length);
  ASSERT_EQ(1, length);
  EXPECT_EQ(3, indices[0]);
  EXPECT_EQ("amod", dependencyGloss(relations[0]));
  // From leaves
  bigTree->dependents(0, indices, relations, &length);
  EXPECT_EQ(0, length);
  bigTree->dependents(3, indices, relations, &length);
  EXPECT_EQ(0, length);
}

//
// Dependency Edge Size Check
//
TEST_F(TreeTest, DependencyEdgeFitsIn64Bits) {
  ASSERT_EQ(8, sizeof(dependency_edge));
}

//
// Hash Crash Test
//
TEST_F(TreeTest, HashSanityCheck) {
  EXPECT_NE(0x0, tree->hash());
  EXPECT_NE(0x0, bigTree->hash());
  EXPECT_NE(tree->hash(), bigTree->hash());
}

//
// Hash Repeatability Check
//
TEST_F(TreeTest, HashRepeatable) {
  Tree t1(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t2(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  EXPECT_EQ(t1.hash(), t2.hash());
}

//
// Hash Value Test
//
TEST_F(TreeTest, HashValueCheck) {
#if TWO_PASS_HASH!=0
  EXPECT_EQ(3957455256695363289, tree->hash());
#else
  EXPECT_EQ(1037381270807490254, tree->hash());
#endif
}

//
// Hash Field Test
//
TEST_F(TreeTest, HashRelevantFieldsIncluded) {
  Tree t1(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t2(string("44\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t3(string("42\t3\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t4(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tnsubj"));
  EXPECT_NE(t1.hash(), t2.hash());
  EXPECT_NE(t1.hash(), t3.hash());
  EXPECT_NE(t1.hash(), t4.hash());
  EXPECT_NE(t2.hash(), t3.hash());
  EXPECT_NE(t2.hash(), t4.hash());
  EXPECT_NE(t3.hash(), t4.hash());
}

//
// Hash Order Independence Test
//
TEST_F(TreeTest, HashOrderIndependent) {
  Tree t1(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t2(string("44\t2\tdobj\n") +
          string("43\t0\troot\n") +
          string("42\t2\tnsubj"));
  EXPECT_EQ(t1.hash(), t2.hash());
}

//
// Hash Not Bag Of Words
//
TEST_F(TreeTest, HashNotBagOfWords) {
  Tree t1(string("42\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("44\t2\tdobj"));
  Tree t2(string("44\t2\tnsubj\n") +
          string("43\t0\troot\n") +
          string("42\t2\tdobj"));
  EXPECT_NE(t1.hash(), t2.hash());
}

//
// Hash Mutate (simple)
//
TEST_F(TreeTest, HashMutateSimple) {
  const uint64_t originalHash = tree->hash();
  Tree mutated(string("50\t2\tnsubj\n") +
               string("43\t0\troot\n") +
               string("44\t2\tdobj"));
  const uint64_t expectedMutatedHash = mutated.hash();
  const uint64_t actualMutatedHash
    = tree->updateHashFromMutation( originalHash, 0, 42, 43, 50);
  EXPECT_EQ(expectedMutatedHash, actualMutatedHash);
}

//
// Hash Mutate (simple with children)
//
TEST_F(TreeTest, HashMutateSimpleWithChildren) {
  const uint64_t originalHash = tree->hash();
  Tree mutated(string("42\t2\tnsubj\n") +
               string("50\t0\troot\n") +
               string("44\t2\tdobj"));
  const uint64_t expectedMutatedHash = mutated.hash();
  const uint64_t actualMutatedHash
    = tree->updateHashFromMutation( originalHash, 1, 43, TREE_ROOT_WORD, 50);
  EXPECT_EQ(expectedMutatedHash, actualMutatedHash);
}

//
// Hash Delete (simple)
//
TEST_F(TreeTest, HashDeleteSimple) {
  const uint64_t originalHash = tree->hash();
  Tree deleted(string("43\t0\troot\n") +
               string("44\t1\tdobj"));
  const uint64_t expectedDeleteHash = deleted.hash();
  const uint64_t actualDeleteHash
    = tree->updateHashFromDeletions( originalHash, 0, 42, 43,
                                     TREE_DELETE(0x0, 0) );
  EXPECT_EQ(expectedDeleteHash, actualDeleteHash);
}

//
// Hash Delete Subtree
//
TEST_F(TreeTest, HashDeleteSubtree) {
  const uint64_t originalHash = bigTree->hash();
  Tree deleted(
               string("44\t0\troot\n") +
               string("45\t3\tamod\n") +
               string("46\t1\tdobj"));
  const uint64_t expectedDeleteHash = deleted.hash();
  const uint32_t deleteMask = bigTree->createDeleteMask(1);
  const uint64_t actualDeleteHash
    = bigTree->updateHashFromDeletions( originalHash, 1, 43, 44, deleteMask );
  EXPECT_EQ(expectedDeleteHash, actualDeleteHash);
}

//
// Hash Multiple Steps
//
TEST_F(TreeTest, HashMultipleSteps) {
  Tree source(string("42\t2\tnsubj\n") +
              string("43\t0\troot\n") +
              string("44\t2\tdobj"));
  Tree target(string("50\t0\troot\n") +
              string("51\t1\tdobj"));
  uint64_t hash = source.hash();
  hash = source.updateHashFromMutation( hash, 1, 43, TREE_ROOT_WORD, 50);
  uint32_t deletionMask = source.createDeleteMask(0);
  hash = source.updateHashFromDeletions( hash, 0, 42, 50, deletionMask);
  hash = source.updateHashFromMutation( hash, 2, 44, 50, 51);
  EXPECT_EQ(target.hash(), hash);
}

// ----------------------------------------------
// KNHeap (Priority Queue)
// ----------------------------------------------

class KNHeapTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    simpleHeap = new KNHeap<float,uint32_t>(
      std::numeric_limits<float>::infinity(),
      -std::numeric_limits<float>::infinity());
    pathHeap = new KNHeap<float,SynPath>(
      std::numeric_limits<float>::infinity(),
      -std::numeric_limits<float>::infinity());
      
  }
  
  virtual void TearDown() {
    delete simpleHeap;
    delete pathHeap;
  }

  KNHeap<float,uint32_t>* simpleHeap;
  KNHeap<float,SynPath>* pathHeap;
};

TEST_F(KNHeapTest, HaveInfinity) {
  ASSERT_TRUE(std::numeric_limits<float>::is_iec559);
}

TEST_F(KNHeapTest, SimpleTest) {
  ASSERT_EQ(0, simpleHeap->getSize());
  // Insert
  simpleHeap->insert(5.0, 5);
  simpleHeap->insert(1.0, 1);
  simpleHeap->insert(9.0, 9);
  simpleHeap->insert(4.0, 4);
  simpleHeap->insert(8.0, 8);
  simpleHeap->insert(7.0, 7);
  simpleHeap->insert(2.0, 2);
  simpleHeap->insert(3.0, 3);
  simpleHeap->insert(6.0, 6);
  ASSERT_EQ(9, simpleHeap->getSize());
  // Remove
  float key;
  uint32_t value;
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(1, key); EXPECT_EQ(1.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(2, key); EXPECT_EQ(2.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(3, key); EXPECT_EQ(3.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(4, key); EXPECT_EQ(4.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(5, key); EXPECT_EQ(5.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(6, key); EXPECT_EQ(6.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(7, key); EXPECT_EQ(7.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(8, key); EXPECT_EQ(8.0, value);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(9, key); EXPECT_EQ(9.0, value);
  ASSERT_EQ(0, simpleHeap->getSize());
}

TEST_F(KNHeapTest, SimpleTestKeysDontMatchValue) {
  ASSERT_EQ(0, simpleHeap->getSize());
  // Insert
  simpleHeap->insert(5.0, 1);
  simpleHeap->insert(1.0, 2);
  simpleHeap->insert(9.0, 3);
  ASSERT_EQ(3, simpleHeap->getSize());
  // Remove
  float key;
  uint32_t value;
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(2, value); EXPECT_EQ(1.0, key);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(1, value); EXPECT_EQ(5.0, key);
  simpleHeap->deleteMin(&key, &value);
  EXPECT_EQ(3, value); EXPECT_EQ(9.0, key);
}

TEST_F(KNHeapTest, SimpleTestWithPaths) {
  ASSERT_EQ(0, pathHeap->getSize());
  pathHeap->insert(1.5, SynPath());
  pathHeap->insert(1.0, SynPath());
  pathHeap->insert(5.0, SynPath());
  ASSERT_EQ(3, pathHeap->getSize());
  // Remove
  float key;
  SynPath value;
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(1.0, key);
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(1.5, key);
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(5.0, key);
  ASSERT_EQ(0, pathHeap->getSize());
}
