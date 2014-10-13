#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Channel.h"
#include "Utils.h"

using namespace std;

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


#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
inline uint64_t searchLoop(
    std::function<void(const ScoredSearchNode)> enqueue,
    std::function<const bool (ScoredSearchNode*)> dequeue,
    std::function<void(const ScoredSearchNode&)> registerVisited,
    SearchNode* history, uint64_t& historySize,
    const SynSearchCosts* costs, const syn_search_options& opts, 
    const Graph* graph, const Tree& tree) {
  // Variables
  uint64_t ticks = 0;
  uint8_t  dependentIndices[8];
  natlog_relation  dependentRelations[8];
  uint8_t memorySize = 0;
  SearchNode memory[SEARCH_CYCLE_MEMORY];
  ScoredSearchNode scoredNode;

  // Main Loop
  while (ticks < opts.maxTicks && dequeue(&scoredNode)) {
    // Register the dequeue
    registerVisited(scoredNode);
    
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
    const uint32_t myIndex = historySize;
//    fprintf(stderr, "%u>> %s (points to %u)\n", 
//      myIndex, toString(*graph, tree, node).c_str(),
//      node.getBackpointer());
    history[myIndex] = node;
    historySize += 1;
    ticks += 1;
    if (!opts.silent && ticks % 100000 == 0) { 
      printTime("[%c] "); 
      fprintf(stderr, "  |Search Progress| ticks=%luK\n", ticks / 1000);
    }

    // PUSH 1: Mutations
    uint32_t numEdges;
    const edge* edges = graph->incomingEdgesFast(node.token(), &numEdges);
    for (uint32_t edgeI = 0; edgeI < numEdges; ++edgeI) {
      // (ignore when sense doesn't match)
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
        isNewChild &= (mutatedChild != memory[i]);
      }
      if (isNewChild) {
#endif
      enqueue(ScoredSearchNode(mutatedChild, cost));
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
        enqueue(ScoredSearchNode(deletedChild, cost));
//        fprintf(stderr, "  push deletion %s\n", toString(*graph, tree, deletedChild).c_str());
      }

      // PUSH 3: Index Move
      // (create child)
      const SearchNode indexMovedChild(node, tree, dependentIndex,
                                    myIndex);
      // (push child)
      enqueue(ScoredSearchNode(indexMovedChild, scoredNode.cost));
//      fprintf(stderr, "  push index move %s\n", toString(*graph, tree, indexMovedChild).c_str());
    }
  }

  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "  |Search End| ticks=%lu\n", ticks);
  }
  return ticks;
}
#pragma GCC pop_options  // matches push_options above



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
  
  // -- Helpers --
  // Allocate history
  SearchNode* history = (SearchNode*) malloc(opts.maxTicks * sizeof(SearchNode));
  uint64_t historySize = 0;
  // The fringe
  KNHeap<float,SearchNode> fringe(
    std::numeric_limits<float>::infinity(),
    -std::numeric_limits<float>::infinity());
  // The database lookup function
  // (the matches found)
  vector<vector<SearchNode>>& matches = response.paths;
  // (the lookup function)
  std::function<bool(uint64_t)> lookupFn = [&db](uint64_t value) -> bool {
    auto iter = db.find( value );
    return iter != db.end();
  };
  // (register a node as visited)
  auto registerVisited = [&db,&matches,&lookupFn,&history] (const ScoredSearchNode& scoredNode) -> void {
    const SearchNode& node = scoredNode.node;
    if (node.truthState() && lookupFn(node.factHash())) {
      bool unique = true;
      for (auto iter = matches.begin(); iter != matches.end(); ++iter) {
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
        matches.push_back(path);
      }
    }
  };

  // -- Run Search --
  // Enqueue the first element
  // (to the fringe)
  fringe.insert(0.0f, SearchNode(*input));
  // (to the history)
  history[0] = SearchNode(*input);
  historySize += 1;

  // Run Search
  response.totalTicks = searchLoop( 
    // Insert to fringe
    [&fringe](const ScoredSearchNode elem) -> void { fringe.insert(elem.cost, elem.node); },
    // Pop from fringe
    [&fringe](ScoredSearchNode* output) -> bool { 
      if (fringe.isEmpty()) { return false; }
      fringe.deleteMin(&(output->cost), &(output->node));
      return true;
    },
    // Register visited
    registerVisited,
    // Other crap
    history, historySize, costs, opts, mutationGraph, *input);

  // Clean up
  free(history);
  
  // Return
  return response;
}
