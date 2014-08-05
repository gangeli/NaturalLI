#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <exception>
#include <thread>
#include <mutex>
#include <signal.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/errno.h>
#include <sys/resource.h>
#include <arpa/inet.h>
#include <unistd.h>

#include "Messages.pb.h" 
#include "Search.h" 
#include "Graph.h" 
#include "Trie.h" 
#include "Utils.h" 


#ifndef SERVER_PORT
#define SERVER_PORT       1337
#define SERVER_TCP_BUFFER 25
#define SERVER_READ_SIZE  1024
#define MEM_ENV_VAR       "MAXMEM_GB"
#endif

#ifndef ERESTART
#define ERESTART EINTR
#endif
extern int errno;

std::mutex factDBReadLock;

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
 * Create a very simple FactDB based on an input query.
 */
FactDB* makeFactDB(Query& query, Graph* graph) {
  Trie* facts = new TrieRoot();
  uint32_t size = query.knownfact_size();
  for (int factI = 0; factI < size; ++factI) {
    uint32_t factLength = query.knownfact(factI).word_size();
    edge fact[factLength];
    for (int wordI = 0; wordI < factLength; ++wordI) {
      Word word = query.knownfact(factI).word(wordI);
      // Set the word
      fact[wordI].source = word.word();
      // Set the sense
      if (word.has_sense()) {
        fact[wordI].source_sense = word.sense();
      } else {
        fact[wordI].source_sense = 0;
      }
      fact[wordI].sink = 0;
      fact[wordI].sink_sense = 0;
      // Set the edge type
      if (query.knownfact(factI).word(wordI).has_pos()) {
        const char* posTag = query.knownfact(factI).word(wordI).pos().c_str();
        if (posTag != NULL && (posTag[0] == 'a' || posTag[0] == 'A')) {
          fact[wordI].type = DEL_UNIVERSAL;
        } else if (posTag != NULL && (posTag[0] == 'e' || posTag[0] == 'E')) {
          fact[wordI].type = DEL_EXISTENTIAL;       // existential
        } else if (posTag != NULL && (posTag[0] == 'm' || posTag[0] == 'M')) {
          fact[wordI].type = DEL_QUANTIFIER_OTHER;  // most, etc.
        } else if (posTag != NULL && (posTag[0] == 'f' || posTag[0] == 'f')) {
          fact[wordI].type = DEL_QUANTIFIER_OTHER;  // few, etc.
        } else if (posTag != NULL && (posTag[0] == 'a' || posTag[0] == 'A')) {
          fact[wordI].type = DEL_UNIVERSAL;         // forall
        } else if (posTag != NULL && (posTag[0] == 'g' || posTag[0] == 'G')) {
          fact[wordI].type = DEL_NEGATION;          // negation
        } else if (posTag != NULL && (posTag[0] == 'n' || posTag[0] == 'N')) {
          fact[wordI].type = DEL_NOUN;              // nouns
        } else if (posTag != NULL && (posTag[0] == 'v' || posTag[0] == 'V')) {
          fact[wordI].type = DEL_VERB;              // verbs
        } else if (posTag != NULL && (posTag[0] == 'j' || posTag[0] == 'J')) {
          fact[wordI].type = DEL_ADJ;               // adjectives
        } else if (posTag != NULL && posTag[0] == '?') {
          fact[wordI].type = DEL_OTHER;             // seriously, who needs _all_ the PTB tags :/
        } else {
          printf("Invalid POS tag: %s", posTag);
          std::exit(1);
        }
      } else {
        fact[wordI].type = DEL_OTHER;
      }
      fact[wordI].cost = 1.0f;  // not really relevant...
    }
    // Add the fact
    facts->add(fact, factLength, graph);
  }
  return facts;
}


/**
 * Close a given socket connection, either due to the request being completed, or
 * to clean up after a failure.
 */
void closeConnection(int socket, sockaddr_in* client) {
  // Close the connection
  printf("[%d] CONNECTION CLOSING: %s port %d\n", socket,
		     inet_ntoa(client->sin_addr),
         ntohs(client->sin_port));
  if (shutdown(socket, SHUT_RDWR) != 0) {
    printf("Failed to shutdown connection\n");
    printf("  (");
    switch (errno) {
      case EBADF: printf("The socket argument is not a valid file descriptor"); break;
      case EINVAL: printf("The socket is not accepting connections"); break;
      case ENOTCONN: printf("The socket is not connected"); break;
      case ENOTSOCK: printf("The socket argument does not refer to a socket"); break;
      case ENOBUFS: printf("No buffer space is available"); break;
      default: printf("???"); break;
    }
    printf(")\n");
  }
  close(socket);
  free(client);
  fflush(stdout);
}

