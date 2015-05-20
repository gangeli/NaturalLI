#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "Utils.h"
#include "Graph.h"
#include "GZip.h"
#include "SynSearch.h"
#include "btree_set.h"

using namespace std;

class MockGZIterator : public GZIterator {
 public:
  MockGZIterator(const uint32_t size, const GZRow* rows) :
    GZIterator(),
    size(size), rows(rows), index(0) { }

  virtual bool hasNext()  {
    return index < size;
  }

  virtual GZRow next() {
    index += 1;
    return rows[index - 1];
  }

 private:
  const uint32_t size;
  const GZRow* rows;
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
  uint32_t size;
  btree::btree_set<tagged_word> invalidDeletions;
  btree::btree_set<word> invalidDeletionWords;
  
 public:
  InMemoryGraph(char** index2gloss,
                edge** edgesBySink,
                uint32_t* edgesSizes,
                uint32_t size,
                btree::btree_set<tagged_word> invalidDeletions)
        : index2gloss(index2gloss), edgesBySink(edgesBySink), 
          edgesSizes(edgesSizes), size(size),
          invalidDeletions(invalidDeletions) {
    for (auto iter = invalidDeletions.begin();
              iter != invalidDeletions.end(); ++iter) {
      invalidDeletionWords.insert(iter->word);
    }
  }

  ~InMemoryGraph() {
    for (int i = 0; i < size; ++i) { 
      if (index2gloss[i] != NULL) {
        free(index2gloss[i]);
      }
      if (edgesBySink[i] != NULL) {
        free(edgesBySink[i]);
      }
    }
    free(index2gloss);
    free(edgesBySink);
    free(edgesSizes);
  }

  virtual const edge* incomingEdgesFast(const word& sink, uint32_t* size) const {
    assert (sink < this->size);
    *size = edgesSizes[sink];
    return edgesBySink[sink];
  }

  virtual const char* gloss(const tagged_word& word) const {
    const uint32_t w = word.word;
    if (w == INVALID_WORD) {
      return "<INVALID_WORD>";
    } else if (index2gloss[w] == NULL) {
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
    if (invalidDeletionWords.find( deletion.source ) != invalidDeletionWords.end()) {
      tagged_word w = getTaggedWord(deletion.source,  deletion.source_sense, MONOTONE_DEFAULT);
      return invalidDeletions.find( w ) == invalidDeletions.end();
    } else {
      return true;
    }
  }
  
  /** {@inheritDoc} */
  virtual const uint64_t vocabSize() const {
    return size;
  }
};
  

//
// BidirectionalGraph()
//
BidirectionalGraph::BidirectionalGraph(const Graph* impl) 
    : impl(impl),
      size(impl->vocabSize()) {
  outgoingEdgeData.resize(size);
  uint32_t length;
  for (uint32_t sink = 0; sink < size; ++sink) {
    const edge* incomingFromSink = incomingEdgesFast(sink, &length);
    for (uint32_t source = 0; source < length; ++source) {
      outgoingEdgeData[source].push_back(incomingFromSink[source]);
    }
  }
}

//
// Read Any Graph
//
Graph* readGraph(const uint32_t numWords, 
                 GZIterator* wordIter,
                 GZIterator* edgeIter,
                 GZIterator* invalidDeletionIter,
                 const bool& mock) {
  // Read words
  char** index2gloss = (char**) malloc( numWords * sizeof(char*) );
  memset(index2gloss, 0, numWords * sizeof(char*));
  uint64_t wordI = 0;
  while (wordIter->hasNext()) {
    // Get word
    GZRow row = wordIter->next();
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
      fprintf(stderr, "loaded %luM words\n", wordI / 1000000);
    }
  }
  
