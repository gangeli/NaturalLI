# -------------------
# LIKELY TO OVERWRITE
# -------------------
# In addition, note that protoc must be set up
# on the machine. This can be done by setting the $PROTOC
# environment variable, and adding the include path
# to $CUSTOM_I and $CUSTOM_L.
#
# For example:
#   export CUSTOM_L=='-L/u/nlp/packages/protobuf-2.4.1/lib'
#   export CUSTOM_I='-I/u/nlp/packages/protobuf-2.4.1/include'
#   export RAMCLOUD_HOME='/u/angeli/workspace/ramcloud'
#   export PROTOC='/u/nlp/packages/protobuf-2.4.1/bin/protoc'
# -------------------
PG_CONFIG?=pg_config
SCALA_HOME?=/home/gabor/programs/scala
RAMCLOUD_HOME?=/home/gabor/workspace/ramcloud
GTEST_ROOT?=${RAMCLOUD_HOME}/gtest
PROTOC?=protoc
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
CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim.jar:${LIB}/scripts/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar:${LIB}/postgresql.jar:${LIB}/typesafe-config.jar:${LIB}/demo/gson.jar:${LIB}/demo/servlet-api.jar
TEST_CP=${CP}:${LIB}/test/scalatest.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar
# (c++)
CUSTOM_INCLUDES?=''
CC = g++
INCLUDE=-I`${PG_CONFIG} --includedir` -I${RAMCLOUD_HOME}/src -I${RAMCLOUD_HOME}/obj.master -I${RAMCLOUD_HOME}/logcabin -I${GTEST_ROOT}/include ${CUSTOM_I}
LD_PATH=-L`${PG_CONFIG} --libdir` -Llib ${CUSTOM_L}
LDFLAGS=-lpq -lramcloud -lprotobuf #-lprofiler
CPP_FLAGS=-fprofile-arcs -ftest-coverage -std=c++0x -O3
# (files)
_OBJS = Search.o FactDB.o Graph.o Postgres.o Utils.o Bloom.o Messages.pb.o #RamCloudBackend.o
OBJS = $(patsubst %,${BUILD}/%,${_OBJS})
TEST_OBJS = $(patsubst %,${TEST_BUILD}/Test%,${_OBJS})


# -- TARGETS --
default: ${DIST}/client.jar ${DIST}/server
all: ${DIST}/client.jar ${DIST}/server ${DIST}/test_client.jar ${DIST}/test_server

client: ${DIST}/client.jar
	${SCALA} -cp ${CP}:${DIST}/client.jar -J-mx4g org.goobs.truth.Client

server: ${DIST}/server
	${DIST}/server

test: ${DIST}/test_server ${DIST}/test_client.jar
	${DIST}/test_server --gtest_output=xml:build/test.junit.xml
	${SCALA} -cp ${TEST_CP}:${DIST}/test_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.scalatest.tools.Runner -R ${DIST}/test_client.jar -o -w org.goobs.truth

