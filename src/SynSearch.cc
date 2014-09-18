#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Utils.h"

using namespace std;

// ----------------------------------------------
// PATH ELEMENT (SEARCH NODE)
// ----------------------------------------------
inline syn_path_data mkSynPathData(
    const uint64_t&    factHash,
    const uint8_t&     index,
    const bool&        validity,
    const uint32_t&    deleteMask,
    const tagged_word& currentToken,
    const ::word       governor) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.validity = validity;
  dat.deleteMask = deleteMask;
  dat.currentToken = currentToken;
  dat.governor = governor;
  return dat;
}

SynPath::SynPath()
    : costIfTrue(0.0), costIfFalse(0.0), backpointer(0),
      data(mkSynPathData(42l, 255, false, 42, getTaggedWord(0, 0, 0), TREE_ROOT_WORD)) { }

SynPath::SynPath(const SynPath& from)
    : costIfTrue(from.costIfTrue), costIfFalse(from.costIfFalse),
      backpointer(from.backpointer),
      data(from.data) { }
  
SynPath::SynPath(const Tree& init)
    : costIfTrue(0.0f), costIfFalse(0.0f),
      backpointer(0),
      data(mkSynPathData(init.hash(), init.root(), true, 
                         0x0, init.token(init.root()), TREE_ROOT_WORD))
  { }
  
//
// SynPath() ''mutate constructor
//
SynPath::SynPath(const SynPath& from, const uint64_t& newHash,
                 const tagged_word& newToken,
                 const float& costIfTrue, const float& costIfFalse,
                 const uint32_t& backpointer)
    : costIfTrue(costIfTrue), costIfFalse(costIfFalse),
      backpointer(backpointer),
      data(mkSynPathData(newHash, from.data.index, from.data.validity,
                         from.data.deleteMask, newToken,
                         from.data.governor)) { }

//
// SynPath() ''delete constructor
//
SynPath::SynPath(const SynPath& from, const uint64_t& newHash,
          const float& costIfTrue, const float& costIfFalse,
          const uint32_t& addedDeletions, const uint32_t& backpointer)
    : costIfTrue(costIfTrue), costIfFalse(costIfFalse),
      backpointer(backpointer),
      data(mkSynPathData(newHash, from.data.index, from.data.validity,
                         addedDeletions | from.data.deleteMask, from.data.currentToken,
                         from.data.governor)) { }

//
// SynPath() ''move index constructor
//
SynPath::SynPath(const SynPath& from, const Tree& tree,
                 const uint8_t& newIndex, const uint32_t& backpointer)
    : costIfTrue(from.costIfTrue), costIfFalse(from.costIfFalse),
      backpointer(backpointer),
      data(mkSynPathData(from.data.factHash, newIndex, from.data.validity, 
                         from.data.deleteMask, tree.token(newIndex), 
                         tree.token(tree.governor(newIndex)).word)) { }

// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

//
// Tree::Tree(params)
//
Tree::Tree(const uint8_t&     length, 
           const tagged_word* words,
           const uint8_t*     governors,
           const uint8_t*     relations)
       : length(length),
         availableCacheLength(34 + (MAX_QUERY_LENGTH - length) * sizeof(dep_tree_word)) {
  // Set trivial fields
  for (uint8_t i = 0; i < length; ++i) {
    data[i].word = words[i];
    data[i].governor = governors[i];
    data[i].relation = relations[i];
  }
  // Zero out cache
  memset(cacheSpace(), 42, availableCacheLength * sizeof(uint8_t));
}
  
//
// Tree::Tree(conll)
//
uint8_t computeLength(const string& conll) {
  uint8_t length = 0;
  stringstream ss(conll);
  string item;
  while (getline(ss, item, '\n')) {
    length += 1;
  }
  return length;
}

Tree::Tree(const string& conll) 
      : length(computeLength(conll)),
         availableCacheLength(34 + (MAX_QUERY_LENGTH - length) * sizeof(dep_tree_word)) {
  stringstream lineStream(conll);
  string line;
  uint8_t lineI = 0;
  while (getline(lineStream, line, '\n')) {
    stringstream fieldsStream(line);
    string field;
    uint8_t fieldI = 0;
    while (getline(fieldsStream, field, '\t')) {
      switch (fieldI) {
        case 0:
          data[lineI].word = getTaggedWord(atoi(field.c_str()), 0, 0);
          break;
        case 1:
          data[lineI].governor = atoi(field.c_str());
          if (data[lineI].governor == 0) {
            data[lineI].governor = TREE_ROOT;
          } else {
            data[lineI].governor -= 1;
          }
          break;
        case 2:
          data[lineI].relation = indexDependency(field.c_str());
          break;
      }
      fieldI += 1;
    }
    if (fieldI != 3) {
      printf("Bad number of CoNLL fields in line (expected 3): %s\n", line.c_str());
      std::exit(1);
    }
    lineI += 1;
  }
  // Zero out cache
  memset(cacheSpace(), 42, availableCacheLength * sizeof(uint8_t));
}
  
