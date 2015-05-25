#include "NaturalLIIO.h"

#include "Utils.h"

#include <arpa/inet.h>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <mutex>
#include <netinet/in.h>
#include <sstream>
#include <sys/errno.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <signal.h>
#include <regex>
#include <thread>
#include <unistd.h>

#ifndef SERVER_PORT
#define SERVER_PORT 1337
#endif

#define SERVER_TCP_BUFFER 25
#define SERVER_READ_SIZE 1024

extern int errno;

using namespace std;
using namespace btree;


/**
 * Set a memory limit, if defined
 */
void setmemlimit() {
  struct rlimit memlimit;
  long bytes;
  if (getenv(MEM_ENV_VAR) != NULL) {
    bytes = atol(getenv(MEM_ENV_VAR)) * (1024 * 1024 * 1024);
    memlimit.rlim_cur = bytes;
    memlimit.rlim_max = bytes;
    setrlimit(RLIMIT_AS, &memlimit);
  }
}


/**
 * The function to call for caught signals. In practice, this is a NOOP.
 */
void signalHandler(int32_t s) {
  fprintf(stderr, "(caught signal %d; use 'kill' or EOF to end the program)\n",
          s);
  fprintf(stderr, "What do we say to the God of death?\n");
  fprintf(stderr, "  \"Not today...\"\n");
}


//
// init()
//
void init() {
  // Handle signals
  // (set memory limit)
  setmemlimit();
  // (set up handler)
  struct sigaction sigIntHandler;
  sigIntHandler.sa_handler = signalHandler;
  sigemptyset(&sigIntHandler.sa_mask);
  sigIntHandler.sa_flags = 0;
  // (catch signals)
  //  sigaction(SIGINT,  &sigIntHandler, NULL);  // Stopping SIGINT causes the
  //  java process to die
  sigaction(SIGPIPE, &sigIntHandler, NULL);
}


/**
 * Compute the confidence of a search response.
 * Note that this is truth independent at this point.
 *
 * @param response The search response.
 * @return A confidence value between 0 and 1/2;
 */
double confidence(const syn_search_response &response,
                  const vector<SearchNode> **argmax,
                  const feature_vector **argmax2) {
  if (response.size() == 0) {
    return 0.0;
  }
  double max = -std::numeric_limits<float>::infinity();
  for (uint64_t i = 0; i < response.paths.size(); ++i) {
    float cost = response.paths[i].cost;
    double confidence = 1.0 / (1.0 + exp(cost));
    if (confidence > max) {
      max = confidence;
      *argmax = &(response.paths[i].nodeSequence);
      *argmax2 = &(response.featurizedPaths[i]);
    }
  }
  return max;
}

/**
 * Compute the truth of a fact, given a confidence and the truth state
 * of the confidence.
 *
 * @param confidence The confidence of this truth value.
 * @param truth The hypothesized truth value of the fact.
 *
 * @return A truth value between 0 and 1
 */
inline double probability(const double &confidence, const bool &truth) {
  if (truth) {
    return 0.5 + confidence;
  } else {
    return 0.5 - confidence;
  }
}


/**
 * Parse an alignment instance of the form:
 *
 * tokenindex:target:bonus:penalty
 */
bool parseAlignmentInstance(
    string& spec,
    vector<alignment_instance>* alignments) {
  string delimiter = ":";
  const char* origSpec = spec.c_str();
  size_t pos = 0;

  // Parse the token index
  if ((pos = spec.find(delimiter)) == string::npos) {
    fprintf(stderr, "WARNING: bad spec for alignment [1]: %s\n", origSpec);
    return false;
  }
  uint8_t tokenIndex = atoi(spec.substr(0, pos).c_str());
  spec.erase(0, pos + delimiter.length());
  // Parse the target word
  if ((pos = spec.find(delimiter)) == string::npos) {
    fprintf(stderr, "WARNING: bad spec for alignment [2]: %s\n", origSpec);
    return false;
  }
  ::word target = atoi(spec.substr(0, pos).c_str());
  spec.erase(0, pos + delimiter.length());
  // Parse the bonus
  if ((pos = spec.find(delimiter)) == string::npos) {
    fprintf(stderr, "WARNING: bad spec for alignment [3]: %s\n", origSpec);
    return false;
  }
  float bonus = atof(spec.substr(0, pos).c_str());
  spec.erase(0, pos + delimiter.length());
  // Parse the penalty
  if ((pos = spec.find(delimiter)) == 0) {
    fprintf(stderr, "WARNING: bad spec for alignment [4]: %s\n", origSpec);
    return false;
  }
  float penalty = atof(spec.substr(0, pos).c_str());
  spec.erase(0, pos + delimiter.length());
  // Enqueue the alignment
  fprintf(stderr, "align(%d, %d, %f, %f)\n", tokenIndex, target, bonus, penalty);
  // TODO(gabor) this was supposed to have a bonus and penalty associated
  alignments->emplace_back(tokenIndex, target, MONOTONE_UP);
  return true;
}

