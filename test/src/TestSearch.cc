#include <limits.h>
#include <cfloat>

#include "gtest/gtest.h"
#include "Config.h"
#include "Utils.h"
#include "Search.h"
#include "Graph.h"
#include "FactDB.h"

using namespace std;

//
// Path
//
class PathTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    bool outOfMemory = true;
    cache = new CacheStrategyNone();
    searchType = new BreadthFirstSearch();
    lemursHaveTails_ = lemursHaveTails();
    animalsHaveTails_ = animalsHaveTails();
    catsHaveTails_ = catsHaveTails();
    // Add base path element
    root = new Path(&lemursHaveTails_[0], lemursHaveTails_.size());
    // Register paths with search
    searchType->start(root);
    EXPECT_EQ(*root, *searchType->root);
    EXPECT_EQ(*root, *searchType->peek());
    searchType->push(root, 0, 1, ANIMAL, 0, WORDNET_DOWN, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
    ASSERT_FALSE(outOfMemory);
    dist1 = ((BreadthFirstSearch*) searchType)->debugGet(0);
    EXPECT_EQ(*root, *searchType->root);
    EXPECT_TRUE(searchType->root->parent == NULL);
    searchType->push(dist1, 0, 1, CAT, 0, WORDNET_UP, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
    ASSERT_FALSE(outOfMemory);
    EXPECT_EQ(*root, *searchType->root);
    EXPECT_TRUE(searchType->root->parent == NULL);
    EXPECT_TRUE(searchType->peek()->parent == NULL);
    // Refresh pointers
    dist1 = ((BreadthFirstSearch*) searchType)->debugGet(0);
    dist2 = ((BreadthFirstSearch*) searchType)->debugGet(1);
  }

  virtual void TearDown() {
    delete searchType;
    delete cache;
  }

  const Path* root;
  const Path* dist1;
  const Path* dist2;
  SearchType* searchType;
  CacheStrategy* cache;

  std::vector<word> lemursHaveTails_;
  std::vector<word> animalsHaveTails_;
  std::vector<word> catsHaveTails_;
};


// Ensures that a Path element can be created
TEST_F(PathTest, CanCreate) {
  EXPECT_FALSE(root == NULL);
  EXPECT_FALSE(dist1 == NULL);
  EXPECT_FALSE(dist2 == NULL);
}

// Test path equality
TEST_F(PathTest, HasEquality) {
  EXPECT_EQ(*root, *root);
  EXPECT_EQ(*dist1, *dist1);
  EXPECT_EQ(*dist2, *dist2);
}

// Test copy constructor
TEST_F(PathTest, CanCopy) {
  Path newPath(*root);
  EXPECT_EQ(*root, newPath);
  Path newDist1(*dist1);
  EXPECT_EQ(*dist1, newDist1);
  Path newDist2(*dist2);
  EXPECT_EQ(*dist2, newDist2);
}

// Test getting the source path from a path
TEST_F(PathTest, Parent) {
  // Basic null checks
  EXPECT_TRUE(root->parent == NULL);
  EXPECT_FALSE(dist1->parent == NULL);
  EXPECT_FALSE(dist2->parent == NULL);

  // Pointer equality
  EXPECT_EQ(root,  dist1->parent);
  EXPECT_EQ(dist1, dist2->parent);
  
  // Fine-grained equality check
  EXPECT_EQ(root->parent, dist1->parent->parent);
  EXPECT_EQ(root->edgeType, dist1->parent->edgeType);
  ASSERT_EQ(root->factLength, dist1->parent->factLength);
  for (int i = 0; i < root->factLength; ++i) {
    EXPECT_EQ(root->fact[i], dist1->parent->fact[i]);
  }

  // Comprehensive equality check
  EXPECT_EQ(*root, *dist1->parent);
  EXPECT_EQ(*dist1, *dist2->parent);
}


//
// Common Search Type Tests
//

