#include <cstdio>
#include <cstdlib>
#include <set>

#include "Search.h"
#include "Utils.h"

using namespace std;

//
// Static Variables
//
uint64_t nextId = 1;

// TODO(gabor) this is not threadsafe
inline uint64_t generateUniqueId() {
  nextId++;
}

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
      id(generateUniqueId()),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const Path& source, const word* fact, const uint8_t factLength, const edge_type& edgeType)
    : edgeType(edgeType), sourceId(source.id),
      id(generateUniqueId()),
      factLength(factLength)
    {
  for (int i = 0; i < factLength; ++i) {
    this->fact[i] = fact[i];
  }
};

Path::Path(const word* fact, const uint8_t factLength)
    : edgeType(255), sourceId(0),
      id(generateUniqueId()),
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

void Path::operator=(const Path& copy) {
  this->id = copy.id;
  this->sourceId = copy.sourceId;
  this->edgeType = copy.edgeType;
  this->factLength = copy.factLength;
  for (int i = 0; i < copy.factLength; ++i) {
    this->fact[i] = copy.fact[i];
  }
}

Path* Path::source(SearchType& queue) const {
  if (sourceId == 0) {
    return NULL;
  } else {
    return queue.findPathById(sourceId);
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
  while (id2path.size() <= toAdd.id) {
    id2path.push_back(toAdd);
  }
  id2path[toAdd.id] = toAdd;
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
  
Path* BreadthFirstSearch::findPathById(uint64_t id) {
  return &id2path[id];
}

//
// Class UCSSearch
//
UCSSearch::UCSSearch() {
  this->impl = new priority_queue<Path, vector<Path>, PathCompare>();  // TODO(gabor) binomial_heap_tag
}

UCSSearch::~UCSSearch() {
  delete impl;
}
  
void UCSSearch::push(const Path& toAdd) {
  while (id2path.size() <= toAdd.id) {
    id2path.push_back(toAdd);
  }
  id2path[toAdd.id] = toAdd;
  impl->push(toAdd);
}

const Path UCSSearch::pop() {
  const Path frontElement = impl->top();
  impl->pop();
  return frontElement;
}

bool UCSSearch::isEmpty() {
  return impl->empty();
}
  
Path* UCSSearch::findPathById(uint64_t id) {
  return &id2path[id];
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
  const uint32_t tickTime = 1000;
  printf("started search.\n");

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
    printf("search tick %lu; popped %s\n", time, toString(*graph, *fringe, &parent).c_str());
    if (time % tickTime == 0) {
      const uint32_t tickOOM
        = tickTime < 1000 ? 1 : (tickTime < 1000000 ? 1000 : (tickTime  < 1000000000 ? 1000000 : 1) );
      printf("[%lu / %lu%s] search tick; %lu paths found\n",
             time / tickOOM, timeout / tickOOM,
             tickTime < 1000 ? "" : (tickTime < 1000000 ? "k" : (tickTime  < 1000000000 ? "m" : "") ),
             responses.size());
    }
    // Add the element to the cache
    cache->add(parent);

    // -- Check If Valid --
    if (knownFacts->contains(parent.fact, parent.factLength)) {
      Path* result = new Path(parent);
      printf("> %s\n", toString(*graph, *fringe, result).c_str());
      responses.push_back(result);
    }

    // -- Mutations --
    for (int indexToMutate = 0;
         indexToMutate < parent.factLength;
         ++indexToMutate) {  // for each index to mutate...
      const vector<edge>& mutations
        = graph->outgoingEdges(parent.fact[indexToMutate]);
      printf("  mutating index %d; %lu children\n", indexToMutate, mutations.size());
      for(vector<edge>::const_iterator it = mutations.begin();
          it != mutations.end();
          ++it) {  // for each possible mutation on that index...
        if (it->type == 9) { continue; } // TODO(gabor) don't ignore nearest neighbors
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




