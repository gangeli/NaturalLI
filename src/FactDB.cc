#include "FactDB.h"

#include <cstdio>

using namespace std;
using namespace btree;

#define CHUNK_SIZE 1048576

//
// Append To KB
//
void appendToKB(const uint64_t* factStream,
                const uint64_t& count,
                btree_set<uint64_t>* kb) {
  for (uint64_t i = 0; i < count; ++i) {
    kb->insert(factStream[i]);
  }
}

const btree_set<uint64_t> readKB(string path) {
  btree_set<uint64_t> kb;

  // Open the KB file
  FILE* file;
  file = fopen(path.c_str(), "r");
  if (file == NULL) {
    fprintf(stderr, "Can't open KB file %s!\n", path.c_str());
    exit(1);
  }

  // Read chunks
  uint64_t* buffer = (uint64_t*) malloc(CHUNK_SIZE * sizeof(uint64_t));
  uint64_t numRead;
  uint64_t nextPrint = 10 * 1000 * 1000;
  fprintf(stderr, "Reading the knowledge base...");
  while ( 
      (numRead = fread(buffer, sizeof(uint64_t), CHUNK_SIZE, file)) 
        == CHUNK_SIZE ) {
    appendToKB(buffer, numRead, &kb);
    if (kb.size() > nextPrint) {
      nextPrint += (10 * 1000 * 1000);
      fprintf(stderr, ".");
    }
  }
  appendToKB(buffer, numRead, &kb);
  fprintf(stderr, "done [size=%lu].\n", kb.size());

  // Return
  free(buffer);
  fclose(file);
  return kb;
}

