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

default: ${DIST}/truth.jar ${DIST}/server

check:
	ls ${POSTGRES_ROOT} | grep libpq-fe.h


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

${DIST}/server: $(wildcard ${SRC}/*.cc) $(wildcard ${SRC}/*.h)
	@mkdir -p ${BUILD}
	@mkdir -p ${DIST}
	g++ -ggdb -I`${PG_CONFIG} --includedir` `find ${SRC} -name "*.h"` `find ${SRC} -name "*.cc"` -L`${PG_CONFIG} --libdir` -lpq -o ${DIST}/server 

${TEST_BUILD}/libgtest.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	g++ -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest-all.cc -o ${TEST_BUILD}/libgtest.a

${TEST_BUILD}/libgtest_main.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	g++ -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest_main.cc -o ${TEST_BUILD}/libgtest_main.a

${DIST}/test_server: ${TEST_BUILD}/libgtest.a ${TEST_BUILD}/libgtest_main.a $(wildcard ${TEST_SRC}/*.cc) $(wildcard ${TEST_SRC}/*.h)
	@mkdir -p ${DIST}
	g++ -ggdb -isystem ${GTEST_ROOT}/include -pthread `find ${TEST_SRC} -name "*.h"` `find ${TEST_SRC} -name "*.cc"` ${TEST_BUILD}/libgtest.a ${TEST_BUILD}/libgtest_main.a -o ${DIST}/test_server 

doc:
	@echo "Documenting (${SCALADOC})..."
	mkdir -p ${DOC}
	@${SCALADOC} -d ${DOC} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`

# -- TARGETS --
test: ${DIST}/test_server

run: ${DIST}/truth.jar
	./run


clean:
	rm -rf ${BUILD}
	rm -rf ${TEST_BUILD}
	rm -f ${DIST}/truth.jar
	rm -f ${DIST}/server
	rm -f ${DIST}/test_server
	rm -f java.hprof.txt
