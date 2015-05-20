#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Utils.h"

using namespace std;

// ----------------------------------------------
// SEARCH ALGORITHM
// ----------------------------------------------


inline uint64_t memoryItem(const uint64_t& fact, const uint8_t& currentIndex,
                           const bool& truth) {
  uint64_t currentIndexShifted = currentIndex << 1;
  return (fact << 9) | currentIndexShifted | (truth ? 1l : 0l);
} 


//
// -----------
// SEARCH LOOP
// -----------
//
//
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
inline uint64_t searchLoop(
    std::function<void(const ScoredSearchNode)> enqueue,
    std::function<const bool (ScoredSearchNode*)> dequeue,
    std::function<void(const ScoredSearchNode&)> registerVisited,
    SearchNode* history, uint64_t& historySize,
    const SynSearchCosts* costs, const syn_search_options& opts,
    const vector<AlignmentSimilarity>& softAlignments,
    const Graph* graph, const Tree& tree) {

  // Variables
  uint64_t ticks = 0;
  uint8_t  dependentIndices[8];
  natlog_relation  dependentRelations[8];
  ScoredSearchNode* scoredNode = (ScoredSearchNode*) alloca(sizeof(ScoredSearchNode));
  featurized_edge features;
  // (initialize the memory)
#if SEARCH_FULL_MEMORY!=0
  btree::btree_set<uint64_t> fullMemory;
#else
#if SEARCH_CYCLE_MEMORY!=0
  uint8_t memorySize = 0;
  SearchNode memory[SEARCH_CYCLE_MEMORY];
#endif
#endif
  // (initialize the scores array)
  float currentNodeSoftAlignmentScores[MAX_FUZZY_MATCHES];
  float childNodeSoftAlignmentScores[MAX_FUZZY_MATCHES];

  // Compute quantifiers
  const uint8_t numQuantifiers = tree.getNumQuantifiers();
  uint8_t quantifierToVisit = 0;
  uint8_t quantifierVisitOrder[numQuantifiers];
  for (uint8_t i = 0; i < tree.length; ++i) {
    if (tree.isQuantifier(i)) {
      quantifierVisitOrder[quantifierToVisit] = i;
      quantifierToVisit += 1;
    }
  }
  assert(quantifierToVisit == numQuantifiers);

  // Compute topological order
  uint8_t topologicalOrder[tree.length + 1];
  tree.topologicalSort(topologicalOrder);

  // Main Loop
  while (ticks < opts.maxTicks && dequeue(scoredNode)) {
    // ---
    // POP NODE
    // ---

    // Register the dequeue'd element
    const SearchNode& node = scoredNode->node;
    // (handle the memory: e.g., duplicate visits)
#if SEARCH_FULL_MEMORY!=0
    const uint64_t fullMemoryItem = memoryItem(node.factHash(), node.tokenIndex(), node.truthState());
    if (fullMemory.find(fullMemoryItem) != fullMemory.end()) {
      continue;  // Prohibit duplicate visits
    }
    fullMemory.insert(fullMemoryItem);
#else
#if SEARCH_CYCLE_MEMORY!=0
    // ??? [gabor May 2015 was wondering]
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
#endif
    // (handle soft alignments)
#if MAX_FUZZY_MATCHES > 0
    memcpy(currentNodeSoftAlignmentScores, node.softAlignmentScores(), MAX_FUZZY_MATCHES * sizeof(float));
    memcpy(childNodeSoftAlignmentScores, currentNodeSoftAlignmentScores, MAX_FUZZY_MATCHES * sizeof(float));
#endif
    
    // Register visited
    registerVisited(*scoredNode);

    // Collect info on whether this was a quantifier
    const uint8_t tokenIndex = node.tokenIndex();
    int8_t quantifierIndex = tree.quantifierIndex(tokenIndex);
    int8_t nextQuantifierTokenIndex = -1;
    for (uint8_t i = 0; i < numQuantifiers; ++i) {
      if (quantifierVisitOrder[i] == tokenIndex) {
        if (i < numQuantifiers - 1) {
          nextQuantifierTokenIndex = quantifierVisitOrder[i + 1];
        } else {
          nextQuantifierTokenIndex = tree.root();
        }
      }
    }

    // Update history
    const uint32_t myIndex = historySize;
    // >> debug (warning: very verbose!)
//    vector<SearchNode> path;
//    path.push_back(node);
//    if (node.getBackpointer() != 0) {
//      SearchNode head = node;
//      while (head.getBackpointer() != 0) {
//        path.push_back(head);
//        head = history[head.getBackpointer()];
//      }
//    }
//    fprintf(stderr, "%u>> %s = %s (points to %u; nextQuant=%d; truth=%u; index=%u)\n", 
//      myIndex, toString(*graph, tree, node).c_str(),
//      kbGloss(*graph, tree, path).c_str(),
//      node.getBackpointer(), nextQuantifierTokenIndex,
//      node.truthState(), node.tokenIndex());
    // << end debug 
    assert (myIndex < (opts.maxTicks + 1));  // + 1 to allow for the root
    history[myIndex] = node;
    historySize += 1;
    ticks += 1;
    assert (historySize == (ticks + 1));
    if (!opts.silent && ticks % 100000 == 0) {
      printTime("[%c] "); 
      fprintf(stderr, "  |Search Progress| ticks=%luK\n", ticks / 1000);
    }
    
    // ---
    // HANDLE MUTATIONS
    // ---

    // PUSH 1: Mutations
    uint32_t numEdges;
    const tagged_word nodeToken = node.token();
    assert(nodeToken.word < graph->vocabSize());
    const edge* edges = graph->incomingEdgesFast(nodeToken.word, &numEdges);
    for (uint32_t edgeI = 0; edgeI < numEdges; ++edgeI) {
      const edge& edge = edges[edgeI];
//      fprintf(stderr, "%u / %u: edge %u[%u]  -->  %u[%u]\n", 
//          edgeI, numEdges,
//          edge.source, edge.source_sense, edge.sink, edge.sink_sense);
      assert(edge.source < graph->vocabSize());
      assert(nodeToken.word < graph->vocabSize());
      assert(edge.sink == nodeToken.word);
      // (ignore when sense doesn't match)
      if (edge.source_sense != 0 && edge.sink_sense != nodeToken.sense) { 
        continue; 
      }
      // (ignore meronym edges if not a location)
      if ( (edge.type == MERONYM || edge.type == HOLONYM) &&
           !tree.isLocation(tokenIndex) ) {
        continue;
      }
      // (ignore multiple quantifier mutations)
      if (edge.type == QUANTREWORD || edge.type == QUANTNEGATE ||
          edge.type == QUANTUP || edge.type == QUANTDOWN) {
        if (quantifierIndex < 0) {
          continue;
        } else if (tree.word(tokenIndex) != nodeToken.word) { 
          continue;  // don't mutate quantifiers twice (never likely to fire)
        } else if (quantifierIndex < 0) { 
          continue;  // can only quantifier mutate quantifiers
        }
        assert (quantifierIndex >= 0);
        const quantifier_monotonicity& originalMonotonicity = tree.quantifier(quantifierIndex);
        const quantifier_monotonicity& nodeMonotonicity = node.quantifier(quantifierIndex);
        if (originalMonotonicity != nodeMonotonicity) {
          continue;  // don't mutate quantifiers twice (the more likely check)
        }
      } else if ( quantifierIndex >= 0 ) {
//                  && (edge.type == SENSEREMOVE || edge.type == SENSEADD) ) {
        // Disallow quantifiers changing their sense.
        continue;
      }
      // (get cost)
      bool newTruthValue;
      assert (!isinf(edge.cost));
      assert (edge.cost == edge.cost);
      assert (edge.cost >= 0.0);
      const float mutationCost = costs->mutationCost(
          tree, node, edge.type,
          node.truthState(), &newTruthValue, 
          &features);
      if (isinf(mutationCost)) { continue; }
      const float cost = mutationCost * edge.cost;

      // (create child)
      SearchNode mutatedChild  // not const; we may mutate it below
        = node.mutation(edge, myIndex, newTruthValue, tree, graph);
      mutatedChild.incomingFeatures = features;
      assert(mutatedChild.incomingFeatures.insertionTaken == 255);
      assert(mutatedChild.incomingFeatures.mutationTaken != 31);
      assert(mutatedChild.incomingFeatures.transitionTaken != 7);
      assert(mutatedChild.word() < graph->vocabSize());
      // (handle quantifier mutation)
      if (quantifierIndex >= 0) {
        // ((compute new monotonicity information)
        quantifier_type subjType, objType;
        monotonicity subjMono, objMono;
        characterizeQuantifier(edge.source, &subjType, &objType, &subjMono, &objMono);
        // ((mutate the quantifier))
        mutatedChild.mutateQuantifier(quantifierIndex,
            subjMono, subjType, objMono, objType);
      }
      // (push child)
      // ((check memory))
#if SEARCH_FULL_MEMORY!=0
#else
#if SEARCH_CYCLE_MEMORY!=0
      bool isNewChild = true;
      for (uint8_t i = 0; i < memorySize; ++i) {
        isNewChild &= (mutatedChild != memory[i]);
      }
      if (isNewChild) {
#endif
#endif
      // ((update alignment scores))
      for (uint8_t alignI = 0; alignI < MAX_FUZZY_MATCHES; ++alignI) {
        if (alignI < softAlignments.size()) {
          childNodeSoftAlignmentScores[alignI] = softAlignments[alignI].updateScore(
              currentNodeSoftAlignmentScores[alignI],
              mutatedChild.tokenIndex(),
              edge.sink,
              edge.source,
              tree.polarityAt(node, node.tokenIndex()),
              tree.polarityAt(mutatedChild, mutatedChild.tokenIndex()) );
        }
      }
      // ((perform push))
//      fprintf(stderr, "  push mutation %s\n", toString(*graph, tree, mutatedChild).c_str());
      assert(!isinf(cost));
      assert(cost == cost);  // NaN check
      assert(cost >= 0.0);
      assert(mutatedChild.incomingFeatures.transitionTaken != 7);
#if MAX_FUZZY_MATCHES > 0
      enqueue(ScoredSearchNode(mutatedChild, cost, childNodeSoftAlignmentScores));
#else 
      enqueue(ScoredSearchNode(mutatedChild, cost));
#endif
      assert(mutatedChild.incomingFeatures.transitionTaken != 7);
#if SEARCH_FULL_MEMORY!=0
#else
#if SEARCH_CYCLE_MEMORY!=0
      }
#endif
#endif
    }
    
    // ---
    // HANDLE DELETIONS
    // ---
  
    // Get Children
    uint8_t numDependents;
    tree.dependents(tokenIndex, 8, dependentIndices,
                    dependentRelations, &numDependents);
   
    // Iterate over children
    for (uint8_t dependentI = 0; dependentI < numDependents; ++dependentI) {
      const uint8_t& dependentIndex = dependentIndices[dependentI];

      // PUSH 2: Deletions
      if (node.isDeleted(dependentIndex)) { continue; }
      bool newTruthValue;
      const float cost = costs->insertionCost(
            tree, node, tree.relation(dependentIndex),
            tree.word(dependentIndex), node.truthState(), &newTruthValue,
            &features);
      if (!isinf(cost)) {
        // (create child)
        SearchNode deletedChild 
          = node.deletion(myIndex, newTruthValue, tree, dependentIndex);
        deletedChild.incomingFeatures = features;
        assert(deletedChild.incomingFeatures.mutationTaken == 31);
        assert(deletedChild.incomingFeatures.transitionTaken != 7);
        assert(deletedChild.incomingFeatures.insertionTaken != 255);
        assert(deletedChild.word() < graph->vocabSize());
        // ((update alignment scores))
        for (uint8_t alignI = 0; alignI < MAX_FUZZY_MATCHES; ++alignI) {
          if (alignI < softAlignments.size()) {
            childNodeSoftAlignmentScores[alignI] = softAlignments[alignI].updateScore(
                currentNodeSoftAlignmentScores[alignI],
                deletedChild.tokenIndex(),
                node.word(),
                INVALID_WORD,
                tree.polarityAt(node, node.tokenIndex()),
                tree.polarityAt(deletedChild, deletedChild.tokenIndex()) );
          }
        }
        // (push child)
//        fprintf(stderr, "  push deletion %s\n", toString(*graph, tree, deletedChild).c_str());
        assert(!isinf(cost));
        assert(cost == cost);  // NaN check
        assert(cost >= 0.0);
        assert(deletedChild.incomingFeatures.insertionTaken != 255);
#if MAX_FUZZY_MATCHES > 0
        enqueue(ScoredSearchNode(deletedChild, cost, childNodeSoftAlignmentScores));
#else 
        enqueue(ScoredSearchNode(deletedChild, cost));
#endif
        assert(deletedChild.incomingFeatures.insertionTaken != 255);
      }
    }  // end children loop
    
    // ---
    // HANDLE INDICES
    // ---

    if (nextQuantifierTokenIndex < 0) {
      // PUSH 3: Index Move (regular order)
      // (find the next index in the topological order)
      uint8_t i = 0;
      while (topologicalOrder[i] != tokenIndex &&
          topologicalOrder[i] != 255) {
        i += 1;
      }
      assert (topologicalOrder[i] == tokenIndex || topologicalOrder[i] == 255);
      const uint8_t& nextIndex = topologicalOrder[i] == 255 ? 255 : topologicalOrder[i + 1];
      // (if there is such an index, push it)
      if (nextIndex != 255 && !node.isDeleted(nextIndex)) {
        const SearchNode indexMovedChild(node, tree, nextIndex, myIndex);
        assert (indexMovedChild.truthState() == node.truthState());
//        fprintf(stderr, "  push index move (reg) -> %u: %s\n", indexMovedChild.tokenIndex(), toString(*graph, tree, indexMovedChild).c_str());
        assert(nextIndex < tree.length);
        assert(nextIndex >= 0);
        assert(indexMovedChild.word() < graph->vocabSize());
        assert(indexMovedChild.incomingFeatures.mutationTaken == 31);
        assert(indexMovedChild.incomingFeatures.transitionTaken == 7);
        assert(indexMovedChild.incomingFeatures.insertionTaken == 255);
        // (push child)
#if MAX_FUZZY_MATCHES > 0
        enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost, currentNodeSoftAlignmentScores));
#else
        enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost));
