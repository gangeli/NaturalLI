#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/errno.h>
#include <arpa/inet.h>

#include "Messages.pb.h" 
#include "Search.h" 


#ifndef SERVER_PORT
#define SERVER_PORT       1337
#define SERVER_TCP_BUFFER 25
#define SERVER_READ_SIZE  1024
#endif

#ifndef ERESTART
#define ERESTART EINTR
#endif
extern int errno;

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
 * Handle an incoming connection.
 * This involves reading a query, starting a new inference, and then
 * closing the connection.
 */
void handleConnection(int socket, sockaddr_in* client) {
  // Parse Query
  printf("[%d] Reading query...\n", socket);
  Query query;
  if (!query.ParseFromFileDescriptor(socket)) { closeConnection(socket, client); return; }
  printf("[%d] ...read query.\n", socket);

  // Run Search
  // TODO(gabor)

  // Return Result
  // (create proto)
  const Fact& queryFact = query.queryfact();
  const Fact& antecedent = query.knownfact(0);
  Inference antecedentInference;
  antecedentInference.mutable_fact()->CopyFrom(antecedent);
  Inference inference;
  inference.mutable_fact()->CopyFrom(queryFact);
  inference.mutable_impliedfrom()->CopyFrom(antecedentInference);
  Response response;
  response.add_inference()->CopyFrom(inference);
  // (send proto)
  response.SerializeToFileDescriptor(socket);
  // (close connection)
  closeConnection(socket, client);
}

/**
 * Set up listening on a server port
 */
int startServer(int port) {
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

  // set the socket for listening (queue backlog of 5)
	if (listen(sock, SERVER_TCP_BUFFER) < 0) {
		perror("Could not open port for listening");
    return 100;
	} else {
    printf("Listening on port %d...\n", port);
  }

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

    std::thread t(handleConnection, requestSocket, clientAddress);
    t.detach();
	}

  return 0;
}

/**
 * The server's entry point.
 */
int main( int argc, char *argv[] ) {
  startServer(argc < 2 ? SERVER_PORT : atoi(argv[1]) );
//  while (startServer(1337) != 0) { }
  printf("\n");
  printf("--------------\n");
  printf("STOPPED SERVER\n");
  printf("--------------\n");
}
