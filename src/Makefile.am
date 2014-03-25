SUBDIRS = fnv
bin_PROGRAMS=server

Messages.pb.h Messages.pb.cc: Messages.proto
	${PROTOC} -I=. --cpp_out=. Messages.proto

org/goobs/truth/Messages.java: Messages.proto
	${PROTOC} -I=. --java_out=. Messages.proto

clean-local:
	rm -f Messages.pb.cc Messages.pb.h org/goobs/truth/Messages.java

BUILT_SOURCES = Messages.proto

server_SOURCES=[ \
  Bloom.cc  Messages.pb.cc      Trie.cc \
  FactDB.cc Postgres.cc         Utils.cc \
  Graph.cc  InferenceServer.cc  Search.cc \
  Bloom.h   Graph.h        Utils.h \
  Config.h  Messages.pb.h  Search.h \
  FactDB.h  Postgres.h     Trie.h \
  ]

server_CXXFLAGS=-std=c++0x -pthread ${POSTGRESQL_CFLAGS} ${PROTO_CFLAGS}
server_LDADD=${POSTGRESQL_LDFLAGS} ${PROTO_LDFLAGS} -lprotobuf -Lfnv -lfnv32
if DEBUG
  server_CXXFLAGS+= -O0 -ggdb
  server_LDADD+=-lprofiler
else
  server_CXXFLAGS+= -O3
endif

client.jar: $(wildcard org/goobs/truth/*.scala) $(wildcard org/goobs/truth/*.java) $(wildcard org/goobs/truth/scripts/*.scala) $(wildcard org/goobs/truth/conf/*.conf) org/goobs/truth/Messages.java
	@echo "Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/typesafe-config.jar `find . -name "*.java"`
	@echo "Compiling (${SCALA_HOME}/bin/scalac)..."
	@${SCALA_HOME}/bin/scalac -feature -deprecation -d . -cp ${CP} `find . -name "*.scala"` `find . -name "*.java"`
	jar cf client.jar -C . .
