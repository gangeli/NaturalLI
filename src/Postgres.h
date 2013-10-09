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
 * Clean up after issuing a query.
 */
void release(session&);

#endif