/**
 * Parse a full alignment specification of the form:
 *
 * tokenindex:target:bonus:penalty#tokenindex:target:bonus:penalty ...
 */
AlignmentSimilarity parseAlignment(string& spec) {
  const char* origSpec = spec.c_str();
  string delimiter = "#";
  size_t pos = 0;
  vector<alignment_instance> instances;
  // Loop through alignments
  while ((pos = spec.find(delimiter)) != string::npos) {
    string instanceSpec = spec.substr(0, pos);
    parseAlignmentInstance(instanceSpec, &instances);
    spec.erase(0, pos + delimiter.length());
  }
  // Add last alignment
  string instanceSpec = spec.substr(0, pos);
  parseAlignmentInstance(instanceSpec, &instances);
  // Return
  if (instances.size() == 0) {
    fprintf(stderr, "WARNING: no alignments parsed in spec: %s\n", origSpec);
  }
  return AlignmentSimilarity(instances);
}

/**
 * A little helper to convert a string into a boolean.
 */
bool to_bool(std::string str) {
  transform(str.begin(), str.end(), str.begin(), ::tolower);
  istringstream is(str);
  bool b;
  is >> boolalpha >> b;
  return b;
}

/** The regex for a key@specifier=value triple */
regex regexSetValue("([^ ]+) *@ *([^ ]+) *= *([^ ]+)",
                    std::regex_constants::extended);
/** The regex for a key=value pair */
regex regexSetFlag("([^ ]+) *= *([^ ]+) *", std::regex_constants::extended);


//
// parseMetadata()
//
bool parseMetadata(const char *rawLine, SynSearchCosts **costs,
                   vector<AlignmentSimilarity>* alignments,
                   syn_search_options *opts) {
  assert(rawLine[0] != '\0');
  assert(rawLine[0] == '%');
  string line(&(rawLine[1]));
  smatch result;
  if (regex_search(line, result, regexSetValue)) {
    if (result.size() != 4) {
      return false;
    }
    string toSet = result[1].str();
    string key = result[2].str();
    string value = result[3].str();
    uint32_t ivalue = atoi(result[3].str().c_str());
    if (toSet == "mutationLexicalCost") {
      (*costs)->mutationLexicalCost[indexEdgeType(key)] = atof(value.c_str());
    } else if (toSet == "insertionLexicalCost") {
      (*costs)->insertionLexicalCost[indexEdgeType(key)] = atof(value.c_str());
    } else if (toSet == "transitionCostFromTrue") {
      (*costs)->transitionCostFromTrue[indexNatlogRelation(key)] = atof(value.c_str());
    } else if (toSet == "transitionCostFromFalse") {
      (*costs)->transitionCostFromFalse[indexNatlogRelation(key)] = atof(value.c_str());
    } else {
      fprintf(stderr, "Unknown key: '%s'\n", toSet.c_str());
      return false;
    }
    return true;
  } else if (regex_search(line, result, regexSetFlag)) {
    string toSet = result[1].str();
    string value = result[2].str();
    if (toSet == "maxTicks") {
      opts->maxTicks = atof(value.c_str());
      fprintf(stderr, "set maxTicks to %u\n", opts->maxTicks);
    } else if (toSet == "checkFringe") {
      opts->checkFringe = to_bool(value);
      fprintf(stderr, "set checkFringe to %u\n", to_bool(value));
    } else if (toSet == "skipNegationSearch") {
      opts->skipNegationSearch = to_bool(value);
      fprintf(stderr, "set skipNegationSearch to %u\n", to_bool(value));
    } else if (toSet == "alignment") {
      if (alignments->size() < MAX_FUZZY_MATCHES) {
        alignments->push_back(parseAlignment(value));
      } else {
        fprintf(stderr, "WARNING: too many candidate premises passed in; dropping this and future alignments\n");
      }
    } else if (toSet == "softCosts") {
      if (to_bool(value)) {
        delete *costs;
        *costs = softNaturalLogicCosts();
        fprintf(stderr, "set costs to 'softCosts'\n");
      }
    } else if (toSet == "defaultCosts") {
      if (to_bool(value)) {
        delete *costs;
        *costs = intermediateNaturalLogicCosts();
        fprintf(stderr, "set costs to 'defaultCosts'\n");
      }
    } else if (toSet == "strictCosts") {
      if (to_bool(value)) {
        delete *costs;
        *costs = strictNaturalLogicCosts();
        fprintf(stderr, "set costs to 'strictCosts'\n");
      }
    } else {
      fprintf(stderr, "Unknown flag: '%s'\n", toSet.c_str());
      return false;
    }
    return true;
  }
  fprintf(stderr, "WARNING line NOT parsed as a directive: '%s'\n",
          line.c_str());
  return false; // By default, no metadata
}


