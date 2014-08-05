#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "Graph.h"
#include "Utils.h"
#include "Postgres.h"
#include "btree_set.h"

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
  edge** edgesBySink;
  uint32_t* edgesSizes;
  uint64_t size;
  btree::btree_set<tagged_word> invalidDeletions;
  
 public:
  InMemoryGraph(char** index2gloss,
                edge** edgesBySink,
                uint32_t* edgesSizes,
                uint64_t size,
                btree::btree_set<tagged_word> invalidDeletions)
    : index2gloss(index2gloss), edgesBySink(edgesBySink), 
      edgesSizes(edgesSizes), size(size),
      invalidDeletions(invalidDeletions) { }

  ~InMemoryGraph() {
    for (int i = 0; i < size; ++i) { 
      if (index2gloss[i] != NULL) {
        free(index2gloss[i]);
      }
      free(edgesBySink[i]);
    }
    free(index2gloss);
    free(edgesBySink);
    free(edgesSizes);
  }

  virtual const edge* incomingEdgesFast(const tagged_word& sink, uint32_t* size) const {
    const word w = sink.word;
    *size = edgesSizes[w];
    return edgesBySink[w];
  }

  virtual const char* gloss(const tagged_word& word) const {
    const uint32_t w = word.word;
    if (index2gloss[w] == NULL) {
      return "<UNK>";
    } else {
      return index2gloss[word.word];
    }
  }
  
  virtual const vector<word> keys() const {
    vector<word> keys(size);
    for (int i = 0; i < size; ++i) {
      keys[i] = i;
    }
    return keys;
  }
  
  virtual const bool containsDeletion(const edge& deletion) const {
    tagged_word w = getTaggedWord(deletion.source,  deletion.source_sense, MONOTONE_DEFAULT);
    return invalidDeletions.find( w ) == invalidDeletions.end();
  }
};

//
// Read Any Graph
//
Graph* readGraph(const uint32_t numWords, 
                 PGIterator* wordIter,
                 PGIterator* edgeIter,
                 PGIterator* invalidDeletionIter,
                 const bool& mock) {
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
    e.source       = fast_atoi(row[0]);
    e.source_sense = fast_atoi(row[1]);
    e.sink         = fast_atoi(row[2]);
    e.sink_sense   = fast_atoi(row[3]);
    e.type         = fast_atoi(row[4]);
    e.cost         = atof(row[5]);
    const word& sink = e.sink;
    if (edgesSizes[sink] >= edgeCapacities[sink] - 1) {
      struct edge* newEdges = (struct edge*) malloc(edgeCapacities[sink] * 2 * sizeof(struct edge));
      memcpy(newEdges, edges[sink], edgeCapacities[sink] * sizeof(struct edge));
      edgeCapacities[sink] = 2 * edgeCapacities[sink];
      free(edges[sink]);
      edges[sink] = newEdges;
    }
    edges[sink][edgesSizes[sink]] = e;
    edgesSizes[sink] += 1;
    edgeI += 1;
    if (!mock && edgeI % 1000000 == 0) {
      printf("  loaded %luM edges\n", edgeI / 1000000);
    }
  }
  free(edgeCapacities);
  if (!mock) { printf("  %lu edges loaded.\n", edgeI); }
  
  // Read invalid deletions
  btree::btree_set<tagged_word> invalidDeletions;
  while (invalidDeletionIter->hasNext()) {
    PGRow row = invalidDeletionIter->next();
    invalidDeletions.insert(getTaggedWord(atoi(row[0]), atoi(row[1]), MONOTONE_DEFAULT));
  }
  if (!mock) { printf("  %lu invalid deletions.\n", invalidDeletions.size()); }
  
  // Finish
  if (!mock) { printf("%s\n", "  done reading the graph."); }
  return new InMemoryGraph(index2gloss, edges, edgesSizes, numWords, invalidDeletions);
}


//
// Read Real Graph
//
Graph* ReadGraph() {
  printf("Reading graph...\n");
  // Words
  printf("  creating word iterator...\n");
  char wordQuery[128];
  snprintf(wordQuery, 127, "SELECT COUNT(*) FROM %s;", PG_TABLE_WORD);
  const uint32_t numWords
    = atoi(PGIterator(wordQuery).next()[0]);
  snprintf(wordQuery, 127, "SELECT * FROM %s;", PG_TABLE_WORD);
  PGIterator wordIter = PGIterator(wordQuery);
  
  // Edges
  printf("  creating edge iterator...\n");
  char edgeQuery[128];
  snprintf(edgeQuery, 127, "SELECT * FROM %s WHERE type <> %u ORDER BY type, sink_sense ASC;",
    PG_TABLE_EDGE, WORDNET_NOUN_SYNONYM); // TODO(gabor) re-enable synonyms
  PGIterator edgeIter = PGIterator(edgeQuery);

  // Invalid deletions
  printf("  creating valid deletion iterator...\n");
  char invalidDeletionQuery[128];
  snprintf(invalidDeletionQuery, 127,
           "SELECT * FROM %s;", PG_TABLE_PRIVATIVE);
  PGIterator invalidDeletionIter = PGIterator(invalidDeletionQuery);

  return readGraph(numWords, &wordIter, &edgeIter, &invalidDeletionIter, false);
}

//
// Read Dummy Graph
//
Graph* ReadMockGraph() {
  const char* lemur[]  {LEMUR_STR,  "lemur" };  PGRow lemurRow(lemur);
  const char* animal[] {ANIMAL_STR, "animal" }; PGRow animalRow(animal);
  const char* potto[]  {POTTO_STR,  "potto" }; PGRow pottoRow(potto);
  const char* cat[]    {CAT_STR,    "cat" };    PGRow catRow(cat);
  const char* have[]   {HAVE_STR,   "have" };   PGRow haveRow(have);
  const char* tail[]   {TAIL_STR,   "tail" };   PGRow tailRow(tail);
  PGRow words[]   { lemurRow, animalRow, pottoRow, catRow, haveRow, tailRow };
  MockPGIterator wordIter(6, words);
  
  const char* lemur2potto[]{LEMUR_STR,   "0", POTTO_STR,  "0", "1", "0.01"  }; PGRow lemur2pottoRow(lemur2potto);
  const char* potto2lemur[]{POTTO_STR,   "0", LEMUR_STR,  "0", "0", "0.01"  }; PGRow potto2lemurRow(potto2lemur);
  const char* lemur2animal[]{LEMUR_STR,  "0", ANIMAL_STR, "0", "0", "0.42"  }; PGRow lemur2animalRow(lemur2animal);
  const char* animal2lemur[]{ANIMAL_STR, "0", LEMUR_STR,  "0", "1", "0.42"  }; PGRow animal2lemurRow(animal2lemur);
  const char* animal2cat[]{  ANIMAL_STR, "0", CAT_STR,    "0", "1", "42.00" }; PGRow animal2catRow(animal2cat);
  const char* cat2animal[]{  CAT_STR,    "0", ANIMAL_STR, "0", "0", "42.00" }; PGRow cat2animalRow(cat2animal);
  PGRow edges[]{ /*lemur2pottoRow, lemur2animalRow, animal2catRow, */
                 potto2lemurRow, animal2lemurRow, cat2animalRow };
  MockPGIterator edgeIter(3, edges);
  
  MockPGIterator invalidDeletionIter(0, NULL);
  
  return readGraph(200000, &wordIter, &edgeIter, & invalidDeletionIter, true);
}
