#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <signal.h>
#include <iostream>
#include <iomanip>
#include <unistd.h>

#include "NaturalLIIO.h"
#include "SynSearch.h"
#include "Graph.h"

using namespace std;


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
