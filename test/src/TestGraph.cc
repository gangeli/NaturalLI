#include <limits.h>

#include "gtest/gtest.h"
#include "Config.h"
#include "Graph.h"

using namespace std;

class MockGraphTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    mockGraph = ReadMockGraph();
    EXPECT_FALSE(mockGraph == NULL);
  }

  virtual void TearDown() {
    delete mockGraph;
  }

  Graph* mockGraph;
};

// Check to make sure that our test vocabulary is included in
// the mock graph.
TEST_F(MockGraphTest, HasVocabulary) {
  EXPECT_EQ("lemur",  string(mockGraph->gloss(2479928)));
  EXPECT_EQ("Timone", string(mockGraph->gloss(16442985)));
  EXPECT_EQ("animal", string(mockGraph->gloss(3701)));
  EXPECT_EQ("cat",    string(mockGraph->gloss(27970)));
  EXPECT_EQ("have",   string(mockGraph->gloss(3844)));
  EXPECT_EQ("tail",   string(mockGraph->gloss(14221)));
}

// An independent check to make sure the edge counts of the graph
// are sufficient for HasCorrectEdges
TEST_F(MockGraphTest, HasCorrectEdgeCounts) {
  EXPECT_EQ(2, mockGraph->outgoingEdges(2479928).size());
  EXPECT_EQ(1, mockGraph->outgoingEdges(3701).size());
}

// Check to make sure the mock graph has the correct edges
TEST_F(MockGraphTest, HasCorrectEdges) {
  EXPECT_EQ(16442985, mockGraph->outgoingEdges(2479928)[0].sink);
  EXPECT_EQ(1, mockGraph->outgoingEdges(2479928)[0].type);
  EXPECT_FLOAT_EQ(0.01, mockGraph->outgoingEdges(2479928)[0].cost);
  EXPECT_EQ(3701, mockGraph->outgoingEdges(2479928)[1].sink);
  EXPECT_EQ(0, mockGraph->outgoingEdges(2479928)[1].type);
  EXPECT_FLOAT_EQ(0.42, mockGraph->outgoingEdges(2479928)[1].cost);

  EXPECT_EQ(27970, mockGraph->outgoingEdges(3701)[0].sink);
  EXPECT_EQ(1, mockGraph->outgoingEdges(3701)[0].type);
  EXPECT_FLOAT_EQ(42.0, mockGraph->outgoingEdges(3701)[0].cost);

  EXPECT_EQ(0, mockGraph->outgoingEdges(27970).size());
  EXPECT_EQ(0, mockGraph->outgoingEdges(3844).size());
  EXPECT_EQ(0, mockGraph->outgoingEdges(14221).size());
}