//
// Tree::dependents()
//
void Tree::dependents(const uint8_t& index,
      const uint8_t& maxChildren,
      uint8_t* childrenIndices, 
      uint8_t* childrenRelations, 
      uint8_t* childrenLength) const {
  *childrenLength = 0;
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
      childrenRelations[(*childrenLength)] = data[i].relation;
      childrenIndices[(*childrenLength)++] = i;  // must be last line in block
      if (*childrenLength >= maxChildren) { return; }
    }
  }
}
  
//
// Tree::createDeleteMask()
//
uint32_t Tree::createDeleteMask(const uint8_t& root) const {
  uint32_t bitmask = TREE_DELETE(0x0, root);
  bool cleanPass = false;
  while (!cleanPass) {
    cleanPass = true;
    for (uint8_t i = 0; i < length; ++i) {
      if (!TREE_IS_DELETED(bitmask, i) &&
          data[i].governor != TREE_ROOT &&
          TREE_IS_DELETED(bitmask, data[i].governor)) {
        bitmask = TREE_DELETE(bitmask, i);
        cleanPass = false;
      }
    }
  }
  return bitmask;
}
  
//
// Tree::root()
//
uint8_t Tree::root() const {
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == TREE_ROOT) {
      return i;
    }
  }
  printf("No root found in tree!\n");
  std::exit(1);
  return 255;
}
  
//
// Tree::operator==()
//
bool Tree::operator==(const Tree& rhs) const {
  if (length != rhs.length) { return false; }
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].word != rhs.data[i].word) { return false; }
    if (data[i].governor != rhs.data[i].governor) { return false; }
    if (data[i].relation != rhs.data[i].relation) { return false; }
  }
  return true;
}

//
// Tree::hash()
//
inline uint64_t mix( const uint64_t u ) {
  uint64_t v = u * 3935559000370003845l + 2691343689449507681l;
  v ^= v >> 21;
  v ^= v << 37;
  v ^= v >>  4;
  v *= 4768777513237032717l;
  v ^= v << 20;
  v ^= v >> 41;
  v ^= v <<  5;
  return v;
}

inline uint64_t hashEdge(const dependency_edge& edge) {
#if TWO_PASS_HASH!=0
  #ifdef __GNUG__
    return fnv_64a_buf(const_cast<dependency_edge*>(&edge), sizeof(dependency_edge), FNV1_64_INIT);
  #else
    return fnv_64a_buf(&edge, sizeof(dependency_edge), FNV1_64_INIT);
  #endif
#else
    uint64_t edgeHash;
    memcpy(&edgeHash, &edge, sizeof(uint64_t));
    return mix(edgeHash);
#endif
}

  
uint64_t Tree::hash() const {
  uint64_t value = 0x0;
  for (uint8_t i = 0; i < length; ++i) {
    value ^= hashEdge(edgeInto(i));
  }
  return value;
}
  
//
// Tree::updateHashFromMutation()
//
uint64_t Tree::updateHashFromMutation(
                                      const uint64_t& oldHash,
                                      const uint8_t& index, 
                                      const ::word& oldWord,
                                      const ::word& governor,
                                      const ::word& newWord) const {
  uint64_t newHash = oldHash;
  // Fix incoming dependency
//  printf("Mutating %u; dep=%u -> %u  gov=%u\n", index, oldWord, newWord, governor);
  newHash ^= hashEdge(edgeInto(index, oldWord, governor));
  newHash ^= hashEdge(edgeInto(index, newWord, governor));
  // Fix outgoing dependencies
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
//      printf("  Also re-hashing word at %u; dep=%u  gov=%u -> %u\n", i,
//              data[i].word.word, oldWord, newWord);
      newHash ^= hashEdge(edgeInto(i, data[i].word.word, oldWord));
      newHash ^= hashEdge(edgeInto(i, data[i].word.word, newWord));
    }
  }
  // Return
  return newHash;
}

