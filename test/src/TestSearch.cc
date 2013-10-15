#include <limits.h>

#include "gtest/gtest.h"
#include "Config.h"
#include "Utils.h"
#include "Search.h"
#include "Graph.h"
#include "FactDB.h"

//
// Path
//
class PathTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Add base path element
    root = new Path(&lemursHaveTails()[0], lemursHaveTails().size());
    dist1 = new Path(*root, &animalsHaveTails()[0], animalsHaveTails().size(), 0);
    dist2 = new Path(*dist1, &catsHaveTails()[0], catsHaveTails().size(), 1);
    // Some paths with explicit ids
    explicit1 = new Path(1, 0, &lemursHaveTails()[0], lemursHaveTails().size(), 255);
    explicit2 = new Path(2, 1, &animalsHaveTails()[0], animalsHaveTails().size(), 0);
  }

  virtual void TearDown() {
    delete root, dist1, dist2, explicit1, explicit2;
  }

  Path* root;
  Path* dist1;
  Path* dist2;
  Path* explicit1;
  Path* explicit2;
};


// Ensures that a Path element can be created
TEST_F(PathTest, CanCreate) {
  EXPECT_FALSE(root == NULL);
  EXPECT_FALSE(dist1 == NULL);
  EXPECT_FALSE(dist2 == NULL);
}

// Test path elements made with explicit ids
TEST_F(PathTest, CanManageIdExplicitly) {
  EXPECT_FALSE(explicit1 == NULL);
  EXPECT_FALSE(explicit2 == NULL);
  EXPECT_TRUE(explicit1->source() == NULL);
  EXPECT_EQ(1, explicit2->sourceId);
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
TEST_F(PathTest, GetSource) {
  EXPECT_TRUE(root->source() == NULL);
  EXPECT_FALSE(dist1->source() == NULL);
  EXPECT_FALSE(dist2->source() == NULL);
  
  EXPECT_EQ(root,  dist1->source());
  EXPECT_EQ(dist1, dist2->source());

  EXPECT_EQ(*root, *dist1->source());
  EXPECT_EQ(*dist1, *dist2->source());
}


//
// Common Search Type Tests
//

#define InitSearchTypeTests(ClassName, TestName) \
class TestName : public ::testing::Test { \
 protected: \
  virtual void SetUp() { \
    a = new Path(&lemursHaveTails()[0], lemursHaveTails().size()); \
    b = new Path(*a, &animalsHaveTails()[0], animalsHaveTails().size(), 0); \
    c = new Path(*b, &catsHaveTails()[0], catsHaveTails().size(), 1); \
    cache = new CacheStrategyNone(); \
    graph = ReadMockGraph(); \
    facts = ReadMockFactDB(); \
  } \
  \
  virtual void TearDown() { \
    delete a, b, c, cache, graph, facts; \
  } \
  \
  ClassName search; \
  Path* a; \
  Path* b; \
  Path* c; \
  CacheStrategy* cache; \
  Graph* graph; \
  FactDB* facts; \
}; \
TEST_F(TestName, IsEmpty) { \
  EXPECT_TRUE(search.isEmpty()); \
} \
\
TEST_F(TestName, Push) { \
  EXPECT_TRUE(search.isEmpty()); \
  search.push(*a); \
  EXPECT_FALSE(search.isEmpty()); \
  search.push(*b); \
  EXPECT_FALSE(search.isEmpty()); \
} \
\
TEST_F(TestName, Pop) { \
  EXPECT_TRUE(search.isEmpty()); \
  search.push(*a); \
  EXPECT_FALSE(search.isEmpty()); \
  EXPECT_EQ(*a, search.pop()); \
  EXPECT_TRUE(search.isEmpty()); \
}

//
// Breadth First Search (Memory)
//
InitSearchTypeTests(BreadthFirstSearch, BreadthFirstSearchTest)

// Make sure breadth first search really is a queue
TEST_F(BreadthFirstSearchTest, FIFOOrdering) {
  EXPECT_TRUE(search.isEmpty());
  search.push(*a);
  search.push(*b);
  EXPECT_FALSE(search.isEmpty());
  EXPECT_EQ(*a, search.pop());
  EXPECT_EQ(*b, search.pop());
  EXPECT_TRUE(search.isEmpty());
}

TEST_F(BreadthFirstSearchTest, RunBFS) {
  vector<Path*> result = Search(graph, facts,
                                &lemursHaveTails()[0], lemursHaveTails().size(),
                                &search, cache, 100);
  ASSERT_EQ(1, result.size());
  ASSERT_EQ(3, result[0]->factLength);
  EXPECT_EQ(c->fact[0], result[0]->fact[0]);
  EXPECT_EQ(c->fact[1], result[0]->fact[1]);
  EXPECT_EQ(c->fact[2], result[0]->fact[2]);
  EXPECT_EQ(c->edgeType, result[0]->edgeType);
}


//
// Cache Strategy (None)
//
class CacheStrategyNoneTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Add base path element
    root = new Path(&lemursHaveTails()[0], lemursHaveTails().size());
  }

  virtual void TearDown() {
    delete root;
  }

  CacheStrategyNone cache;
  Path* root;
};

// Make sure that we don't see a node in an empty cache
TEST_F(CacheStrategyNoneTest, AlwaysNotSeen) {
  EXPECT_FALSE(cache.isSeen(*root));
}

// Make sure adding a node to the noop cache has no effect
TEST_F(CacheStrategyNoneTest, AddHasNoEffect) {
  EXPECT_FALSE(cache.isSeen(*root));
  cache.add(*root);
  EXPECT_FALSE(cache.isSeen(*root));
}

