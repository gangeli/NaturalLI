#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Channel.h"
#include "Utils.h"

using namespace std;
using namespace moodycamel;

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
  fprintf(stderr, "%s", s);
}

//
// Handle push/pop to the priority queue
//
void priorityQueueWorker(
    Channel<ScoredSearchNode>* enqueueChannel, Channel<ScoredSearchNode>* dequeueChannel, 
    bool* timeout, bool* pqEmpty, const syn_search_options& opts) {
  // Variables
  uint64_t idleTicks = 0;
  KNHeap<float,SearchNode> pq(
    std::numeric_limits<float>::infinity(),
    -std::numeric_limits<float>::infinity());
  float key;
  ScoredSearchNode value;

  // Main loop
  while (!(*timeout)) {
    bool somethingHappened = false;

    // Enqueue
    while (enqueueChannel->poll(&value)) {
      pq.insert(value.cost, value.node);
      somethingHappened = true;
    }

    // Dequeue
    if (!pq.isEmpty()) {
      *pqEmpty = false;
      pq.deleteMin(&key, &(value.node));
      while (!dequeueChannel->push(value)) {
        idleTicks += 1;
        if (idleTicks % 1000000 == 0) { 
          if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
          if (*timeout) { 
            if (!opts.silent) {
              printTime("[%c] ");
              fprintf(stderr, "  |PQ Dirty Timeout| idleTicks=%lu\n", idleTicks);
            }
            return;
          }
        }
      }
      somethingHappened = true;
    } else {
      if (!(*pqEmpty)){ 
        *pqEmpty = true;
      }
    }

    // Debug
    if (!somethingHappened) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        if (!opts.silent) {printTime("[%c] "); fprintf(stderr, "  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
      }
    }
  }

  // Return
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "  |PQ Normal Return| idleTicks=%lu\n", idleTicks);
  }
}


