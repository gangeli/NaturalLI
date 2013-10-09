#include <stdio.h>
#include <stdlib.h>

#include "InferenceServer.h"
#include "Graph.h"
#include "Postgres.h"

/**
 * A simple in-memory stored Graph, with the word indexer and the edge
 * matrix.
 */
class InMemoryGraph : public Graph {
 private:
  char** index2gloss;
  vector<edge>* edges;
  uint64_t size;
  
 public:
  InMemoryGraph(char** index2gloss, vector<edge>* edges, uint64_t size)
    : index2gloss(index2gloss), edges(edges), size(size) { }

  ~InMemoryGraph() {
    for (int i = 0; i < size; ++i) { 
      free(index2gloss[i]);
    }
    free(index2gloss);
    free(edges);
  }

  virtual const vector<edge>& outgoingEdges(word source) const {
    return edges[source];
  }

  virtual const string gloss(word word) const {
    return string(index2gloss[word]);
  }
};



Graph* ReadGraph() {
  printf("Reading graph...\n");
  const uint32_t numWords = 18858251; // TODO(gabor) don't hard code

  // Read words
  char** index2gloss = (char**) malloc( numWords * sizeof(char*) );
  // (query)
  char wordQuery[127];
  snprintf(wordQuery, 127, "SELECT * FROM %s;", PG_TABLE_WORD.c_str());
  session wordSession = query(wordQuery);
  if (!wordSession.valid) {
    return NULL;
  }
  // (process)
  for (int i = 0; i < wordSession.numResults; ++i) {
    if (i % 1000000 == 0) { printf("loaded %iM words\n", i / 1000000); fflush(stdout); }
    if (PQgetisnull(wordSession.result, i, 0) || PQgetisnull(wordSession.result, i, 1)) {
      printf("null row in word indexer; query=%s\n", wordQuery);
      return NULL;
    }
    word key     = atoi(PQgetvalue(wordSession.result, i, 0));
    char* gloss = PQgetvalue(wordSession.result, i, 1);
    index2gloss[key] = gloss;
//    printf("row: %d -> %s\n", key, gloss);
  }
  // (clean up)
  release(wordSession);
  
  // Read edges
  // (query)
  char edgeQuery[127];
  snprintf(edgeQuery, 127, "SELECT * FROM %s;", PG_TABLE_EDGE.c_str());
  session edgeSession = query(edgeQuery);
  if (!edgeSession.valid) {
    return NULL;
  }
  // (process)
  vector<edge>* edges = (vector<edge>*) malloc((numWords+1) * sizeof(vector<edge>));
  for (int i = 0; i < edgeSession.numResults; ++i) {
    if (i % 1000000 == 0) { printf("loaded %iM edges\n", i / 1000000); fflush(stdout); }
    if (PQgetisnull(edgeSession.result, i, 0) ||
        PQgetisnull(edgeSession.result, i, 1) ||
        PQgetisnull(edgeSession.result, i, 2) ||
        PQgetisnull(edgeSession.result, i, 3)    ) {
      printf("null row in edges; query=%s\n", wordQuery);
      return NULL;
    }
    edge edge;
    edge.source = atoi(PQgetvalue(edgeSession.result, i, 0));
    edge.sink   = atoi(PQgetvalue(edgeSession.result, i, 1));
    edge.type   = atoi(PQgetvalue(edgeSession.result, i, 2));
    edge.cost   = atof(PQgetvalue(edgeSession.result, i, 3));
    edges[edge.source].push_back(edge);
//    printf("row: %s --[%d]--> %s  (%f)\n", index2gloss[edge.source], edge.type,
//                                           index2gloss[edge.sink], edge.cost);
  }
  // (clean up)
  release(edgeSession);
  
  
  // Finish
  printf("%s\n", "Done reading the graph.");
  return new InMemoryGraph(index2gloss, edges, numWords);
}
