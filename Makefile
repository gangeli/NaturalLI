# -------------------
# LIKELY TO OVERWRITE
# -------------------
PG_CONFIG=pg_config
SCALA_HOME=/home/gabor/programs/scala
RAMCLOUD_HOME=/home/gabor/workspace/ramcloud
GTEST_ROOT=${RAMCLOUD_HOME}/gtest
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
CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim.jar:${LIB}/scripts/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar
# (c++)
CC = g++
INCLUDE=-I`${PG_CONFIG} --includedir` -I${RAMCLOUD_HOME}/src -I${RAMCLOUD_HOME}/obj.master -I${RAMCLOUD_HOME}/logcabin -I${GTEST_ROOT}/include
LD_PATH=-L`${PG_CONFIG} --libdir` -Llib
LDFLAGS=-lpq -lramcloud -lprofiler -lprotobuf
CPP_FLAGS=-ggdb -fprofile-arcs -ftest-coverage -std=c++0x
# (files)
_OBJS = Search.o FactDB.o Graph.o Postgres.o RamCloudBackend.o Utils.o Messages.pb.o
OBJS = $(patsubst %,${BUILD}/%,${_OBJS})
TEST_OBJS = $(patsubst %,${TEST_BUILD}/Test%,${_OBJS})


# -- TARGETS --
default: ${DIST}/truth.jar ${DIST}/server

test: ${DIST}/test_server
	${DIST}/test_server --gtest_output=xml:build/test.junit.xml

client: ${DIST}/truth.jar
	./run

server: ${DIST}/server
	${DIST}/server

clean:
	rm -rf ${BUILD}
	rm -rf ${TEST_BUILD}
	rm -f ${DIST}/*
	rm -f ${SRC}/org/goobs/truth/Messages.java
	rm -f ${SRC}/Messages.pb.h
	rm -f ${SRC}/Messages.pb.cc
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda


# -- BUILD --
# (dependenceies)
${SRC}/org/goobs/truth/Messages.java: ${SRC}/Messages.proto
	protoc -I=${SRC} --java_out=${SRC}/ ${SRC}/Messages.proto

${SRC}/Messages.pb.h: ${SRC}/Messages.proto
	protoc -I=${SRC} --cpp_out=${SRC}/ ${SRC}/Messages.proto

${SRC}/Messages.pb.cc: ${SRC}/Messages.proto
	protoc -I=${SRC} --cpp_out=${SRC}/ ${SRC}/Messages.proto

${BUILD}/%.o: ${SRC}/%.cc ${SRC}/%.h ${SRC}/Config.h
	@mkdir -p ${BUILD}
	${CC} ${CPP_FLAGS} -c ${INCLUDE} -o $@ $< -c ${CPP_FLAGS}

${DIST}/server.a: ${OBJS}
	ar rcs ${DIST}/server.a $^

# (client)
${DIST}/truth.jar: $(wildcard ${SRC}/org/goobs/truth/*.scala) $(wildcard ${SRC}/org/goobs/truth/*.java) $(wildcard ${SRC}/org/goobs/truth/scripts/*.scala) $(wildcard ${SRC}/org/goobs/truth/conf/*.conf) ${SRC}/org/goobs/truth/Messages.java
	@mkdir -p ${BUILD}
	@echo "Compiling (${JAVAC})..."
	${JAVAC} -d ${BUILD} -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/typesafe-config.jar `find ${SRC} -name "*.java"`
	@echo "Compiling (${SCALAC})..."
	@${SCALAC} -feature -deprecation -d ${BUILD} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
	cp ${SRC}/org/goobs/truth/conf/* ${BUILD}/
	@mkdir -p ${DIST}
	jar cf ${DIST}/truth.jar -C $(BUILD) .
	jar uf ${DIST}/truth.jar -C $(SRC) .

# (server)
${DIST}/server: ${OBJS} ${BUILD}/InferenceServer.o $(wildcard ${SRC}/*.h)
	@mkdir -p ${DIST}
	${CC} ${CPP_FLAGS} ${INCLUDE} -o ${DIST}/server $^ `find ${SRC} -name "*.h"` ${LD_PATH} ${LDFLAGS}
	mv -f *.gcno ${BUILD}


# -- TEST --
${TEST_BUILD}/libgtest.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest-all.cc -o ${TEST_BUILD}/libgtest.a

${TEST_BUILD}/libgtest_main.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest_main.cc -o ${TEST_BUILD}/libgtest_main.a

${TEST_BUILD}/%.o: ${TEST_SRC}/%.cc
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -c ${INCLUDE} -Isrc -o $@ $< -c ${CPP_FLAGS} -lgcov

${DIST}/test_server: ${DIST}/server.a ${TEST_OBJS} ${TEST_BUILD}/libgtest.a ${TEST_BUILD}/libgtest_main.a $(wildcard ${SRC}/*.h)
	@mkdir -p ${DIST}
	${CC} ${CPP_FLAGS} ${INCLUDE} -Isrc $^ ${DIST}/server.a `find ${TEST_SRC} -name "*.h"` ${LD_PATH} ${LDFLAGS} -pthread -o ${DIST}/test_server
	mv -f *.gcno ${TEST_BUILD}

doc:
	@echo "Documenting (${SCALADOC})..."
	mkdir -p ${DOC}
	@${SCALADOC} -d ${DOC} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
