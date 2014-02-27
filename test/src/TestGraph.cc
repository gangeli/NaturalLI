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
  EXPECT_EQ("lemur",  string(mockGraph->gloss(LEMUR)));
  EXPECT_EQ("Timone", string(mockGraph->gloss(TIMONE)));
  EXPECT_EQ("animal", string(mockGraph->gloss(ANIMAL)));
  EXPECT_EQ("cat",    string(mockGraph->gloss(CAT)));
  EXPECT_EQ("have",   string(mockGraph->gloss(HAVE)));
  EXPECT_EQ("tail",   string(mockGraph->gloss(TAIL)));
}

// An independent check to make sure the edge counts of the graph
// are sufficient for HasCorrectEdges
TEST_F(MockGraphTest, HasCorrectEdgeCounts) {
  EXPECT_EQ(2, mockGraph->outgoingEdges(LEMUR).size());
  EXPECT_EQ(1, mockGraph->outgoingEdges(ANIMAL).size());
}

// Check to make sure the mock graph has the correct edges
TEST_F(MockGraphTest, HasCorrectEdges) {
  EXPECT_EQ(TIMONE, mockGraph->outgoingEdges(LEMUR)[0].sink);
  EXPECT_EQ(WORDNET_DOWN, mockGraph->outgoingEdges(LEMUR)[0].type);
  EXPECT_FLOAT_EQ(0.01, mockGraph->outgoingEdges(LEMUR)[0].cost);
  EXPECT_EQ(ANIMAL, mockGraph->outgoingEdges(LEMUR)[1].sink);
  EXPECT_EQ(WORDNET_UP, mockGraph->outgoingEdges(LEMUR)[1].type);
  EXPECT_FLOAT_EQ(0.42, mockGraph->outgoingEdges(LEMUR)[1].cost);

  EXPECT_EQ(CAT, mockGraph->outgoingEdges(ANIMAL)[0].sink);
  EXPECT_EQ(WORDNET_DOWN, mockGraph->outgoingEdges(ANIMAL)[0].type);
  EXPECT_FLOAT_EQ(42.0, mockGraph->outgoingEdges(ANIMAL)[0].cost);

  EXPECT_EQ(0, mockGraph->outgoingEdges(CAT).size());
  EXPECT_EQ(0, mockGraph->outgoingEdges(HAVE).size());
  EXPECT_EQ(0, mockGraph->outgoingEdges(TAIL).size());
}
