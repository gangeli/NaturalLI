#include <stdio.h>
#include <stdlib.h>

#include "Postgres.h"

using namespace std;

session query(char* query) {
  session session;
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

void release(session& session) {
  PQclear(session.result);
  PQfinish(session.conn);
}
