MAIN_SRC=../../src
SUBDIRS = gtest ../../src/fnv ../../src/knheap
EXTRA_DIST = edu

clean-local:
	rm -f *.gcno *.gcda

_OBJS_SPEC = Graph.o Utils.o GZip.o Models.o \
             SynSearch.o SynSearchSingleThreaded.o
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
AM_LDFLAGS  =  -L${MAIN_SRC}/fnv -lfnv32 -lfnv64 \
               -L${MAIN_SRC}/knheap -lknheap \
               ${GTEST_LDFLAGS} ${GTEST_LIBS} \
               -Lgtest/lib -lgtest gtest/gtest_main.o


test_server_SOURCES = TestGraph.cc TestGZip.cc \
                      TestUtils.cc TestTypes.cc \
                      TestSynSearch.cc TestModels.cc
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

test_preprocess.jar: $(wildcard edu/stanford/nlp/naturalli/*.java) 
	$(MAKE) -C ../../src naturalli_preprocess.jar
	@echo "[test] Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp ${TEST_CP}:../../src/naturalli_preprocess.jar `find . -name "*.java"`
	${JDK_HOME}/bin/jar cf test_preprocess.jar `find . -name "*.class"`
	${JDK_HOME}/bin/jar uf test_preprocess.jar `find ../data/ -name "*"`
	${JDK_HOME}/bin/jar uf test_preprocess.jar `find . -name "*.java"`
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'