//
// Sigmoid utility function
//
inline double sigmoid(double x) {
  return 1.0 / (1.0 + exp(-x));
}

//
// Find if any top alignments increased in the true setting
//
bool existsSoftFalseJudgment(const float* ifTrue, const float* ifFalse, const float maxValue,
                             const uint8_t& numAlignments) {
  float maxTowardsFalse = 0.0f;
  float maxTowardsTrue = 0.0f;
  for (uint8_t i = 0; i < numAlignments; ++i) {
    if (ifFalse[i] - ifTrue[i] > maxTowardsFalse) {
      maxTowardsFalse = ifFalse[i] - ifTrue[i];
    }
    if (ifTrue[i] - ifFalse[i] > maxTowardsTrue) {
      maxTowardsTrue = ifTrue[i] - ifFalse[i];
    }
  }
  return maxTowardsFalse > maxTowardsTrue;
}

//
// executeQuery()
//
string executeQuery(const vector<Tree*> premises, const btree_set<uint64_t> *kb,
                    const Tree* query,
                    const Graph *graph, const SynSearchCosts *costs,
                    vector<AlignmentSimilarity> alignments,
                    const syn_search_options &options,
                    double *truth) {
  // Create KB
  bool doAlignments = (alignments.size() == 0);
  btree_set<uint64_t> auxKB;
  uint32_t factsInserted = 0;
  for (auto treeIter = premises.begin(); treeIter != premises.end();
       ++treeIter) {
    Tree *premise = *treeIter;
    // hash the tree
    const uint64_t hash = SearchNode(*premise).factHash();
    printTime("[%c] ");
    fprintf(stderr, "|KB| adding premise with hash: %lu\n", hash);
    auxKB.insert(hash);
    factsInserted += 1;
    // align the tree
    if (doAlignments && alignments.size() < MAX_FUZZY_MATCHES) {
      fprintf(stderr, "ALIGNING %s\n", toString(*premise, *graph).c_str());
      AlignmentSimilarity alignment = query->alignToPremise(*premise, *graph);
      alignment.debugPrint(*query, *graph);  // debug print the alignment
      fprintf(stderr, "  score (if true):  %f\n", alignment.score(*query, true));
      fprintf(stderr, "  score (if false): %f\n", alignment.score(*query, false));
      alignments.push_back(alignment);
    }
  }
  printTime("[%c] ");
  fprintf(stderr, "|KB| %lu premise(s) added, yielding %u total facts\n",
          premises.size(), factsInserted);
  printTime("[%c] ");
  fprintf(stderr, "|ALIGN| %lu alignment(s) registered\n", alignments.size());

  // Error check query
  if (query == NULL) {
    *truth = 0.0;
    return "{\"success\": false, \"reason\": \"hypothesis (query) is too "
           "long\"}";
  }

  // Run Search
  // (assuming the KB is true)
  const syn_search_response resultIfTrue =
      SynSearch(graph, kb, auxKB, query, costs, true, options, alignments);
  // (assuming the KB is false)
  syn_search_options falseOptions = options;
  if (options.skipNegationSearch) {
    falseOptions.maxTicks = 0l;
  }
  const syn_search_response resultIfFalse =
      SynSearch(graph, kb, auxKB, query, costs, false, falseOptions, alignments);

  // Grok result
  // (confidence)
  const vector<SearchNode> *bestPathIfTrue = NULL;
  const vector<SearchNode> *bestPathIfFalse = NULL;
  const feature_vector *bestFeaturesIfTrue = NULL;
  const feature_vector *bestFeaturesIfFalse = NULL;
  double confidenceOfTrue =
      confidence(resultIfTrue, &bestPathIfTrue, &bestFeaturesIfTrue);
  double confidenceOfFalse =
      confidence(resultIfFalse, &bestPathIfFalse, &bestFeaturesIfFalse);
  // (soft alignments)
  const uint8_t* closestSoftAlignment = &resultIfTrue.closestSoftAlignment;
  const float* closestSoftAlignmentScores = resultIfTrue.closestSoftAlignmentScores;
  const float* closestSoftAlignmentSearchCosts = resultIfTrue.closestSoftAlignmentSearchCosts;
  // (truth)
  const char* hardGuess = "unknown";
  const char* softGuess = "unknown";
  const vector<SearchNode> *bestPath = NULL;
  const feature_vector *bestFeatures = NULL;
  if (confidenceOfTrue > confidenceOfFalse) {
    // Case: hard true
    *truth = probability(confidenceOfTrue, true);
    bestPath = bestPathIfTrue;
    bestFeatures = bestFeaturesIfTrue;
    hardGuess = "true";
    softGuess = "true";
  } else if (confidenceOfTrue < confidenceOfFalse) {
    // Case: hard false
    *truth = probability(confidenceOfFalse, false);
    bestPath = bestPathIfFalse;
    bestFeatures = bestFeaturesIfFalse;
    closestSoftAlignmentScores = resultIfFalse.closestSoftAlignmentScores;
    closestSoftAlignmentSearchCosts = resultIfFalse.closestSoftAlignmentSearchCosts;
    hardGuess = "false";
    softGuess = "false";
  } else {
    if (resultIfTrue.paths.size() > 0 && resultIfFalse.paths.size() > 0) {
      // Case: weird tie on hard truth
      printTime("[%c] ");
      fprintf(stderr, "\033[1;33mWARNING:\033[0m Exactly the same max "
                      "confidence returned for both 'true' and 'false'");
      fprintf(stderr, "  if true: %s\n",
              toJSON(*graph, *query, *bestPathIfTrue).c_str());
      fprintf(stderr, "  if false: %s\n",
              toJSON(*graph, *query, *bestPathIfFalse).c_str());
      *truth = 0.5;
    } else if (resultIfFalse.closestSoftAlignmentScore < resultIfTrue.closestSoftAlignmentScore) {
      // Case: soft true
      printTime("[%c] ");
      fprintf(stderr, "Closest alignment is true, in absense of responses\n");
      *truth = 0.5 + sigmoid(resultIfTrue.closestSoftAlignmentScore) / 1000.0 + 1e-5;
      softGuess = "true";
    } else if (
                // Either the closest alignment is false...
                resultIfTrue.closestSoftAlignmentScore < resultIfFalse.closestSoftAlignmentScore ||
                // ... or the alignments are tied and one of the tied elements became more
                //     true in the false state.
                ( abs(resultIfTrue.closestSoftAlignmentScore - resultIfFalse.closestSoftAlignmentScore) < 1e-10 &&
                  existsSoftFalseJudgment(resultIfTrue.closestSoftAlignmentScores,
                                          resultIfFalse.closestSoftAlignmentScores,
                                          resultIfTrue.closestSoftAlignmentScore,
                                          alignments.size())
                ) ) {
      // Case: soft false
      printTime("[%c] ");
      fprintf(stderr, "Closest alignment is false, in absense of responses\n");
      closestSoftAlignment = &resultIfFalse.closestSoftAlignment;
      closestSoftAlignmentScores = resultIfFalse.closestSoftAlignmentScores;
      closestSoftAlignmentSearchCosts = resultIfFalse.closestSoftAlignmentSearchCosts;
      *truth = 0.5 - sigmoid(resultIfFalse.closestSoftAlignmentScore) / 1000.0 - 1e-5;
      softGuess = "false";
    } else {
      *truth = 0.5;
    }
  }
  // (debug)
  //  fprintf(stderr, "trueCount: %lu  falseCount: %lu  trueConf: %f  falseConf:
  //  %f  truth:  %f\n",
  //         resultIfTrue.size(), resultIfFalse.size(),
  //         confidenceOfTrue, confidenceOfFalse, *truth);
  // (sanity check)
  if (*truth < 0.0) {
    *truth = 0.0;
  }
  if (*truth > 1.0) {
    *truth = 1.0;
  }

  // Generate JSON
  stringstream rtn;
  rtn << fixed
      << "{\"numResults\": " << (resultIfTrue.size() + resultIfFalse.size())
      << ", "
      << "\"totalTicks\": "
      << (resultIfTrue.totalTicks + resultIfFalse.totalTicks) << ", "
      << "\"truth\": " << (*truth) << ", "
      << "\"hardGuess\": \"" << (hardGuess) << "\", "
      << "\"softGuess\": \"" << (softGuess) << "\", "
      << "\"query\": \"" << escapeQuote(toString(*query, *graph)) << "\", "
      << "\"bestPremise\": "
      << (bestPath == NULL
              ? "null"
              : ("\"" + escapeQuote(kbGloss(*graph, *query, *bestPath)) + "\""))
      << ", "
      << "\"success\": true"
      << ", "
#if MAX_FUZZY_MATCHES > 0
      << "\"closestSoftAlignment\": " << to_string(*closestSoftAlignment) << ", "
      << "\"closestSoftAlignmentScores\": " << toJSON(closestSoftAlignmentScores, MAX_FUZZY_MATCHES) << ", "
      << "\"closestSoftAlignmentSearchCosts\": " << toJSON(closestSoftAlignmentSearchCosts, MAX_FUZZY_MATCHES) << ", "
#endif
      << "\"path\": "
      << (bestPath != NULL ? toJSON(*graph, *query, *bestPath) : "[]");
  // (dump feature vector)
  if (bestFeatures != NULL) {
    rtn << ", \"features\": {"
        << " \"mutationCounts\": [";
    for (uint8_t i = 0; i < NUM_MUTATION_TYPES - 1; ++i) {
      rtn << bestFeatures->mutationCounts[i] << ", ";
    }
    rtn << bestFeatures->mutationCounts[NUM_MUTATION_TYPES - 1] << "], "
        << " \"transitionFromTrueCounts\": [";
    for (uint8_t i = 0; i < FUNCTION_INDEPENDENCE; ++i) {
      rtn << bestFeatures->transitionFromTrueCounts[i] << ", ";
    }
    rtn << bestFeatures->transitionFromTrueCounts[FUNCTION_INDEPENDENCE]
        << "], "
        << " \"transitionFromFalseCounts\": [";
    for (uint8_t i = 0; i < FUNCTION_INDEPENDENCE; ++i) {
      rtn << bestFeatures->transitionFromFalseCounts[i] << ", ";
    }
    rtn << bestFeatures->transitionFromFalseCounts[FUNCTION_INDEPENDENCE]
        << "], "
        << " \"insertionCounts\": [";
    for (uint8_t i = 0; i < NUM_DEPENDENCY_LABELS - 1; ++i) {
      rtn << bestFeatures->insertionCounts[i] << ", ";
    }
    rtn << bestFeatures->insertionCounts[NUM_DEPENDENCY_LABELS - 1] << "]"
        << "} ";
  }
  rtn << "}";
  return rtn.str();
}

