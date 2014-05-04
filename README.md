NaturalLI: Natural [Language|Logic] Inference
===========

The program is composed of two main components:

  * A *server*, in C++, which handles the actual inference search.
    This component takes as input a query fact, already tagged with
    monotonicity, part of speech, etc., and returns a set of scored
    paths to something it found in the database.
    The interface to and from the server is via protocol buffers.

  * A *client*, in Java, which performs the pre-processing required to create
    a query to the server and handles the learning / rescoring, etc.
    of the output of the server.
    This is the entry point most users are looking for.

Installation
===========
This section describes the installation of the system.
Now, before anyone starts complaining about how godawfully long
this section is (true story: all build systems suck), you're always
entitled to try the version I use at home; the rest of the section can
be thought of as debugging help.

    ./configure CXX=clang++-3.4
    make -j4
    ./run

Both the server and client are configured using autoconf, and so
you should follow the configuration instructions for the server
before installing the client.

Prerequisites
-------------
The code tries to have relatively few dependencies.
Many of the java dependencies are simply bundled into the `lib`
directory; the remaining dependencies are:

General:
  * Java 7 JDK
  * Scala 2.10.0 (the exact version sadly may matter here)

Java (too large to include in Git):

  * CoreNLP (http://nlp.stanford.edu/software/corenlp.shtml)
  * CoreNLP models (included in CoreNLP)
  * CoreNLP caseless models (http://nlp.stanford.edu/software/corenlp.shtml#caseless)

C++:

  * Protobuf 2.4 compiler (protoc)
  * Postgresql (9.2.4 tested)

Server (C++)
-------------
The server is configured using autoconf.
That is, the high-level procedure to install the server is as follows,
yielding a final execuable `naturalli_server` (by default, in `src/`):
    
    ./configure --prefix=/path/to/installation/directory
    make check
    make install

You may have to set a few options to the configure script, in case
certain libraries are not in your path.
In particular, since Scala is awful at any sort of compatibility,
it may be useful to just download the version required for this code
and link to it via the `with-scala` option:
    
    --with-scala=/path/to/SCALA_HOME      # scalac should be bin/scalac
    --with-java=/path/to/JDK_HOME         # javac should be bin/javac
    --with-protoc=/path/to/PROTOC_HOME    # protoc should be bin/protoc
    --with-postgresql=/path/to/pg_config  # look for pg_config here

Other possibly useful options are:

    --enable-debug                        # compile with -O0 -ggdb (and enable profiler & asserts)

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

The other variables either set up some constants in the database,
or set various parameters (these should not need changing).

    PG_TABLE_WORD=<the table name for the word indexer; default=word>
    PG_TABLE_EDGE=<the table name for the edges in the graph; default=edge>
    PG_TABLE_FACT=<the table name for the knowledge base; default=fact>
    PG_TABLE_PRIVATIVE=<the table of privative adjectives; default=privative>

    MAX_FACT_LENGTH=<the maximum fact length in words; between 1 and 255>
    MAX_COMPLETIONS=<the maximum number of insertions to consider; default=25>

    SEARCH_TIMEOUT=<default search timeout in queue pops>
    MIN_FACT_COUNT=<the minimum count for a valid fact>

Some other potentially useful variables to set may be:

    CXX=<c++ compiler; g++ and clang++ 3.4 or later have been tested)

Client (Java)
-------------
For now, `make src/naturalli_client.jar` pretty much works.
You're on your own in terms of setting up the classpath / etc. though

Data (Postgres)
-------------
Some day this will be in an easily distributable form...
