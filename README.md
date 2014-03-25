

Compilation
===========

If you're an optimist, the following might even work:
    
    ./configure
    make check


Otherwise, likely you'll have to set a few options to configure.
For example:
    
    ./configure --with-scala=/path/to/scala


The code also compiles with clang (3.4 or later has been teste);
to use this feature, use:
    
    ./configure CXX=clang++
