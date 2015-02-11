#include "FactDB.h"

#include <cstdlib>
#include <cstdio>

using namespace std;

#define CHUNK_SIZE 1048576

/*
 * Reads a sequence of text lines representing hashed facts
 * (uint64_t values), and writes the corresponding values to a text file
 * in a way that can be read by readKB(string)
 */
int32_t main( int32_t argc, char *argv[] ) {
  if (argc < 2) {
    fprintf(stderr, "usage: mk_kb filename\n");
    exit(1);
  }

  // Create output file
  char* filename = argv[1];
  FILE* file;
  file = fopen(filename, "wb");
  if (file == NULL) {
    fprintf(stderr, "Can't open KB file for writing: %s!\n", filename);
    exit(1);
  }

  // Read from stdin, write to buffer
  char line[256];
  uint64_t* buffer = (uint64_t*) malloc(CHUNK_SIZE * sizeof(uint64_t));
  uint64_t index = 0;
  memset(line, 0, sizeof(line));
  while (!cin.fail()) {
    // Parse the line
    cin.getline(line, 255);
    if (line[0] == '\0') { continue; }
    // Convert the line to an integer
    const uint64_t hash = strtoul(line, NULL, 10);
    buffer[index] = hash;
    index += 1;
    // Optionally write the chunk
    if (index == CHUNK_SIZE) {
      fwrite(buffer, CHUNK_SIZE * sizeof(uint64_t), 1, file);
      index = 0;
    }
  }

  // Write the last few facts
  fwrite(buffer, sizeof(uint64_t), index, file);
  fclose(file);

  // Re-read the knowledge base, printing out the values
//  fprintf(stderr, "Re-reading file\n");
//  const btree::btree_set<uint64_t> kb = readKB(filename);
//  for ( auto iter = kb.begin(); iter != kb.end(); ++iter ) {
//    fprintf(stderr, "contains: %lu\n", *iter);
//  }
//  fprintf(stderr, "DONE\n");

}


