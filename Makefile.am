SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4

clean-local:
	rm -f java.hprof.txt
	rm -f rm -f *.gcno
	rm -f rm -f *.gcda

LIB=$(realpath lib)
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/research/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-models-current.jar:/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-caseless-models-current.jar
export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim.jar:${LIB}/scripts/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar:${LIB}/postgresql.jar:${LIB}/typesafe-config.jar:${LIB}/demo/gson.jar:${LIB}/demo/servlet-api.jar
export TEST_CP=${CP}:${LIB}/test/scalatest.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar


client:
	$(MAKE) -C src client.jar
	${SCALA_HOME}/bin/scala -cp ${CP}:src/client.jar -J-mx4g org.goobs.truth.Client

server: src/truth

test: ${DIST}/test_server ${DIST}/test_client.jar
	${DIST}/test_server --gtest_output=xml:build/test.junit.xml
	${SCALA} -cp ${TEST_CP}:${DIST}/test_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.scalatest.tools.Runner -R ${DIST}/test_client.jar -o -w org.goobs.truth

itest: ${DIST}/itest_server ${DIST}/test_client.jar
	${DIST}/itest_server --gtest_output=xml:build/itest.junit.xml
	${SCALA} -cp ${TEST_CP}:${DIST}/test_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.goobs.truth.RegressionTest
