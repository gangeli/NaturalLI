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


//
// ResultIterator
//
ResultIterator::ResultIterator(const char* queryString, uint64_t fetchSize)
    : fetchSize(fetchSize), numResults(0), nextIndex(0) {
  // Define connection
  char psqlConnectionSpec[127];
  snprintf(psqlConnectionSpec, 127,
           "host='%s' port='%i' dbname='%s' user='%s' password='%s'",
           PG_HOST.c_str(), PG_PORT, PG_DATABASE.c_str(), PG_USER.c_str(),
           PG_PASSWORD.c_str());
  this->psql = PQconnectdb(psqlConnectionSpec);
  if (this->psql == NULL || PQstatus(psql) != CONNECTION_OK) {
    printf("could not connect to postgresql server at %s\n", psqlConnectionSpec);
    switch(PQstatus(this->psql)) {
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
    exit(2);
  }
  
  // Begin transaction
  this->result = query("BEGIN;");
  PQclear(this->result);

  // Create cursor
  char cursorQuery[256];
  snprintf(cursorQuery, 255,
           "DECLARE c CURSOR FOR %s;",
           queryString);
  this->result = query(cursorQuery);
  PQclear(this->result);

  // Null results in preperation for iteration
  this->result = NULL;
}

ResultIterator::~ResultIterator() {
  PQclear(this->result);
  PQfinish(this->psql);
}

pg_result* ResultIterator::query(const char* query) {
  pg_result* result = PQexec(this->psql, query);
  if (result == NULL) {
    printf( "Postgres query '%s' failed: %s\n",
            query,
            PQerrorMessage( this->psql ));
    exit(2);
  }
  return result;
}

bool ResultIterator::hasNext() {
  if (nextIndex < numResults) {
    // Have more in this fetch
    return true;
  } else if (this->result != NULL && numResults == 0) {
    // No results in this fetch
    return false;
  } else {
    // Exhausted cursor fetch
    char fetchQuery[256];
    snprintf(fetchQuery, 255, "FETCH %lu FROM c;", fetchSize);
    if (this->result != NULL) {
      PQclear(this->result);
    }
    this->result = this->query(fetchQuery);
    this->numResults = PQntuples(this->result);
    this->nextIndex = 0;
    return this->hasNext();
  }
}

DatabaseRow ResultIterator::next() {
  if (!hasNext()) {
    printf("ResultIterator is empty!\n");
    exit(1);
  }
  nextIndex += 1;
  return DatabaseRow(result, nextIndex - 1);
}


//
// DatabaseRow
//
char* DatabaseRow::operator[](uint64_t index) {
  if (PQgetisnull(result, this->index, index)) {
    return NULL;
  } else {
    return PQgetvalue(result, this->index, index);
  }
}


