/*
 * TODO(gabor) implement negation intro / elimination
 *
 */

#include <thread>

#include "NaturalLIIO.h"
#include "FactDB.h"

using namespace std;
using namespace btree;


/**
 * The Entry point for querying the truth of facts.
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

  // Create bridge
  JavaBridge *proc = new JavaBridge();
  // Load graph
  Graph *graph = ReadGraph();

  // Start server
  std::thread t(startServer, SERVER_PORT, proc, graph, kb);
  t.detach();

  // Start REPL
  uint32_t retVal = repl(graph, proc, kb);
  delete graph;
  delete proc;
  delete kb;
  return retVal;
}
