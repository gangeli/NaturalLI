#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "Graph.h"
#include "Postgres.h"
#include "Utils.h"

using namespace std;

class MockPGIterator : public PGIterator {
 public:
  MockPGIterator(const uint32_t size, const PGRow* rows) :
    PGIterator(NULL, 0),
    size(size), rows(rows), index(0) { }

  virtual bool hasNext()  {
    return index < size;
  }

  virtual PGRow next() {
    index += 1;
    return rows[index - 1];
  }

 private:
  const uint32_t size;
  const PGRow* rows;
  uint32_t index;
};

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
      if (index2gloss[i] != NULL) {
        free(index2gloss[i]);
      }
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
    const uint32_t w = getWord(word);
    if (index2gloss[w] == NULL) {
      return "<UNK>";
    } else {
      return index2gloss[getWord(word)];
    }
  }
  
  virtual const vector<word> keys() const {
    vector<word> keys(size);
    for (int i = 0; i < size; ++i) {
      keys[i] = i;
    }
    return keys;
  }
};

//
// Read Any Graph
//
Graph* readGraph(const uint32_t numWords, PGIterator* wordIter, PGIterator* edgeIter, bool mock) {
  // Read words
  char** index2gloss = (char**) malloc( numWords * sizeof(char*) );
  memset(index2gloss, 0, numWords * sizeof(char*));
  uint64_t wordI = 0;
  while (wordIter->hasNext()) {
    // Get word
    PGRow row = wordIter->next();
    size_t len = strlen(row[1]);
    char* gloss = (char*) malloc( len * sizeof(char) + 1 );
    // Strip out Unicode
    // Really, this is lossy, but who cares it's all indexed anyways...
    uint32_t target = 0;
    for (uint32_t i = 0; i < len; ++i) {
      const char& c = row[1][i];
      if (32 <= c && c < 127) {
        gloss[target] = c;
        target += 1;
      }
    }
    gloss[target] = '\0';
    // Set gloss
    index2gloss[atoi(row[0])] = gloss;
    wordI += 1;
    if (wordI % 1000000 == 0) {
      printf("loaded %luM words\n", wordI / 1000000);
    }
  }
  
  // Read edges
  // (initialize variables)
  struct edge** edges = (struct edge**) malloc((numWords+1) * sizeof(struct edge*));
  uint32_t* edgesSizes = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  memset(edgesSizes, 0, numWords * sizeof(uint32_t));
  uint32_t* edgeCapacities = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  for (uint32_t i = 0; i < numWords; ++i) {
    edges[i] = (struct edge*) malloc(4 * sizeof(struct edge));
    edgeCapacities[i] = 4;
  }
  uint64_t edgeI = 0;
  // (iterate over rows in DB)
  while (edgeIter->hasNext()) {
    PGRow row = edgeIter->next();
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
  printf("%lu edges loaded.\n", edgeI);
  
  
  // Finish
  printf("%s\n", "Done reading the graph.");
  return new InMemoryGraph(index2gloss, edges, edgesSizes, numWords);
}


//
// Read Real Graph
//
Graph* ReadGraph() {
  printf("Reading graph...\n");
  // Words
  char wordQuery[127];
  snprintf(wordQuery, 127, "SELECT COUNT(*) FROM %s;", PG_TABLE_WORD.c_str());
  const uint32_t numWords
    = atoi(PGIterator(wordQuery).next()[0]);
  snprintf(wordQuery, 127, "SELECT * FROM %s;", PG_TABLE_WORD.c_str());
  PGIterator wordIter = PGIterator(wordQuery);
  
  // Edges
  char edgeQuery[127];
  // TODO(gabor) load all edge types, eventually
  snprintf(edgeQuery, 127, "SELECT * FROM %s WHERE type=1 OR type=0 OR type=18 OR type=19 OR type=14 OR type=15 ORDER BY type, source_sense ASC;", PG_TABLE_EDGE.c_str());
  PGIterator edgeIter = PGIterator(edgeQuery);

  return readGraph(numWords, &wordIter, &edgeIter, false);
}

//
// Read Dummy Graph
//
Graph* ReadMockGraph() {
  const char* lemur[]  {LEMUR_STR,  "lemur" };  PGRow lemurRow(lemur);
  const char* animal[] {ANIMAL_STR, "animal" }; PGRow animalRow(animal);
  const char* timone[] {TIMONE_STR, "Timone" }; PGRow timoneRow(timone);
  const char* cat[]    {CAT_STR,    "cat" };    PGRow catRow(cat);
  const char* have[]   {HAVE_STR,   "have" };   PGRow haveRow(have);
  const char* tail[]   {TAIL_STR,   "tail" };   PGRow tailRow(tail);
  PGRow words[]   { lemurRow, animalRow, timoneRow, catRow, haveRow, tailRow };
  MockPGIterator wordIter(6, words);
  
  const char* lemur2timone[]{LEMUR_STR,  "0", TIMONE_STR, "0", "1", "0.01"  }; PGRow lemur2timoneRow(lemur2timone);
  const char* lemur2animal[]{LEMUR_STR,  "0", ANIMAL_STR, "0", "0", "0.42"  }; PGRow lemur2animalRow(lemur2animal);
  const char* animal2cat[]{  ANIMAL_STR, "0", CAT_STR,    "0", "1", "42.00" }; PGRow animal2catRow(animal2cat);
  PGRow edges[]{ lemur2timoneRow, lemur2animalRow, animal2catRow };
  MockPGIterator edgeIter(3, edges);
  
  return readGraph(19000000, &wordIter, &edgeIter, true);
}
