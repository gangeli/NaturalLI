#ifndef NaturalLI_H
#define NaturalLI_H

#include "config.h"
#include "SynSearch.h"
#include "JavaBridge.h"

#define MEM_ENV_VAR "MAXMEM_GB"

#ifndef ERESTART
#define ERESTART EINTR
#endif


/**
 * A bunch of stuff to be done when starting the program. For example:
 * <ul>
 *  <li> Set up signal handlers </li>
 *  <li> Set the maximum memory </li>
 * </ul>
 */
void init();

/**
 * Parse a metadata line, returning false if the line didn't match any
 * known directives.
 */
bool parseMetadata(const char *rawLine, SynSearchCosts *costs,
                   std::vector<AlignmentSimilarity>* alignments,
                   syn_search_options *opts);

/**
 * Execute a query, returning a JSON formatted response.
 *
 * @param premises The premises to use to augment the knowledge base.
 * @param kb The [optionally empty] large knowledge base to evaluate against.
 * @param knownFacts An optional knowledge base to use to augment the facts in 
 *                   kb.
 * @param query The query to execute against the knowledge base.
 * @param graph The graph of valid edge instances which can be taken.
 * @param costs The search costs to use for this query
 * @param alignments The soft alignments (if there are any) to run alongside the
 *                   search.
 * @param options The options for the search, to be passed along directly.
 * @param truth [output] The probability that the query is true, as just a
 *              simple float value.
 *
 * @return A JSON formatted response with the result of the search.
 */
std::string executeQuery(const std::vector<Tree*> premises, const btree::btree_set<uint64_t> *kb,
                         const std::vector<std::string> &knownFacts, const std::string &query,
                         const Graph *graph, const SynSearchCosts *costs,
                         const std::vector<AlignmentSimilarity>& alignments,
                         const syn_search_options &options,
                         double *truth);

/**
 * Execute a query using the java bridge to annotate the trees.
 */
std::string executeQuery(const JavaBridge *proc, const btree::btree_set<uint64_t> *kb,
                         const std::vector<std::string> &knownFacts, const std::string &query,
                         const Graph *graph, const SynSearchCosts *costs,
                         const std::vector<AlignmentSimilarity>& alignments,
                         const syn_search_options &options,
                         double *truth);

/**
 * Reads a tree from standard input, where the standard input is
 * already a CoNLL representation of the tree.
 */
Tree* readTreeFromStdin();

/**
 * Reads a tree from standard input, where the standard input is
 * already a CoNLL representation of the tree.
 * This method additionally parses optional metadata passed in along with the tree.
 */
Tree* readTreeFromStdin(SynSearchCosts* costs,
                        std::vector<AlignmentSimilarity>* alignments,
                        syn_search_options* opts);

/**
 * Start a REPL loop over stdin and stdout.
 * Each query consists of a number of entries (possibly zero) in the knowledge
 * base, seperated by newlines, and then a query.
 * A double newline signals the end of a block, where the first k-1 lines are
 *the
 * knowledge base, and the last line is the query.
 *
 * @param graph The graph containing the edge instances we can traverse.
 * @param proc The preprocessor to use during the search.
 * @param kb The main [i.e., large] knowledge base to use, possibly empty.
 *
 * @return The number of failed examples, if any were annotated. 0 by default.
 */
uint32_t repl(const Graph *graph, JavaBridge *proc,
              const btree::btree_set<uint64_t> *kb);

/**
 * @see repl(Graph* JavaBridge* btree::btree_set<uint64_t>)
 */
uint32_t repl(const Graph *graph, const btree::btree_set<uint64_t> *kb);

/**
 * Set up listening on a server port.
 */
bool startServer(const uint32_t &port, const JavaBridge *proc,
                 const Graph *graph, const btree::btree_set<uint64_t> *kb);

#endif
