#include "NaturalLIIO.h"

#include <thread>

#include "FactDB.h"

using namespace std;

int32_t main(int32_t argc, char *argv[]) {
  Graph *graph = ReadGraph();

  while (!cin.fail()) {
    // (create alignments)
    Tree* premise = NULL;
    
    while (!cin.fail()) {
      Tree* tree = readTreeFromStdin();
      if (tree == NULL) {
        continue;
      }
      if (tree->length == 0) {
        break;
      }

      if (premise == NULL) {
        premise = tree;
      } else {
        AlignmentSimilarity sim = tree->alignToPremise(*premise, *graph);
        sim.printFeatures(*tree);
        fflush(stdout);
        delete premise;
        delete tree;
        premise = NULL;
      }
    }
  }
}
