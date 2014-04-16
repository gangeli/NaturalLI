

Compilation
===========

Configuration
-------------

If you're an optimist, the following might even just work:
    
    ./configure
    make check

Otherwise, likely you'll have to set a few options to configure.
For example:
    
    --with-scala=/path/to/SCALA_HOME    # scalac should be in bin/
    --with-java=/path/to/JDK_HOME       # javac should be in bin/
    --with-protoc=/path/to/PROTOC_HOME  # protoc should be in bin/protoc
    --enable-debug                      # compile with -O0 -ggdb (and enable profiler + asserts)

In addition, a number of environment variables are very likely to be
useful. At minimum, the program should be pointed to the location
of the Postgres database to read the graph from.
This can be done by setting the following environment variables,
either with:

    VARIABLE=value ./configure ...

or, preferably, with:
   
   ./configure VARIABLE=value ...

The relevant variables are as follows.
The only essential ones should be the postgres configuration variables
at the beginning.
These can be debugged by setting them on the console and running
`psql` -- you should connect to the database without needing any
command line arguments.

    PGHOST=<postgres hostname>
    PGPORT=<postgres port>
    PGUSER=<postgres username>
    PGPASSWORD=<postgres password>
    DBNAME=<postgres database name>

    PG_TABLE_WORD=<the table name for the word indexer; default=word>
    PG_TABLE_EDGE=<the table name for the edges in the graph; default=edge>
    PG_TABLE_FACT=<the table name for the knowledge base; default=fact>

    MAX_FACT_LENGTH=<the maximum fact length in words; between 1 and 255>
    MAX_COMPLETIONS=<the maximum number of insertions to consider; default=25>

    SEARCH_TIMEOUT=<default search timeout in queue pops>
    MIN_FACT_COUNT=<the minimum count for a valid fact>

Extra Options
-------------
The code also compiles with clang (3.4 or later has been tested);
to use this feature, use:
    
    ./configure CXX=clang++
