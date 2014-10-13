#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <signal.h>
#include <iostream>
#include <iomanip>
#include <mutex>
#include <unistd.h>

#include "config.h"
#include "SynSearch.h"

using namespace std;

/**
 * The interface for the Java pre-processor, responsible for
 * annotating the raw text of a sentence into a dependency parse, already
 * indexed and marked with quantifiers and quantifier properties.
 */
class Preprocessor {
 public:
  /**
   * Create a bidirectional pipe to the Java program, and start the program
   */
  Preprocessor() {
    // Set up subprocess
    pid_t pid;
    int pipeIn[2];
    int pipeOut[2];
    pipe(pipeIn);
    pipe(pipeOut);
    pid = fork();
    if (pid == 0) {
  
      // --Child is running here--
      // Get directory of running executable
      char thisPathBuffer[256];
      memset(thisPathBuffer, 0, sizeof(thisPathBuffer));
      readlink("/proc/self/exe", thisPathBuffer, 255);
      string thisPath = string(thisPathBuffer);
      int lastSlash = thisPath.find_last_of("/");
      string thisDir = thisPath.substr(0, lastSlash);
      // Set up paths
      char javaExecutable[128];
      snprintf(javaExecutable, 127, "%s/bin/java", JDK_HOME);
      std::string javaClass = "edu.stanford.nlp.naturalli.Preprocess";
      char wordnetEnv[128];
      snprintf(wordnetEnv, 127, "-Dwordnet.database.dir=%s", WORDNET_DICT);
      char classpath[1024];
      snprintf(classpath, 1024, "%s/naturalli_client.jar:%s/jaws.jar:%s/../lib/jaws.jar:%s:%s", 
          thisDir.c_str(), thisDir.c_str(), thisDir.c_str(),
          CORENLP, CORENLP_MODELS);
      // Start program
      dup2(pipeIn[0], STDIN_FILENO);
      dup2(pipeOut[1], STDOUT_FILENO);
      execl(javaExecutable, javaExecutable, "-mx1g", wordnetEnv, "-cp", classpath,
          javaClass.c_str(), (char*) NULL);
      // Should never reach here
      exit(1);
  
    } else {
      // --Parent is running here--
      this->childIn = pipeIn[1];
      this->childOut = pipeOut[0];
      this->pid = pid;
    }
  }

  /**
   * Kill the subprocess program and close the pipes.
   */
  ~Preprocessor() {
    close(childIn);
    close(childOut);
    kill(this->pid, SIGTERM);
  }

  /**
   * Annotate a given sentence into a Tree object which can be used 
   * for search
   */
  Tree* annotate(const char* sentence) {
    // Write sentence
    int sentenceLength = strlen(sentence);
    this->lock.lock();
    if (sentence[sentenceLength - 1] != '\n') {
      char buffer[sentenceLength + 2];
      memcpy(buffer, sentence, sentenceLength * sizeof(char));
      buffer[sentenceLength] = '\n';
      buffer[sentenceLength + 1] = '\0';
      write(this->childIn, buffer, strlen(buffer));
    } else {
      write(this->childIn, sentence, strlen(sentence));
    }

    // Read tree
    int numNewlines = 0;
    string conll = "";
    char c;
    while (numNewlines < 2) {
      if (read(this->childOut, &c, 1) > 0) {
        conll.append(&c, 1);
        if (c == '\n') {
          numNewlines += 1;
        } else {
          numNewlines = 0;
        }
      }
    }
    this->lock.unlock();
    return new Tree(conll);
  }

 private:
  /** The file descriptor of stdin of the child process */
  int childIn;
  /** The file descriptor of stdout of the child process */
  int childOut;
  /** The process id of the child process */
  pid_t pid;
  /** A lock, to make sure we're not making concurrent calls to the annotator */
  mutex lock;
};

const char* escapeQuote(const string& input) {
  size_t index = 0;
  string str = string(input.c_str());
  while (true) {
    /* Locate the substring to replace. */
    index = str.find("\"", index);
    if (index == string::npos) break;
    /* Make the replacement. */
    str.replace(index, 1, "\\\"");
    /* Advance index forward so the next iteration doesn't pick it up as well. */
    index += 2;
  }
  return str.c_str();
}


/**
 * Execute a query, returning a JSON formatted response.
 *
 * @param proc The preprocessor to use to annotate the query string(s).
 * @param kb An optional knowledge base to use in place of the default knowledge base.
 * @param query The query to execute against the knowledge base.
 * @param graph The graph of valid edge instances which can be taken.
 * @param costs The search costs to use for this query
 * @param options The options for the search, to be passed along directly.
 * @param truth [output] The probability that the query is true, as just a simple
 *                       float value.
 *
 * @return A JSON formatted response with the result of the search.
 */
