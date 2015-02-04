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

/**
 * Reads a tree from standard input, where the standard input is
 * already a CoNLL representation of the tree.
 */
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
    if (numLines > 0 && line[0] == '\0') { 
      break;
    }
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
 */
int32_t main( int32_t argc, char *argv[] ) {
  while (!cin.fail()) {
    Tree* sentence = readTreeFromStdin();
    if (sentence != NULL) {
      if (sentence->length > 0) {
        printf("%lu\n", sentence->hash());
      } else {
        fprintf(stderr, "No sentence input!\n");
        printf("-1\n");
      }
      delete sentence;
    } else {
      fprintf(stderr, "Input is too long! skipping.\n");
      printf("-1\n");
    }
    fflush(stderr);
  }

  return 0;
}