/**
 * Parse a query string for whether it has an expected truth value.
 * This code is shared between the REPL and the server.
 *
 * @param query The query to parse.
 * @haveExpectedTruth True if the query is annotated with an expected truth
 *                    value. False by default.
 * @expectUnknown Returns True if we are expecting an 'unknown' judgment
 *                from the query. This is only well defined if
 *                haveExpectedTruth is True.
 * @expectedTruth The expected truth value. This is only well defined if
 *                haveExpectedTruth is True and if expectUnknown is false.
 * @return The modified query, with the potential prefix stripped.
 */
string grokExpectedTruth(string query, bool *haveExpectedTruth,
                         bool *expectUnknown, bool *expectedTruth) {
  *haveExpectedTruth = false;
  if (query.substr(0, 5) == "TRUE:") {
    *haveExpectedTruth = true;
    *expectedTruth = true;
    query = query.substr(5);
    while (query.at(0) == ' ' || query.at(0) == '\t') {
      query = query.substr(1);
    }
  } else if (query.substr(0, 6) == "FALSE:") {
    *haveExpectedTruth = true;
    *expectedTruth = false;
    query = query.substr(6);
    while (query.at(0) == ' ' || query.at(0) == '\t') {
      query = query.substr(1);
    }
  } else if (query.substr(0, 4) == "UNK:") {
    *haveExpectedTruth = true;
    *expectUnknown = true;
    query = query.substr(4);
    while (query.at(0) == ' ' || query.at(0) == '\t') {
      query = query.substr(1);
    }
  }
  return query;
}