  // Read edges
  // (initialize variables)
  struct edge** edges = (struct edge**) malloc((numWords+1) * sizeof(struct edge*));
  memset(edges, 0, (numWords+1) * sizeof(struct edge*));
  uint32_t* edgesSizes = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  memset(edgesSizes, 0, (numWords+1) * sizeof(uint32_t));
  uint32_t* edgeCapacities = (uint32_t*) malloc((numWords+1) * sizeof(uint32_t));
  for (uint32_t i = 0; i < numWords; ++i) {
    edges[i] = (struct edge*) malloc(4 * sizeof(struct edge));
    edgeCapacities[i] = 4;
  }
  uint64_t edgeI = 0;
  // (iterate over rows in DB)
  while (edgeIter->hasNext()) {
    GZRow row = edgeIter->next();
    if (row.size() != 6) {
      fprintf(stderr, "Invalid row in edge iterator (size=%u)!", row.size());
      exit(1);
    }
    edge e;
    e.source       = fast_atoi(row[0]);
    if (e.source >= numWords) {
      fprintf(stderr, "Invalid source word=%u (numWords=%u)\n", e.source, numWords);
      exit(1);
    }
    e.source_sense = fast_atoi(row[1]);
    if (e.source_sense >= (0x1 << SENSE_ENTROPY)) {
      fprintf(stderr, "Invalid source sense=%u (SENSE_ENTROPY=%u; edgeType=%s)\n", e.source_sense, SENSE_ENTROPY, row[4]);
      exit(1);
    }
    e.sink         = fast_atoi(row[2]);
    if (e.sink >= numWords) {
      fprintf(stderr, "Invalid sink word=%u (numWords=%u)\n", e.sink, numWords);
      exit(1);
    }
    e.sink_sense   = fast_atoi(row[3]);
    if (e.sink_sense >= (0x1 << SENSE_ENTROPY)) {
      fprintf(stderr, "Invalid sink sense=%u (SENSE_ENTROPY=%u; edgeType=%s)\n", e.sink_sense, SENSE_ENTROPY, row[4]);
      exit(1);
    }
    e.type         = fast_atoi(row[4]);
    if (e.type >= NUM_MUTATION_TYPES) {
      fprintf(stderr, "Invalid mutation type=%u (NUM_MUTATION_TYPES=%u)\n", e.type, NUM_MUTATION_TYPES);
      exit(1);
    }
    if (e.sink == e.source && e.sink_sense == e.source_sense) {
      continue;  // Ignore any identity edges
    }
    e.cost         = atof(row[5]);
    if (isinf(e.cost)) { fprintf(stderr, "Infinite cost edge: %f (parsed from %s)\n", e.cost, row[5]); exit(1); }
    if (e.cost != e.cost) { fprintf(stderr, "NaN cost edge: %f (parsed from %s)\n", e.cost, row[5]); exit(1); }
    if (e.cost != e.cost) { fprintf(stderr, "NaN cost edge: %f (parsed from %s)\n", e.cost, row[5]); exit(1); }
    if (e.cost < 0.0) { fprintf(stderr, "Negative cost edge: %f (parsed from %s)\n", e.cost, row[5]); exit(1); }
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
      fprintf(stderr, "  loaded %luM edges\n", edgeI / 1000000);
    }
  }
  free(edgeCapacities);
  if (!mock) { fprintf(stderr, "  %lu edges loaded.\n", edgeI); }
  
  // Read invalid deletions
  btree::btree_set<tagged_word> invalidDeletions;
  while (invalidDeletionIter->hasNext()) {
    GZRow row = invalidDeletionIter->next();
    invalidDeletions.insert(getTaggedWord(atoi(row[0]), atoi(row[1]), MONOTONE_DEFAULT));
  }
  if (!mock) { fprintf(stderr, "  %lu invalid deletions.\n", invalidDeletions.size()); }
  
  // Finish
  if (!mock) { fprintf(stderr, "%s\n", "  done reading the graph."); }
  return new InMemoryGraph(index2gloss, edges, edgesSizes, numWords, invalidDeletions);
}


