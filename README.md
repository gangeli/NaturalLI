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

  - 


In addition, a number of environment variables are relevant for the configure
script.
These are listed below:

  - `JDK_HOME`: The root of the Java runtime to use. This should be Java 8+,
     and must include both `java` and `javac` (i.e., not just a JRE).

  - `CORENLP`: The location of CoreNLP -- generally, a Jar file.
    This must be CoreNLP Version 3.5.3 or newer.

  - `CORENLP_MODELS`: The location of the CoreNLP models jar.

  - `CORENLP_CASELESS_MODELS`: The location of the CoreNLP caseless models jar.

  - `CXX`: The C compiler to use. Both `g++` (4.8+; 4.9+ highly recommended) 
     and `clang++` (3.5+) should compile.



