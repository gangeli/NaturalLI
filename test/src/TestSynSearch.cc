#include <limits.h>
#include <config.h>

#include "gtest/gtest.h"
#include "btree_set.h"
#include "SynSearch.h"
#include "Utils.h"

#define SILENT true

#define ALL_CATS_HAVE_TAILS \
                       string(ALL_STR) + string("\t2\tdet\t0\tanti-additive\t2-3\tmultiplicative\t3-5\n") + \
                       string(CAT_STR) + string("\t3\tnsubj\t0\t-\t-\t-\t-\n") + \
                       string(HAVE_STR) + string("\t0\troot\t0\t-\t-\t-\t-\n") + \
                       string(TAIL_STR) + string("\t3\tdobj\t0\t-\t-\t-\t-\n")

#define CATS_HAVE_TAILS \
                       string(CAT_STR) + string("\t2\tnsubj\t0\t-\t-\t-\t-\n") + \
                       string(HAVE_STR) + string("\t0\troot\t0\t-\t-\t-\t-\n") + \
                       string(TAIL_STR) + string("\t2\tdobj\t0\t-\t-\t-\t-\n")

using namespace std;
using namespace btree;

// ----------------------------------------------
// SearchNode (Search State)
// ----------------------------------------------
class SearchNodeTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
  }
  
  virtual void TearDown() {
  }
};

TEST_F(SearchNodeTest, HasExpectedSizes) {
  EXPECT_EQ(4, sizeof(tagged_word));
  EXPECT_EQ(1, sizeof(quantifier_monotonicity));
  EXPECT_EQ(26, MAX_QUERY_LENGTH);
  EXPECT_EQ(20, sizeof(syn_path_data));
#if MAX_QUANTIFIER_COUNT < 10
  EXPECT_EQ(32, sizeof(SearchNode));
  EXPECT_LE(sizeof(SearchNode), CACHE_LINE_SIZE);
#endif
}

TEST_F(SearchNodeTest, HasZeroCostInitially) {
  SearchNode path(Tree(string("42\t2\tnsubj\n") +
                    string("43\t0\troot\n") +
                    string("44\t2\tdobj")
  ));
}

TEST_F(SearchNodeTest, InitFromTree) {
  Tree tree(string("42\t2\tnsubj\n") +
            string("43\t0\troot\n") +
            string("44\t2\tdobj"));

  SearchNode path(tree);
  EXPECT_EQ(tree.hash(), path.factHash());
  EXPECT_EQ(TREE_ROOT_WORD, path.governor());
  EXPECT_EQ(getTaggedWord(43, 0, 0), path.token());
  EXPECT_EQ(1, path.tokenIndex());
  EXPECT_EQ(0, path.getBackpointer());
  EXPECT_EQ(path, path);
}