#define InitSearchTypeTests(ClassName, TestName) \
class TestName : public ::testing::Test { \
 protected: \
  virtual void SetUp() { \
    lemursHaveTails_ = lemursHaveTails(); \
    animalsHaveTails_ = animalsHaveTails(); \
    catsHaveTails_ = catsHaveTails(); \
    root = new Path(&lemursHaveTails_[0], lemursHaveTails_.size()); \
    cache = new CacheStrategyNone(); \
    bloom = new CacheStrategyBloom(); \
    graph = ReadMockGraph(); \
    facts = ReadMockFactDB(); \
    EXPECT_TRUE(search.isEmpty()); \
  } \
\
  virtual void TearDown() { \
    delete cache; \
    delete bloom; \
    delete graph; \
    delete facts; \
  } \
\
  ClassName search; \
  const Path* root; \
  CacheStrategy* cache; \
  CacheStrategy* bloom; \
  Graph* graph; \
  FactDB* facts; \
\
  std::vector<word> lemursHaveTails_; \
  std::vector<word> animalsHaveTails_; \
  std::vector<word> catsHaveTails_; \
}; \
TEST_F(TestName, IsEmpty) { \
  EXPECT_TRUE(search.isEmpty()); \
} \
\
TEST_F(TestName, PushPop) { \
  bool outOfMemory = true; \
  EXPECT_TRUE(search.isEmpty()); \
  search.start(root); \
  EXPECT_FALSE(search.isEmpty()); \
  search.push(root, 0, 1, ANIMAL, 0, WORDNET_DOWN, 0.0f, INFER_EQUIVALENT, cache, outOfMemory); \
  ASSERT_FALSE(outOfMemory); \
  EXPECT_FALSE(search.isEmpty()); \
  EXPECT_EQ((Path*) NULL, search.popWithoutScore()->parent); \
  EXPECT_FALSE(search.isEmpty()); \
  const Path* path = search.popWithoutScore(); \
  ASSERT_FALSE(path->parent == NULL); \
  EXPECT_EQ(*root, *path->parent); \
  EXPECT_TRUE(search.isEmpty()); \
} \
\
TEST_F(TestName, RunToySearch) { \
  WeightVector w; \
  vector<scored_path> result = Search(graph, facts, \
                                &lemursHaveTails_[0], lemursHaveTails_.size(), \
                                &search, cache, &w, 100); \
  ASSERT_EQ(1, result.size()); \
  ASSERT_EQ(3, result[0].path->factLength); \
  EXPECT_EQ(CAT, result[0].path->fact[0]); \
  EXPECT_EQ(HAVE,  result[0].path->fact[1]); \
  EXPECT_EQ(TAIL, result[0].path->fact[2]); \
  EXPECT_EQ(ANIMAL, result[0].path->parent->fact[0]); \
  EXPECT_EQ(HAVE,  result[0].path->parent->fact[1]); \
  EXPECT_EQ(TAIL, result[0].path->parent->fact[2]); \
  EXPECT_EQ(LEMUR, result[0].path->parent->parent->fact[0]); \
  EXPECT_EQ(HAVE,    result[0].path->parent->parent->fact[1]); \
  EXPECT_EQ(TAIL,   result[0].path->parent->parent->fact[2]); \
  EXPECT_TRUE(result[0].path->parent->parent->parent == NULL || \
              result[0].path->parent->parent->parent->parent == NULL); \
}\
\
TEST_F(TestName, RunToySearchWithCache) { \
  WeightVector w; \
  vector<scored_path> result = Search(graph, facts, \
                                &lemursHaveTails_[0], lemursHaveTails_.size(), \
                                &search, bloom, &w, 100); \
  ASSERT_EQ(1, result.size()); \
  ASSERT_EQ(3, result[0].path->factLength); \
  EXPECT_EQ(CAT, result[0].path->fact[0]); \
  EXPECT_EQ(HAVE,  result[0].path->fact[1]); \
  EXPECT_EQ(TAIL, result[0].path->fact[2]); \
  EXPECT_EQ(ANIMAL, result[0].path->parent->fact[0]); \
  EXPECT_EQ(HAVE,  result[0].path->parent->fact[1]); \
  EXPECT_EQ(TAIL, result[0].path->parent->fact[2]); \
  EXPECT_EQ(LEMUR, result[0].path->parent->parent->fact[0]); \
  EXPECT_EQ(HAVE,    result[0].path->parent->parent->fact[1]); \
  EXPECT_EQ(TAIL,   result[0].path->parent->parent->fact[2]); \
  EXPECT_TRUE(result[0].path->parent->parent->parent == NULL || \
              result[0].path->parent->parent->parent->parent == NULL); \
}

//
// Breadth First Search (Memory)
//
InitSearchTypeTests(BreadthFirstSearch, BreadthFirstSearchTest)