/**
 * Parse the actual and expected response of a query, and print it as
 * a String prefix. This code is shared between the REPL and the server.
 */
string passOrFail(const double &truth, const bool &haveExpectedTruth,
                  const bool &expectUnknown, const bool &expectedTruth,
                  uint32_t* failureCount) {
  if (haveExpectedTruth) {
    if (expectUnknown) {
      if (truth > 0.48 && truth < 0.52) {
        return "PASS: ";
      } else {
        *failureCount += 1;
        return "FAIL: ";
      }
    } else {
      if ((truth > 0.5 && expectedTruth) || (truth < 0.5 && !expectedTruth)) {
        return "PASS: ";
      } else {
        *failureCount += 1;
        return "FAIL: ";
      }
    }
  }
  return "";
}

//
// readTreeFromStdin()
//
Tree* readTreeFromStdin(SynSearchCosts** costs,
                        vector<AlignmentSimilarity>* alignments,
                        syn_search_options* opts) {
  string conll = "";
  char line[256];
  char newline = '\n';
  uint32_t numLines = 0;
  while (!cin.fail()) {
    cin.getline(line, 255);
    if (line[0] == '#') {
      continue;
    }
    if (line[0] != '%' || !parseMetadata(line, costs, alignments, opts)) {
      numLines += 1;
      conll.append(line, strlen(line));
      conll.append(&newline, 1);
      if (numLines > 0 && line[0] == '\0') { 
        break;
      }
    }
  }
  if (numLines >= MAX_FACT_LENGTH) {
    return NULL;
  } else {
    return new Tree(conll);
  }
}

