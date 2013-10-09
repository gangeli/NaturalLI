#include <string>
#include <cstdio>

#include "Config.h"
#include "Graph.h"
#include "FactDB.h"
#include "Search.h"

using namespace std;

//
// Debugging Helpers
//
/**
 *  The fact (lemur, have, tail)
 */
const vector<word> lemurHaveTail() {
  vector<word> fact;
  fact.push_back(2479928);
  fact.push_back(3844);
  fact.push_back(14221);
  return fact;
}

/**
 * Print the string gloss for the given fact.
 */
string toString(const Graph* graph, const vector<word> fact) {
  string gloss = "";
  for(vector<word>::const_iterator it = fact.begin(); it != fact.end(); ++it) {
    gloss = gloss + (gloss == "" ? "" : " ") + graph->gloss(*it);
  }
  return gloss;
}

/**
 * Print a human readable dump of a search path.
 */
string toString(const Graph* graph, const Path* path) {
  if (path == NULL) {
    return "<start>";
  } else {
    return toString(graph, path->fact) +
           "; from\n\t" +
           toString(graph, path->source());
  }
}


//
// ENTRY
//
int main(int argc, char** argv) {
  // Populate Data
  printf("---------------\n");
  printf("Populating Data\n");
  const Graph* graph = ReadGraph();
  if (graph == NULL) {
    return 1;
  }
  const FactDB* facts = ReadFactDB();
  if (facts == NULL) {
    return 1;
  }
  printf("done.\n");
  printf("---------------\n");

  // Parameterize search
  SearchType* searchStrategy = new BreadthFirstSearch();
  CacheStrategy* cache = new CacheStrategyNone();

  // Run search
  printf("---------------\n");
  printf("    Search     \n");
  vector<Path*> paths
    = Search( graph, facts,
              lemurHaveTail(),
              searchStrategy,
              cache,
              SEARCH_TIMEOUT   );

  // Print result
  for(vector<Path*>::iterator it = paths.begin(); it != paths.end(); ++it) {
    printf("Path\n----\n%s\n\n", toString(graph, *it).c_str());
  }
  printf("done.\n");
  printf("---------------\n");
}
