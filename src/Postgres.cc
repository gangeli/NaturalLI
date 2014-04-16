#include <stdio.h>
#include <stdlib.h>

#include "Postgres.h"

using namespace std;

//
// PGIterator
//
PGIterator::PGIterator(const char* queryString, uint64_t fetchSize)
    : fetchSize(fetchSize), numResults(0), nextIndex(0), connected(false) {
  if (queryString != NULL) {
    // Define connection
    char psqlConnectionSpec[512];
    snprintf(psqlConnectionSpec, 512,
             "host='%s' port='%i' dbname='%s' user='%s' password='%s'",
             PG_HOST, PG_PORT, PG_DATABASE, PG_USER,
             PG_PASSWORD);
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
      exit(1);
    }
    
    // Begin transaction
    this->connected = true;
    this->result = query("BEGIN;");
    PQclear(this->result);

    // Create cursor
    char cursorQuery[512];
    snprintf(cursorQuery, 512,
             "DECLARE c CURSOR FOR %s;",
             queryString);
    this->result = query(cursorQuery);
    PQclear(this->result);

    // Null results in preperation for iteration
    this->result = NULL;
  }
}

PGIterator::~PGIterator() {
  if (this->connected) {
    PQclear(this->result);
    PQfinish(this->psql);
  }
}

pg_result* PGIterator::query(const char* query) {
  pg_result* result = PQexec(this->psql, query);
  if (result == NULL) {
    printf( "Postgres query '%s' failed: %s\n",
            query,
            PQerrorMessage( this->psql ));
    exit(2);
  }
  return result;
}

bool PGIterator::hasNext() {
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

PGRow PGIterator::next() {
  if (!hasNext()) {
    printf("PGIterator is empty!\n");
    exit(1);
  }
  nextIndex += 1;
  return PGRow(result, nextIndex - 1);
}


//
// PGRow
//
const char* PGRow::operator[](uint64_t index) {
  if (mockElems != NULL) {
    return mockElems[index];
  }
  if (PQgetisnull(result, this->index, index)) {
    return NULL;
  } else {
    return PQgetvalue(result, this->index, index);
  }
}