clean:
	$(MAKE) -C ${SRC}/fnv/ clean
	rm -rf ${BUILD}
	rm -rf ${TEST_BUILD}
	rm -f ${DIST}/*
	rm -f ${SRC}/org/goobs/truth/Messages.java
	rm -f ${SRC}/Messages.pb.h
	rm -f ${SRC}/Messages.pb.cc
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda


# -- DEPENDENCIES --
${SRC}/fnv/longlong.h:
	$(MAKE) -C ${SRC}/fnv/ longlong.h

${SRC}/fnv/libfnv.a: $(wildcard ${SRC}/fnv/*.c) $(wildcard ${SRC}/fnv/*.h)
	$(MAKE) -C ${SRC}/fnv/ libfnv.a


# -- BUILD --
# (dependenceies)
${SRC}/org/goobs/truth/Messages.java: ${SRC}/Messages.proto
	echo "protoc at '${PROTOC}'"
	${PROTOC} -I=${SRC} --java_out=${SRC}/ ${SRC}/Messages.proto

${SRC}/Messages.pb.h: ${SRC}/Messages.proto
	echo "protoc at '${PROTOC}'"
	${PROTOC} -I=${SRC} --cpp_out=${SRC}/ ${SRC}/Messages.proto

${SRC}/Messages.pb.cc: ${SRC}/Messages.proto
	echo "protoc at '${PROTOC}'"
	${PROTOC} -I=${SRC} --cpp_out=${SRC}/ ${SRC}/Messages.proto

${BUILD}/InferenceServer.o: ${SRC}/InferenceServer.cc ${SRC}/Config.h
	@mkdir -p ${BUILD}
	${CC} ${CPP_FLAGS} -c ${INCLUDE} -o $@ $< -c ${CPP_FLAGS}

${BUILD}/%.o: ${SRC}/%.cc ${SRC}/%.h ${SRC}/Config.h ${SRC}/fnv/libfnv.a ${SRC}/fnv/longlong.h
	@mkdir -p ${BUILD}
	${CC} ${CPP_FLAGS} -c ${INCLUDE} -o $@ $< -c ${CPP_FLAGS}

${DIST}/server.a: ${OBJS}
	ar rcs ${DIST}/server.a $^ ${SRC}/fnv/libfnv.a 

# (client)
${DIST}/client.jar: $(wildcard ${SRC}/org/goobs/truth/*.scala) $(wildcard ${SRC}/org/goobs/truth/*.java) $(wildcard ${SRC}/org/goobs/truth/scripts/*.scala) $(wildcard ${SRC}/org/goobs/truth/conf/*.conf) ${SRC}/org/goobs/truth/Messages.java
	@mkdir -p ${BUILD}
	@echo "Compiling (${JAVAC})..."
	${JAVAC} -d ${BUILD} -cp ${CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/typesafe-config.jar `find ${SRC} -name "*.java"`
	@echo "Compiling (${SCALAC})..."
	@${SCALAC} -feature -deprecation -d ${BUILD} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
	cp ${SRC}/org/goobs/truth/conf/* ${BUILD}/
	@mkdir -p ${DIST}
	jar cf ${DIST}/client.jar -C $(BUILD) .
	jar uf ${DIST}/client.jar -C $(SRC) .

# (server)
${DIST}/server: ${OBJS} ${BUILD}/InferenceServer.o $(wildcard ${SRC}/*.h) ${SRC}/fnv/libfnv.a 
	@mkdir -p ${DIST}
	${CC} ${CPP_FLAGS} ${INCLUDE} $^ ${SRC}/fnv/libfnv.a ${LD_PATH} ${LDFLAGS} -o ${DIST}/server
	mv -f *.gcno ${BUILD} || true


# -- TEST --
${TEST_BUILD}/libgtest.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest-all.cc -o ${TEST_BUILD}/libgtest.a

${TEST_BUILD}/libgtest_main.a: ${GTEST_ROOT}
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -isystem ${GTEST_ROOT}/include -I${GTEST_ROOT} -pthread -c ${GTEST_ROOT}/src/gtest_main.cc -o ${TEST_BUILD}/libgtest_main.a

${TEST_BUILD}/%.o: ${TEST_SRC}/%.cc ${SRC}/fnv/fnv.h ${SRC}/fnv/longlong.h
	@mkdir -p ${TEST_BUILD}
	${CC} ${CPP_FLAGS} -c ${INCLUDE} -Isrc -o $@ $< -c ${CPP_FLAGS} -lgcov

${DIST}/test_server: ${DIST}/server.a ${TEST_OBJS} ${TEST_BUILD}/libgtest.a ${TEST_BUILD}/libgtest_main.a $(wildcard ${SRC}/*.h)
	@mkdir -p ${DIST}
	${CC} ${CPP_FLAGS} ${INCLUDE} -Isrc $^ ${DIST}/server.a ${SRC}/fnv/libfnv.a `find ${TEST_SRC} -name "*.h"` ${LD_PATH} ${LDFLAGS} -pthread -o ${DIST}/test_server
	mv -f *.gcno ${TEST_BUILD} || true

${DIST}/test_client.jar: ${DIST}/client.jar $(wildcard ${TEST_SRC}/org/goobs/truth/*.scala) $(wildcard ${TEST_SRC}/org/goobs/truth/*.java)
	@mkdir -p ${TEST_BUILD}
	@echo "[test] Compiling (${JAVAC})..."
#	${JAVAC} -d ${TEST_BUILD} -cp ${TEST_CP}:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/typesafe-config.jar `find ${TEST_SRC} -name "*.java"`
	@echo "[test] Compiling (${SCALAC})..."
	@${SCALAC} -feature -deprecation -d ${TEST_BUILD} -cp ${TEST_CP}:${DIST}/client.jar `find ${TEST_SRC} -name "*.scala"` `find ${TEST_SRC} -name "*.java"`
	@mkdir -p ${DIST}
	jar cf ${DIST}/test_client.jar -C $(TEST_BUILD) .
	jar uf ${DIST}/test_client.jar -C $(TEST_SRC) .
	jar uf ${DIST}/test_client.jar -C $(BUILD) .
	jar uf ${DIST}/test_client.jar -C $(SRC) .

doc:
	@echo "Documenting (${SCALADOC})..."
	mkdir -p ${DOC}
	@${SCALADOC} -d ${DOC} -cp ${CP} `find ${SRC} -name "*.scala"` `find ${SRC} -name "*.java"`
