#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <signal.h>
#include <netdb.h>

#include <sys/wait.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <arpa/inet.h>

#include <netinet/in.h>

#include "Config.h"
#include "Utils.h"
#include "Graph.h"
#include "FactDB.h"
#include "Search.h"
#include "RamCloudBackend.h"

using namespace std;

//
// ENTRY
//

#define PORT "3490"  // the port users will be connecting to

#define BACKLOG 10     // how many pending connections queue will hold

int main(void)
{
    return 0;
}
