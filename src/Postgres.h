#ifndef POSTGRES_H
#define POSTGRES_H

#include "libpq-fe.h"

#include "Config.h"

/**
 * Represents a single row of a database query result.
 */
class PGRow {
 public:
  PGRow(pg_result *result, uint64_t index)
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
class PGIterator {
 public:
  PGIterator(const char* query, uint64_t fetchSize = 1000000);
  ~PGIterator();

  bool hasNext();
  PGRow next();
 
 private:
  pg_conn *psql;
  pg_result *result;
  uint64_t fetchSize;
  uint64_t numResults;
  uint64_t nextIndex;
  
  pg_result *query(const char* query);
};

#endif