//
// Tree::updateHashFromDeletion()
//
uint64_t Tree::updateHashFromDeletions(
                                       const uint64_t& oldHash,
                                       const uint8_t& deletionIndex, 
                                       const ::word& deletionWord,
                                       const ::word& governor,
                                       const uint32_t& newDeletions) const {
  uint64_t newHash = oldHash;
  for (uint8_t i = 0; i < length; ++i) {
    if (TREE_IS_DELETED(newDeletions, i)) {
      if (i == deletionIndex) {
//        printf("Deleting word at %u; dep=%u  gov=%u\n", i, deletionWord, governor);
        // Case: we are deleting the root of the deletion chunk
        newHash ^= hashEdge(edgeInto(i, deletionWord, governor));
      } else {
//        printf("Deleting word at %u; dep=%u  gov=%u\n", i, edgeInto(i).dependent, edgeInto(i).governor);
        // Case: we are deleting an entire edge
        newHash ^= hashEdge(edgeInto(i));
      }
    }
  }
  return newHash;
}
  
// ----------------------------------------------
// CHANNEL
// ----------------------------------------------
  
bool Channel::push(const SynPath& value) {
  if (data.pushPointer == data.pollPointer) {
    // Case: empty buffer
    data.buffer[data.pushPointer] = value;
    data.pushPointer += 1;
    return true;
  } else {
    // Case: something in buffer
    const uint16_t available =
      ((data.pollPointer + CHANNEL_BUFFER_LENGTH) - data.pushPointer) % CHANNEL_BUFFER_LENGTH - 1;
    if (available > 0) {
      data.buffer[data.pushPointer] = value;
      data.pushPointer = (data.pushPointer + 1) % CHANNEL_BUFFER_LENGTH;
      return true;
    } else {
      return false;
    }
  }
}

bool Channel::poll(SynPath* output) {
  const uint16_t available =
    ((data.pushPointer + CHANNEL_BUFFER_LENGTH) - data.pollPointer) % CHANNEL_BUFFER_LENGTH;
  if (available > 0) {
  }
  if (available > 0) {
    *output = data.buffer[data.pollPointer];
    data.pollPointer = (data.pollPointer + 1) % CHANNEL_BUFFER_LENGTH;
    return true;
  } else { 
    return false;
  }
}
  
// ----------------------------------------------
// SEARCH ALGORITHM
// ----------------------------------------------

//
// Print the current time
//
void printTime(const char* format) {
  char s[128];
  time_t t = time(NULL);
  struct tm* p = localtime(&t);
  strftime(s, 1000, format, p);
  printf("%s", s);
}

//
// Create the input options for a search
//
syn_search_options SynSearchOptions(
    const uint32_t& maxTicks,
    const float& costThreshold,
    const bool& stopWhenResultFound,
    const bool& silent) {
  syn_search_options opts;
  opts.maxTicks = maxTicks;
  opts.costThreshold = costThreshold;
  opts.stopWhenResultFound = stopWhenResultFound;
  opts.silent = silent;
  return opts;
}

//
// A helper to evict the cache. This is rather expensive.
//
#define EVICT_CACHE  void* __ptr = malloc(L3_CACHE_SIZE); memset(__ptr, 0x0, L3_CACHE_SIZE); free(__ptr);

