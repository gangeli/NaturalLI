#include <arpa/inet.h>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <mutex>
#include <netinet/in.h>
#include <signal.h>
#include <sstream>
#include <sys/errno.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <thread>
#include <unistd.h>

#include "config.h"
#include "SynSearch.h"
#include "Utils.h"
#include "JavaBridge.h"
#include "FactDB.h"

#ifndef SERVER_PORT
#define SERVER_PORT       1337
#endif

#define SERVER_TCP_BUFFER 25
#define SERVER_READ_SIZE  1024
#define MEM_ENV_VAR       "MAXMEM_GB"

#ifndef ERESTART
#define ERESTART EINTR
#endif
extern int errno;

using namespace std;
using namespace btree;

/**
 * Set a memory limit, if defined
 */
void setmemlimit() {
  struct rlimit memlimit;
  long bytes;
  if(getenv(MEM_ENV_VAR)!=NULL) {
    bytes = atol(getenv(MEM_ENV_VAR)) * (1024 * 1024 * 1024);
    memlimit.rlim_cur = bytes;
    memlimit.rlim_max = bytes;
    setrlimit(RLIMIT_AS, &memlimit);
  }
}



/**
 * Compute the confidence of a search response.
 * Note that this is truth independent at this point.
 *
 * @param response The search response.
 * @return A confidence value between 0 and 1/2;
 */
