SUBDIRS = fnv
bin_PROGRAMS=truth

Messages.pb.h Messages.pb.cc: Messages.proto
	${PROTOC} -I=. --cpp_out=. Messages.proto

BUILT_SOURCES = Messages.proto

truth_SOURCES=[ \
  Bloom.cc  Messages.pb.cc      Trie.cc \
  FactDB.cc Postgres.cc         Utils.cc \
  Graph.cc  InferenceServer.cc  Search.cc \
  Bloom.h   Graph.h        Utils.h \
  Config.h  Messages.pb.h  Search.h \
  FactDB.h  Postgres.h     Trie.h \
  ]

truth_CPPFLAGS=-std=c++0x ${POSTGRESQL_CFLAGS}
truth_LDADD=${POSTGRESQL_LDFLAGS} -lprotobuf -Lfnv -lfnv32
if DEBUG
  truth_CPPFLAGS+= -O0 -ggdb
  truth_LDADD+=-lprofiler
else
  truth_CPPFLAGS+= -O3
endif

clean-local:
	rm -f org/goobs/truth/Messages.java
	rm -f Messages.pb.h
	rm -f Messages.pb.cc
