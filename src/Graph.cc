#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "Graph.h"
#include "Postgres.h"
#include "Utils.h"

using namespace std;

/**
 * A simple in-memory stored Graph, with the word indexer and the edge
 * matrix.
 */
class InMemoryGraph : public Graph {
 private:
  char** index2gloss;
  edge** edges;
  uint32_t* edgesSizes;
  uint64_t size;
  
 public:
  InMemoryGraph(char** index2gloss, edge** edges, uint32_t* edgesSizes, uint64_t size)
    : index2gloss(index2gloss), edges(edges), edgesSizes(edgesSizes), size(size) { }

  ~InMemoryGraph() {
    for (int i = 0; i < size; ++i) { 
      free(index2gloss[i]);
      free(edges[i]);
    }
    free(index2gloss);
    free(edges);
    free(edgesSizes);
  }

  virtual const edge* outgoingEdgesFast(const tagged_word& source, uint32_t* size) const {
    const word w = getWord(source);
    *size = edgesSizes[w];
    return edges[w];
  }

  virtual const char* gloss(const tagged_word& word) const {
    return index2gloss[getWord(word)];
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
  const uint32_t numWords
    = atoi(PGIterator("SELECT COUNT(*) FROM word_indexer;").next()[0]);

  // Read words
  char** index2gloss = (char**) malloc( numWords * sizeof(char*) );
  // (query)
  char wordQuery[127];
  snprintf(wordQuery, 127, "SELECT * FROM %s;", PG_TABLE_WORD.c_str());
  PGIterator words = PGIterator(wordQuery);
  uint64_t wordI = 0;
  while (words.hasNext()) {
    PGRow row = words.next();
    size_t len = strlen(row[1]);
    char* gloss = (char*) malloc( len * sizeof(char) );
    strncpy(gloss, row[1], len);
    index2gloss[atoi(row[0])] = gloss;
    wordI += 1;
    if (wordI % 1000000 == 0) {
      printf("loaded %luM words\n", wordI / 1000000);
    }
  }
  
  // Read edges
  struct edge** edges = (struct edge**) malloc((numWords+1) * sizeof(struct edge*));
  uint32_t* edgesSizes = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  uint32_t* edgeCapacities = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  for (uint32_t i = 0; i < numWords + 1; ++i) {
    edgesSizes[i] = 0;
    edges[i] = (struct edge*) malloc(4 * sizeof(struct edge));
    edgeCapacities[i] = 4;
  }
  // (query)
  char edgeQuery[127];
  snprintf(edgeQuery, 127, "SELECT * FROM %s WHERE type=1 OR type=2 ORDER BY type ASC;", PG_TABLE_EDGE.c_str());
  PGIterator edgeIter = PGIterator(edgeQuery);
  uint64_t edgeI = 0;
  while (edgeIter.hasNext()) {
    PGRow row = edgeIter.next();
    edge e;
    e.source = atol(row[0]);
    e.sink   = atoi(row[1]);
    e.type   = atoi(row[2]);
    e.cost   = atof(row[3]);
    if (edgesSizes[e.source] >= edgeCapacities[e.source] - 1) {
      struct edge* newEdges = (struct edge*) malloc(edgeCapacities[e.source] * 2 * sizeof(struct edge));
      memcpy(newEdges, edges[e.source], edgeCapacities[e.source] * sizeof(struct edge));
      edgeCapacities[e.source] = 2 * edgeCapacities[e.source];
      free(edges[e.source]);
      edges[e.source] = newEdges;
    }
    edges[e.source][edgesSizes[e.source]] = e;
    edgesSizes[e.source] += 1;
    edgeI += 1;
    if (edgeI % 1000000 == 0) {
      printf("loaded %luM edges\n", edgeI / 1000000);
    }
  }
  free(edgeCapacities);
  
  
  // Finish
  printf("%s\n", "Done reading the graph.");
  return new InMemoryGraph(index2gloss, edges, edgesSizes, numWords);
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
  
  virtual const edge* outgoingEdgesFast(const tagged_word& source, uint32_t* outputLength) const {
    vector<edge> edges = outgoingEdges(source);
    *outputLength = edges.size();
    edge* rtn = (struct edge*) malloc( edges.size() * sizeof(edge) );  // WARNING: memory leak
    for (int i = 0; i < edges.size(); ++i) { 
      rtn[i] = edges[i];
    }
    return rtn;
  }

  virtual const vector<edge> outgoingEdges(const tagged_word& source) const {
    const word w = getWord(source);
    switch (w) {
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

  virtual const char* gloss(const tagged_word& taggedWord) const {
    const word w = getWord(taggedWord);
    switch (w) {
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
