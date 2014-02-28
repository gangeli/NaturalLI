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
  snprintf(edgeQuery, 127, "SELECT * FROM %s WHERE type=1 OR type=0 ORDER BY type, source_sense ASC;", PG_TABLE_EDGE.c_str());
  PGIterator edgeIter = PGIterator(edgeQuery);
  uint64_t edgeI = 0;
  while (edgeIter.hasNext()) {
    PGRow row = edgeIter.next();
    edge e;
    word source = atoi(row[0]);
    e.sense     = atoi(row[1]);
    e.sink      = atoi(row[2]);
        // sink sense: row[3]
    e.type      = atoi(row[4]);
    e.cost      = atof(row[5]);
    if (edgesSizes[source] >= edgeCapacities[source] - 1) {
      struct edge* newEdges = (struct edge*) malloc(edgeCapacities[source] * 2 * sizeof(struct edge));
      memcpy(newEdges, edges[source], edgeCapacities[source] * sizeof(struct edge));
      edgeCapacities[source] = 2 * edgeCapacities[source];
      free(edges[source]);
      edges[source] = newEdges;
    }
    edges[source][edgesSizes[source]] = e;
    edgesSizes[source] += 1;
    edgeI += 1;
    if (edgeI % 1000000 == 0) {
      printf("loaded %luM edges\n", edgeI / 1000000);
    }
  }
  free(edgeCapacities);
  printf("%s edges loaded.\n", edgeI);
  
  
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
    lemurToTimone.sink = TIMONE;
    lemurToTimone.sense = 0;
    lemurToTimone.type = WORDNET_DOWN;
    lemurToTimone.cost = 0.01;
    lemurEdges->push_back(lemurToTimone);
    edge lemurToAnimal;
    lemurToAnimal.sink = ANIMAL;
    lemurToTimone.sense = 0;
    lemurToAnimal.type = WORDNET_UP;
    lemurToAnimal.cost = 0.42;
    lemurEdges->push_back(lemurToAnimal);
    // Edges out of Animal
    animalEdges = new vector<edge>();
    edge animalToCat;
    animalToCat.sink = CAT;
    lemurToTimone.sense = 0;
    animalToCat.type = WORDNET_DOWN;
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
      case LEMUR:  // lemur
        return *lemurEdges;
      case ANIMAL:     // animal
        return *animalEdges;
      case TIMONE: // timone
        return *timoneEdges;
      case CAT:    // cat
        return *catEdges;
      case HAVE:     // have
        return *haveEdges;
      case TAIL:    // tail
        return *tailEdges;
      default:
        return *noEdges;
    }
  }

  virtual const char* gloss(const tagged_word& taggedWord) const {
    const word w = getWord(taggedWord);
    switch (w) {
      case LEMUR:  // lemur
        return "lemur";
      case ANIMAL:     // animal
        return "animal";
      case TIMONE: // timone
        return "Timone";
      case CAT:    // cat
        return "cat";
      case HAVE:     // have
        return "have";
      case TAIL:    // tail
        return "tail";
      default:
        return "<<unk>>";
    }
  }
  
  virtual const vector<word> keys() const {
    vector<word> keys(6);
    keys[0] = LEMUR;
    keys[1] = ANIMAL;
    keys[2] = TIMONE;
    keys[3] = CAT;
    keys[4] = HAVE;
    keys[5] = TAIL;
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
