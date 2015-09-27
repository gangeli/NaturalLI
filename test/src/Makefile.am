MAIN_SRC=../../src
SUBDIRS = gtest ../../src/fnv ../../src/knheap
EXTRA_DIST = edu

clean-local:
	rm -f *.gcno *.gcda

_OBJS_SPEC = Graph.o Utils.o GZip.o Models.o \
             SynSearch.o SynSearchSingleThreaded.o \
						 FactDB.o Types.o
OBJ_NAMES = $(patsubst %,naturalli-%,${_OBJS_SPEC})
OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})

_TEST_OBJS = $(patsubst %,${MAIN_SRC}/%,${OBJ_NAMES})
TEST_SRC   = $(patsubst %.o,%.cc,${_TEST_OBJS})


${MAIN_SRC}/%.o: ${MAIN_SRC}/%.cc ${MAIN_SRC}/%.h
	$(MAKE) -C ${MAIN_SRC} $<

# TESTS -- Programs run automatically by "make check"
TESTS = naturalli_test
# check_PROGRAMS -- Programs built by "make check" but not necessarily run
check_PROGRAMS = naturalli_test naturalli_itest

AM_CPPFLAGS = -std=c++0x ${POSTGRESQL_CFLAGS} \
              -isystem gtest/include -I${MAIN_SRC} ${GTEST_CPPFLAGS}
AM_CXXFLAGS = ${GTEST_CXXFLAGS} -std=c++0x
AM_LDFLAGS  =  -L${MAIN_SRC}/fnv -lfnv32 -lfnv64 \
               -L${MAIN_SRC}/knheap -lknheap \
               ${GTEST_LDFLAGS} ${GTEST_LIBS} \
               -Lgtest/lib -lgtest gtest/gtest_main.o


naturalli_test_SOURCES = TestGraph.cc TestGZip.cc \
                         TestUtils.cc TestTypes.cc \
                         TestSynSearch.cc TestModels.cc \
							   				 TestFactDB.cc
naturalli_test_LDADD =  ${OBJS}

naturalli_itest_SOURCES= ITest.cc
naturalli_itest_LDADD =  ${OBJS}

if HAVE_TCMALLOC
  naturalli_test_LDADD += -ltcmalloc
  naturalli_itest_LDADD += -ltcmalloc
endif
if DEBUG
if IS_GCC
  naturalli_test_CXXFLAGS = -fprofile-arcs -ftest-coverage -fPIC
  naturalli_itest_LDADD += -lgcov
endif
endif

naturalli_preprocess_test.jar: $(wildcard edu/stanford/nlp/naturalli/*.java) 
	$(MAKE) -C ../../src naturalli_preprocess.jar
	@echo "[test] Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d . -cp ${TEST_CP}:../../src/naturalli_preprocess.jar `find . -name "*.java"`
	${JDK_HOME}/bin/jar cf naturalli_preprocess_test.jar `find . -name "*.class"`
	${JDK_HOME}/bin/jar uf naturalli_preprocess_test.jar `find ../data/ -name "*"`
	${JDK_HOME}/bin/jar uf naturalli_preprocess_test.jar `find . -name "*.java"`
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'