/**
 * Convert a (concise) path object into an Inference object to be passed over the wire
 * to the client.
 */
Inference inferenceFromPath(const Path* path,
                            const Graph* graph,
                            const float& score,
                            const uint8_t& rootPolarity,
                            const Inference* child,
                            const Path* pathChild) {
  Inference node;

  // -- Populate fact
  Fact fact;
  for (int i = 0; i < path->factLength; ++i) {
    Word word;
    word.set_word(path->fact[i].word);
    fact.add_word()->CopyFrom(word);
  }
  fact.set_gloss(toString(*graph, path->fact, path->factLength));
  node.mutable_fact()->CopyFrom(fact);

  // -- Set backpointer
  if (pathChild != NULL) {
    if (pathChild->nodeState.incomingEdge != NULL_EDGE_TYPE) {
      node.set_incomingedgetype(pathChild->nodeState.incomingEdge);
    }
    node.set_incomingedgecost(pathChild->nodeState.incomingCost);
    switch (pathChild->fact[pathChild->lastMutationIndex].monotonicity) {
      case MONOTONE_UP:
        node.set_monotonecontext(Monotonicity::UP);
        break;
      case MONOTONE_DOWN:
        node.set_monotonecontext(Monotonicity::DOWN);
        break;
      case MONOTONE_FLAT:
        node.set_monotonecontext(Monotonicity::FLAT);
        break;
      default:
      printf("Invalid monotonicity: %u", pathChild->fact[pathChild->lastMutationIndex].monotonicity);
      std::exit(1);
      break;
    }
    if (pathChild->nodeState.incomingEdge >= MONOTONE_INDEPENDENT_BEGIN) {
      node.set_monotonecontext(Monotonicity::ANY_OR_INVALID);
    }
  }
  
  // -- Set truth state
  switch (path->nodeState.truth) {
    case INFER_TRUE:
      switch (rootPolarity) {
        case INFER_TRUE:
          node.set_state(CollapsedInferenceState::TRUE);
          break;
        case INFER_FALSE:
          node.set_state(CollapsedInferenceState::FALSE);
          break;
        case INFER_UNKNOWN:
          node.set_state(CollapsedInferenceState::TRUE);
          break;
        default:
          printf("Invalid inference state: %u", path->nodeState.truth);
          std::exit(1);
          break;
      }
      break;
    case INFER_FALSE:
      switch (rootPolarity) {
        case INFER_TRUE:
          node.set_state(CollapsedInferenceState::FALSE);
          break;
        case INFER_FALSE:
          node.set_state(CollapsedInferenceState::TRUE);
          break;
        case INFER_UNKNOWN:
          node.set_state(CollapsedInferenceState::FALSE);
          break;
        default:
          printf("Invalid inference state: %u", path->nodeState.truth);
          std::exit(1);
          break;
      }
      break;
    case INFER_UNKNOWN:
      node.set_state(CollapsedInferenceState::UNKNOWN);
      break;
    default:
      printf("Invalid inference state: %u", path->nodeState.truth);
      std::exit(1);
      break;
  }
  
  // -- Set inferred from
  if (child != NULL) {
    node.mutable_impliedfrom()->CopyFrom(*child);
  }

  // -- Handle Cases
  if (path->parent == NULL) {
    // Base case: "start" of the inference; "end" of the reverse path
    return node;
  } else {
    // Recursive case: reverse the rest of the path
    return inferenceFromPath(path->parent, graph, score, rootPolarity, &node, path);
  }
}

/**
 * A simple utility to convert a proto costs file to unigram edge costs
 */
float* mkCosts(const UnlexicalizedCosts& proto, const bool& trueCost) {
  assert (proto.truecost_size() == proto.falsecost_size());
  float* costs = (float*) malloc(proto.truecost_size() * sizeof(float));
  for (int i = 0; i < proto.truecost_size(); ++i) {
    costs[i] = (trueCost ? proto.truecost(i) : proto.falsecost(i));
  }
  return costs;
}

  
/**
 * Handle an incoming connection.
 * This involves reading a query, starting a new inference, and then
 * closing the connection.
 */
