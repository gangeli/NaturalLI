#include <limits.h>

#include <config.h>
#include "gtest/gtest.h"
#include "Graph.h"
#include "Models.h"

using namespace std;

class MockGraphTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    mockGraph = ReadMockGraph();
    ASSERT_FALSE(mockGraph == NULL);
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
  EXPECT_EQ("potto",  string(mockGraph->gloss(POTTO)));
  EXPECT_EQ("animal", string(mockGraph->gloss(ANIMAL)));
  EXPECT_EQ("cat",    string(mockGraph->gloss(CAT)));
  EXPECT_EQ("have",   string(mockGraph->gloss(HAVE)));
  EXPECT_EQ("tail",   string(mockGraph->gloss(TAIL)));
}

// An independent check to make sure the edge counts of the graph
// are sufficient for HasCorrectEdges
TEST_F(MockGraphTest, HasCorrectEdgeCounts) {
  EXPECT_EQ(2, mockGraph->incomingEdges(LEMUR).size());
  EXPECT_EQ(1, mockGraph->incomingEdges(ANIMAL).size());
}

// Check to make sure the mock graph has the correct edges
TEST_F(MockGraphTest, HasCorrectEdges) {
  EXPECT_EQ(POTTO.word, mockGraph->incomingEdges(LEMUR)[0].source);
  EXPECT_EQ(HYPERNYM, mockGraph->incomingEdges(LEMUR)[0].type);
  EXPECT_FLOAT_EQ(0.01, mockGraph->incomingEdges(LEMUR)[0].cost);
  EXPECT_EQ(ANIMAL.word, mockGraph->incomingEdges(LEMUR)[1].source);
  EXPECT_EQ(HYPONYM, mockGraph->incomingEdges(LEMUR)[1].type);
  EXPECT_FLOAT_EQ(0.42, mockGraph->incomingEdges(LEMUR)[1].cost);

  EXPECT_EQ(CAT.word, mockGraph->incomingEdges(ANIMAL)[0].source);
  EXPECT_EQ(HYPERNYM, mockGraph->incomingEdges(ANIMAL)[0].type);
  EXPECT_FLOAT_EQ(42.0, mockGraph->incomingEdges(ANIMAL)[0].cost);

  EXPECT_EQ(0, mockGraph->incomingEdges(CAT).size());
  EXPECT_EQ(0, mockGraph->incomingEdges(HAVE).size());
  EXPECT_EQ(0, mockGraph->incomingEdges(TAIL).size());
}

// Check invalid deletions
TEST_F(MockGraphTest, CheckInvalidDeletions) {
  // Initialize edge
  edge e;
  e.sink = 0;
  e.sink_sense = 0;
  e.type = 0;
  e.source = HAVE.word;
  // SHould be true
  e.source_sense = 0;
  EXPECT_TRUE(mockGraph->containsDeletion(e));
  // Should be false
  e.source_sense = 3;
  EXPECT_FALSE(mockGraph->containsDeletion(e));
  // Should be true again
  e.source_sense = 4;
  EXPECT_TRUE(mockGraph->containsDeletion(e));
}