//
// readTreeFromStdin()
//
Tree* readTreeFromStdin() {
  SynSearchCosts* costs = intermediateNaturalLogicCosts();
  vector<AlignmentSimilarity> alignments;
  syn_search_options opts;
  Tree* rtn =  readTreeFromStdin(&costs, &alignments, &opts);
  delete costs;
  return rtn;
}

//
// repl()
//
uint32_t repl(const Graph *graph, JavaBridge *proc,
              const btree_set<uint64_t> *kb) {
  uint32_t failedExamples = 0;
  SynSearchCosts* costs = intermediateNaturalLogicCosts();
  syn_search_options opts;

  fprintf(stderr, "REPL is ready for text (maybe still waiting on CBridge)\n");
  while (!cin.fail()) {
    // Process lines
    vector<string> lines;
    char line[256];
    memset(line, 0, sizeof(line));
    // (create alignments)
    vector<AlignmentSimilarity> alignments;
    
    while (!cin.fail()) {
      cin.getline(line, 255);
      if (line[0] == '\0') {
        break;
      }
      if (line[0] != '#' &&
          (line[0] != '%' || !parseMetadata(line, &costs, &alignments, &opts))) {
        lines.push_back(string(line));
      }
    }

    if (lines.size() > 0) {
      // Check if query is annotated with truth
      string query = lines.back();
      lines.pop_back();
      bool haveExpectedTruth = false;
      bool expectedTruth;
      bool expectUnknown = false;
      query = grokExpectedTruth(query, &haveExpectedTruth, &expectUnknown,
                                &expectedTruth);
      // Run query
      double truth;
      string response =
          executeQuery(proc, kb, lines, query, graph, costs, alignments, opts, &truth);
      // Print
      fprintf(stderr, "\n");
      fflush(stderr);
      printf("%s", passOrFail(truth, haveExpectedTruth, expectUnknown,
                              expectedTruth, &failedExamples).c_str());
      printf("%s\n", response.c_str()); // Should be the only output to stdout
      fflush(stdout);
      fprintf(stderr, "\n");
      fflush(stderr);
    }
  }

  // Should never reach here!
  fprintf(stderr, "EOF on stdin -- exiting REPL\n");
  delete costs;
  return failedExamples;
}

//
// repl (with trees)
//
uint32_t repl(const Graph *graph, const btree_set<uint64_t> *kb) {
  uint32_t failedExamples = 0;
  SynSearchCosts* costs = intermediateNaturalLogicCosts();
  syn_search_options opts;

  fprintf(stderr, "REPL is ready for trees\n");
  while (!cin.fail()) {
    vector<Tree*> trees;
    // (create alignments)
    vector<AlignmentSimilarity> alignments;
    
    while (!cin.fail()) {
      Tree* tree = readTreeFromStdin(&costs, &alignments, &opts);
      if (tree == NULL) {
        continue;
      }
      if (tree->length == 0) {
        break;
      }
      trees.push_back(tree);
    }

    if (trees.size() > 0) {
      // Check if query is annotated with truth
      Tree* query = trees.back();
      trees.pop_back();
      fprintf(stderr, "%lu premise trees read\n", trees.size());
      // Run query
      double truth;
      string response =
          executeQuery(trees, kb, query, graph, costs, alignments, opts, &truth);
      // Print
      fprintf(stderr, "\n");
      fflush(stderr);
      printf("%s\n", response.c_str()); // Should be the only output to stdout
      fflush(stdout);
      fprintf(stderr, "\n");
      fflush(stderr);
    }

    // Clean up trees
    for (auto treeIter = trees.begin(); treeIter != trees.end();
         ++treeIter) {
      delete *treeIter;
    }
  }

  // Should never reach here!
  fprintf(stderr, "EOF on stdin -- exiting REPL\n");
  delete costs;
  return failedExamples;
}

/**
 * Close a given socket connection, either due to the request being completed,
 * or
 * to clean up after a failure.
 */
void closeConnection(const uint32_t socket, sockaddr_in *client) {
  // Close the connection
  fprintf(stderr, "  [%d] CONNECTION CLOSING: %s port %d\n", socket,
          inet_ntoa(client->sin_addr), ntohs(client->sin_port));
  if (shutdown(socket, SHUT_RDWR) != 0) {
    fprintf(stderr, "  Failed to shutdown connection ");
    fprintf(stderr, "  (");
    switch (errno) {
    case EBADF:
      fprintf(stderr, "The socket argument is not a valid file descriptor");
      break;
    case EINVAL:
      fprintf(stderr, "The socket is not accepting connections");
      break;
    case ENOTCONN:
      fprintf(stderr, "The socket is not connected");
      break;
    case ENOTSOCK:
      fprintf(stderr, "The socket argument does not refer to a socket");
      break;
    case ENOBUFS:
      fprintf(stderr, "No buffer space is available");
      break;
    default:
      fprintf(stderr, "???");
      break;
    }
    fprintf(stderr, ")\n");
  }
  close(socket);
  free(client);
  fflush(stdout);
}