//
// Handle push/pop to the priority queue
//
void priorityQueueWorker(
    Channel* enqueueChannel, Channel* dequeueChannel, 
    bool* timeout, bool* pqEmpty, const syn_search_options& opts) {
  // Variables
  uint64_t idleTicks = 0;
  KNHeap<float,SynPath> pq(
    std::numeric_limits<float>::infinity(),
    -std::numeric_limits<float>::infinity());
  float key;
  SynPath value;

  // Main loop
  while (!(*timeout)) {
    bool somethingHappened = false;

    // Enqueue
    while (enqueueChannel->poll(&value)) {
      pq.insert(value.getPriorityKey(), value);
      somethingHappened = true;
    }

    // Dequeue
    if (!pq.isEmpty()) {
      *pqEmpty = false;
      pq.deleteMin(&key, &value);
      while (!dequeueChannel->push(value)) {
        idleTicks += 1;
        if (idleTicks % 1000000 == 0) { 
          EVICT_CACHE;
          if (!opts.silent) { printTime("[%c] "); printf("  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
          if (*timeout) { 
            if (!opts.silent) {
              printTime("[%c] ");
              printf("  |PQ Dirty Timeout| idleTicks=%lu\n", idleTicks);
            }
            return;
          }
        }
      }
      somethingHappened = true;
    } else {
      if (!(*pqEmpty)){ 
        *pqEmpty = true;
        EVICT_CACHE;
      }
    }

    // Debug
    if (!somethingHappened) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        EVICT_CACHE;
        if (!opts.silent) {printTime("[%c] "); printf("  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
      }
    }
  }

  // Return
  if (!opts.silent) {
    printTime("[%c] ");
    printf("  |PQ Normal Return| idleTicks=%lu\n", idleTicks);
  }
}


//
// Handle creating children for the priority queue
//
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
void pushChildrenWorker(
    Channel* enqueueChannel, Channel* dequeueChannel,
    SynPath* history, uint64_threadsafe_t* historySize,
    bool* timeout, bool* pqEmpty,
    const syn_search_options& opts, const Graph* graph, const Tree& tree,
    uint64_t* ticks) {
  // Variables
  uint64_t idleTicks = 0;
  *ticks = 0;
  uint8_t  dependentIndices[8];
  uint8_t  dependentRelations[8];
  uint8_t memorySize = 0;
  SynPath memory[SEARCH_CYCLE_MEMORY];
  SynPath node;
  // Main Loop
  while (*ticks < opts.maxTicks) {
    // Dequeue an element
    while (!dequeueChannel->poll(&node)) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        if (!opts.silent) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM  seemsDone=%u\n", *ticks / 1000, idleTicks / 1000000, *pqEmpty); }
        // Check if the priority queue is empty
        if (*pqEmpty) {
          // (evict the cache)
          EVICT_CACHE;
          // (try to poll again)
          if (dequeueChannel->poll(&node)) { break; }
          // (priority queue really is empty)
          if (!opts.silent) { printTime("[%c] "); printf("  |PQ Empty| ticks=%lu\n", *ticks); }
          *timeout = true;
          return;
        }
      }
    }
    
    // Register the dequeue'd element
#if SEARCH_CYCLE_MEMORY!=0
    memory[0] = history[node.getBackpointer()];
    memorySize = 1;
    for (uint8_t i = 1; i < SEARCH_CYCLE_MEMORY; ++i) {
      if (memory[i - 1].getBackpointer() != 0) {
        memory[i] = history[memory[i - 1].getBackpointer()];
        memorySize = i + 1;
      }
    }
    const SynPath& parent = history[node.getBackpointer()];
#endif
    const uint32_t myIndex = historySize->value;
//    printf("%u>> %s (points to %u)\n", 
//      myIndex, toString(*graph, tree, node).c_str(),
//      node.getBackpointer());
    history[myIndex] = node;
    historySize->value += 1;
    *ticks += 1;

    // PUSH 1: Mutations
    uint32_t numEdges;
    const edge* edges = graph->incomingEdgesFast(node.token(), &numEdges);
    for (uint32_t edgeI = 0; edgeI < numEdges; ++edgeI) {
      // (compute new fields)
      const uint64_t newHash = tree.updateHashFromMutation(
          node.factHash(), node.tokenIndex(), node.token().word,
          node.governor(), edges[edgeI].source
        );
      const tagged_word newToken = getTaggedWord(
          edges[edgeI].source,
          edges[edgeI].source_sense,
          node.token().monotonicity);
      const float costIfTrue = 0.0;  // TODO(gabor) costs
      const float costIfFalse = 0.0;
      // (create child)
      const SynPath mutatedChild(node, newHash, newToken,
                                 costIfTrue, costIfFalse,
                                 myIndex);
      // (push child)
#if SEARCH_CYCLE_MEMORY!=0
      bool isNewChild = true;
      for (uint8_t i = 0; i < memorySize; ++i) {
//        printf("  CHECK %lu  vs %lu\n", mutatedChild.factHash(), memory[i].factHash());
        isNewChild &= (mutatedChild != memory[i]);
      }
      if (isNewChild) {
#endif
      while (!enqueueChannel->push(mutatedChild)) {
        idleTicks += 1;
        if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
      }
//      printf("  push mutation %s\n", toString(*graph, tree, mutatedChild).c_str());
#if SEARCH_CYCLE_MEMORY!=0
      }
#endif
    }
  
    // INTERM: Get Children
    uint8_t numDependents;
    tree.dependents(node.tokenIndex(), 8, dependentIndices,
                    dependentRelations, &numDependents);
    
    for (uint8_t dependentI = 0; dependentI < numDependents; ++dependentI) {
      const uint8_t dependentIndex = dependentIndices[dependentI];

      // PUSH 2: Deletions
      if (!node.isDeleted(dependentIndex)) {
        // (compute deletion)
        uint32_t deletionMask = tree.createDeleteMask(dependentIndex);
        uint64_t newHash = tree.updateHashFromDeletions(
            node.factHash(), dependentIndex, tree.token(dependentIndex).word,
            node.token().word, deletionMask);
        const float costIfTrue = 0.0;  // TODO(gabor) costs
        const float costIfFalse = 0.0;
        // (create child)
        const SynPath deletedChild(node, newHash,
                                   costIfTrue, costIfFalse, deletionMask,
                                   myIndex);
        // (push child)
        while (!enqueueChannel->push(deletedChild)) {
          idleTicks += 1;
          if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
        }
//        printf("  push deletion %s\n", toString(*graph, tree, deletedChild).c_str());

        // PUSH 3: Index Move
        // (create child)
        const SynPath indexMovedChild(node, tree, dependentIndex,
                                      myIndex);
        // (push child)
        while (!enqueueChannel->push(indexMovedChild)) {
          idleTicks += 1;
          if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
        }
//        printf("  push index move %s\n", toString(*graph, tree, indexMovedChild).c_str());
      }
    }
  }

  if (!opts.silent) {
    printTime("[%c] ");
    printf("  |CF Normal Return| ticks=%lu  idle=%lu\n", *ticks, idleTicks);
  }
  *timeout = true;
  EVICT_CACHE;
}
#pragma GCC pop_options  // matches push_options above


