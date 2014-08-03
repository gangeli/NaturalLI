SUBDIRS = fnv
bin_PROGRAMS=naturalli_server

Messages.pb.h Messages.pb.cc: Messages.proto
	${PROTOC} -I=. --cpp_out=. Messages.proto

org/goobs/naturalli/Messages.java: Messages.proto
	${PROTOC} -I=. --java_out=. Messages.proto

clean-local:
	rm -f Messages.pb.cc Messages.pb.h org/goobs/naturalli/Messages.java
	rm -f *.gcno *.gcda
	rm -f naturalli.war

BUILT_SOURCES = Messages.proto

naturalli_server_SOURCES = Bloom.cc  Messages.pb.cc      Trie.cc \
                 FactDB.cc Postgres.cc         Utils.cc \
                 Graph.cc  InferenceServer.cc  Search.cc \
                 Map.cc \
                 Bloom.h   Graph.h        Utils.h \
                 Types.h   Messages.pb.h  Search.h \
                 FactDB.h  Postgres.h     Trie.h \
                 Map.h

naturalli_server_CXXFLAGS=-std=c++0x -pthread ${POSTGRESQL_CFLAGS} ${PROTO_CFLAGS} ${OPENMP_CFLAGS}
naturalli_server_LDADD=${POSTGRESQL_LDFLAGS} ${PROTO_LDFLAGS} -lprotobuf -Lfnv -lfnv32 -lfnv64
if HAVE_TCMALLOC
  naturalli_server_LDADD += -ltcmalloc
endif

if DEBUG
  naturalli_server_LDADD += -lprofiler
if IS_GCC
  naturalli_server_CXXFLAGS += -fprofile-arcs -ftest-coverage -fPIC
  naturalli_server_LDADD += -lprofiler -lgcov
endif
endif

naturalli_client.jar: $(wildcard org/goobs/naturalli/*.scala) $(wildcard org/goobs/naturalli/*.java) $(wildcard org/goobs/naturalli/scripts/*.scala) $(wildcard org/goobs/naturalli/demo/*.java) $(wildcard org/goobs/natualli/conf/*.conf) org/goobs/naturalli/Messages.java
	@echo "Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/config-1.2.0.jar `find . -name "*.java" -not -path "./org/goobs/naturalli/demo/*.java"`
	@echo "Compiling (${SCALA_HOME}/bin/scalac)..."
	@${SCALA_HOME}/bin/scalac -feature -deprecation -d . -cp ${CP} `find . -name "*.scala"` `find . -name "*.java"`
	@echo "Compiling Demo (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp .:${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/config-1.2.0.jar `find ./org/goobs/naturalli/demo -name "*.java"`
	${JDK_HOME}/bin/jar cf naturalli_client.jar -C . .
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

naturalli.war: naturalli_client.jar $(wildcard org/goobs/naturalli/demo/*.java) 
	@echo "Creating WAR..."
	cd ../ && ant war
