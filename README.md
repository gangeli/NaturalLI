NaturalLI
===========
**Natural [Language|Logic] Inference**

NaturalLI is a Natural Logic reasoning engine aimed at fast inference
from a large database of known facts.
The project's primary goal is to infer whether arbitrary common-sense
facts are true, given a large database of known facts.
The system is described in:

    Gabor Angeli and Christopher D. Manning. "NaturalLI: Natural Logic for Common Sense Reasoning." EMNLP 2014.

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

The rest of this README is laid out as follows:
- [Installation](#installation)
  - [Prerequisites](#prerequisites)
  - [Server (C++)](#server-c)
  - [Client (Java)](#client-java)
  - [Database](#database)
    - [Install Postgres](#install-postgres)
    - [Import Data](#import-data)
  - [Optimization](#optimization)
- [Tests](#tests)
  - [Common Bugs](#common-bugs)

Installation
----------
This section describes the installation of the system.
Now, before anyone starts complaining about how long
this section is, or how autoconf is awful
(true story: all build systems are awful), you're always
entitled to try the version I use at home:

    ./configure CXX=clang++-3.4
    make -j4
    ./run FraCaS

The rest of the section can be thought of as debugging help.
Both the server and client are configured using autoconf and built
using Make.
The rest of this section will describe the prerequisites you'll need
installed on your system, followed by instructions on configuring
and installing the server and client component.

###Prerequisites
The code tries to have relatively few dependencies.
Many of the java dependencies are simply bundled into the `lib`
directory; the remaining dependencies are:

General:
  * Java 8 JDK (with minor tweaks it should compile on Java 7)
  * Scala 2.11.0 (2.10 compiles with classpath tweaks)

Java (too large to include in Git):

  * CoreNLP (http://nlp.stanford.edu/software/corenlp.shtml)
  * CoreNLP models (included in CoreNLP)
  * CoreNLP caseless models (http://nlp.stanford.edu/software/corenlp.shtml#caseless)

C++:

  * Protobuf 2.4 compiler (protoc)
  * Postgresql (9.2.4 tested)

###Server (C++)
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

Other possibly relevant paths are:

    --with-java=/path/to/JDK_HOME         # javac should be bin/javac
    --with-protoc=/path/to/PROTOC_HOME    # protoc should be bin/protoc
    --with-postgresql=/path/to/pg_config  # look for pg_config here

    # Paths to CoreNLP jars
    --with-corenlp=/path/to/stanford-corenlp-XYZ.jar
    --with-corenlp-models=/path/to/stanford-corenlp-models-XYZ.jar
    --with-corenlp-caseless-models=/path/to/stanford-corenlp-caseless-models-XYZ.jar

Other possibly useful options are:

    --enable-debug                        # compile with asserts and profiling

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
at the beginning:

    PGHOST=<postgres hostname>
    PGPORT=<postgres port>
    PGUSER=<postgres username>
    PGPASSWORD=<postgres password>
    DBNAME=<postgres database name>

These can be debugged by setting them on the console and running
`psql` -- you should connect to the database without needing any
command line arguments.
See the section on [configuring Postgres](#database) below.

The other variables either set up some constants in the database,
or set various parameters; if you've imported the default data into
Postgres, these should not need changing.

    PG_TABLE_WORD=<the table name for the word indexer; default=word>
    PG_TABLE_EDGE=<the table name for the edges in the graph; default=edge>
    PG_TABLE_FACT=<the table name for the knowledge base; default=fact>
    PG_TABLE_PRIVATIVE=<the table of privative adjectives; default=privative>

    MAX_FACT_LENGTH=<the maximum fact length in words; between 1 and 255. default=255>
    MAX_COMPLETIONS=<the maximum number of insertions to consider. default=25>

    SEARCH_TIMEOUT=<default search timeout in queue pops. default=100k>
    MIN_FACT_COUNT=<the minimum count for a valid fact. default=1>
    HIGH_MEMORY=<enable memory-intensive optmization for small databases. default=0>
    FACT_MAP_SIZE=<size of fact map, as 2^x. default=30>
    GREEDY_LOAD=<greedily load the fact database. default=0>

Some other potentially useful variables to set may be:

    CXX=<c++ compiler; GCC 4.5 or later and Clang++ 3.4 or later have been tested)
        NOTE: set CXX=gcc not CXX=g++ if you want to use GCC.

###Client (Java)
For now, `make src/naturalli_client.jar` pretty much works.
Make sure you're running the right version of Java (Java 8), and a
recent version of Scala (2.11, though 2.10 likely compiles as well).
Also, make sure either CoreNLP (and the associated models) are in the
`lib` directory, or the appropriate options were set during
configuration (see [the configuration paths described here](#server-c))
If both of these conditions are met and it still throws an error, please
let me know!
Or better yet, submit a patch!

###Database

####Install Postgres
_(If you're in the Stanford NLP group, skip this section and talk to me)_

You will need a Postgres instance running which is accessible by both
the client and the server program.
Make sure you have at least 40GB of free space on the instance.

The following are necessary and recommended tweaks to `postgres.conf` 
in the data directory:

    # Necessary
    listen_addresses = '*'
    # Recommended
    max_connections = 512
    shared_buffers = 1GB

In addition, you should configure `pg_hba.conf` to allow for connections
from both the server and client.

####Import Data
Now that you have a running Postgres instance, you should import the
mutation graph and the database of facts.
For this, you'll need three files:

    http://nlp.stanford.edu/projects/naturalli/naturalli_schema.sql
    http://nlp.stanford.edu/projects/naturalli/naturalli_data.sql.gz  # Warning! 3.7GB!
    http://nlp.stanford.edu/projects/naturalli/naturalli_indexes.sql

The first is the schema definition, the second is the actual data, and
the third contains the recommended indexes over the data.
If you really want to, you can probably skip the third file.

Next, we import the data. For this part, I'll assume you have the
following environment variables set:

    PGHOST=<postgres hostname>
    PGPORT=<postgres port>
    PGUSER=<postgres username>
    PGPASSWORD=<postgres password>
    DBNAME=<postgres database name>

You can then import the data with:

    $ psql $DBNAME < naturalli_schema.sql
    $ gunzip naturalli_data.sql.gz | psql $DBNAME
    $ psql $DBNAME < naturalli_indexes.sql

With that, you should be all set! You can validate that everything
went well by verifying that you get similar output from:

    $ psql $DBNAME
    your_db_name=> \dt+
                                  List of relations
     Schema |    Name    | Type  | Owner         |    Size    | Description 
    --------+------------+-------+---------------+------------+-------------
     public | edge       | table | your_username | 580 MB     | 
     public | edge_type  | table | your_username | 8192 bytes | 
     public | fact       | table | your_username | 26 GB      | 
     public | privative  | table | your_username | 328 kB     | 
     public | word       | table | your_username | 8616 kB    | 
     public | word_sense | table | your_username | 41 MB      | 
    (6 rows)

###Optimization
By default, autoconf compiles with `-02 -g`. 
This can be changed by creating a file `share/config.site` within the
prefix directory set in the configure script using `--prefix=`.
For better performance, add the line:

    CXXFLAGS="-03"

For a better debugging experience, add the line:
    
    CXXFLAGS="-00 -ggdb"

Tests
-----
If you have configured everything correctly, running:

    $ make check

should compile and run without error.
If the database is not accessible from the current machine, 
or the data has not been loaded properly, then `PostgresTest` will 
throw a slew of errors.

If you'd like to run more in-depth tests, you can run:

     $ test/src/itest_server

To run the Java/Scala tests, run:

     $ make java_test

Note that for the java tests, you will almost certainly need to
set the path for CoreNLP and the CoreNLP models in the
[configure script](#server-c):
    
    --with-corenlp=/path/to/stanford-corenlp-XYZ.jar
    --with-corenlp-models=/path/to/stanford-corenlp-models-XYZ.jar
    --with-corenlp-caseless-models=/path/to/stanford-corenlp-caseless-models-XYZ.jar

###Common Bugs

  - **I get an error 'Overflowed allocated HashIntMap buckets'.**
    This is caused by the number of facts overflowing the hash map
    buckets. You can fix this by setting FACT_MAP_SIZE to a value
    larger than 30 in the config file. Note that it can't go above
    32; if you're still getting the error, let me know!
