#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include "InferenceServer.h"
#include "libpq-fe.h"

#include "Config.h"
#include "PopulateRamCloud.h"

using namespace std;

struct Session {
  pg_result* result;  // typedef'd PGResult
  pg_conn*    conn;   // typedef'd PGconn
  int numResults;
  bool valid;
};

struct Edge {
  int   source;
  int   sink;
  char  type;
  float cost;
};

Session query(char* query) {
  Session session;
  session.valid = false;

  // Create connection
  char psqlConnectionSpec[127];
  snprintf(psqlConnectionSpec, 127,
           "hostaddr='%s' port='%i' dbname='%s' user='%s' password='%s'",
           PG_HOST.c_str(), PG_PORT, PG_DATABASE.c_str(), PG_USER.c_str(),
           PG_PASSWORD.c_str());
  PGconn *psql = PQconnectdb(psqlConnectionSpec);
  if (psql == NULL || PQstatus(psql) != CONNECTION_OK) {
    printf("could not connect to postgresql server at %s\n", psqlConnectionSpec);
    return session;
  }

  // Execute query
  PGresult *result = PQexec(psql, query);
  if (result == NULL || (session.numResults = PQntuples(result)) == 0) {
    printf("no results for query %s\n", query);
    return session;
  }

  // Return
  session.conn   = psql;
  session.result = result;
  return session;
}

Session release(Session& session) {
  PQclear(session.result);
  PQfinish(session.conn);
}

bool PopulateRamCloud() {
  int numWords = 18858251; // TODO(gabor) don't hard code

  // Read words
  char** index2gloss = (char**) malloc( numWords * sizeof(char*) );
  // (query)
  char wordQuery[127];
  snprintf(wordQuery, 127, "SELECT * FROM %s;", PG_TABLE_WORD.c_str());
  Session wordSession = query(wordQuery);
  // (process)
  for (int i = 0; i < wordSession.numResults; ++i) {
    if (i % 1000000 == 0) { printf("loaded %iM words\n", i / 1000000); }
    if (PQgetisnull(wordSession.result, i, 0) || PQgetisnull(wordSession.result, i, 1)) {
      printf("null row in word indexer; query=%s\n", wordQuery);
      return false;
    }
    int key     = atoi(PQgetvalue(wordSession.result, i, 0));
    char* gloss = PQgetvalue(wordSession.result, i, 1);
    index2gloss[key] = gloss;
//    printf("row: %d -> %s\n", key, gloss);
    numWords += 1;
  }
  // (clean up)
  release(wordSession);
  
  // Read edges
  // (query)
  char edgeQuery[127];
  snprintf(edgeQuery, 127, "SELECT * FROM %s;", PG_TABLE_EDGE.c_str());
  Session edgeSession = query(edgeQuery);
  // (process)
  vector<Edge>* edges = (vector<Edge>*) malloc((numWords+1) * sizeof(vector<Edge>));
  for (int i = 0; i < edgeSession.numResults; ++i) {
    if (i % 1000000 == 0) { printf("loaded %iM edges\n", i / 1000000); }
    if (PQgetisnull(edgeSession.result, i, 0) ||
        PQgetisnull(edgeSession.result, i, 1) ||
        PQgetisnull(edgeSession.result, i, 2) ||
        PQgetisnull(edgeSession.result, i, 3)    ) {
      printf("null row in edges; query=%s\n", wordQuery);
      return false;
    }
    Edge edge;
    edge.source = atoi(PQgetvalue(edgeSession.result, i, 0));
    edge.sink   = atoi(PQgetvalue(edgeSession.result, i, 1));
    edge.type   = atoi(PQgetvalue(edgeSession.result, i, 2));
    edge.cost   = atof(PQgetvalue(edgeSession.result, i, 3));
    edges[edge.source].push_back(edge);
//    printf("row: %s --[%d]--> %s  (%f)\n", index2gloss[edge.source], edge.type,
//                                           index2gloss[edge.sink], edge.cost);
  }
  // (clean up)
  release(edgeSession);
  
  
  // Finish
  printf("%s\n", "RamCloud populated.");
  return true;
}