void handleConnection(int socket, sockaddr_in* client,
                      Graph* graph, FactDB** dbOrNull) {

  // Parse Query
  printf("[%d] Reading query...\n", socket);
  Query query;
  if (!query.ParseFromFileDescriptor(socket)) { closeConnection(socket, client); return; }
  printf("[%d] ...read query.\n", socket);
  if (query.shutdownserver()) {
    // Clean up memory (for Valgrind)
    delete graph;
    delete *dbOrNull;
    closeConnection(socket, client);
    // Exit
    printf("\n");
    printf("--------------\n");
    printf("STOPPED SERVER\n");
    printf("--------------\n");
    std::exit(0);
  }

  // Construct weights
  CostVector* costs;
  if (query.has_costs() &&
      query.costs().has_unlexicalizedmonotoneup() &&
      query.costs().has_unlexicalizedmonotonedown() &&
      query.costs().has_unlexicalizedmonotoneflat() &&
      query.costs().has_unlexicalizedmonotoneany() ) {
    costs = new CostVector(
      mkCosts(query.costs().unlexicalizedmonotoneup(),   true),
      mkCosts(query.costs().unlexicalizedmonotoneup(),   false),
      mkCosts(query.costs().unlexicalizedmonotonedown(), true),
      mkCosts(query.costs().unlexicalizedmonotonedown(), false),
      mkCosts(query.costs().unlexicalizedmonotoneflat(), true),
      mkCosts(query.costs().unlexicalizedmonotoneflat(), false),
      mkCosts(query.costs().unlexicalizedmonotoneany(),  true),
      mkCosts(query.costs().unlexicalizedmonotoneany(),  false)
      );
  } else {
    costs = new CostVector();
  }

  // Run Search
  // (prepare factdb)
  FactDB* factDB = *dbOrNull;
  if (query.userealworld()) {
    if (factDB == NULL) {
      // Read the real knowledge base
      factDBReadLock.lock();
      if (*dbOrNull == NULL) {
//        *dbOrNull = ReadOldFactTrie(graph);
        *dbOrNull = ReadFactTrie(graph);
        factDB = *dbOrNull;
      }
      factDBReadLock.unlock();
    }
    printf("[%d] created real factdb.\n", socket);
  } else {
    // Read a dummy knowledge base
    factDB = makeFactDB(query, graph);
    printf("[%d] created mock factdb.\n", socket);
  }
  // (create query)
  uint8_t queryLength = query.queryfact().word_size();
  uint8_t monotoneBoundary = query.queryfact().monotoneboundary();
  tagged_word queryFact[queryLength];
  for (int i = 0; i < queryLength; ++i) {
    if (query.queryfact().word(i).monotonicity() < 0 ||
        query.queryfact().word(i).monotonicity() > 3) {
    // case: invalid monotonicity markings
    printf("[%d] invalid monotonicity marking: %d\n", socket, query.queryfact().word(i).monotonicity());
    if (!query.userealworld()) { delete factDB; }
    delete costs;
    closeConnection(socket, client);
    return;
      
    }
    queryFact[i] = getTaggedWord(
      query.queryfact().word(i).word(),
      query.queryfact().word(i).sense(),
      query.queryfact().word(i).monotonicity() );
  }
  printf("[%d] constructed query.\n", socket);
  // (create search)
  SearchType*    search;
  if (query.searchtype() == "bfs") {
    printf("[%d] using BFS\n", socket);
    search = new BreadthFirstSearch();
  } else if (query.searchtype() == "ucs") {
    printf("[%d] using UCS\n", socket);
    search = new UniformCostSearch();
  } else {
    // case: could not create this search type
    printf("[%d] unknown search type: %s\n", socket, query.searchtype().c_str());
    delete costs;
    if (!query.userealworld()) { delete factDB; }
    closeConnection(socket, client);
    return;
  }
  // (create cache)
  CacheStrategy* cache;
  if (query.cachetype() == "none") {
    cache = new CacheStrategyNone();
  } else if (query.cachetype() == "bloom") {
    cache = new CacheStrategyBloom();
  } else {
    printf("[%d] unknown cache strategy: %s\n", socket, query.cachetype().c_str());
    delete costs;
    delete search;
    if (!query.userealworld()) { delete factDB; }
    closeConnection(socket, client);
    return;
  }
  // (make sure we're valid)
  if (!search->isValid() || !cache->isValid()) {
    printf("[%d] ERROR: SEARCH OR CACHE IS INVALID: %u, %u\n",
           socket, search->isValid(), cache->isValid());
    delete costs;
    delete search;
    delete cache;
    if (!query.userealworld()) { delete factDB; }
    closeConnection(socket, client);
    return;
  }
  // (run search)
  printf("[%d] running search (timeout: %lu)...\n", socket, query.timeout());
  search_response result;
  try {
    result = Search(graph, factDB, queryFact, queryLength, monotoneBoundary, search, cache, costs, query.timeout());
    printf("[%d] ...finished search; %lu results found\n", socket, result.paths.size());
  } catch (std::exception& e) {
    printf("%s\n", e.what());
  }

  // Compute final score

  // Return Result
  // (send result)
  Response response;
  for (int i = 0; i < result.paths.size(); ++i) {
    if (query.allowlookup() || result.paths[i].path->parent != NULL) {
      double score = exp(-result.paths[i].cost + (query.has_costs() ? query.costs().bias() : 0.0));
      response.add_inference()->CopyFrom(
        inferenceFromPath(result.paths[i].path, 
                          graph,
                          score,
                          result.paths[i].path->nodeState.truth,
                          NULL, NULL));
    }
  }
  response.set_totalticks(result.totalTicks);
  response.SerializeToFileDescriptor(socket);
  // (close connection)
  if (!query.userealworld()) { delete factDB; }
  delete cache;
  delete search;
  delete costs;
  closeConnection(socket, client);
}

