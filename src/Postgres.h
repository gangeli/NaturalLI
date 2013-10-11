#ifndef POSTGRES_H
#define POSTGRES_H

#include "libpq-fe.h"

#include "Config.h"

/**
 * A Postgres session; this is the result of a query
 */
struct session {
  pg_result* result;  // typedef'd PGResult
  pg_conn*    conn;   // typedef'd PGconn
  int numResults;
  bool valid;
};

/**
 * Execute a Postgres query. The resulting session should be cleaned
 * up with release() below.
 */
session query(char*);

/**
 * Represents a single row of a database query result.
 */
class DatabaseRow {
 public:
  DatabaseRow(pg_result *result, uint64_t index)
    : result(result), index(index) { }
  
  char* operator[] (uint64_t index);

 private:
  pg_result *result;
  uint64_t index;
};

/**
 * An iterator for a particular query. 
 * Automatically manages the cursor, and creating + cleaning up
 * the connection and memory when constructed and deconstructed.
 */
class ResultIterator {
 public:
  ResultIterator(const char* query, uint64_t fetchSize = 1000000);
  ~ResultIterator();

  bool hasNext();
  DatabaseRow next();
 
 private:
  pg_conn *psql;
  pg_result *result;
  uint64_t fetchSize;
  uint64_t numResults;
  uint64_t nextIndex;
  
  pg_result *query(const char* query);
};

/**
 * Clean up after issuing a query.
 */
void release(session&);

#endif
