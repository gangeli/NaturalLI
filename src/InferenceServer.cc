#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <exception>
#include <thread>
#include <mutex>
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
FactDB* makeFactDB(Query& query) {
  Trie* facts = new Trie();
  uint32_t size = query.knownfact_size();
  for (int factI = 0; factI < size; ++factI) {
    uint32_t factLength = query.knownfact(factI).word_size();
    edge fact[factLength];
    for (int wordI = 0; wordI < factLength; ++wordI) {
      Word word = query.knownfact(factI).word(wordI);
      // Set the word
      fact[wordI].sink = word.word();
      // Set the sense
      if (word.has_sense()) {
        fact[wordI].sense = word.sense();
      } else {
        fact[wordI].sense = 0;
      }
      // Set the edge type
      if (query.knownfact(factI).word(wordI).has_pos()) {
        const char* posTag = query.knownfact(factI).word(wordI).pos().c_str();
        if (posTag != NULL && (posTag[0] == 'n' || posTag[0] == 'N')) {
          fact[wordI].type = ADD_NOUN;
        } else if (posTag != NULL && (posTag[0] == 'v' || posTag[0] == 'V')) {
          fact[wordI].type = ADD_VERB;
        } else if (posTag != NULL && (posTag[0] == 'j' || posTag[0] == 'J')) {
          fact[wordI].type = ADD_ADJ;
        } else if (posTag != NULL && (posTag[0] == 'r' || posTag[0] == 'R')) {
          fact[wordI].type = ADD_ADV;
        } else {
          fact[wordI].type = ADD_OTHER;
        }
      } else {
        fact[wordI].type = ADD_OTHER;
      }
      fact[wordI].cost = 1.0f;  // not really relevant...
    }
    // Add the fact
    facts->add(fact, factLength);
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
    perror("Failed to shutdown connection");
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
  free(client);
  fflush(stdout);
}


/**
 * Convert a (concise) path object into an Inference object to be passed over the wire
 * to the client.
 */
Inference inferenceFromPath(const Path* path, const Graph* graph, const float& score) {
  Inference inference;
  // Populate fact
  Fact fact;
  for (int i = 0; i < path->factLength; ++i) {
    Word word;
    word.set_word(path->fact[i].word);
    fact.add_word()->CopyFrom(word);
  }
  fact.set_gloss(toString(*graph, path->fact, path->factLength));
  inference.mutable_fact()->CopyFrom(fact);
  // Return
  if (path->parent != NULL) {
    inference.mutable_impliedfrom()->CopyFrom(inferenceFromPath(path->parent, graph, score));
  }
  inference.set_score(score);
  return inference;
}

/**
 * A simple utility to convert a proto weights file to unigram edge weights
 */
float* mkUnigramWeight(const UnlexicalizedWeights& proto) {
  float* weights = (float*) malloc(proto.edgeweight_size() * sizeof(float));
  for (int i = 0; i < proto.edgeweight_size(); ++i) {
    weights[i] = proto.edgeweight(i);
  }
  return weights;
}

/**
 * A simple utility to convert a proto weights file to bigram edge weights
 */
float* mkBigramWeight(const UnlexicalizedWeights& proto) {
  float* weights = (float*) malloc(proto.edgepairweight_size() * sizeof(float));
  for (int i = 0; i < proto.edgepairweight_size(); ++i) {
    weights[i] = proto.edgepairweight(i);
  }
  return weights;
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
  WeightVector* weights;
  if (query.has_weights() &&
      query.weights().has_unlexicalizedmonotoneup() &&
      query.weights().has_unlexicalizedmonotonedown() &&
      query.weights().has_unlexicalizedmonotoneflat() &&
      query.weights().has_unlexicalizedmonotoneany() ) {
    weights = new WeightVector(
      mkUnigramWeight(query.weights().unlexicalizedmonotoneup()),
      mkBigramWeight(query.weights().unlexicalizedmonotoneup()),
      mkUnigramWeight(query.weights().unlexicalizedmonotonedown()),
      mkBigramWeight(query.weights().unlexicalizedmonotonedown()),
      mkUnigramWeight(query.weights().unlexicalizedmonotoneflat()),
      mkBigramWeight(query.weights().unlexicalizedmonotoneflat()),
      mkUnigramWeight(query.weights().unlexicalizedmonotoneany()),
      mkBigramWeight(query.weights().unlexicalizedmonotoneany()));
  } else {
    weights = new WeightVector();
  }

  // Run Search
  // (prepare factdb)
  FactDB* factDB = *dbOrNull;
  if (query.userealworld()) {
    if (factDB == NULL) {
      // Read the real knowledge base
      factDBReadLock.lock();
      *dbOrNull = ReadFactTrie();
      factDBReadLock.unlock();
      factDB = *dbOrNull;
    }
    printf("[%d] created real factdb.\n", socket);
  } else {
    // Read a dummy knowledge base
    factDB = makeFactDB(query);
    printf("[%d] created mock factdb.\n", socket);
  }
  // (create query)
  uint8_t queryLength = query.queryfact().word_size();
  tagged_word queryFact[queryLength];
  for (int i = 0; i < queryLength; ++i) {
    if (query.queryfact().word(i).monotonicity() < MONOTONE_FLAT ||
        query.queryfact().word(i).monotonicity() > MONOTONE_DOWN) {
    // case: invalid monotonicity markings
    printf("[%d] invalid monotonicity marking: %d\n", socket, query.queryfact().word(i).monotonicity());
    if (!query.userealworld()) { delete factDB; }
    delete weights;
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
    delete weights;
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
    delete weights;
    if (!query.userealworld()) { delete factDB; }
    closeConnection(socket, client);
    return;
  }
  // (run search)
  printf("[%d] running search (timeout: %lu)...\n", socket, query.timeout());
  search_response result;
  try {
    result = Search(graph, factDB, queryFact, queryLength, search, cache, weights, query.timeout());
    printf("[%d] ...finished search; %lu results found\n", socket, result.paths.size());
  } catch (std::exception& e) {
    printf("%s\n", e.what());
  }

  // Compute final score

  // Return Result
  // (send result)
  Response response;
  for (int i = 0; i < result.paths.size(); ++i) {
    double score = exp(-result.paths[i].cost + (query.has_weights() ? query.weights().bias() : 0.0));
    response.add_inference()->CopyFrom(inferenceFromPath(result.paths[i].path, graph, score));
  }
  response.set_totalticks(result.totalTicks);
  response.SerializeToFileDescriptor(socket);
  // (close connection)
  if (!query.userealworld()) { delete factDB; }
  delete cache;
  delete search;
  delete weights;
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
		perror("could not create socket");
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
  	perror("Could not bind socket");
    return 10;
  } else {
    printf("Opened server socket (hostname: %s)\n", hostname);
  }

  // -- Initialize Global Variables --
  Graph*         graph   = ReadGraph();
  FactDB*        db      = NULL;  // lazy load me
  // --                             --

  // set the socket for listening (queue backlog of 5)
	if (listen(sock, SERVER_TCP_BUFFER) < 0) {
		perror("Could not open port for listening");
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
        perror("Failed to accept connection request");
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
 * The server's entry point.
 */
int main( int argc, char *argv[] ) {
  while (startServer(argc < 2 ? SERVER_PORT : atoi(argv[1]))) { usleep(1000000); }
}
