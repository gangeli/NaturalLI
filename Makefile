# LIKELY TO OVERWRITE
# -------------------
PG_CONFIG=pg_config
GTEST_ROOT=/home/gabor/lib/c/gtest
SCALA_HOME=/home/gabor/programs/scala
# -------------------

# -- VARIABLES --
# (programs)
JAVAC=javac
SCALAC=${SCALA_HOME}/bin/fsc
SCALA=${SCALA_HOME}/bin/scala
SCALADOC=${SCALA_HOME}/bin/scaladoc
# (locations)
SRC=src
TEST_SRC=test/src
LIB=lib
BUILD=build
TEST_BUILD=test/build
DIST=dist
TMP=tmp
DOC=scaladoc
# (classpaths)
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/research/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:${JAVANLP_HOME}/projects/scala-2.10/classes
CP=${JAVANLP}:lib/corenlp-scala.jar:lib/scripts/sim.jar:lib/scripts/jaws.jar:lib/scripts/trove.jar
# (c++)
CC = g++
INCLUDE=-I`${PG_CONFIG} --includedir`
LD_PATH=-L`${PG_CONFIG} --libdir`
LDFLAGS=-lpq
CPP_FLAGS=-ggdb -fprofile-arcs -ftest-coverage
# (files)
_OBJS = Search.o FactDB.o Graph.o Postgres.o
OBJS = $(patsubst %,${BUILD}/%,${_OBJS})
TEST_OBJS = $(patsubst %,${TEST_BUILD}/Test%,${_OBJS})


# -- Targets --
default: ${DIST}/truth.jar ${DIST}/server

client: ${DIST}/truth.jar
	./run

server: ${DIST}/server
	${DIST}/server

test: ${DIST}/test_server
	${DIST}/test_server

clean:
	rm -rf ${BUILD}
	rm -rf ${TEST_BUILD}
	rm -f ${DIST}/*
	rm -f java.hprof.txt
	rm -f *.gcno


# -- BUILD --
${DIST}/truth.jar: $(wildcard ${SRC}/org/goobs/truth/*.scala) $(wildcard ${SRC}/org/goobs/truth/*.java) $(wildcard ${SRC}/org/goobs/truth/scripts/*.scala) $(wildcard ${SRC}/org/goobs/truth/conf/*.conf)
	@mkdir -p ${BUILD}
	@echo "Compiling (${JAVAC})..."
	${JAVAC} -d ${BUILD} -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/typesafe-config.jar `find ${SRC} -name "*.java"`
	@echo "Compiling (${SCALAC})..."
	@${SCALAC} -feature -deprecation -d ${BUILD} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
	cp ${SRC}/org/goobs/truth/conf/* ${BUILD}/
	@mkdir -p ${DIST}
	jar cf ${DIST}/truth.jar -C $(BUILD) .
	jar uf ${DIST}/truth.jar -C $(SRC) .

${BUILD}/%.o: ${SRC}/%.cc
	@mkdir -p ${BUILD}
	${CC} -c ${INCLUDE} -o $@ $< -c ${CPP_FLAGS}

${DIST}/server: ${OBJS} ${BUILD}/InferenceServer.o $(wildcard ${SRC}/*.h)
	@mkdir -p ${DIST}
	g++ ${CPP_FLAGS} ${INCLUDE} -o ${DIST}/server $^ `find ${SRC} -name "*.h"` ${LD_PATH} ${LDFLAGS}
	mv -f *.gcno ${BUILD}

${DIST}/server.a: ${OBJS}
	ar rcs ${DIST}/server.a $^



# -- TEST --
${TEST_BUILD}/libgtest.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	g++ -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest-all.cc -o ${TEST_BUILD}/libgtest.a

${TEST_BUILD}/libgtest_main.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	g++ -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest_main.cc -o ${TEST_BUILD}/libgtest_main.a

${TEST_BUILD}/%.o: ${TEST_SRC}/%.cc
	@mkdir -p ${TEST_BUILD}
	${CC} -c ${INCLUDE} -isystem ${GTEST_ROOT}/include -o $@ $< -c ${CPP_FLAGS} -lgcov

${DIST}/test_server: ${DIST}/server.a ${TEST_OBJS} ${TEST_BUILD}/libgtest.a ${TEST_BUILD}/libgtest_main.a $(wildcard ${SRC}/*.h)
	@mkdir -p ${DIST}
	g++ ${CPP_FLAGS} ${INCLUDE} -isystem ${GTEST_ROOT}/include $^ `find ${TEST_SRC} -name "*.h"` ${LD_PATH} ${LDFLAGS} -pthread -o ${DIST}/test_server
	mv -f *.gcno ${TEST_BUILD}

#${DIST}/test_server: ${DIST}/server ${BUILD}/libgtest.a ${BUILD}/libgtest_main.a $(wildcard ${TEST_SRC}/*.cc) $(wildcard ${TEST_SRC}/*.h)
#	@mkdir -p ${DIST}
#	echo ${GCOV_PREFIX}
#	g++ -ggdb -fprofile-arcs -ftest-coverage -isystem ${GTEST_ROOT}/include -pthread `find ${TEST_SRC} -name "*.h"` `find ${TEST_SRC} -name "*.cc"` ${BUILD}/libgtest.a ${BUILD}/libgtest_main.a -lgcov -o ${DIST}/test_server 

doc:
	@echo "Documenting (${SCALADOC})..."
	mkdir -p ${DOC}
	@${SCALADOC} -d ${DOC} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`

# -- TARGETS --