//
// The entry method for searching
//
syn_search_response SynSearch(
    const Graph* mutationGraph, 
    const Tree* input,
    const syn_search_options& opts) {
  syn_search_response response;
  // Debug print parameters
  if (opts.maxTicks > 1000000000) {
    printTime("[%c] ");
    printf("|ERROR| Max number of ticks is too large: %u\n", opts.maxTicks);
    response.totalTicks = 0;
    return response;
  }
  if (!opts.silent) {
    printTime("[%c] ");
    printf("|BEGIN SEARCH| fact='%s'\n", toString(*mutationGraph, *input).c_str());
  }

  // Allocate some shared variables
  // (communication)
  Channel* enqueueChannel = threadsafeChannel();
  Channel* dequeueChannel = threadsafeChannel();
  SynPath* history = (SynPath*) malloc(opts.maxTicks * sizeof(SynPath));
  uint64_threadsafe_t* historySize = malloc_uint64_threadsafe_t();
  // (from the tree cache space)
  bool* cacheSpace = (bool*) input->cacheSpace();
  bool* timeout = cacheSpace;
  *timeout = false;
  bool* pqEmpty = cacheSpace + 1;
  *pqEmpty = false;
  bool* searchDone = cacheSpace + 2;
  *searchDone = false;

  // (sanity check)
  if (((uint64_t) searchDone) + 1 >= ((uint64_t) input) + sizeof(Tree)) {
    printf("Allocated too much into the cache memory area!\n");
    std::exit(1);
  }

  // Enqueue the first element
  // (to the fringe)
  if (!dequeueChannel->push(SynPath(*input))) {
    printf("Could not push root!?\n");
    std::exit(1);
  }
  // (to the history)
  history[0] = SynPath(*input);
  historySize->value += 1;

  // Start threads
  std::thread priorityQueueThread(priorityQueueWorker, 
      enqueueChannel, dequeueChannel, timeout, pqEmpty, opts);
  std::thread pushChildrenThread(pushChildrenWorker, 
      enqueueChannel, dequeueChannel, 
      history, historySize,
      timeout, pqEmpty,
      opts, mutationGraph, *input, &(response.totalTicks));
      

  // Join threads
  if (!opts.silent) {
    printTime("[%c] ");
    printf("|AWAITING JOIN|\n");
  }
  // (priority queue)
  if (!opts.silent) { printTime("[%c] "); printf("  Priority queue...\n"); }
  priorityQueueThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    printf("    Priority queue joined.\n");
  }
  // (push children)
  if (!opts.silent) { printTime("[%c] "); printf("  Child factory...\n"); }
  pushChildrenThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    printf("    Child factory joined.\n");
  }

  // Clean up
  delete enqueueChannel;
  delete dequeueChannel;
  free(history);
  free(historySize);
  
  // TODO(gabor) populate paths
  return response;
}
