

Compilation
-----------

If you're an optimist, the following might even work:
    
    ./configure
    make check


Otherwise, likely you'll have to set a few options to configure.
For example:
    
    --with-scala=/path/to/SCALA_HOME    # scalac should be in bin/
    --with-java=/path/to/JDK_HOME       # javac should be in bin/
    --with-protoc=/path/to/PROTOC_HOME  # protoc should be in bin/protoc

    -DNDEBUG  # disable debugging (for performance)


The code also compiles with clang (3.4 or later has been teste);
to use this feature, use:
    
    ./configure CXX=clang++