//
// Handle creating children for the priority queue
//
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
void pushChildrenWorker(
    Channel<ScoredSearchNode>* enqueueChannel, 
    Channel<ScoredSearchNode>* dequeueChannel,
    SearchNode* history, uint64_threadsafe_t* historySize,
    const SynSearchCosts* costs,
    bool* timeout, bool* pqEmpty,
    const syn_search_options& opts, const Graph* graph, const Tree& tree,
    uint64_t* ticks) {
  // Variables
  uint64_t idleTicks = 0;
  *ticks = 0;
  uint8_t  dependentIndices[8];
  natlog_relation  dependentRelations[8];
  uint8_t memorySize = 0;
  SearchNode memory[SEARCH_CYCLE_MEMORY];
  ScoredSearchNode scoredNode;
  // Main Loop
  while (*ticks < opts.maxTicks) {
    // Dequeue an element
    while (!dequeueChannel->poll(&scoredNode)) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  |CF Idle| (can't dequeue) ticks=%luK  idle=%luM  seemsDone=%u\n", *ticks / 1000, idleTicks / 1000000, *pqEmpty); }
        // Check if the priority queue is empty
        if (*pqEmpty) {
          // (try to poll again)
          if (dequeueChannel->poll(&scoredNode)) { break; }
          // (priority queue really is empty)
          if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  |PQ Empty| ticks=%lu\n", *ticks); }
          *timeout = true;
          return;
        }
      }
    }
    
    // Register the dequeue'd element
    const SearchNode& node = scoredNode.node;
#if SEARCH_CYCLE_MEMORY!=0
    memory[0] = history[node.getBackpointer()];
    memorySize = 1;
    for (uint8_t i = 1; i < SEARCH_CYCLE_MEMORY; ++i) {
      if (memory[i - 1].getBackpointer() != 0) {
        memory[i] = history[memory[i - 1].getBackpointer()];
        memorySize = i + 1;
      }
    }
    const SearchNode& parent = history[node.getBackpointer()];
#endif
    const uint32_t myIndex = historySize->value;
//    fprintf(stderr, "%u>> %s (points to %u)\n", 
//      myIndex, toString(*graph, tree, node).c_str(),
//      node.getBackpointer());
    history[myIndex] = node;
    historySize->value += 1;
    *ticks += 1;
    if (!opts.silent && (*ticks) % 1000000 == 0) { 
      printTime("[%c] "); 
      fprintf(stderr, "  |CF Progress| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000);
    }

    // PUSH 1: Mutations
    uint32_t numEdges;
    const edge* edges = graph->incomingEdgesFast(node.token(), &numEdges);
    for (uint32_t edgeI = 0; edgeI < numEdges; ++edgeI) {
      if (edges[edgeI].sink_sense != node.token().sense) { continue; }
      bool newTruthValue;
      const float cost = costs->mutationCost(
          tree, node.tokenIndex(), edges[edgeI].type,
          node.truthState(), &newTruthValue) * edges[edgeI].cost;
      if (isinf(cost)) { continue; }
      // (compute new fields)
      const uint64_t newHash = tree.updateHashFromMutation(
          node.factHash(), node.tokenIndex(), node.token().word,
          node.governor(), edges[edgeI].source
        );
      const tagged_word newToken = getTaggedWord(
          edges[edgeI].source,
          edges[edgeI].source_sense,
          node.token().monotonicity);
      // (create child)
      const SearchNode mutatedChild(node, newHash, newToken,
                                    newTruthValue, myIndex);
      // (push child)
#if SEARCH_CYCLE_MEMORY!=0
      bool isNewChild = true;
      for (uint8_t i = 0; i < memorySize; ++i) {
//        fprintf(stderr, "  CHECK %lu  vs %lu\n", mutatedChild.factHash(), memory[i].factHash());
        isNewChild &= (mutatedChild != memory[i]);
      }
      if (isNewChild) {
#endif
      while (!enqueueChannel->push(ScoredSearchNode(mutatedChild, cost))) {
        idleTicks += 1;
        if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); fprintf(stderr, "  |CF Idle| (can't enqueue) ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
      }
//      fprintf(stderr, "  push mutation %s\n", toString(*graph, tree, mutatedChild).c_str());
#if SEARCH_CYCLE_MEMORY!=0
      }
#endif
    }
  
    // INTERM: Get Children
    uint8_t numDependents;
    tree.dependents(node.tokenIndex(), 8, dependentIndices,
                    dependentRelations, &numDependents);
    
    for (uint8_t dependentI = 0; dependentI < numDependents; ++dependentI) {
      const uint8_t& dependentIndex = dependentIndices[dependentI];

      // PUSH 2: Deletions
      if (node.isDeleted(dependentIndex)) { continue; }
      bool newTruthValue;
      const float cost = costs->insertionCost(
            tree, node.tokenIndex(), tree.relation(dependentIndex),
            tree.word(dependentIndex), node.truthState(), &newTruthValue);
      if (!isinf(cost)) {
        // (compute deletion)
        uint32_t deletionMask = tree.createDeleteMask(dependentIndex);
        uint64_t newHash = tree.updateHashFromDeletions(
            node.factHash(), dependentIndex, tree.token(dependentIndex).word,
            node.token().word, deletionMask);
        // (create child)
        const SearchNode deletedChild(node, newHash,
                                      newTruthValue, deletionMask, myIndex);
        // (push child)
        while (!enqueueChannel->push(ScoredSearchNode(deletedChild, cost))) {
          idleTicks += 1;
          if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); fprintf(stderr, "  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
        }
//        fprintf(stderr, "  push deletion %s\n", toString(*graph, tree, deletedChild).c_str());
      }

      // PUSH 3: Index Move
      // (create child)
      const SearchNode indexMovedChild(node, tree, dependentIndex,
                                    myIndex);
      // (push child)
      while (!enqueueChannel->push(ScoredSearchNode(indexMovedChild, scoredNode.cost))) {  // Copy the cost
        idleTicks += 1;
        if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); fprintf(stderr, "  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
      }
//      fprintf(stderr, "  push index move %s\n", toString(*graph, tree, indexMovedChild).c_str());
    }
  }

  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "  |CF Normal Return| ticks=%lu  idle=%lu\n", *ticks, idleTicks);
  }
  *timeout = true;
}
#pragma GCC pop_options  // matches push_options above