//
// Read Real Graph
//
Graph* ReadGraph() {
  fprintf(stderr, "Reading graph...\n");
  // Words
  fprintf(stderr, "  creating word iterator...\n");
  GZIterator wordCountIter = GZIterator(VOCAB_FILE);
  uint32_t numWords = 0;
  while (wordCountIter.hasNext()) {
    uint32_t wordIndex = fast_atoi(wordCountIter.next()[0]);
    numWords = (wordIndex >= numWords ? (wordIndex + 1) : numWords);
  }
  GZIterator wordIter = GZIterator(VOCAB_FILE);
  
  // Edges
  fprintf(stderr, "  creating edge iterator...\n");
  GZIterator edgeIter = GZIterator(GRAPH_FILE);

  // Invalid deletions
  fprintf(stderr, "  creating valid deletion iterator...\n");
  GZIterator invalidDeletionIter = GZIterator(PRIVATIVE_FILE);

  return readGraph(numWords, &wordIter, &edgeIter, &invalidDeletionIter, false);
}

//
// Read Dummy Graph
//
Graph* ReadMockGraph(const bool& allowCycles) {
  const char* lemur[]  {LEMUR_STR,  "lemur" };  GZRow lemurRow(lemur, 2);
  const char* animal[] {ANIMAL_STR, "animal" }; GZRow animalRow(animal, 2);
  const char* potto[]  {POTTO_STR,  "potto" };  GZRow pottoRow(potto, 2);
  const char* cat[]    {CAT_STR,    "cat" };    GZRow catRow(cat, 2);
  const char* have[]   {HAVE_STR,   "have" };   GZRow haveRow(have, 2);
  const char* tail[]   {TAIL_STR,   "tail" };   GZRow tailRow(tail, 2);
  const char* all[]    {ALL_STR,    "all" };    GZRow allRow(all, 2);
  const char* furry[]  {FURRY_STR,  "furry" };  GZRow furryRow(furry, 2);
  GZRow words[]   { lemurRow, animalRow, pottoRow, catRow, haveRow, tailRow, allRow, furryRow };
  MockGZIterator wordIter(8, words);
  
  char hypo[8];
  sprintf(hypo, "%d", HYPONYM);
  char hyper[8];
  sprintf(hyper, "%d", HYPERNYM);

  const char* lemur2potto[]{LEMUR_STR,   "0", POTTO_STR,  "0", hypo, "0.01"  }; GZRow lemur2pottoRow(lemur2potto, 6);
  const char* potto2lemur[]{POTTO_STR,   "0", LEMUR_STR,  "0", hyper, "0.01"  }; GZRow potto2lemurRow(potto2lemur, 6);
  const char* lemur2animal[]{LEMUR_STR,  "0", ANIMAL_STR, "0", hyper, "0.42"  }; GZRow lemur2animalRow(lemur2animal, 6);
  const char* animal2lemur[]{ANIMAL_STR, "0", LEMUR_STR,  "0", hypo, "0.42"  }; GZRow animal2lemurRow(animal2lemur, 6);
  const char* animal2cat[]{  ANIMAL_STR, "0", CAT_STR,    "0", hypo, "42.00" }; GZRow animal2catRow(animal2cat, 6);
  const char* cat2animal[]{  CAT_STR,    "0", ANIMAL_STR, "0", hyper, "42.00" }; GZRow cat2animalRow(cat2animal, 6);
  GZRow edges[] = {cat2animalRow, cat2animalRow, cat2animalRow, cat2animalRow, cat2animalRow, cat2animalRow };
  edges[0] = potto2lemurRow;
  edges[1] = animal2lemurRow;
  edges[2] = cat2animalRow;
  if (allowCycles) {
    edges[3] = lemur2pottoRow;
    edges[4] = lemur2animalRow;
    edges[5] = animal2catRow;
  }
  MockGZIterator edgeIter(allowCycles ? 6 : 3, edges);
  
  const char* invalidDeletions[]{HAVE_STR, "3"};
  GZRow invalidDeletionsRow(invalidDeletions, 2); 
  GZRow dels[]{ invalidDeletionsRow };
  MockGZIterator invalidDeletionIter(1, dels);
  
  return readGraph(HIGHEST_MOCK_WORD_INDEX + 1, &wordIter, &edgeIter, 
  & invalidDeletionIter, true);
}
