SUBDIRS = gtest

_OBJS_SPEC = Search.o FactDB.o Trie.o Graph.o Postgres.o Utils.o \
             Bloom.o Messages.pb.o
OBJ_NAMES = $(patsubst %,naturalli_server-%,${_OBJS_SPEC})
OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})

_TEST_OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})
TEST_SRC   = $(patsubst %.o,%.cc,${_TEST_OBJS})

MAIN_SRC=../../src

${MAIN_SRC}/%.o: ${MAIN_SRC}/%.cc ${MAIN_SRC}/%.h
	$(MAKE) -C ${MAIN_SRC} $<

# TESTS -- Programs run automatically by "make check"
TESTS = test_server
# check_PROGRAMS -- Programs built by "make check" but not necessarily run
check_PROGRAMS = test_server itest_server

AM_CPPFLAGS = -std=c++0x ${POSTGRESQL_CFLAGS} \
              -isystem gtest/include -I${MAIN_SRC} ${GTEST_CPPFLAGS}
AM_CXXFLAGS = ${GTEST_CXXFLAGS} -std=c++0x
AM_LDFLAGS  =  ${POSTGRESQL_LDFLAGS} -lprotobuf -L${MAIN_SRC}/fnv -lfnv32 \
               ${GTEST_LDFLAGS} ${GTEST_LIBS} \
               -Lgtest/lib -lgtest gtest/gtest_main.o


test_server_SOURCES = TestPostgres.cc TestBloom.cc TestRamCloudBackend.cc \
                      TestFactDB.cc TestSearch.cc  TestGraph.cc TestTrie.cc \
                      TestMessages.pb.cc  TestUtils.cc
test_server_LDADD =  ${OBJS}

itest_server_SOURCES= ITest.cc
itest_server_LDADD =  ${OBJS}

if HAVE_TCMALLOC
  test_server_LDADD += -ltcmalloc
  itest_server_LDADD += -ltcmalloc
endif
if DEBUG
  test_server_CXXFLAGS = -O0 -ggdb -fprofile-arcs -ftest-coverage -fPIC
  test_server_LDADD += -lprofiler -lgcov
  itest_server_CXXFLAGS = -O0 -ggdb -fprofile-arcs -ftest-coverage -fPIC
  itest_server_LDADD += -lprofiler -lgcov
endif