//
// Handle the fact lookup
//
void factLookupWorker(
    vector<vector<SearchNode>>* matches, std::function<bool(uint64_t)> contains,
    const SearchNode* history, const uint64_threadsafe_t* historySize, 
    bool* searchDone, const syn_search_options& opts) {
  // Variables
  uint32_t onPrice = 0;
  uint64_t idleTicks = 0l;


  while (!(*searchDone)) {
    while (onPrice < historySize->value) {
      const SearchNode& node = history[onPrice];
      if (node.truthState() && contains(node.factHash())) {
        bool unique = true;
        for (auto iter = matches->begin(); iter != matches->end(); ++iter) {
          vector<SearchNode> path = *iter;
          SearchNode endOfPath = path.back();
          if (endOfPath.factHash() == node.factHash()) {
            unique = false;
          }
        }
        if (unique) {
          // Add this path to the result
          // (get the complete path)
          vector<SearchNode> path;
          path.push_back(node);
          if (node.getBackpointer() != 0) {
            SearchNode head = node;
            while (head.getBackpointer() != 0) {
              path.push_back(head);
              head = history[head.getBackpointer()];
            }
          }
          // (reverse the path)
          std::reverse(path.begin(), path.end());
          // (add to the results list)
          matches->push_back(path);
        }
      }
      onPrice += 1;
    }

    // Debug print
    idleTicks += 1;
    if (!opts.silent && idleTicks % 10000000 == 0) { 
      printTime("[%c] "); 
      fprintf(stderr, "  |Lookup Idle| idle=%luM\n", idleTicks / 1000000);
    }
  }

  // Return
  if (!opts.silent) {
    printTime("[%c] "); 
    fprintf(stderr, "  |Lookup normal return| resultCount=%lu  idle=%lu\n", 
           matches->size(), idleTicks);
  }
}

//
// The entry method for searching
//
syn_search_response SynSearch(
    const Graph* mutationGraph, 
    const btree::btree_set<uint64_t>& db,
    const Tree* input, const SynSearchCosts* costs,
    const syn_search_options& opts) {
  syn_search_response response;
  // Debug print parameters
  if (opts.maxTicks > 1000000000) {
    printTime("[%c] ");
    fprintf(stderr, "ERROR: Max number of ticks is too large: %u\n", opts.maxTicks);
    response.totalTicks = 0;
    return response;
  }
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "|BEGIN SEARCH| fact='%s'\n", toString(*mutationGraph, *input).c_str());
  }

  // Allocate some shared variables
  // (from the tree cache space)
  bool* cacheSpace = (bool*) malloc(3*sizeof(bool));
  bool* timeout = cacheSpace;
  *timeout = false;
  bool* pqEmpty = cacheSpace + 1;
  *pqEmpty = false;
  bool* searchDone = cacheSpace + 2;
  *searchDone = false;
  // (communication)
  Channel<ScoredSearchNode> enqueueChannel(1048576);
  Channel<ScoredSearchNode> dequeueChannel(1048576);
  SearchNode* history = (SearchNode*) malloc(opts.maxTicks * sizeof(SearchNode));
  uint64_threadsafe_t* historySize = malloc_uint64_threadsafe_t();

  // (sanity check)
  if (((uint64_t) searchDone) + 1 >= ((uint64_t) input) + sizeof(Tree)) {
    fprintf(stderr, "Allocated too much into the cache memory area!\n");
    std::exit(1);
  }

  // Enqueue the first element
  // (to the fringe)
  if (!dequeueChannel.push(ScoredSearchNode(SearchNode(*input), 0.0f))) {
    fprintf(stderr, "Could not push root!?\n");
    std::exit(1);
  }
  // (to the history)
  history[0] = SearchNode(*input);
  historySize->value += 1;

  // Start threads
  // (priority queue)
  std::thread priorityQueueThread(priorityQueueWorker, 
      &enqueueChannel, &dequeueChannel, timeout, pqEmpty, opts);
  // (child creator)
  std::thread pushChildrenThread(pushChildrenWorker, 
      &enqueueChannel, &dequeueChannel, 
      history, historySize,
      costs,
      timeout, pqEmpty,
      opts, mutationGraph, *input, &(response.totalTicks));
  // (database lookup)
  std::function<bool(uint64_t)> lookupFn = [&db](uint64_t value) -> bool {
    auto iter = db.find( value );
    return iter != db.end();
  };
  std::thread lookupThread(factLookupWorker, 
      &(response.paths), lookupFn, history, historySize, searchDone, opts);
      

  // Join threads
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "AWAITING JOIN...\n");
  }
  // (priority queue)
  if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  Priority queue...\n"); }
  priorityQueueThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "    Priority queue joined.\n");
  }
  // (push children)
  if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  Child factory...\n"); }
  pushChildrenThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "    Child factory joined.\n");
  }
  // (database lookup)
  // (note: this must come after the above two have joined)
  *searchDone = true;
  if (!opts.silent) { printTime("[%c] "); fprintf(stderr, "  Database lookup...\n"); }
  lookupThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "    Database lookup joined.\n");
  }

  // Clean up
  free(history);
  free(historySize);
  free(cacheSpace);
  
  // Return
  return response;
}