//
// executeQuery() w/JavaBridge
//
string executeQuery(const JavaBridge *proc, const btree_set<uint64_t> *kb,
                    const vector<string> &knownFacts, const string &query,
                    const Graph *graph, const SynSearchCosts *costs,
                    const vector<AlignmentSimilarity>& alignments,
                    const syn_search_options &options,
                    double *truth) {
  // Collect trees
  vector<Tree*> canonicalTrees;  // the first tree from every premise
  vector<Tree*> fragments;       // the other trees from every premise
  for (auto iter = knownFacts.begin(); iter != knownFacts.end(); ++iter) {
    vector<Tree *> treesForFact = proc->annotatePremise(iter->c_str());
    auto treeIter = treesForFact.begin();
    canonicalTrees.push_back(*treeIter);
    ++treeIter;
    fragments.insert(fragments.end(), treeIter, treesForFact.end());
  }
  vector<Tree*> allTrees;
  allTrees.insert(allTrees.end(), canonicalTrees.begin(), canonicalTrees.end());
  allTrees.insert(allTrees.end(), fragments.begin(), fragments.end());
  // Parse the query
  const Tree *input = proc->annotateQuery(query.c_str());
  // Run the query
  string retval = executeQuery(allTrees, kb, input,
                               graph, costs, alignments, options, truth);
  // Clean up trees
  for (auto treeIter = allTrees.begin(); treeIter != allTrees.end();
       ++treeIter) {
    delete *treeIter;
  }
  return retval;
}

/**
 * Read a line from the socket, with a maximum length.
 * Effectively stolen from:
 *http://man7.org/tlpi/code/online/dist/sockets/read_line.c.html
 *
 * @param fd The socket to read from.
 * @param buffer The buffer to place the line into.
 * @param n The maximum number of characters to read.
 */
ssize_t readLine(uint32_t fd, char *buf, size_t n) {
  ssize_t numRead;
  size_t totRead;
  char ch;
  if (n <= 0 || buf == NULL) {
    errno = EINVAL;
    return -1;
  }
  totRead = 0;
  for (;;) {
    numRead = read(fd, &ch, 1);
    if (numRead == -1) {
      if (errno == EINTR) {
        continue;
      } else {
        return -1;
      }
    } else if (numRead == 0) {
      if (totRead == 0) {
        return 0;
      } else {
        break;
      }
    } else {
      if (totRead == 0 && ch == ' ') {
        continue;
      } else if (ch == '\n') {
        break;
      }
      if (totRead < n - 1) {
        totRead++;
        *buf++ = ch;
      }
    }
  }
  *buf = '\0';
  return totRead;
}

/**
 * Handle an incoming connection.
 * This involves reading a query, starting a new inference, and then
 * closing the connection.
 */
void handleConnection(const uint32_t &socket, sockaddr_in *client,
                      const JavaBridge *proc, const Graph *graph,
                      const btree_set<uint64_t> *kb) {

  // Initialize options
  SynSearchCosts* costs = intermediateNaturalLogicCosts();
  syn_search_options opts(100000,  // maxTicks
                          10000.0f, // costThreshold
                          false,    // stopWhenResultFound
                          true,     // checkFringe
                          false);   // silent

  // Parse input
  fprintf(stderr, "[%d] Reading query...\n", socket);
  char *buffer = (char *)malloc(32768 * sizeof(char));
  buffer[0] = '\0';
  readLine(socket, buffer, 32768);
  vector<string> knownFacts;
  vector<AlignmentSimilarity> alignments;
  while (buffer[0] != '\0' && buffer[0] != '\n' && buffer[0] != '\r' &&
         knownFacts.size() < 256) {
    if (buffer[0] != '%' || !parseMetadata(buffer, &costs, &alignments, &opts)) {
      fprintf(stderr, "  [%d] read line: %s\n", socket, buffer);
      knownFacts.push_back(string(buffer));
    }
    if (readLine(socket, buffer, 32768) == 0) {
      buffer[0] = '\0';
    }
  }
  fprintf(stderr, "  [%d] query read; running inference.\n", socket);

  // Parse query
  if (knownFacts.size() == 0) {
    free(buffer);
    closeConnection(socket, client);
    return;
  } else {
    string query = knownFacts.back();
    bool haveExpectedTruth = false;
    bool expectUnknown;
    bool expectedTruth;
    query = grokExpectedTruth(query, &haveExpectedTruth, &expectUnknown,
                              &expectedTruth);
    knownFacts.pop_back();
    double truth = 0.0;
    string json =
        executeQuery(proc, kb, knownFacts, query, graph, costs, alignments, opts, &truth);
    uint32_t failedExamples = 0;
    string passFail =
        passOrFail(truth, haveExpectedTruth, expectUnknown, expectedTruth, &failedExamples);
    write(socket, (passFail + json).c_str(), json.length() + passFail.length());
    fprintf(stderr, "  [%d] wrote output; I'm done.\n", socket);
  }

  //  if (!query.ParseFromFileDescriptor(socket)) { closeConnection(socket,
  //  client); return; }
  free(buffer);
  closeConnection(socket, client);
}

