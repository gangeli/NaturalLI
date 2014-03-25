SUBDIRS = gtest

# TESTS -- Programs run automatically by "make check"
TESTS = test_server
# check_PROGRAMS -- Programs built by "make check" but not necessarily run
check_PROGRAMS = test_server itest_server

MAIN_SRC=../../src

AM_CPPFLAGS = ${GTEST_CXXFLAGS} \
              -std=c++0x ${POSTGRESQL_CFLAGS} \
              -isystem gtest/include -I${MAIN_SRC} ${GTEST_CPPFLAGS}
AM_LDFLAGS =  ${POSTGRESQL_LDFLAGS} -lprotobuf -L${MAIN_SRC}/fnv -lfnv32 \
              ${GTEST_LDFLAGS} ${GTEST_LIBS} \
              -Lgtest/lib -lgtest gtest/gtest_main.o

_OBJS = Search.o FactDB.o Trie.o Graph.o Postgres.o Utils.o \
        Bloom.o Messages.pb.o
OBJS = $(patsubst %,${MAIN_SRC}/server-%,${_OBJS})

_TEST_OBJS = $(patsubst %,${MAIN_SRC}/server-%,${_OBJS})
TEST_OBJS = $(patsubst %.o,%.cc,${_TEST_OBJS})


test_server_SOURCES= [ \
  TestPostgres.cc TestBloom.cc TestRamCloudBackend.cc \
  TestFactDB.cc TestSearch.cc  TestGraph.cc TestTrie.cc \
  TestMessages.pb.cc  TestUtils.cc \
  ]
test_server_LDADD =  ${OBJS}

itest_server_SOURCES= [ ITest.cc ]
itest_server_LDADD =  ${OBJS}
