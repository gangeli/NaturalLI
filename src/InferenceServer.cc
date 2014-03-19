#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <thread>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/errno.h>
#include <sys/resource.h>
#include <arpa/inet.h>

#include "Messages.pb.h" 
#include "Search.h" 
#include "Graph.h" 
#include "Trie.h" 
#include "Utils.h" 


#ifndef SERVER_PORT
#define SERVER_PORT       1337
#define SERVER_TCP_BUFFER 25
#define SERVER_READ_SIZE  1024
#define MEM_ENV_VAR "MAXMEM_GB"
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
 * A simple user defined knowlege base, to be populated entirely from the query.
 */
class UserDefinedFactDB : public FactDB {
 public:
  UserDefinedFactDB(Query& query);
  virtual ~UserDefinedFactDB();
  
  virtual const bool contains(const tagged_word* words, const uint8_t wordLength, 
                              tagged_word* canInsert, uint8_t* canInsertLength);
 
 private:
  word** contents;
  uint64_t* lengths;
  uint64_t size;

};

/**
 * Create a new user defined knowledge base from a Query protobuf message.
 */
UserDefinedFactDB::UserDefinedFactDB(Query& query) {
  size = query.knownfact_size();
  contents = (word**) malloc(size * sizeof(word*));
  lengths = (uint64_t*) malloc(size * sizeof(uint64_t));
  for (int i = 0; i < size; ++i) {
    lengths[i] = query.knownfact(i).word_size();
    contents[i] = (word*) malloc(query.knownfact(i).word_size() * sizeof(word*));
    for (int k = 0; k < query.knownfact(i).word_size(); ++k) {
      contents[i][k] = query.knownfact(i).word(k).word();
    }
  }
}

/**
 * Clean up after ourselves.
 */
UserDefinedFactDB::~UserDefinedFactDB() {
  free(lengths);
  for (int i = 0; i < size; ++i) { free(contents[i]); }
  free(contents);
}
  
/**
 * Determine if this knowledge base contains the given element; implementing the
 * interface defined in FactDB.
 */
const bool UserDefinedFactDB::contains(const tagged_word* query, const uint8_t queryLength, 
                                       tagged_word* canInsert, uint8_t* canInsertLength) {
  *canInsertLength = 0;
  for (int i = 0; i < size; ++i) {             // for each element
    if (lengths[i] == queryLength) {           // if the lengths match
      bool found = true;
      for (int k = 0; k < queryLength; ++k) {  // and every word matches
        if (getWord(query[k]) != contents[i][k]) { found = false; }
      }
      if (found) { return true; }              // return true
    }
  }
  return false;
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
    word.set_word(path->fact[i]);
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
    factDB = new UserDefinedFactDB(query);
    printf("[%d] created mock factdb.\n", socket);
  }
  // (create query)
  uint8_t queryLength = query.queryfact().word_size();
  word queryFact[queryLength];
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
  std::vector<scored_path> result;
  try {
    result = Search(graph, factDB, queryFact, queryLength, search, cache, weights, query.timeout());
    printf("[%d] ...finished search; %lu results found\n", socket, result.size());
  } catch (...) {
    printf("[%d] EXCEPTION IN SEARCH (probably Out Of Memory)\n", socket);
  }

  // Compute final score

  // Return Result
  // (send result)
  Response response;
  for (int i = 0; i < result.size(); ++i) {
    double score = exp(-result[i].cost + (query.has_weights() ? query.weights().bias() : 0.0));
    response.add_inference()->CopyFrom(inferenceFromPath(result[i].path, graph, score));
  }
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
