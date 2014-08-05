#include <limits.h>
#include <bitset>
#include <ctime>

#include "gtest/gtest.h"

#include "Types.h"
#include "Utils.h"

using namespace std;

class UtilsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    bool outOfMemory = true;
    cache = new CacheStrategyNone();
    // Add base path element
    searchType = new BreadthFirstSearch();
    lemursHaveTails_ = lemursHaveTails();
    lemurs = new Path(&lemursHaveTails_[0], lemursHaveTails().size(), 1);
    searchType->start(lemurs);
    edge e;
    e.type = 0;
    e.cost = 1.0;
    searchType->push(lemurs, 0, 1, ANIMAL, NULL_WORD, e, 0.0f, cache, outOfMemory);
    ASSERT_FALSE(outOfMemory);
    animals = ((BreadthFirstSearch*) searchType)->debugGet(0);
    e.type = 1;
    searchType->push(animals, 0, 1, CAT, NULL_WORD, e, 0.0f, cache, outOfMemory);
    ASSERT_FALSE(outOfMemory);
    animals = ((BreadthFirstSearch*) searchType)->debugGet(0);
    cats = ((BreadthFirstSearch*) searchType)->debugGet(1);
    graph = ReadMockGraph();
  }

  virtual void TearDown() {
    delete cache;
    delete graph;
  }

  const Path* lemurs;
  const Path* animals;
  const Path* cats;
  Graph* graph;
  SearchType* searchType;
  CacheStrategy* cache;
  std::vector<tagged_word> lemursHaveTails_;
};

TEST_F(UtilsTest, ToStringPhrase) {
  EXPECT_EQ(string("[^]lemur_0 [^]have_0 [^]tail_0"), toString(*graph,  &lemursHaveTails()[0], lemursHaveTails().size()));
  EXPECT_EQ(string("[^]animal_0 [^]have_0 [^]tail_0"), toString(*graph, &animalsHaveTails()[0], animalsHaveTails().size()));
  EXPECT_EQ(string("[^]cat_0 [^]have_0 [^]tail_0"), toString(*graph,    &catsHaveTails()[0], animalsHaveTails().size()));
}

TEST_F(UtilsTest, ToStringPath) {
  EXPECT_EQ(string("[^]lemur_0 [^]have_0 [^]tail_0; from\n  <start>"),
            toString(*graph, *searchType, lemurs));
  EXPECT_EQ(string("[^]animal_0 [^]have_0 [^]tail_0; from\n  [^]lemur_0 [^]have_0 [^]tail_0; from\n  <start>"),
            toString(*graph, *searchType, animals));
  EXPECT_EQ(string("[^]cat_0 [^]have_0 [^]tail_0; from\n  [^]animal_0 [^]have_0 [^]tail_0; from\n  [^]lemur_0 [^]have_0 [^]tail_0; from\n  <start>"),
            toString(*graph, *searchType, cats));
}

TEST_F(UtilsTest, ToStringTime) {
  EXPECT_EQ(string("0s"), toString(0l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1s"), toString(1l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("59s"), toString(59l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1m 0s"), toString(60l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1m 1s"), toString(61l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("2h 20m 3s"), toString((2l*60l*60l + 20l*60l + 3l) * CLOCKS_PER_SEC));
  EXPECT_EQ(string("10d 2h 20m 3s"), toString((10l*24l*60l*60l + 2l*60l*60l + 20l*60l + 3l) * CLOCKS_PER_SEC));
}

TEST_F(UtilsTest, FastATOI) {
  EXPECT_EQ(0, fast_atoi("0"));
  EXPECT_EQ(1, fast_atoi("1"));
  EXPECT_EQ(104235, fast_atoi("104235"));
  for (uint8_t shift = 0; shift < 31; ++shift) {
    uint32_t i = (1l << shift);
    char buffer[32];
    snprintf(buffer, 31, "%u", i);
    EXPECT_EQ(i, fast_atoi(buffer));
    EXPECT_EQ(atoi(buffer), fast_atoi(buffer));
  }
}

TEST_F(UtilsTest, FastATOIPeculiarities) {
  // Parsing from PSQL is a bit of a pain, and it makes it easier
  // if we can just accept the final '}' and discard it in atoi()
  EXPECT_EQ(104235, fast_atoi("104235}"));
}