#endif
      }
  
    } else if (nextQuantifierTokenIndex >= 0) {
      // PUSH 4: Index Move (quantifier)
      // (create child)
      SearchNode indexMovedChild(node, tree, nextQuantifierTokenIndex,
                                       myIndex);
      assert (indexMovedChild.truthState() == node.truthState());
      assert(nextQuantifierTokenIndex < tree.length);
      assert(nextQuantifierTokenIndex >= 0);
      assert(indexMovedChild.word() < graph->vocabSize());
      bool shouldEnqueue = true;
      if (nextQuantifierTokenIndex == tree.root()) {
        if (!indexMovedChild.allQuantifiersSeen()) {
          // (case: first time through quantifiers; continue to root)
          indexMovedChild.setAllQuantifiersSeen();
//          fprintf(stderr, "  push index move (quant) -> %u : %s\n", indexMovedChild.tokenIndex(), toString(*graph, tree, indexMovedChild).c_str());
          assert(indexMovedChild.incomingFeatures.mutationTaken == 31);
          assert(indexMovedChild.incomingFeatures.transitionTaken == 7);
          assert(indexMovedChild.incomingFeatures.insertionTaken == 255);
#if MAX_FUZZY_MATCHES > 0
          enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost, currentNodeSoftAlignmentScores));
#else
          enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost));
