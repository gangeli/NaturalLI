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
           "host='%s' port='%i' dbname='%s' user='%s' password='%s'",
           PG_HOST.c_str(), PG_PORT, PG_DATABASE.c_str(), PG_USER.c_str(),
           PG_PASSWORD.c_str());
  PGconn *psql = PQconnectdb(psqlConnectionSpec);
  if (psql == NULL || PQstatus(psql) != CONNECTION_OK) {
    printf("could not connect to postgresql server at %s\n", psqlConnectionSpec);
    switch(PQstatus(psql)) {
      case CONNECTION_STARTED:
         printf("  (cause: CONNECTION_STARTED)\n");
         break;
      case CONNECTION_MADE:
         printf("  (cause: CONNECTION_MADE)\n");
         break;
      case CONNECTION_AWAITING_RESPONSE:
         printf("  (cause: CONNECTION_AWAITING_RESPONSE)\n");
         break;
      case CONNECTION_AUTH_OK:
         printf("  (cause: CONNECTION_AUTH_OK)\n");
         break;
      case CONNECTION_SSL_STARTUP:
         printf("  (cause: CONNECTION_SSL_STARTUP)\n");
         break;
      case CONNECTION_SETENV:
         printf("  (cause: CONNECTION_SETENV)\n");
         break;
      case CONNECTION_BAD:
         printf("  (cause: CONNECTION_BAD)\n");
         break;
      default:
         printf("  (cause: <<unknown>>)\n");
         break;
    }
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
  session.valid  = true;
  return session;
}

void release(session& session) {
  PQclear(session.result);
  PQfinish(session.conn);
}
