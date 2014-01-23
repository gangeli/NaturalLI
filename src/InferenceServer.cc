#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/errno.h>
#include <arpa/inet.h>


#ifndef SERVER_PORT
#define SERVER_PORT       1337
#define SERVER_TCP_BUFFER 25
#endif

#ifndef ERESTART
#define ERESTART EINTR
#endif
extern int errno;

/**
 * Handle an incoming connection.
 * This involves reading a query, starting a new inference, and then
 * closing the connection.
 */
void handleConnection(uint32_t socket, sockaddr_in address) {
  // Read the query
  printf("[%d] reading query...\n", socket);
  // Close the connection
  shutdown(socket, 2);
  printf("[%d] CONNECTION CLOSED: %s port %d\n", socket,
		     inet_ntoa(address.sin_addr),
         ntohs(address.sin_port));
}

/**
 * Set up listening on a server port
 */
int startServer(int port) {
  // Create a socket
  int32_t sock = socket(AF_INET, SOCK_STREAM, 0);
  // Bind the socket
  // (setup the bind)
  struct sockaddr_in address;
  memset( (char*) &address, 0, sizeof(address) );  // zero the socket
  address.sin_family = AF_INET;
  address.sin_addr.s_addr = htonl(INADDR_ANY);
  address.sin_port = htons(port);
  // (actually perform the bind)
  if (bind(sock, (struct sockaddr*) &address, sizeof(address)) != 0) {
  	perror("Could not bind socket!");
    return 1;
  } else {
    printf("Opened server socket.\n");
  }

  // set the socket for listening (queue backlog of 5)
	if (listen(sock, SERVER_TCP_BUFFER) < 0) {
		perror("Could not open port for listening!");
    return 2;
	} else {
    printf("Listening on port %d...\n", port);
  }

  // Allow immediate re-use of the port
  int32_t sockoptval = 1;
  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &sockoptval, sizeof(int32_t));

  // loop, accepting connection requests
	for (;;) {
    // Accept an incoming connection
    int32_t requestSocket;
    socklen_t requestLength;
		while ((requestSocket = accept(sock, (struct sockaddr*) &address, &requestLength)) < 0) {
			// we may break out of accept if the system call
      // was interrupted. In this case, loop back and
      // try again
      if ((errno != ECHILD) && (errno != ERESTART) && (errno != EINTR)) {
        // If we got here, there was a serious problem with the connection
        perror("Failed to accept connection request");
        return 3;
      } 
    }
		// Connection established
    printf("[%d] CONNECTION ESTABLISHED: %s port %d\n", requestSocket,
			     inet_ntoa(address.sin_addr),
           ntohs(address.sin_port));
    std::thread t(handleConnection, requestSocket, address);
	}

  return 0;
}

int main( int argc, char *argv[] ) {
  startServer(argc < 2 ? SERVER_PORT : atoi(argv[1]) );
//  while (startServer(1337) != 0) { }
  printf("\n");
  printf("--------------\n");
  printf("STOPPED SERVER\n");
  printf("--------------\n");
}