#endif
        }
      } else {
        // (case: still mutating quantifiers)
//        fprintf(stderr, "  push index move (quant) -> %u : %s\n", indexMovedChild.tokenIndex(), toString(*graph, tree, indexMovedChild).c_str());
        assert(indexMovedChild.incomingFeatures.mutationTaken == 31);
        assert(indexMovedChild.incomingFeatures.transitionTaken == 7);
        assert(indexMovedChild.incomingFeatures.insertionTaken == 255);
#if MAX_FUZZY_MATCHES > 0
        enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost, currentNodeSoftAlignmentScores));
#else
        enqueue(ScoredSearchNode(indexMovedChild, scoredNode->cost));
#endif
      }
    }  // end quantifier push conditional
  }  // end search loop

  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "  finished search loop after %lu ticks\n", ticks);
  }
  return ticks;
}
#pragma GCC pop_options  // matches push_options above



//
// The entry method for searching
//
syn_search_response SynSearch(
    const Graph* mutationGraph, 
    const btree::btree_set<uint64_t>* kb,
    const btree::btree_set<uint64_t>& auxKB,
    const Tree* input, const SynSearchCosts* costs,
    const bool& assumedInitialTruth, const syn_search_options& opts,
    const vector<AlignmentSimilarity>& softAlignments) {
  syn_search_response response;

  // Debug print parameters
  if (opts.maxTicks >= 0x1 << 25) {
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
  SearchNode* history = (SearchNode*) malloc((opts.maxTicks + 1) * sizeof(SearchNode));  // + 1 to allow for root.
  uint64_t historySize = 0;
  // The fringe
  KNHeap<float,SearchNode>* fringe = new KNHeap<float,SearchNode>(
    std::numeric_limits<float>::infinity(),
    -std::numeric_limits<float>::infinity());
  // The closeset approximate match
  uint8_t closestSoftAlignment = 0;
  float   closestSoftAlignmentScore = -std::numeric_limits<float>::infinity();
  float   closestSoftAlignmentScores[MAX_FUZZY_MATCHES];
  float   closestSoftAlignmentSearchCosts[MAX_FUZZY_MATCHES];
  for (uint8_t i = 0; i < MAX_FUZZY_MATCHES; ++i) {
    closestSoftAlignmentScores[i] = -std::numeric_limits<float>::infinity();
    closestSoftAlignmentSearchCosts[i] = 0.0f;
  }
  // The database lookup function
  // (the matches found)
  vector<syn_search_path>& matches = response.paths;
  vector<feature_vector>& featurizedPaths = response.featurizedPaths;
  // (the lookup function)
  std::function<bool(uint64_t)> lookupFn = [&kb,&auxKB](const uint64_t& value) -> bool {
    return kb->find(value) != kb->end() || auxKB.find(value) != auxKB.end();
  };
  // (register a node as visited)
  auto registerVisited = [&matches,&lookupFn,&history,&mutationGraph,&input,
                          &opts,&assumedInitialTruth,&featurizedPaths,
                          &closestSoftAlignment,&closestSoftAlignmentScore,
                          &closestSoftAlignmentScores,&closestSoftAlignmentSearchCosts]
        (const ScoredSearchNode& scoredNode) -> void {
    const SearchNode& node = scoredNode.node;
    // Check the soft alignments
#if MAX_FUZZY_MATCHES > 0
    for (uint8_t alignI = 0; alignI < MAX_FUZZY_MATCHES; ++alignI) {
      const float& nodeAlignmentScore = node.softAlignmentScores()[alignI];
      if (nodeAlignmentScore > closestSoftAlignmentScores[alignI] + 1e-7) {  // 1e-7 to be robust to floating point drift
        // Update the per-premise alignment scores
        closestSoftAlignmentScores[alignI] = node.softAlignmentScores()[alignI];
        closestSoftAlignmentSearchCosts[alignI] = scoredNode.cost;
        if (!opts.silent) {
          printTime("[%c] "); 
          fprintf(stderr, "  best soft match @ %u = %f\n", alignI, closestSoftAlignmentScore);
        }
        // Update the global best alignment
        if (nodeAlignmentScore > closestSoftAlignmentScore) {
          closestSoftAlignment = alignI;
          closestSoftAlignmentScore = nodeAlignmentScore;
          if (!opts.silent) {
            printTime("[%c] "); 
            fprintf(stderr, "  best alignment = %u\n", alignI);
          }
        }
      }
    }
#endif
    
    if (node.truthState() && lookupFn(node.factHash())) {

      // Make sure nodes are unique
      bool unique = true;
      for (auto iter = matches.begin(); iter != matches.end(); ++iter) {
        vector<SearchNode> path = iter->nodeSequence;
        SearchNode otherEntry = path.front();
        if (otherEntry.factHash() == node.factHash()) {
          unique = false;
        }
      }

      // Make sure nodes are more than one word (this is degenerate)
      uint8_t numWordsInPremise = 0;
      for (uint8_t i = 0; i < input->length; ++i) {
        if (!node.isDeleted(i)) {
          numWordsInPremise += 1;
        }
      }
      const bool degenerate = (numWordsInPremise < 2);

      // Add the node
      if (unique && !degenerate) {
        // Add this path to the result
        // (get the complete path)
        vector<SearchNode> path;
        feature_vector myFeatures;
        myFeatures.increment(node.incomingFeatures, assumedInitialTruth);
        path.push_back(node);
        if (node.getBackpointer() != 0) {
          SearchNode head = node;
          while (head.getBackpointer() != 0) {
            head = history[head.getBackpointer()];
            path.push_back(head);
            myFeatures.increment(head.incomingFeatures, assumedInitialTruth ^ head.truthState());
          }
        }
        // (add to the results list)
        if (!opts.silent) {
          printTime("[%c] "); 
          fprintf(stderr, "  found premise: %s {hash: %lu; points to: %u}\n", 
              kbGloss(*mutationGraph, *input, path).c_str(),
              path.front().factHash(), path.front().getBackpointer());
        }
        matches.push_back(syn_search_path(path, scoredNode.cost));
        featurizedPaths.push_back(myFeatures);
      }
    }
  };

  // -- Run Search --
  // Enqueue the first element
  SearchNode start;
  // (compute quantifiers)
  if (!opts.silent) { printTime("[%c] "); }
  if (input->getNumQuantifiers() > 0) {
    // (case: there are quantifiers in the sentence)
    start = SearchNode(*input, assumedInitialTruth, input->quantifierTokenIndex(0));
    if (!opts.silent) {
      fprintf(stderr, "  %u quantifier(s); starting on index %u\n", 
          input->getNumQuantifiers(), input->quantifierTokenIndex(0));
    }
  } else {
    // (case: no quantifiers in sentence)
    start = SearchNode(*input, assumedInitialTruth);
    if (!opts.silent) {
      fprintf(stderr, "  no quantifiers; starting at root=%u\n", input->root());
    }
  }
  // (compute fuzzy scores for soft alignment)
#if MAX_FUZZY_MATCHES > 0
  float fuzzyScores[MAX_FUZZY_MATCHES];
  for (uint8_t i = 0; i < MAX_FUZZY_MATCHES; ++i) {
    if (i < softAlignments.size()) {
      fuzzyScores[i] = softAlignments[i].score(*input);
    } else {
      fuzzyScores[i] = -std::numeric_limits<float>::infinity();
    }
  }
  start.setFuzzyScores(fuzzyScores);
#endif
  // (add the node to the fringe)
  fringe->insert(0.0f, start);

  // (to the history)
  history[0] = start;
  historySize += 1;

  // Run Search
  response.totalTicks = searchLoop(
    // Insert to fringe
    [&fringe](const ScoredSearchNode& elem) -> void { 
      fringe->insert(elem.cost, elem.node);
    },
    // Pop from fringe
    [&fringe](ScoredSearchNode* output) -> bool { 
      if (fringe->isEmpty()) { return false; }
      if (fringe->getSize() > 10000000) { return false; }
      fringe->deleteMin(&(output->cost), &(output->node));
      return true;
    },
    // Register visited
    registerVisited,
    // Other crap
    history, historySize, costs, opts, 
    softAlignments,
    mutationGraph, *input
    );

  // Check the fringe for known facts
  if (opts.checkFringe && response.paths.empty()) {
    if (!opts.silent) {
      printTime("[%c] ");
      fprintf(stderr, "  |Checking Fringe| size=%u\n", fringe->getSize());
    }
    ScoredSearchNode* scoredNode = (ScoredSearchNode*) alloca(sizeof(ScoredSearchNode));
    while(!fringe->isEmpty()) {
      fringe->deleteMin(&(scoredNode->cost), &(scoredNode->node));
      registerVisited(*scoredNode);
    }
    if (!opts.silent) {
      printTime("[%c] ");
      fprintf(stderr, "    Done\n");
    }
  }

  // Clean up
  free(history);
  delete fringe;
  
  // Return
  // (set closest matches)
  response.closestSoftAlignment = closestSoftAlignment;
  memcpy(response.closestSoftAlignmentScores, closestSoftAlignmentScores, MAX_FUZZY_MATCHES * sizeof(float));
  memcpy(response.closestSoftAlignmentSearchCosts, closestSoftAlignmentSearchCosts, MAX_FUZZY_MATCHES * sizeof(float));
  response.closestSoftAlignmentScore = closestSoftAlignmentScore;
  // (debug)
  if (!opts.silent) {
    printTime("[%c] ");
    fprintf(stderr, "  |Search End| Returning %lu responses\n",response.paths.size());
  }
  // (return)
  return response;
}
