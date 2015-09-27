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

### Quick Start
On Ubuntu, the program can be built (including all dependencies) with:

    ./install_deps.sh
    ./autogen.sh
    ./configure
    make

### Compiler Versions

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
