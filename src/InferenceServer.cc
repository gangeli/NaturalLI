#include <string>
#include <cstdio>

#include "Config.h"
#include "Utils.h"
#include "Graph.h"
#include "FactDB.h"
#include "Search.h"

using namespace std;

//
// ENTRY
//
int main(int argc, char** argv) {
  // Populate Data
  printf("---------------\n");
  printf("Populating Data\n");
  const Graph* graph = ReadGraph();
  if (graph == NULL) {
    printf("Graph was null; exiting...\n");
    return 1;
  }
  const FactDB* facts = ReadFactDB();
  if (facts == NULL) {
    printf("Facts were null; exiting...\n");
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
              lemursHaveTails(),
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
