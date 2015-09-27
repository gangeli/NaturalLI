NaturalLI
===========
**Natural [Language|Logic] Inference**

NaturalLI is a Natural Logic reasoning engine aimed at fast inference
from a large database of known facts.
The project's primary goal is to infer whether arbitrary common-sense
facts are true, given a large database of known facts.
The system is described in:

    Gabor Angeli and Christopher D. Manning. "NaturalLI: Natural Logic for Common Sense Reasoning." EMNLP 2014.



Installation
------------

#### Quick Start
On Ubuntu, the program can be built (including all dependencies) with:

    ./install_deps.sh
    ./autogen.sh
    ./configure
    make

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

  - `--with-corenlp-models`: The location of the CoreNLP models jar.

  - `--with-corenlp-caseless-models`: The location of the CoreNLP caseless 
    models jar.


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



