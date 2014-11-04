MAIN_SRC=../../src
SUBDIRS = gtest ../../src/fnv ../../src/knheap
EXTRA_DIST = edu org

clean-local:
	rm -f *.gcno *.gcda

_OBJS_SPEC = Graph.o Postgres.o Utils.o \
             SynSearch.o SynSearchSingleThreaded.o Messages.pb.o
OBJ_NAMES = $(patsubst %,naturalli-%,${_OBJS_SPEC})
OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})

_TEST_OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})
TEST_SRC   = $(patsubst %.o,%.cc,${_TEST_OBJS})


${MAIN_SRC}/%.o: ${MAIN_SRC}/%.cc ${MAIN_SRC}/%.h
	$(MAKE) -C ${MAIN_SRC} $<

# TESTS -- Programs run automatically by "make check"
TESTS = test_server
# check_PROGRAMS -- Programs built by "make check" but not necessarily run
check_PROGRAMS = test_server itest_server

AM_CPPFLAGS = -std=c++0x ${POSTGRESQL_CFLAGS} \
              -isystem gtest/include -I${MAIN_SRC} ${GTEST_CPPFLAGS}
AM_CXXFLAGS = ${GTEST_CXXFLAGS} -std=c++0x
AM_LDFLAGS  =  ${POSTGRESQL_LDFLAGS} -lprotobuf \
               -L${MAIN_SRC}/fnv -lfnv32 -lfnv64 \
               -L${MAIN_SRC}/knheap -lknheap \
               ${GTEST_LDFLAGS} ${GTEST_LIBS} \
               -Lgtest/lib -lgtest gtest/gtest_main.o


test_server_SOURCES = TestPostgres.cc TestGraph.cc \
                      TestMessages.pb.cc  TestUtils.cc TestTypes.cc \
                      TestSynSearch.cc
test_server_LDADD =  ${OBJS}

itest_server_SOURCES= ITest.cc
itest_server_LDADD =  ${OBJS}

if HAVE_TCMALLOC
  test_server_LDADD += -ltcmalloc
  itest_server_LDADD += -ltcmalloc
if DEBUG
  test_server_LDADD += -lprofiler
  itest_server_LDADD += -lprofiler
if IS_GCC
  test_server_CXXFLAGS = -fprofile-arcs -ftest-coverage -fPIC
  itest_server_LDADD += -lgcov
endif
endif
endif

test_preprocess.jar: $(wildcard org/goobs/naturalli/*.scala) $(wildcard org/goobs/naturalli/*.java) $(wildcard org/goobs/naturalli/*.dat) $(wildcard edu/stanford/nlp/natlog/*.java) $(wildcard org/goobs/naturalli/*.java) 
	$(MAKE) -C ../../src naturalli_preprocess.jar
	@echo "[test] Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp ${TEST_CP}:../../src/naturalli_preprocess.jar:${SCALA_HOME}/lib/scala-library.jar `find . -name "*.java"`
	@echo "[test] Compiling (${SCALA_HOME}/bin/scalac)..."
	@${SCALA_HOME}/bin/scalac -feature -deprecation -d . -cp ${TEST_CP}:../../src/naturalli_preprocess.jar `find . -name "*.scala"` `find . -name "*.java"`
	${JDK_HOME}/bin/jar cf test_preprocess.jar `find . -name "*.class"`
	${JDK_HOME}/bin/jar uf test_preprocess.jar `find . -name "*.dat"`
	${JDK_HOME}/bin/jar uf test_preprocess.jar `find . -name "*.scala"`
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'