TEST_F(SearchNodeTest, AssignmentOperator) {
  SearchNode path(Tree(string("42\t2\tnsubj\n") +
                    string("43\t0\troot\n") +
                    string("44\t2\tdobj")
  ));
  SearchNode path2 = path;
  EXPECT_EQ(path, path2);
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
  EXPECT_EQ(398, sizeof(Tree));
  EXPECT_EQ(6, sizeof(dep_tree_word));
  EXPECT_EQ(1, sizeof(quantifier_monotonicity));
  EXPECT_EQ(3, sizeof(quantifier_span));
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
TEST_F(TreeTest, CoNLLShortConstructor) {
  EXPECT_EQ(getTaggedWord(42, 0, 0), tree->token(0));
  EXPECT_EQ(getTaggedWord(43, 0, 0), tree->token(1));
  EXPECT_EQ(getTaggedWord(44, 0, 0), tree->token(2));
  
  EXPECT_EQ(1, tree->governor(0));
  EXPECT_EQ(TREE_ROOT, tree->governor(1));
  EXPECT_EQ(1, tree->governor(2));
  
  EXPECT_EQ("nsubj", string(dependencyGloss(tree->relation(0))));
  EXPECT_EQ("root",  string(dependencyGloss(tree->relation(1))));
  EXPECT_EQ("dobj",  string(dependencyGloss(tree->relation(2))));
}


//
// Extended CoNLL Format
//
TEST_F(TreeTest, CoNLLLongConstructor) {
  Tree tree(ALL_CATS_HAVE_TAILS);

  // Sanity check
  EXPECT_EQ(ALL, tree.token(0));
  EXPECT_EQ(CAT, tree.token(1));
  EXPECT_EQ(HAVE, tree.token(2));
  EXPECT_EQ(TAIL, tree.token(3));
}

uint8_t countQuantifiers(const Tree& tree, const uint8_t& index) {
  uint8_t count = 0;
  tree.foreachQuantifier( index, [&count] (quantifier_type type, monotonicity mono) mutable -> void {
    count += 1;
  });
  return count;
}

//
// Simple check on quantifier scoping
//
TEST_F(TreeTest, QuantifierScopesSimple) {
  Tree tree(ALL_CATS_HAVE_TAILS);
  ASSERT_EQ(1, tree.getNumQuantifiers());
  // Check quantifier mask
  EXPECT_TRUE(tree.isQuantifier(0));
  EXPECT_FALSE(tree.isQuantifier(1));
  EXPECT_FALSE(tree.isQuantifier(2));
  EXPECT_FALSE(tree.isQuantifier(3));
  
  // Check quantifier scope
  EXPECT_TRUE(tree.isQuantifier(0));
  EXPECT_FALSE(tree.isQuantifier(1));
  EXPECT_FALSE(tree.isQuantifier(2));
  EXPECT_FALSE(tree.isQuantifier(3));
  
  // Check everything under scope
  EXPECT_EQ(0, countQuantifiers(tree, 0));
  EXPECT_EQ(1, countQuantifiers(tree, 1));
  EXPECT_EQ(1, countQuantifiers(tree, 2));
  EXPECT_EQ(1, countQuantifiers(tree, 3));
}

void quantifierMonotonicities(const Tree& tree, const uint8_t& index, 
                              const quantifier_type* types,
                              const monotonicity* monotonicities) {
  uint8_t count = 0;
  tree.foreachQuantifier( index, [&count, types, monotonicities] (quantifier_type type, monotonicity mono) mutable -> void {
    EXPECT_EQ(types[count], type);
    EXPECT_EQ(monotonicities[count], mono);
    count += 1;
  });
}

//
// Quantifier monotonicity
//
TEST_F(TreeTest, QuantifierMonotonicity) {
  Tree tree(ALL_CATS_HAVE_TAILS);
  monotonicity down = MONOTONE_DOWN;
  monotonicity up = MONOTONE_UP;
  quantifier_type additive = QUANTIFIER_TYPE_ADDITIVE;
  quantifier_type multiplicative = QUANTIFIER_TYPE_MULTIPLICATIVE;
//  quantifierMonotonicities(tree, 0, &additive, &down);
//  quantifierMonotonicities(tree, 1, &additive, &down);
  quantifierMonotonicities(tree, 2, &multiplicative, &up);
  quantifierMonotonicities(tree, 3, &multiplicative, &up);
}

//
// Quantifier monotonicity
//
TEST_F(TreeTest, GenericsMonotonicity) {
  Tree tree(CATS_HAVE_TAILS);
  monotonicity up = MONOTONE_UP;
  quantifier_type multiplicative = QUANTIFIER_TYPE_MULTIPLICATIVE;
  quantifierMonotonicities(tree, 0, &multiplicative, &up);
  quantifierMonotonicities(tree, 1, &multiplicative, &up);
  quantifierMonotonicities(tree, 2, &multiplicative, &up);
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
  EXPECT_EQ(2818012195690390063, tree->hash());
#else
  EXPECT_EQ(9589546471634114165, tree->hash());
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
// Hash Mutate Undo (simple)
//
TEST_F(TreeTest, HashMutateUndoSimple) {
  const uint64_t originalHash = tree->hash();
  const uint64_t mutatedHash
    = tree->updateHashFromMutation( originalHash, 0, 42, 43, 50);
  const uint64_t undoneHash
    = tree->updateHashFromMutation( mutatedHash, 0, 50, 43, 42);
  EXPECT_EQ(originalHash, undoneHash);
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

TEST_F(TreeTest, HashCollapseDetNeg) {
  Tree neg(string(NO_STR) + string("\t2\tdet\t0\tanti-additive\t2-3\t-\t-\n") + \
           string(CAT_STR) + string("\t0\tnsubj\t0\t-\t-\t-\t-\n"));
  Tree det(string(NO_STR) + string("\t2\tdet\t0\tanti-additive\t2-3\t-\t-\n") + \
           string(CAT_STR) + string("\t0\tnsubj\t0\t-\t-\t-\t-\n"));
  Tree amod(string(NO_STR) + string("\t2\tamod\t0\tanti-additive\t2-3\t-\t-\n") + \
           string(CAT_STR) + string("\t0\tnsubj\t0\t-\t-\t-\t-\n"));
  EXPECT_EQ(neg.hash(), det.hash());
  EXPECT_NE(neg.hash(), amod.hash());
  EXPECT_NE(det.hash(), amod.hash());
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
    pathHeap = new KNHeap<float,SearchNode>(
      std::numeric_limits<float>::infinity(),
      -std::numeric_limits<float>::infinity());
      
  }
  
  virtual void TearDown() {
    delete simpleHeap;
    delete pathHeap;
  }

  KNHeap<float,uint32_t>* simpleHeap;
  KNHeap<float,SearchNode>* pathHeap;
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
  pathHeap->insert(1.5, SearchNode());
  pathHeap->insert(1.0, SearchNode());
  pathHeap->insert(5.0, SearchNode());
  ASSERT_EQ(3, pathHeap->getSize());
  // Remove
  float key;
  SearchNode value;
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(1.0, key);
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(1.5, key);
  pathHeap->deleteMin(&key, &value);
  EXPECT_EQ(5.0, key);
  ASSERT_EQ(0, pathHeap->getSize());
}

// ----------------------------------------------
// Natural Logic
// ----------------------------------------------
class SynSearchCostsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    strictCosts = strictNaturalLogicCosts();
  }
  
  virtual void TearDown() {
    delete strictCosts;
  }

  SynSearchCosts* strictCosts;
};

