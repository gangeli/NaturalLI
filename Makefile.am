SUBDIRS = src/fnv

ACLOCAL_AMFLAGS=-I m4

bin_PROGRAMS=truth

truth_SOURCES=[ \
  src/Bloom.cc            src/Messages.pb.cc      src/Trie.cc \
  src/FactDB.cc           src/Postgres.cc         src/Utils.cc \
  src/Graph.cc            src/InferenceServer.cc  src/Search.cc \
  src/Bloom.h   src/Graph.h        src/Utils.h \
  src/Config.h  src/Messages.pb.h  src/Search.h \
  src/FactDB.h  src/Postgres.h     src/Trie.h \
  ]

truth_CPPFLAGS=-std=c++0x ${POSTGRESQL_CFLAGS}
truth_LDADD=${POSTGRESQL_LDFLAGS} -lprotobuf -Lsrc/fnv -lfnv32

if DEBUG
  truth_CPPFLAGS+= -O0 -ggdb
  truth_LDADD+=-lprofiler
else
  truth_CPPFLAGS+= -O3
endif
