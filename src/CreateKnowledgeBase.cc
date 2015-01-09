#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <signal.h>
#include <iostream>
#include <iomanip>
#include <unistd.h>

#include "config.h"
#include "SynSearch.h"
#include "Graph.h"

using namespace std;

Tree* readTreeFromStdin() {
  string conll = "";
  char line[256];
  char newline = '\n';
  uint32_t numLines = 0;
  while (!cin.fail()) {
    cin.getline(line, 255);
    numLines += 1;
    conll.append(line, strlen(line));
    conll.append(&newline, 1);
    if (line[0] == '\0') { break; }
  }
  if (numLines >= MAX_FACT_LENGTH) {
    return NULL;
  } else {
    return new Tree(conll);
  }
}

/**
 * The Entry point for streaming dependency trees into candidate
 * facts.
 *
 * TODO(gabor) didn't kill conj in 'Born in Honolulu, Hawaii, Obama is a graduate of Columbia University and Harvard Law School, where he served as president of the Harvard Law Review'
 */
int32_t main( int32_t argc, char *argv[] ) {
  BidirectionalGraph* graph = new BidirectionalGraph(ReadGraph());

  while (!cin.fail()) {
    Tree* sentence = readTreeFromStdin();
    if (sentence != NULL) {
      if (sentence->length > 0) {
        ForwardPartialSearch(graph, sentence, [](SearchNode node) -> void {
          printf("%lu\n", node.factHash());
        });
      }
      delete sentence;
    } else {
      fprintf(stderr, "Input is too long! skipping...\n");
    }
    
  }

  delete graph;
  return 0;
}