double confidence(const syn_search_response& response, 
                  const vector<SearchNode>** argmax) {
  if (response.size() == 0) { return 0.0; }
  double max = -std::numeric_limits<float>::infinity();
  for (uint64_t i = 0; i < response.paths.size(); ++i) {
    float cost = response.paths[i].cost;
    double confidence = 1.0 / (1.0 + exp(cost));
    if (confidence > max) {
      max = confidence;
      *argmax = &(response.paths[i].nodeSequence);
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
inline double probability(const double& confidence, const bool& truth) {
  if (truth) {
    return 0.5 + confidence;
  } else {
    return 0.5 - confidence;
  }
}

/**
 * Execute a query, returning a JSON formatted response.
 *
 * @param proc The preprocessor to use to annotate the query string(s).
 * @param kb The [optionally empty] large knowledge base to evaluate against.
 * @param knownFacts An optional knowledge base to use to augment the facts in kb.
 * @param query The query to execute against the knowledge base.
 * @param graph The graph of valid edge instances which can be taken.
 * @param costs The search costs to use for this query
 * @param options The options for the search, to be passed along directly.
 * @param truth [output] The probability that the query is true, as just a simple
 *                       float value.
 *
 * @return A JSON formatted response with the result of the search.
 */
string executeQuery(const JavaBridge* proc, const btree_set<uint64_t>* kb,
                    const vector<string>& knownFacts, const string& query,
                    const Graph* graph, const SynSearchCosts* costs,
                    const syn_search_options& options, double* truth) {
  // Create KB
  btree_set<uint64_t> auxKB;
  uint32_t factsInserted = 0;
  for (auto iter = knownFacts.begin(); iter != knownFacts.end(); ++iter) {
    vector<Tree*> treesForFact = proc->annotatePremise(iter->c_str());
    for (auto treeIter = treesForFact.begin(); treeIter != treesForFact.end(); ++treeIter) {
      Tree* premise = *treeIter;
      const uint64_t hash = SearchNode(*premise).factHash();
      printTime("[%c] ");
      fprintf(stderr, "|KB| adding premise with hash: %lu\n", hash);
      auxKB.insert(hash);
      factsInserted += 1;
      delete premise;
    }
  }
  printTime("[%c] ");
  fprintf(stderr, "|KB| %lu premise(s) added, yielding %u total facts\n",
          knownFacts.size(), factsInserted);

  // Create Query
  const Tree* input = proc->annotateQuery(query.c_str());
  if (input == NULL) {
    *truth = 0.0;
    return "{\"success\": false, \"reason\": \"hypothesis (query) is too long\"}";
  }

  // Run Search
  const syn_search_response resultIfTrue
    = SynSearch(graph, kb, auxKB, input, costs, true, options);
  const syn_search_response resultIfFalse
    = SynSearch(graph, kb, auxKB, input, costs, false, options);

  // Grok result
  // (confidence)
  const vector<SearchNode>* bestPathIfTrue = NULL;
  const vector<SearchNode>* bestPathIfFalse = NULL;
  double confidenceOfTrue = confidence(resultIfTrue, &bestPathIfTrue);
  double confidenceOfFalse = confidence(resultIfFalse, &bestPathIfFalse);
  // (truth)
  const vector<SearchNode>* bestPath = NULL;
  if (confidenceOfTrue > confidenceOfFalse) {
    *truth = probability(confidenceOfTrue, true);
    bestPath = bestPathIfTrue;
  } else if (confidenceOfTrue < confidenceOfFalse) {
    *truth = probability(confidenceOfFalse, false);
    bestPath = bestPathIfFalse;
  } else {
    if (resultIfTrue.paths.size() > 0 && resultIfFalse.paths.size() > 0) {
      printTime("[%c] ");
      fprintf(stderr, "\033[1;33mWARNING:\033[0m Exactly the same max confidence returned for both 'true' and 'false'");
      fprintf(stderr, "  if true: %s\n", toJSON(*graph, *input, *bestPathIfTrue).c_str());
      fprintf(stderr, "  if false: %s\n", toJSON(*graph, *input, *bestPathIfFalse).c_str());
    }
    *truth = 0.5;
  }
//  fprintf(stderr, "trueCount: %lu  falseCount: %lu  trueConf: %f  falseConf: %f  truth:  %f\n",
//         resultIfTrue.size(), resultIfFalse.size(),
//         confidenceOfTrue, confidenceOfFalse, *truth);
  // (sanity check)
  if (*truth < 0.0) { *truth = 0.0; }
  if (*truth > 1.0) { *truth = 1.0; }

  // Generate JSON
  stringstream rtn;
  rtn << "{\"numResults\": " << (resultIfTrue.size() + resultIfFalse.size()) << ", "
      <<  "\"totalTicks\": " << (resultIfTrue.totalTicks + resultIfFalse.totalTicks) << ", "
      <<  "\"truth\": " << (*truth) << ", "
      <<  "\"query\": \"" << escapeQuote(query) << "\", "
      <<  "\"bestPremise\": " << (bestPath == NULL ? "null" : ("\"" + escapeQuote(kbGloss(*graph, *input, *bestPath)) + "\"")) << ", "
      <<  "\"success\": true" << ", "
      <<  "\"path\": " << (bestPath != NULL ? toJSON(*graph, *input, *bestPath) : "[]")
      << "}";
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
string grokExpectedTruth(string query, 
                         bool* haveExpectedTruth, 
                         bool* expectUnknown,
                         bool* expectedTruth ) {
  *haveExpectedTruth = false;
  if (query.substr(0,5) == "TRUE:") {
    *haveExpectedTruth = true;
    *expectedTruth = true;
    query = query.substr(5);
    while (query.at(0) == ' ' || query.at(0) == '\t') { query = query.substr(1); }
  } else if (query.substr(0,6) == "FALSE:") {
    *haveExpectedTruth = true;
    *expectedTruth = false;
    query = query.substr(6);
    while (query.at(0) == ' ' || query.at(0) == '\t') { query = query.substr(1); }
  } else if (query.substr(0,4) == "UNK:") {
    *haveExpectedTruth = true;
    *expectUnknown = true;
    query = query.substr(4);
    while (query.at(0) == ' ' || query.at(0) == '\t') { query = query.substr(1); }
  }
  return query;
}

/**
 * Parse the actual and expected response of a query, and print it as
 * a String prefix. This code is shared between the REPL and the server.
 */
string passOrFail(const double& truth,
                  const bool& haveExpectedTruth, 
                  const bool& expectUnknown,
                  const bool& expectedTruth) {
  if (haveExpectedTruth) {
    if (expectUnknown) {
      if (truth == 0.5) {
        return "PASS: ";
      } else {
        return "FAIL: ";
      }
    } else {
      if ((truth > 0.5 && expectedTruth) || (truth < 0.5 && !expectedTruth)) { 
        return "PASS: ";
      } else { 
        return "FAIL: ";
      }
    }
  }
  return "";
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
 * @param kb The main [i.e., large] knowledge base to use, possibly empty.
 *
 * @return The number of failed examples, if any were annotated. 0 by default.
 */
uint32_t repl(const Graph* graph, JavaBridge* proc,
              const btree_set<uint64_t>* kb) {
  uint32_t failedExamples = 0;
  const SynSearchCosts* costs = strictNaturalLogicCosts();
  syn_search_options opts(1000000,     // maxTicks
                          10000.0f,    // costThreshold
                          false,       // stopWhenResultFound
                          true,        // checkFringe
                          false);      // silent

  fprintf(stderr, "REPL is ready for text (maybe still waiting on CBridge)\n");
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
      bool expectUnknown = false;
      query = grokExpectedTruth(query, &haveExpectedTruth, &expectUnknown, &expectedTruth);
      // Run query
      double truth;
      string response = executeQuery(proc, kb, lines, query, graph, costs, opts, &truth);
      // Print
      fprintf(stderr, "\n");
      fflush(stderr);
      printf("%s", passOrFail(truth, haveExpectedTruth, expectUnknown, expectedTruth).c_str());
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
  fprintf(stderr, "(caught signal %d; use 'kill' or EOF to end the program)\n",s);
  fprintf(stderr, "What do we say to the God of death?\n");
  fprintf(stderr, "  \"Not today...\"\n");
}
  

/**
 * Close a given socket connection, either due to the request being completed, or
 * to clean up after a failure.
 */
void closeConnection(const uint32_t socket, sockaddr_in* client) {
  // Close the connection
  fprintf(stderr, "[%d] CONNECTION CLOSING: %s port %d\n", socket,
		     inet_ntoa(client->sin_addr),
         ntohs(client->sin_port));
  if (shutdown(socket, SHUT_RDWR) != 0) {
    fprintf(stderr, "  Failed to shutdown connection ");
    fprintf(stderr, "  (");
    switch (errno) {
      case EBADF:    fprintf(stderr, "The socket argument is not a valid file descriptor"); break;
      case EINVAL:   fprintf(stderr, "The socket is not accepting connections"); break;
      case ENOTCONN: fprintf(stderr, "The socket is not connected"); break;
      case ENOTSOCK: fprintf(stderr, "The socket argument does not refer to a socket"); break;
      case ENOBUFS:  fprintf(stderr, "No buffer space is available"); break;
      default:       fprintf(stderr, "???"); break;
    }
    fprintf(stderr, ")\n");
  }
  close(socket);
  free(client);
  fflush(stdout);
}

/**
 * Read a line from the socket, with a maximum length.
 * Effectively stolen from: http://man7.org/tlpi/code/online/dist/sockets/read_line.c.html
 *
 * @param fd The socket to read from.
 * @param buffer The buffer to place the line into.
 * @param n The maximum number of characters to read.
 */
ssize_t readLine(uint32_t fd, char* buf, size_t n) {
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
void handleConnection(const uint32_t& socket, sockaddr_in* client,
                      const JavaBridge* proc,
                      const Graph* graph, const btree_set<uint64_t>* kb) {

  // Parse input
  fprintf(stderr, "[%d] Reading query...\n", socket);
  char* buffer = (char*) malloc(32768 * sizeof(char));
  buffer[0] = '\0';
  readLine(socket, buffer, 32768);
  vector<string> knownFacts;
  while ( buffer[0] != '\0' && buffer[0] != '\n' && buffer[0] != '\r' && 
          knownFacts.size() < 256 ) {
    fprintf(stderr, "  [%d] read line: %s\n", socket, buffer);
    knownFacts.push_back(string(buffer));
    if (readLine(socket, buffer, 32768) == 0) {
      buffer[0] = '\0';
    }
  }
  fprintf(stderr, "  [%d] query read; running inference.\n", socket);

  // Parse options
  const SynSearchCosts* costs = strictNaturalLogicCosts();
  syn_search_options opts(1000000,     // maxTicks
                          10000.0f,    // costThreshold
                          false,       // stopWhenResultFound
                          false,       // checkFringe  TODO(gabor) make me a parameter
                          false);      // silent

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
    query = grokExpectedTruth(query, &haveExpectedTruth, &expectUnknown, &expectedTruth);
    knownFacts.pop_back();
    double truth = 0.0;
    string json = executeQuery(proc, kb, knownFacts, query, graph, costs, opts, &truth);
    string passFail = passOrFail(truth, haveExpectedTruth, expectUnknown, expectedTruth);
    write(socket, (passFail + json).c_str(), json.length() + passFail.length());
    fprintf(stderr, "  [%d] wrote output; I'm done.\n", socket);
  }

//  if (!query.ParseFromFileDescriptor(socket)) { closeConnection(socket, client); return; }
  free(buffer);
  closeConnection(socket, client);
}

/**
 * Set up listening on a server port.
 */
bool startServer(const uint32_t& port, 
                 const JavaBridge* proc, const Graph* graph,
                 const btree_set<uint64_t>* kb) {
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
  memset( (char*) &address, 0, sizeof(address) );  // zero our address
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_ANY);
  // (actually perform the bind)
  if (::bind(sock, (struct sockaddr*) &address, sizeof(address)) != 0) {
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
	  struct sockaddr_in* clientAddress = (sockaddr_in*) malloc(sizeof(sockaddr_in));
    socklen_t requestLength = sizeof(clientAddress);
    // (connect)
		while ((requestSocket = accept(sock, (struct sockaddr*) clientAddress, &requestLength)) < 0) {
			// we may break out of accept if the system call was interrupted. In this
      // case, loop back and try again
      if ((errno != ECHILD) && (errno != ERESTART) && (errno != EINTR)) {
        // If we got here, there was a serious problem with the connection
        fprintf(stderr, "Failed to accept connection request\n");
        fprintf(stderr, "  (");
        switch (errno) {
          case EAGAIN:        fprintf(stderr,"O_NONBLOCK is set for the socket file descriptor and no connections are present to be accepted"); break;
          case EBADF:         fprintf(stderr,"The socket argument is not a valid file descriptor"); break;
          case ECONNABORTED:  fprintf(stderr,"A connection has been aborted"); break;
          case EFAULT:        fprintf(stderr,"The address or address_len parameter can not be accessed or written"); break;
          case EINTR:         fprintf(stderr,"The accept() function was interrupted by a signal that was caught before a valid connection arrived"); break;
          case EINVAL:        fprintf(stderr,"The socket is not accepting connections"); break;
          case EMFILE:        fprintf(stderr,"{OPEN_MAX} file descriptors are currently open in the calling process"); break;
          case ENFILE:        fprintf(stderr,"The maximum number of file descriptors in the system are already open"); break;
          case ENOTSOCK:      fprintf(stderr,"The socket argument does not refer to a socket"); break;
          case EOPNOTSUPP:    fprintf(stderr,"The socket type of the specified socket does not support accepting connections"); break;
          case ENOBUFS:       fprintf(stderr,"No buffer space is available"); break;
          case ENOMEM:        fprintf(stderr,"There was insufficient memory available to complete the operation"); break;
          case ENOSR:         fprintf(stderr,"There was insufficient STREAMS resources available to complete the operation"); break;
          case EPROTO:        fprintf(stderr,"A protocol error has occurred; for example, the STREAMS protocol stack has not been initialised"); break;
          default:            fprintf(stderr,"???"); break;
        }
        fprintf(stderr, ")\n");
        free(clientAddress);
        return false;
      } 
    }
		// Connection established
    fprintf(stderr, "[%d] CONNECTION ESTABLISHED: %s port %d\n", requestSocket,
			     inet_ntoa(clientAddress->sin_addr),
           ntohs(clientAddress->sin_port));

    std::thread t(handleConnection, requestSocket, clientAddress, proc, graph, kb);
    t.detach();
	}

  free(clientAddress);
  return true;
}


/**
 * The Entry point for querying the truth of facts.
 */
int32_t main( int32_t argc, char *argv[] ) {
  // Handle signals
  // (set memory limit)
  setmemlimit();
  // (set up handler)
  struct sigaction sigIntHandler;
  sigIntHandler.sa_handler = signalHandler;
  sigemptyset(&sigIntHandler.sa_mask);
  sigIntHandler.sa_flags = 0;
  // (catch signals)
//  sigaction(SIGINT,  &sigIntHandler, NULL);  // Stopping SIGINT causes the java process to die
  sigaction(SIGPIPE, &sigIntHandler, NULL);

  // Read the knowledge base
  const btree_set<uint64_t>* kb;
  if (KB_FILE[0] != '\0') {
    kb = readKB(string(KB_FILE));
  } else {
    kb = new btree_set<uint64_t>;
    fprintf(stderr, "No knowledge base given (configure with KB_FILE=/path/to/kb)\n");
  }

  // Create bridge
  JavaBridge* proc = new JavaBridge();
  // Load graph
  Graph* graph = ReadGraph();
    
  // Start server
  std::thread t(startServer, SERVER_PORT, proc, graph, kb);
  t.detach();
  
  // Start REPL
  uint32_t retVal =  repl(graph, proc, kb);
  delete graph;
  delete proc;
  delete kb;
  return retVal;
}
