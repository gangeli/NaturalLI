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

class Preprocessor {
 public:
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

  ~Preprocessor() {
    close(childIn);
    close(childOut);
    kill(this->pid, SIGTERM);
  }

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
  int childIn;
  int childOut;
  pid_t pid;
  mutex lock;
};


string executeQuery(Preprocessor& proc, const vector<string>& kb, const string& query,
                    const Graph* graph, const SynSearchCosts* costs, const syn_search_options& options) {
  // Create KB
  btree::btree_set<uint64_t> database;
  if (kb.size() == 0) {
    // TODO(gabor)
  } else {
    for (auto iter = kb.begin(); iter != kb.end(); ++iter) {
      const Tree* fact = proc.annotate(iter->c_str());
      database.insert(fact->hash());
      delete fact;
    }
  }

  // Create Query
  const Tree* input = proc.annotate(query.c_str());

  // Run Search
  const syn_search_response result = SynSearch(graph, database, input, costs, options);

  // Grok result
  char rtn[1024];
  snprintf(rtn, 1023, "{\"numResults\": %lu, \"totalTicks\": %lu}", 
           result.paths.size(), result.totalTicks);
  return rtn;
}

void repl(const Graph* graph, Preprocessor& proc) {
  const SynSearchCosts* costs = strictNaturalLogicCosts();
  syn_search_options opts = SynSearchOptions(10000,     // maxTicks
                                             10000.0f,  // costThreshold
                                             false,     // stopWhenResultFound
                                             false);    // silent

  fprintf(stderr, "--NaturalLI ready for input--\n\n");
  while (true) {
    // Process lines
    vector<string> lines;
    char line[256];
    memset(line, 0, sizeof(line));
    while (true) {
      std::cin.getline(line, 255);
      if (line[0] == '\0') { break; }
      lines.push_back(string(line));
    }

    if (lines.size() > 0) {
      // Run query
      string query = lines.back();
      lines.pop_back();
      string response =  executeQuery(proc, lines, query, graph, costs, opts);
      // Print
      fprintf(stderr, "\n");
      fflush(stderr);
      printf("%s\n", response.c_str());  // Should be the only output to stdout
      fflush(stdout);
      fprintf(stderr, "\n");
      fflush(stderr);
    }
  }
  
  // Should never reach here!
  fprintf(stderr, "How did we get here!!??\n");
  delete costs;
}


/**
 * The function to call for caught signals.
 * In practice, this is a NOOP.
 */
void signalHandler(int32_t s){
  printf("(caught signal %d)\n",s);
  printf("What do we say to the God of death?\n");
  printf("  \"Not today...\"\n");
}


/**
 * The server's entry point.
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
  repl(graph, proc);
}
