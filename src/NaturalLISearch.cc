#include "NaturalLIIO.h"

#include <thread>

#include "FactDB.h"

using namespace std;
using namespace btree;

/**
 * The entry point for querying the truth of facts already parsed as parse
 * trees.
 */
int32_t main(int32_t argc, char *argv[]) {
  init();

  // Read the knowledge base
  const btree_set<uint64_t> *kb;
  if (KB_FILE[0] != '\0') {
    kb = readKB(string(KB_FILE));
  } else {
    kb = new btree_set<uint64_t>;
    fprintf(stderr,
            "No knowledge base given (configure with KB_FILE=/path/to/kb)\n");
  }

  // Load graph
  Graph *graph = ReadGraph();

//  // Start server
//  std::thread t(startServer, SERVER_PORT, proc, graph, kb);
//  t.detach();

  // Start REPL
  uint32_t retVal = repl(graph, kb);
//  delete graph;  // TODO(gabor) this causes a seg fault? Harmless, but maybe points to some memory bug.
  delete kb;
  return retVal;
}
 
/*
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
*/