TEST_F(SynSearchCostsTest, StrictCostsCompileCheck) {
  EXPECT_TRUE(strictCosts != NULL);
}

TEST_F(SynSearchCostsTest, StrictCostsCheckValues) {
  EXPECT_FALSE(isinf(strictCosts->transitionCostFromTrue[FUNCTION_NEGATION]));
  EXPECT_TRUE(isinf(strictCosts->transitionCostFromTrue[FUNCTION_REVERSE_ENTAILMENT]));
}


TEST_F(SynSearchCostsTest, MutationsCostGenerics) {
  Tree tree(CATS_HAVE_TAILS);
  bool outTruth = false;
  float cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 0), WORDNET_UP, true, &outTruth);
  EXPECT_EQ(0.01f, cost);
  EXPECT_EQ(true, outTruth);
  
  cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 0), WORDNET_DOWN, true, &outTruth);
  EXPECT_EQ(true, outTruth);
  EXPECT_TRUE(isinf(cost));
}

TEST_F(SynSearchCostsTest, GenericsAreAdditiveMultiplicative) {
  Tree tree(CATS_HAVE_TAILS);
  bool outTruth = true;
  float cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 1), WORDNET_VERB_ANTONYM, true, &outTruth);
  EXPECT_TRUE(isinf(cost));
  EXPECT_EQ(false, outTruth);
}

TEST_F(SynSearchCostsTest, MutationsCostQuantificiation) {
  Tree tree(ALL_CATS_HAVE_TAILS);
  bool outTruth = false;
  float cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 1), WORDNET_DOWN, true, &outTruth);
  EXPECT_EQ(0.01f, cost);
  EXPECT_EQ(true, outTruth);

  cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 1), WORDNET_UP, true, &outTruth);
  EXPECT_TRUE(isinf(cost));
  EXPECT_EQ(true, outTruth);
}

TEST_F(SynSearchCostsTest, AllAdditiveSubj) {
  Tree tree(ALL_CATS_HAVE_TAILS);
  bool outTruth = true;
  float cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 1), WORDNET_VERB_ANTONYM, true, &outTruth);
  EXPECT_TRUE(isinf(cost));
}

TEST_F(SynSearchCostsTest, AllMultiplicativeObj) {
  Tree tree(ALL_CATS_HAVE_TAILS);
  bool outTruth = true;
  float cost = strictCosts->mutationCost(
    tree, SearchNode(tree, (uint8_t) 3), WORDNET_ADJECTIVE_ANTONYM, true, &outTruth);
  EXPECT_TRUE(isinf(cost));
  EXPECT_EQ(false, outTruth);
}

