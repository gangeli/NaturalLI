NaturalLI
===========
**Natural [Language|Logic] Inference**

[![Build status](http://goobs.org/TeamCity/app/rest/builds/buildType:NaturalLI_CTests/statusIcon "Build status")](http://goobs.org/TeamCity/viewType.html?buildTypeId=NaturalLI_CTests&guest=1)

NaturalLI is a Natural Logic reasoning engine aimed at fast inference
from a large database of known facts.
The project's primary goal is to infer whether arbitrary common-sense
facts are true, given a large database of known facts.
The system is described in:

    Gabor Angeli and Christopher D. Manning. "NaturalLI: Natural Logic for Common Sense Reasoning." EMNLP 2014.



Installation
<<<<<<< HEAD
------------

#### Quick Start
On Ubuntu, the program can be built (including all dependencies) with:

```bash
./install_deps.sh  # optional; read this before running it!
./autogen.sh
./configure
make
make install  # optional; make sure to set --prefix appropriately
```

#### Compiler Versions

The code compiles with both `g++` (4.8+; 4.9+ highly recommended) 
and `clang++` (4.5+).
With GCC 4.8, regular expressions will not work properly, which means
sending flags to the C program will not work properly (an exception will
be raised and the program will crash).

On Ubuntu, you can install g++ 4.9 with:

    sudo add-apt-repository ppa:ubuntu-toolchain-r/test
    sudo apt-get update
    sudo apt-get install g++-4.9

Clang should already be at a new enough version as of Ubuntu 14.04 and onwards.

#### Configuration Options

The following is a list of options that can be passed into the configure script,
and a description of each.

  - `--enable-debug`: Enables code coverage and assertions.
  
  - `--with-java`: The root of the Java runtime to use. This should be Java 8+,
     and must include both `java` and `javac` (i.e., not just a JRE).
  
  - `--with-corenlp`: The location of CoreNLP -- generally, a Jar file.
                      This must be CoreNLP Version 3.5.3 or newer.
                      *Must be an absolute path!*

  - `--with-corenlp-models`: The location of the CoreNLP models jar.
                             *Must be an absolute path!*
=======
----------
This section describes the installation of the system.
Now, before anyone starts complaining about how long
this section is, or how autoconf is awful
(true story: all build systems are awful), you're always
entitled to try the version I use at home:

    ./autogen.sh
    ./configure CXX=clang++-3.4
    make -j4
    make src/naturalli_client.jar
    ./run FraCaS

The rest of the section can be thought of as debugging help.
Both the server and client are configured using autoconf and built
using Make.
The rest of this section will describe the prerequisites you'll need
installed on your system, followed by instructions on configuring
and installing the server and client component.

###Quick Version
This section is intended to be a summary of what follows.
If all goes well, running the following should get you up and running:

    # Set up code
    git clone git@github.com:gangeli/NaturalLI.git
    cd NaturalLI

    # [optional] Configure CoreNLP
    curl 'http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar' > lib/corenlp-models.jar
    curl 'http://nlp.stanford.edu/software/stanford-corenlp-caseless-models-current.jar' > lib/corenlp-caseless-models.jar
    git clone git@github.com:stanfordnlp/CoreNLP.git
    cd CoreNLP
    ant jar
    cd ..
    mv CoreNLP/javanlp-core.jar lib/corenlp.jar

    # Configure server (fill in variables)
    # Some of these can be omitted if they are in your path
    ./autogen.sh
    ./configure --with-protoc=/path/to/protoc/root/ \
                --with-postgresql=/path/to/pg_config \
                --with-scala=/path/to/scala/home/ \
                --with-java=/path/to/jdk/home/ \
                --with-corenlp=/path/to/stanford-corenlp.jar \
                --with-corenlp-models=/path/to/corenlp-models.jar \
                --with-corenlp-caseless-models=/path/to/corenlp-caseless-models.jar \
                --prefix=/installation/directory/ \
                CXX=/path/to/g++ \
                PGHOST=hostname PGPORT=port DBNAME=naturalli \
                PGUSER=user PGPASSWORD=pass \
                GREEDY_LOAD=1
   
    # Get Postgres data
    # This assumes you have a Postgres instance, and a database called `naturalli`
    export DBNAME=naturalli
    curl 'http://nlp.stanford.edu/projects/naturalli/naturalli_schema.sql' > psql
    curl 'http://nlp.stanford.edu/projects/naturalli/naturalli_data.sql.gz' | gunzip > psql
    curl 'http://nlp.stanford.edu/projects/naturalli/naturalli_indexes.sql' > psql

    # Start server
    make
    src/naturalli_server

    # Evaluate (with lookup)
    ./run Evaluate src/org/goobs/truth/conf/conceptnet.conf
    
    # Evaluate (without lookup)
    cat src/org/goobs/truth/conf/conceptnet.conf | sed -e 's/#search.allowlookup/search.allowlookup/g' > /tmp/conceptnet.conf
    mv /tmp/conceptnet.conf src/org/goobs/truth/conf/
    ./run Evaluate src/org/goobs/truth/conf/conceptnet.conf

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
>>>>>>> master

  - `--with-corenlp-caseless-models`: The location of the CoreNLP caseless 
                                       models jar.
                                       *Must be an absolute path!*


In addition, a number of environment variables are relevant for the configure
script.
These are listed below:

  - `CXX`: The C compiler to use. Both `g++` (4.8+; 4.9+ highly recommended) 
     and `clang++` (3.5+) should compile.

  - `MAX_FACT_LENGTH`: The maximum number of tokens a fact can be. This has to be
     less than 255.

  - `MAX_QUERY_LENGTH`: The maximum length of a query. Note that memory during
     search scales linearly with this value. Default is 39.
     Default value has nice cache-alignment properties.
  
  - `MAX_QUANTIFIER_COUNT`: The maximum number of quantifiers in a sentence.
     Note that search memory and search runtime scales linearly with this.
     Default is 6. Default value has nice cache-alignment properties.
  
  - `SERVER_PORT`: The port to serve the server from. Default is 1337.
  
  - `SEARCH_TIMEOUT`: The maximum number of ticks to search for.
     Default is 1000000.
  
  - `SEARCH_CYCLE_MEMORY`: The depth to backtrack during seach looking for cycles.
     Lower values cause search to run faster, but nodes to be repeated more often.
     Default is 3.

  - `SEARCH_CYCLE_FULL_MEMORY`: Check all the way back to the start node for cycles.
    Default is false (0).
  
  - `MAX_FUZZY_MATCHES`: The number of premises to consider from the explicit premise
     set for fuzzy alignment matches. Default is 0.

  - `MAX_BRANCHOUT`: The maximum branching factor of the graph search. This is to
     prevent runaway nodes from taking too much time. Default is 100.



Usage
-----

#### Command Line Inference
The NaturalLI interface takes as input lines from standard in, and outputs
JSON output to standard out (with debugging information on stderr).
You can therefore run the program simply by running:

```bash
src/naturalli
```

You can then enter premise/hypothesis pairs as a series of sentences,
one per line. All but the last sentence are treated as a premise; the last
sentence is treated as the hypothesis.
A double-newline (i.e., a blank line) marks the end of an example.
You can also mark hypotheses as True or False; this will cause the program
to exit with a nonzero error code if some of the hypothesis are not
what they are annotated as.
The error code corresponds to the number of failed examples.

For example:

```
# This is a comment
All cats have tails
Some cats have tails

# This is a new example
# The "True: " prepended to the hypothesis denotes that we expect
# this fact to be true given the premises
Some cats have tails
An irrelevant premise
True: Some animals have tails

```

A useful side-effect of this framework is the ability to pipe in test files,
and get as output annotated json.
For example, the test cases for the program can be run with:

```bash
cat test/data/testcase_wordnet.examples | src/naturalli
```


#### Internet Inference
In addition to the command line interface, you can also talk to the program
via a websocket.
By default, NaturalLI listens on port 1337; the communication protocol
is exactly the same as the command line interface.
For example:

```
$ telnet localhost 1337
Trying 127.0.0.1...
Connected to localhost (127.0.0.1).
Escape character is '^]'.
all cats have tails
some cats have tails

```