// Make sure breadth first search really is a queue
TEST_F(BreadthFirstSearchTest, FIFOOrdering) {
  bool outOfMemory = true;
  EXPECT_TRUE(search.isEmpty());
  search.start(root);
  EXPECT_FALSE(search.isEmpty());
  search.push(root, 0, 1, ANIMAL,  0, 0, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
  search.push(root, 0, 1, CAT, 0, 1, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
  ASSERT_FALSE(outOfMemory);
  const Path* dist1 = search.debugGet(0);
  const Path* dist2 = search.debugGet(1);
  ASSERT_FALSE(search.isEmpty());
  EXPECT_EQ(*root,  *search.popWithoutScore());
  ASSERT_FALSE(search.isEmpty());
  EXPECT_EQ(*dist1, *search.popWithoutScore());
  ASSERT_FALSE(search.isEmpty());
  EXPECT_EQ(*dist2, *search.popWithoutScore());
  EXPECT_TRUE(search.isEmpty());
}

TEST_F(BreadthFirstSearchTest, StressTestAllocation) {
  // Initialize Search
  bool outOfMemory = true;
  EXPECT_TRUE(search.isEmpty());
  search.start(root);
  EXPECT_FALSE(search.isEmpty());
  // populate search
  for (int i = 0; i < 120; ++i) {
    search.push(root, 0, 1, i, 0, 0, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
    EXPECT_EQ(3, search.debugGet(i)->factLength);
  }
  for (int parent = 0; parent < 100; ++parent) {
    EXPECT_EQ(3, search.debugGet(parent)->factLength);
    for (int i = 0; i < 128; ++i) {
      const Path* p = search.debugGet(parent);
      EXPECT_EQ(3, search.debugGet(parent)->factLength);
      search.push(p, 0, 1, 1000 * parent + i, 0, 0, 0.0f, INFER_EQUIVALENT, cache, outOfMemory);
    }
  }
  // pop off elements
  EXPECT_FALSE(search.popWithoutScore() == NULL);  // popWithoutScore the root
  for (int i = 0; i < 120 + 100 * 128; ++i) {
    ASSERT_FALSE(search.isEmpty());
    const Path* elem = search.popWithoutScore();
    EXPECT_FALSE(elem == NULL);  // we didn't popWithoutScore off a null element
    EXPECT_EQ(3, elem->factLength);  // we only did mutations
    EXPECT_FALSE(elem->fact == NULL);  // the element's fact is not null
    EXPECT_FALSE(elem->parent == NULL);  // the element has a parent
    for (int k = 0; k < elem->factLength; ++k) {
      EXPECT_GE(elem->fact[k], 0);  // the word is positive (should always be the case anyways)
      EXPECT_LT(elem->fact[k], 120000);  // the word is within bounds (hopefully catches memory corruption errors)
    }
    EXPECT_FALSE(search.debugGet(i)->fact == NULL);  // the internal memory state is not corrupted
  }
  // Final checks
  EXPECT_TRUE(search.isEmpty());
}

//
// Uniform Cost Search
//
InitSearchTypeTests(UniformCostSearch, UniformCostSearchTest)

TEST_F(UniformCostSearchTest, StressTestAllocationAndOrdering) {
  // Initialize Search
  bool outOfMemory = true;
  EXPECT_TRUE(search.isEmpty());
  search.start(root);
  EXPECT_FALSE(search.isEmpty());
  // populate search
  for (int i = 0; i < 120; ++i) {
    search.push(root, 0, 1, i, 0, 0, static_cast <float> (rand()) / (static_cast <float> (RAND_MAX/100.0)), INFER_EQUIVALENT, cache, outOfMemory );
    EXPECT_EQ(3, search.debugGet(i)->factLength);
  }
  for (int parent = 0; parent < 100; ++parent) {
    EXPECT_EQ(3, search.debugGet(parent)->factLength);
    for (int i = 0; i < 128; ++i) {
      const Path* p = search.debugGet(parent);
      EXPECT_EQ(3, search.debugGet(parent)->factLength);
      search.push(p, 0, 1, 1000 * parent + i, 0, 0, static_cast <float> (rand()) / (static_cast <float> (RAND_MAX/100.0)), INFER_EQUIVALENT, cache, outOfMemory );
    }
  }
  // pop off elements
  EXPECT_FALSE(search.popWithoutScore() == NULL);  // popWithoutScore the root
  float lastReturned = 0.0f;
  for (int i = 0; i < 120 + 100 * 128; ++i) {
    ASSERT_FALSE(search.isEmpty());
    const Path* elem;
    float score = search.pop(&elem);
    EXPECT_GE(score, lastReturned);  // should always return elements in order
    lastReturned = score;
    EXPECT_FALSE(elem == NULL);  // we didn't popWithoutScore off a null element
    EXPECT_EQ(3, elem->factLength);  // we only did mutations
    EXPECT_FALSE(elem->fact == NULL);  // the element's fact is not null
    EXPECT_FALSE(elem->parent == NULL);  // the element has a parent
    for (int k = 0; k < elem->factLength; ++k) {
      EXPECT_GE(elem->fact[k], 0);  // the word is positive (should always be the case anyways)
      EXPECT_LT(elem->fact[k], 120000);  // the word is within bounds (hopefully catches memory corruption errors)
    }
    EXPECT_FALSE(search.debugGet(i)->fact == NULL);  // the internal memory state is not corrupted
  }
  // Final checks
  EXPECT_TRUE(search.isEmpty());
}

TEST_F(UniformCostSearchTest, DegenerateToBFS) {
  BreadthFirstSearch bfs;
  // Initialize Search
  bool outOfMemory = true;
  EXPECT_TRUE(search.isEmpty());
  EXPECT_TRUE(bfs.isEmpty());
  search.start(root);
  root = new Path(&lemursHaveTails_[0], lemursHaveTails_.size());  // need a new root so we don't double free
  bfs.start(root);
  EXPECT_FALSE(search.isEmpty());
  EXPECT_FALSE(bfs.isEmpty());
  // populate search
  float score = 0.0f;
  for (int i = 0; i < 120; ++i) {
    score += 0.1f;
    search.push(root, 0, 1, i, 0, 0, score, INFER_EQUIVALENT, cache, outOfMemory);
    bfs.push(root, 0, 1, i, 0, 0, score, INFER_EQUIVALENT, cache, outOfMemory);
  }
  for (int parent = 0; parent < 100; ++parent) {
    for (int i = 0; i < 128; ++i) {
      const Path* p  = search.debugGet(parent);
      const Path* pBFS = bfs.debugGet(parent);
      ASSERT_EQ(pBFS->fact[0], p->fact[0]);
      ASSERT_EQ(pBFS->fact[1], p->fact[1]);
      ASSERT_EQ(pBFS->fact[2], p->fact[2]);
      score += 0.1f;
      search.push(p, 0, 1, 1000 * parent + i, 0, 0, score, INFER_EQUIVALENT, cache, outOfMemory);
      bfs.push(pBFS, 0, 1, 1000 * parent + i, 0, 0, score, INFER_EQUIVALENT, cache, outOfMemory);
    }
  }
  // pop off elements
  EXPECT_FALSE(search.popWithoutScore() == NULL);  // popWithoutScore the root
  EXPECT_FALSE(bfs.popWithoutScore() == NULL); 
  for (int i = 0; i < 120 + 100 * 128; ++i) {
    ASSERT_FALSE(search.isEmpty());
    ASSERT_FALSE(bfs.isEmpty());
    const Path* elem = search.popWithoutScore();
    const Path* elemBFS = bfs.popWithoutScore();
    EXPECT_FALSE(elem == NULL);  // we didn't popWithoutScore off a null element
    EXPECT_FALSE(elemBFS == NULL);  // we didn't popWithoutScore off a null element
    ASSERT_EQ(elemBFS->fact[0], elem->fact[0]);
    ASSERT_EQ(elemBFS->fact[1], elem->fact[1]);
    ASSERT_EQ(elemBFS->fact[2], elem->fact[2]);
  }
  // Final checks
  EXPECT_TRUE(search.isEmpty());
  EXPECT_TRUE(bfs.isEmpty());
}


//
// Cache Strategy (None)
//
class CacheStrategyNoneTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Add base path element
    root = new Path(&lemursHaveTails_[0], lemursHaveTails_.size());
    lemursHaveTails_ = lemursHaveTails();
    animalsHaveTails_ = animalsHaveTails();
    catsHaveTails_ = catsHaveTails();
  }

  virtual void TearDown() {
    delete root;
  }

  CacheStrategyNone cache;
  Path* root;

  std::vector<word> lemursHaveTails_;
  std::vector<word> animalsHaveTails_;
  std::vector<word> catsHaveTails_;
};

// Make sure that we don't see a node in an empty cache
TEST_F(CacheStrategyNoneTest, AlwaysNotSeen) {
  EXPECT_FALSE(cache.isSeen(root->fact, root->factLength));
}

// Make sure adding a node to the noop cache has no effect
TEST_F(CacheStrategyNoneTest, AddHasNoEffect) {
  EXPECT_FALSE(cache.isSeen(root->fact, root->factLength));
  cache.add(root->fact, root->factLength);
  EXPECT_FALSE(cache.isSeen(root->fact, root->factLength));
}

//
// Cache Strategy (Bloom)
//
class CacheStrategyBloomTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    cache = new CacheStrategyBloom();
    // Add base path element
    root = new Path(&lemursHaveTails_[0], lemursHaveTails_.size());
    lemursHaveTails_ = lemursHaveTails();
    animalsHaveTails_ = animalsHaveTails();
    catsHaveTails_ = catsHaveTails();
  }

  virtual void TearDown() {
    delete root;
    delete cache;
  }

  CacheStrategyBloom* cache;
  Path* root;

  std::vector<word> lemursHaveTails_;
  std::vector<word> animalsHaveTails_;
  std::vector<word> catsHaveTails_;
};

// Make sure that we don't see a node in an empty cache
TEST_F(CacheStrategyBloomTest, NotSeenByDefault) {
  EXPECT_FALSE(cache->isSeen(root->fact, root->factLength));
}

// Make sure adding a node registers it as seen
TEST_F(CacheStrategyBloomTest, AddingImpliesSeen) {
  EXPECT_FALSE(cache->isSeen(root->fact, root->factLength));
  cache->add(root->fact, root->factLength);
  EXPECT_TRUE(cache->isSeen(root->fact, root->factLength));
}
