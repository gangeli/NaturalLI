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
Path::Path(const uint64_t id, const uint64_t source, const word* fact, const uint8_t factLength, const edge_type& edgeType)
    : edgeType(edgeType), sourceId(source), id(id),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const uint64_t source, const word* fact, const uint8_t factLength, const edge_type& edgeType)
    : edgeType(edgeType), sourceId(source),
      id(USE_RAMCLOUD ? nextId++ : ((uint64_t) this) ),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const Path& source, const word* fact, const uint8_t factLength, const edge_type& edgeType)
    : edgeType(edgeType), sourceId(source.id),
      id(USE_RAMCLOUD ? nextId++ : ((uint64_t) this) ),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const word* fact, const uint8_t factLength)
    : edgeType(255), sourceId(0),
      id(USE_RAMCLOUD ? nextId++ : ((uint64_t) this) ),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const Path& copy)
    : id(copy.id), sourceId(copy.sourceId), edgeType(copy.edgeType),
      factLength(copy.factLength)
    {
  for (int i = 0; i < copy.factLength; ++i) {
    this->fact[i] = copy.fact[i];
  }
};

Path::~Path() {
};

bool Path::operator==(const Path& other) const {
  // Check metadata
  if ( !( id == other.id && sourceId == other.sourceId &&
          edgeType == other.edgeType && factLength == other.factLength ) ) {
    return false;
  }
  // Check fact content
  for (int i = 0; i < factLength; ++i) {
    if (other.fact[i] != fact[i]) {
      return false;
    }
  }
  // Return true
  return true;
}

Path* Path::source() const {
  if (USE_RAMCLOUD) {
    printf("TODO(gabor): lookup a path element by id in RamCloud (%lu)\n", this->id);
    std::exit(1);
  } else {
    return (Path*) sourceId;
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
vector<Path*> Search(Graph* graph, FactDB* knownFacts,
                     const word* queryFact, const uint8_t queryFactLength,
                     SearchType* fringe, CacheStrategy* cache,
                     const uint64_t timeout) {
  //
  // Setup
  //
  // Create a vector for the return value to occupy
  vector<Path*> responses;
  // Add start state to the fringe
  fringe->push(Path(queryFact, queryFactLength));  // I need the memory to not go away
  // Initialize timer (number of elements popped from the fringe)
  uint64_t time = 0;
  const uint32_t tickTime = 1;

  //
  // Search
  //
  while (!fringe->isEmpty() && time < timeout) {
    // Get the next element from the fringe
    if (fringe->isEmpty()) {
      printf("IMPOSSIBLE: the search fringe is empty. This means (a) there are leaf nodes in the graph, and (b) this would have caused a memory leak.\n");
      std::exit(1);
    }
    if (time >= timeout) {
      printf("I suck at logic?");
      std::exit(1);
    }
    const Path parent = fringe->pop();
    // Update time
    time += 1;
    if (time % tickTime == 0) {
      printf("[%lu / %lu%s] search tick; %lu paths found\n", time / tickTime, timeout / tickTime,
             tickTime == 1 ? "" : (tickTime == 1000 ? "k" : (tickTime  == 1000000 ? "m" : " units") ),
             responses.size()); fflush(stdout);
    }
    // Add the element to the cache
    cache->add(parent);

    // -- Check If Valid --
    if (knownFacts->contains(parent.fact, parent.factLength)) {
      responses.push_back(new Path(parent));
    }

    // -- Mutations --
    for (int indexToMutate = 0;
         indexToMutate < parent.factLength;
         ++indexToMutate) {  // for each index to mutate...
      const vector<edge>& mutations
        = graph->outgoingEdges(parent.fact[indexToMutate]);
      for(vector<edge>::const_iterator it = mutations.begin();
          it != mutations.end();
          ++it) {  // for each possible mutation on that index...
        if (it->type == 9) { continue; } // ignore nearest neighbors
        // ... create the mutated fact
        word mutated[parent.factLength];
        for (int i = 0; i < indexToMutate; ++i) {
          mutated[i] = parent.fact[i];
        }
        mutated[indexToMutate] = it->sink;
        for (int i = indexToMutate + 1; i < parent.factLength; ++i) {
          mutated[i] = parent.fact[i];
        }
        // Create the corresponding search state
        Path child(parent, mutated, parent.factLength, it->type);
        // Add the state to the fringe
        if (!cache->isSeen(child)) {
          fringe->push(child);
        }
      }
    }
  
  }

  printf("Search complete; %lu paths found\n", responses.size());
  return responses;
}