string executeQuery(Preprocessor& proc, const vector<string>& kb, const string& query,
                    const Graph* graph, const SynSearchCosts* costs,
                    const syn_search_options& options, double* truth) {
  // Create KB
  btree::btree_set<uint64_t> database;
  if (kb.size() == 0) {
    // TODO(gabor)
  } else {
    for (auto iter = kb.begin(); iter != kb.end(); ++iter) {
      const Tree* fact = proc.annotate(iter->c_str());
      database.insert(fact->hash());
      fprintf(stderr, "hash: %lu\n", fact->hash());
      delete fact;
    }
  }

  // Create Query
  const Tree* input = proc.annotate(query.c_str());

  // Run Search
  const syn_search_response result = SynSearch(graph, database, input, costs, options);

  // Grok result
  // TOOD(gabor) real truth calculation
  if (result.paths.size() > 0) {
    *truth = 1.0;
  } else {
    *truth = 0.0;
  }
  // Generate JSON
  char rtn[4096];
  snprintf(rtn, 1023, "{\"numResults\": %lu, \"totalTicks\": %lu, \"truth\": %f, \"query\": \"%s\"}", 
           result.paths.size(), result.totalTicks, *truth, escapeQuote(query));
  return rtn;
}

/**
 * Start a REPL loop over stdin and stdout.
 * Each query consists of a number of entries (possibly zero) in the knowledge
 * base, seperated by newlines, and then a query.
 * A double newline signals the end of a block, where the first k-1 lines are the
 * knowledge base, and the last line is the query.
 *
 * @param graph The graph containing the edge instances we can traverse.
 * @param proc The preprocessor to use during the search.
 *
 * @return The number of failed examples, if any were annotated. 0 by default.
 */
uint32_t repl(const Graph* graph, Preprocessor& proc) {
  uint32_t failedExamples = 0;
  const SynSearchCosts* costs = strictNaturalLogicCosts();
  syn_search_options opts = SynSearchOptions(1000000,     // maxTicks
                                             10000.0f,    // costThreshold
                                             false,       // stopWhenResultFound
                                             false);      // silent

  fprintf(stderr, "--NaturalLI ready for input--\n\n");
  while (!cin.fail()) {
    // Process lines
    vector<string> lines;
    char line[256];
    memset(line, 0, sizeof(line));
    while (!cin.fail()) {
      cin.getline(line, 255);
      if (line[0] == '\0') { break; }
      if (line[0] != '#') {
        lines.push_back(string(line));
      }
    }

    if (lines.size() > 0) {
      // Check if query is annotated with truth
      string query = lines.back();
      lines.pop_back();
      bool haveExpectedTruth = false;
      bool expectedTruth;
      if (query.substr(0,5) == "TRUE:") {
        haveExpectedTruth = true;
        expectedTruth = true;
        query = query.substr(5);
        while (query.at(0) == ' ' || query.at(0) == '\t') { query = query.substr(1); }
      } else if (query.substr(0,6) == "FALSE:") {
        haveExpectedTruth = true;
        expectedTruth = false;
        query = query.substr(6);
        while (query.at(0) == ' ' || query.at(0) == '\t') { query = query.substr(1); }
      }
      // Run query
      double truth;
      string response =  executeQuery(proc, lines, query, graph, costs, opts, &truth);
      // Print
      fprintf(stderr, "\n");
      fflush(stderr);
      if (haveExpectedTruth) {
        if ((truth > 0.5 && expectedTruth) || (truth < 0.5 && !expectedTruth)) { 
          printf("PASS: ");
        } else { 
          printf("FAIL: ");
          failedExamples += 1;
        }
      }
      printf("%s\n", response.c_str());  // Should be the only output to stdout
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


/**
 * The function to call for caught signals. In practice, this is a NOOP.
 */
void signalHandler(int32_t s){
  printf("(caught signal %d; use 'kill' or EOF to end the program)\n",s);
  printf("What do we say to the God of death?\n");
  printf("  \"Not today...\"\n");
}


/**
 * The Entry point.
 */
int32_t main( int32_t argc, char *argv[] ) {
  // Handle signals
  // (set up handler)
  struct sigaction sigIntHandler;
  sigIntHandler.sa_handler = signalHandler;
  sigemptyset(&sigIntHandler.sa_mask);
  sigIntHandler.sa_flags = 0;
  // (catch signals)
//  sigaction(SIGINT,  &sigIntHandler, NULL);
//  sigaction(SIGPIPE, &sigIntHandler, NULL);

  // Start REPL
  Preprocessor proc;
  Graph* graph = ReadGraph();
  return repl(graph, proc);
}
