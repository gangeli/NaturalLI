#include <limits.h>
#include <vector>

#include "gtest/gtest.h"
#include "Config.h"
#include "Graph.h"

using namespace std;

/**
 * Iterate over the entire graph, to make sure that we are
 * not going to either segfault or return an invalid edge.
 */
TEST(GraphITest, AllEdgesValid) {
  Graph* graph = ReadGraph();
  vector<word> keys = graph->keys();
  for (int w = 0; w < keys.size(); ++w) {
    uint32_t numEdges = 0;
    const edge* edges = graph->outgoingEdgesFast(w, &numEdges);
    for (uint32_t i = 0; i < numEdges; ++i) {
      ASSERT_LT(edges[i].sink, keys.size());
      ASSERT_GE(edges[i].cost, 0.0);
    }
  }
}

