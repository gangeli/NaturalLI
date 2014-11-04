#include <limits.h>
#include <vector>
#include <stdint.h>

#include <config.h>
#include "gtest/gtest.h"
#include "Graph.h"
#include "Utils.h"

#ifdef _WIN32
#include <intrin.h>
uint64_t rdtsc(){
    return __rdtsc();
}
#else
uint64_t rdtsc(){
    unsigned int lo,hi;
    __asm__ __volatile__ ("rdtsc" : "=a" (lo), "=d" (hi));
    return ((uint64_t)hi << 32) | lo;
}
#endif

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
    const edge* edges = graph->incomingEdgesFast(w, &numEdges);
    for (uint32_t i = 0; i < numEdges; ++i) {
      ASSERT_LT(edges[i].sink, keys.size());
      ASSERT_LT(edges[i].source, keys.size());
      ASSERT_GE(edges[i].cost, 0.0);
    }
  }
  delete graph;
}
