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