//
// startServer
//
bool startServer(const uint32_t &port, const JavaBridge *proc,
                 const Graph *graph, const btree_set<uint64_t> *kb) {
  // Get hostname, for debugging
  char hostname[256];
  gethostname(hostname, 256);

  // Create a socket
  int sock = socket(AF_INET, SOCK_STREAM, 0);
  if (sock < 0) {
    fprintf(stderr, "could not create socket\n");
    return 1;
  }
  int sockoptval = 1;
  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &sockoptval, sizeof(int));
  // Bind the socket
  // (setup the bind)
  struct sockaddr_in address;
  memset((char *)&address, 0, sizeof(address)); // zero our address
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_ANY);
  // (actually perform the bind)
  if (::bind(sock, (struct sockaddr *)&address, sizeof(address)) != 0) {
    fprintf(stderr, "Could not bind socket\n");
    return 10;
  } else {
    fprintf(stderr, "Opened server socket (hostname: %s)\n", hostname);
  }

  // set the socket for listening (queue backlog of 5)
  if (listen(sock, SERVER_TCP_BUFFER) < 0) {
    fprintf(stderr, "Could not open port for listening\n");
    return 100;
  } else {
    fprintf(stderr, "Listening on port %d...\n", port);
  }
  fflush(stdout);

  // loop, accepting connection requests
  for (;;) {
    // Accept an incoming connection
    // (variables)
    int requestSocket;
    struct sockaddr_in *clientAddress =
        (sockaddr_in *)malloc(sizeof(sockaddr_in));
    socklen_t requestLength = sizeof(clientAddress);
    // (connect)
    while ((requestSocket = accept(sock, (struct sockaddr *)clientAddress,
                                   &requestLength)) < 0) {
      // we may break out of accept if the system call was interrupted. In this
      // case, loop back and try again
      if ((errno != ECHILD) && (errno != ERESTART) && (errno != EINTR)) {
        // If we got here, there was a serious problem with the connection
        fprintf(stderr, "Failed to accept connection request\n");
        fprintf(stderr, "  (");
        switch (errno) {
        case EAGAIN:
          fprintf(stderr, "O_NONBLOCK is set for the socket file descriptor "
                          "and no connections are present to be accepted");
          break;
        case EBADF:
          fprintf(stderr, "The socket argument is not a valid file descriptor");
          break;
        case ECONNABORTED:
          fprintf(stderr, "A connection has been aborted");
          break;
        case EFAULT:
          fprintf(stderr, "The address or address_len parameter can not be "
                          "accessed or written");
          break;
        case EINTR:
          fprintf(stderr, "The accept() function was interrupted by a signal "
                          "that was caught before a valid connection arrived");
          break;
        case EINVAL:
          fprintf(stderr, "The socket is not accepting connections");
          break;
        case EMFILE:
          fprintf(stderr, "{OPEN_MAX} file descriptors are currently open in "
                          "the calling process");
          break;
        case ENFILE:
          fprintf(stderr, "The maximum number of file descriptors in the "
                          "system are already open");
          break;
        case ENOTSOCK:
          fprintf(stderr, "The socket argument does not refer to a socket");
          break;
        case EOPNOTSUPP:
          fprintf(stderr, "The socket type of the specified socket does not "
                          "support accepting connections");
          break;
        case ENOBUFS:
          fprintf(stderr, "No buffer space is available");
          break;
        case ENOMEM:
          fprintf(stderr, "There was insufficient memory available to complete "
                          "the operation");
          break;
        case ENOSR:
          fprintf(stderr, "There was insufficient STREAMS resources available "
                          "to complete the operation");
          break;
        case EPROTO:
          fprintf(stderr, "A protocol error has occurred; for example, the "
                          "STREAMS protocol stack has not been initialised");
          break;
        default:
          fprintf(stderr, "???");
          break;
        }
        fprintf(stderr, ")\n");
        free(clientAddress);
        return false;
      }
    }
    // Connection established
    fprintf(stderr, "[%d] CONNECTION ESTABLISHED: %s port %d\n", requestSocket,
            inet_ntoa(clientAddress->sin_addr), ntohs(clientAddress->sin_port));

    std::thread t(handleConnection, requestSocket, clientAddress, proc, graph,
                  kb);
    t.detach();
  }

  return true;
}