/**
 * Set up listening on a server port
 */
int startServer(int port) {
  setmemlimit();
  // Get hostname, for debugging
	char hostname[256];
	gethostname(hostname, 256);

  // Create a socket
  int sock = socket(AF_INET, SOCK_STREAM, 0);
  if (sock < 0) {
		printf("could not create socket\n");
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
  if (bind(sock, (struct sockaddr*) &address, sizeof(address)) != 0) {
  	printf("Could not bind socket\n");
    return 10;
  } else {
    printf("Opened server socket (hostname: %s)\n", hostname);
  }

  // -- Initialize Global Variables --
  Graph*         graph   = ReadGraph();
#ifndef GREEDY_LOAD
  #define GREEDY_LOAD 0
#endif
#if GREEDY_LOAD==1
  FactDB*        db      = ReadFactTrie(graph);
#else
  FactDB*        db      = NULL;  // lazy load me
#endif
  // --                             --

  // set the socket for listening (queue backlog of 5)
	if (listen(sock, SERVER_TCP_BUFFER) < 0) {
		printf("Could not open port for listening\n");
    return 100;
	} else {
    printf("Listening on port %d...\n", port);
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
        printf("Failed to accept connection request\n");
        printf("  (");
        switch (errno) {
          case EAGAIN: printf("O_NONBLOCK is set for the socket file descriptor and no connections are present to be accepted"); break;
          case EBADF: printf("The socket argument is not a valid file descriptor"); break;
          case ECONNABORTED: printf("A connection has been aborted"); break;
          case EFAULT: printf("The address or address_len parameter can not be accessed or written"); break;
          case EINTR: printf("The accept() function was interrupted by a signal that was caught before a valid connection arrived"); break;
          case EINVAL: printf("The socket is not accepting connections"); break;
          case EMFILE: printf("{OPEN_MAX} file descriptors are currently open in the calling process"); break;
          case ENFILE: printf("The maximum number of file descriptors in the system are already open"); break;
          case ENOTSOCK: printf("The socket argument does not refer to a socket"); break;
          case EOPNOTSUPP: printf("The socket type of the specified socket does not support accepting connections"); break;
          case ENOBUFS: printf("No buffer space is available"); break;
          case ENOMEM: printf("There was insufficient memory available to complete the operation"); break;
          case ENOSR: printf("There was insufficient STREAMS resources available to complete the operation"); break;
          case EPROTO: printf("A protocol error has occurred; for example, the STREAMS protocol stack has not been initialised"); break;
          default: printf("???"); break;
        }
        printf(")\n");
        return 1000;
      } 
    }
		// Connection established
    printf("[%d] CONNECTION ESTABLISHED: %s port %d\n", requestSocket,
			     inet_ntoa(clientAddress->sin_addr),
           ntohs(clientAddress->sin_port));

    std::thread t(handleConnection, requestSocket, clientAddress, graph, &db);
    t.detach();
	}

  return 0;
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
  sigaction(SIGINT, &sigIntHandler, NULL);
  sigaction(SIGPIPE, &sigIntHandler, NULL);

  // (start server)
  while (startServer(argc < 2 ? SERVER_PORT : atoi(argv[1]))) { usleep(1000000); }
}
