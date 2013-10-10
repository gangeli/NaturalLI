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
  
  virtual const vector<word> keys() const {
    vector<word> keys(size);
    for (int i = 0; i < size; ++i) {
      keys[i] = i;
    }
    return keys;
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
  }
  // (clean up)
  release(edgeSession);
  
  
  // Finish
  printf("%s\n", "Done reading the graph.");
  return new InMemoryGraph(index2gloss, edges, numWords);
}


class MockGraph : public Graph {
 public:
  MockGraph() {
    noEdges = new vector<edge>();
    // Edges out of Lemur
    lemurEdges = new vector<edge>();
    edge lemurToTimone;
    lemurToTimone.source = 2479928;
    lemurToTimone.sink = 16442985;
    lemurToTimone.type = 1;
    lemurToTimone.cost = 0.01;
    lemurEdges->push_back(lemurToTimone);
    edge lemurToAnimal;
    lemurToAnimal.source = 2479928;
    lemurToAnimal.sink = 3701;
    lemurToAnimal.type = 0;
    lemurToAnimal.cost = 0.42;
    lemurEdges->push_back(lemurToAnimal);
    // Edges out of Animal
    animalEdges = new vector<edge>();
    edge animalToCat;
    animalToCat.source = 3701;
    animalToCat.sink = 27970;
    animalToCat.type = 1;
    animalToCat.cost = 42.00;
    animalEdges->push_back(animalToCat);
    // Other edges
    timoneEdges = new vector<edge>();
    catEdges = new vector<edge>();
    haveEdges = new vector<edge>();
    tailEdges = new vector<edge>();
  }
  ~MockGraph() {
    delete noEdges, lemurEdges, animalEdges, timoneEdges,
           catEdges, haveEdges, tailEdges;
  }

  virtual const vector<edge>& outgoingEdges(word source) const {
    switch (source) {
      case 2479928:  // lemur
        return *lemurEdges;
      case 3701:     // animal
        return *animalEdges;
      case 16442985: // timone
        return *timoneEdges;
      case 27970:    // cat
        return *catEdges;
      case 3844:     // have
        return *haveEdges;
      case 14221:    // tail
        return *tailEdges;
      default:
        return *noEdges;
    }
  }

  virtual const string gloss(word word) const {
    switch (word) {
      case 2479928:  // lemur
        return "lemur";
      case 3701:     // animal
        return "animal";
      case 16442985: // timone
        return "Timone";
      case 27970:    // cat
        return "cat";
      case 3844:     // have
        return "have";
      case 14221:    // tail
        return "tail";
      default:
        return "<<unk>>";
    }
  }
  
  virtual const vector<word> keys() const {
    vector<word> keys(6);
    keys[0] = 2479928;
    keys[1] = 3701;
    keys[2] = 16442985;
    keys[3] = 27970;
    keys[4] = 3844;
    keys[5] = 14221;
    return keys;
  }

 private:
  vector<edge>* noEdges;
  vector<edge>* lemurEdges;
  vector<edge>* animalEdges;
  vector<edge>* timoneEdges;
  vector<edge>* catEdges;
  vector<edge>* haveEdges;
  vector<edge>* tailEdges;
};

Graph* ReadMockGraph() {
  return new MockGraph();
}
