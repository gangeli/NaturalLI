#include <limits.h>

#include "gtest/gtest.h"

#include "Utils.h"

class UtilsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Add base path element
    lemurs = new Path(lemursHaveTails());
    animals = new Path(*lemurs, animalsHaveTails(), 0);
    cats = new Path(*animals, catsHaveTails(), 1);
    graph = ReadMockGraph();
  }

  virtual void TearDown() {
    delete lemurs, animals, cats;
  }

  Path* lemurs;
  Path* animals;
  Path* cats;
  Graph* graph;
};

TEST_F(UtilsTest, ToStringPhrase) {
  EXPECT_EQ(string("lemur have tail"), toString(graph, lemursHaveTails()));
  EXPECT_EQ(string("animal have tail"), toString(graph, animalsHaveTails()));
  EXPECT_EQ(string("cat have tail"), toString(graph, catsHaveTails()));
}

TEST_F(UtilsTest, ToStringPath) {
  EXPECT_EQ(string("lemur have tail; from\n  <start>"),
            toString(graph, lemurs));
  EXPECT_EQ(string("animal have tail; from\n  lemur have tail; from\n  <start>"),
            toString(graph, animals));
  EXPECT_EQ(string("cat have tail; from\n  animal have tail; from\n  lemur have tail; from\n  <start>"),
            toString(graph, cats));
}
