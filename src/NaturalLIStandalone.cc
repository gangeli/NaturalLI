/*
 * TODO(gabor) implement negation intro / elimination
 *
 */

#include <sys/resource.h>
#include <signal.h>
#include <thread>

#include "NaturalLIIO.h"
#include "FactDB.h"

#define MEM_ENV_VAR "MAXMEM_GB"

#ifndef ERESTART
#define ERESTART EINTR
#endif
extern int errno;

using namespace std;
using namespace btree;

/**
 * Set a memory limit, if defined
 */
void setmemlimit() {
  struct rlimit memlimit;
  long bytes;
  if (getenv(MEM_ENV_VAR) != NULL) {
    bytes = atol(getenv(MEM_ENV_VAR)) * (1024 * 1024 * 1024);
    memlimit.rlim_cur = bytes;
    memlimit.rlim_max = bytes;
    setrlimit(RLIMIT_AS, &memlimit);
  }
}



/**
 * The function to call for caught signals. In practice, this is a NOOP.
 */
void signalHandler(int32_t s) {
  fprintf(stderr, "(caught signal %d; use 'kill' or EOF to end the program)\n",
          s);
  fprintf(stderr, "What do we say to the God of death?\n");
  fprintf(stderr, "  \"Not today...\"\n");
}


/**
 * The Entry point for querying the truth of facts.
 */
int32_t main(int32_t argc, char *argv[]) {
  // Handle signals
  // (set memory limit)
  setmemlimit();
  // (set up handler)
  struct sigaction sigIntHandler;
  sigIntHandler.sa_handler = signalHandler;
  sigemptyset(&sigIntHandler.sa_mask);
  sigIntHandler.sa_flags = 0;
  // (catch signals)
  //  sigaction(SIGINT,  &sigIntHandler, NULL);  // Stopping SIGINT causes the
  //  java process to die
  sigaction(SIGPIPE, &sigIntHandler, NULL);

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
