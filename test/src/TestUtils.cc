#include <limits.h>

#include "gtest/gtest.h"

#include "Utils.h"

using namespace std;

class UtilsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Add base path element
    searchType = new BreadthFirstSearch();
    lemursHaveTails_ = lemursHaveTails();
    lemurs = new Path(&lemursHaveTails_[0], lemursHaveTails().size());
    searchType->start(lemurs);
    animals = searchType->push(lemurs, 0, 1, 3701, 0, 0);
    cats = searchType->push(animals, 0, 1, 27970, 0, 1);
    graph = ReadMockGraph();
  }

  virtual void TearDown() {
    delete lemurs, animals, cats;
  }

  const Path* lemurs;
  const Path* animals;
  const Path* cats;
  Graph* graph;
  SearchType* searchType;
  std::vector<word> lemursHaveTails_;
};

TEST_F(UtilsTest, ToStringPhrase) {
  EXPECT_EQ(string("lemur have tail"), toString(*graph,  &lemursHaveTails()[0], lemursHaveTails().size()));
  EXPECT_EQ(string("animal have tail"), toString(*graph, &animalsHaveTails()[0], animalsHaveTails().size()));
  EXPECT_EQ(string("cat have tail"), toString(*graph,    &catsHaveTails()[0], animalsHaveTails().size()));
}

TEST_F(UtilsTest, ToStringPath) {
  EXPECT_EQ(string("lemur have tail; from\n  <start>"),
            toString(*graph, *searchType, lemurs));
  EXPECT_EQ(string("animal have tail; from\n  lemur have tail; from\n  <start>"),
            toString(*graph, *searchType, animals));
  EXPECT_EQ(string("cat have tail; from\n  animal have tail; from\n  lemur have tail; from\n  <start>"),
            toString(*graph, *searchType, cats));
}