// ----------------------------------------------
// Syntactic Search
// ----------------------------------------------
#define SEARCH_TIMEOUT_TEST 1000
class SynSearchTest : public ::testing::Test {
 public:
  SynSearchTest() : opts(SEARCH_TIMEOUT_TEST, 999.0f, false, SILENT) { }
 protected:
  virtual void SetUp() {
    lemursHaveTails = new Tree(string("73918\t2\tnsubj\n") +
                             string("60042\t0\troot\n") +
                             string("125248\t2\tdobj"));
    catsHaveTails = new Tree(string("20852\t2\tnsubj\n") +
                             string("60042\t0\troot\n") +
                             string("125248\t2\tdobj"));
    animalsHaveTails = new Tree(string("5532\t2\tnsubj\n") +
                             string("60042\t0\troot\n") +
                             string("125248\t2\tdobj"));
    graph = ReadMockGraph();
    cyclicGraph = ReadMockGraph(true);
    opts = syn_search_options(SEARCH_TIMEOUT_TEST, 999.0f, false, SILENT);
    factdb.insert(catsHaveTails->hash());
    costs = softNaturalLogicCosts();
    strictCosts = strictNaturalLogicCosts();
  }
  
  virtual void TearDown() {
    delete lemursHaveTails;
    delete catsHaveTails;
    delete animalsHaveTails;
    delete graph;
    delete cyclicGraph;
    delete costs;
    delete strictCosts;
  }

  Tree* lemursHaveTails;
  Tree* catsHaveTails;
  Tree* animalsHaveTails;
  Graph* graph;
  Graph* cyclicGraph;
  SynSearchCosts* costs;
  SynSearchCosts* strictCosts;
  syn_search_options opts;
  btree_set<uint64_t> factdb;
};

TEST_F(SynSearchTest, ThreadsFinish) {
  syn_search_response response = SynSearch(graph, factdb, lemursHaveTails, costs, true, opts);
}

TEST_F(SynSearchTest, TickCountNoMutation) {
  syn_search_response response = SynSearch(graph, factdb, catsHaveTails, costs, true, opts);
  EXPECT_EQ(9, response.totalTicks);
}

TEST_F(SynSearchTest, TickCountWithMutations) {
  syn_search_response response = SynSearch(graph, factdb, lemursHaveTails, costs, true, opts);
  EXPECT_EQ(15, response.totalTicks);
}

TEST_F(SynSearchTest, TickCountWithMutationsCyclic) {
  syn_search_response response = SynSearch(cyclicGraph, factdb, lemursHaveTails, costs, true, opts);
#if SEARCH_CYCLE_MEMORY==0
  EXPECT_EQ(SEARCH_TIMEOUT_TEST, response.totalTicks);
#else
  EXPECT_EQ(15, response.totalTicks);
#endif
}

TEST_F(SynSearchTest, LiteralLookup) {
  syn_search_response response = SynSearch(graph, factdb, catsHaveTails, costs, true, opts);
  EXPECT_EQ(1, response.paths.size());
}

TEST_F(SynSearchTest, LemursToCatsSoft) {
  syn_search_response response = SynSearch(graph, factdb, lemursHaveTails, costs, true, opts);
  ASSERT_EQ(1, response.paths.size());
  EXPECT_EQ(4, response.paths[0].size());
  EXPECT_EQ(lemursHaveTails->hash(), response.paths[0].front().factHash());
  EXPECT_EQ(catsHaveTails->hash(), response.paths[0].back().factHash());
}

TEST_F(SynSearchTest, LemursToCatsStrict) {
  syn_search_response response = SynSearch(graph, factdb, lemursHaveTails, strictCosts, true, opts);
  ASSERT_EQ(0, response.paths.size());
}

TEST_F(SynSearchTest, LemursToAnimalsSoft) {
  btree_set<uint64_t> factdb;
  factdb.insert(lemursHaveTails->hash());
  syn_search_response response = SynSearch(cyclicGraph, factdb, animalsHaveTails, costs, true, opts);
  ASSERT_EQ(1, response.paths.size());
  EXPECT_EQ(animalsHaveTails->hash(), response.paths[0].front().factHash());
  EXPECT_EQ(lemursHaveTails->hash(), response.paths[0].back().factHash());
}

TEST_F(SynSearchTest, LemursToAnimalsStrict) {
  btree_set<uint64_t> factdb;
  factdb.insert(lemursHaveTails->hash());
  syn_search_response response = SynSearch(cyclicGraph, factdb, animalsHaveTails, strictCosts, true, opts);
  ASSERT_EQ(1, response.paths.size());
  EXPECT_EQ(animalsHaveTails->hash(), response.paths[0].front().factHash());
  EXPECT_EQ(lemursHaveTails->hash(), response.paths[0].back().factHash());
}

TEST_F(SynSearchTest, EmptyDBTest) {
  btree_set<uint64_t> factdb;
  syn_search_response response = SynSearch(cyclicGraph, factdb, animalsHaveTails, strictCosts, true, opts);
  ASSERT_EQ(0, response.paths.size());
}

#undef SEARCH_TIMEOUT_TEST
