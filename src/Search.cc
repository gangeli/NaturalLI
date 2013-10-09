#include "Search.h"

#include <cstdio>
#include <cstdlib>
#include <set>


//
// Static Variables
//
uint64_t nextId = 1;

//
// Class Path
//
Path::Path(const uint64_t source, const vector<word>& fact, const edge_type& edgeType)
    : edgeType(edgeType), fact(fact), sourceId(source),
      id(USE_RAMCLOUD ? nextId++ : ((uint64_t) this) )
    {
};

Path::Path(const Path& source, const vector<word>& fact, const edge_type& edgeType)
    : edgeType(edgeType), fact(fact), sourceId(source.id),
      id(USE_RAMCLOUD ? nextId++ : ((uint64_t) this) )
    {
};

Path::Path(const Path& copy)
    : id(copy.id), sourceId(copy.sourceId), fact(copy.fact), edgeType(copy.edgeType)
    {
};

Path::~Path() {
};

Path* Path::source() const {
  if (USE_RAMCLOUD) {
    printf("TODO(gabor): lookup a path element by id in RamCloud (%lu)\n", this->id);
    std::exit(1);
  } else {
    return (Path*) id;
  }
}

//
// Class SearchType
//

//
// Class BreadthFirstSearch
//
BreadthFirstSearch::BreadthFirstSearch() {
  this->impl = new queue<Path>();
}

BreadthFirstSearch::~BreadthFirstSearch() {
  delete impl;
}
  
void BreadthFirstSearch::push(const Path& toAdd) {
  impl->push(toAdd);
}

const Path BreadthFirstSearch::pop() {
  const Path frontElement = impl->front();
  impl->pop();
  return frontElement;
}

bool BreadthFirstSearch::isEmpty() {
  return impl->empty();
}

//
// Class CacheStrategy
//

//
// Class CacheStrategyNone
//
bool CacheStrategyNone::isSeen(const Path&) {
  return false;
}

void CacheStrategyNone::add(const Path&) { }

//
// Function search()
//

vector<Path*> Search(const Graph* graph, const FactDB* knownFacts,
                     const vector<word>& queryFact,
                     SearchType* fringe, CacheStrategy* cache,
                     const uint64_t timeout) {
  //
  // Setup
  //
  // Create a vector for the return value to occupy
  vector<Path*> responses;
  // Add start state to the fringe
  fringe->push(Path(0, queryFact, 255));
  // Initialize timer (number of elements popped from the fringe)
  uint64_t time = 0;

  //
  // Search
  //
  while (!fringe->isEmpty() && time < timeout) {
    // Get the next element from the fringe
    if (fringe->isEmpty()) {
      printf("IMPOSSIBLE: the search fringe is empty. This means (a) there are leaf nodes in the graph, and (b) this would have caused a memory leak.\n");
      std::exit(1);
    }
    const Path parent = fringe->pop();
    // Update time
    time += 1;
    if (time % 1000 == 0) {
      printf("[%luk] search tick; %lu paths found\n", time / 1000, responses.size()); fflush(stdout);
    }
    // Add the element to the cache
    cache->add(parent);

    // -- Check If Valid --
    if (knownFacts->contains(parent.fact)) {
      responses.push_back(new Path(parent));
    }

    // -- Mutations --
    for (int indexToMutate = 0;
         indexToMutate < parent.fact.size();
         ++indexToMutate) {  // for each index to mutate...
      const vector<edge>& mutations
        = graph->outgoingEdges(parent.fact[indexToMutate]);
      for(vector<edge>::const_iterator it = mutations.begin();
          it != mutations.end();
          ++it) {  // for each possible mutation on that index...
        // ... create the mutated fact
        vector<word> mutated(parent.fact.size());
        for (int i = 0; i < indexToMutate; ++i) {
          mutated.push_back(parent.fact[i]);
        }
        mutated.push_back(it->sink);
        for (int i = indexToMutate + 1; i < parent.fact.size(); ++i) {
          mutated.push_back(parent.fact[i]);
        }
        // Create the corresponding search state
        Path* child = new Path(parent, mutated, it->type);
        // Add the state to the fringe
        if (!cache->isSeen(*child)) {
          fringe->push(*child);
        }
      }
    }
  
  }

  printf("Search complete; %lu paths found\n", responses.size());
  return responses;
}




