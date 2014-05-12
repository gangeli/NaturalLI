SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4

clean-local:
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda
	rm -f src/naturalli_client.jar
	rm -f test/src/test_client.jar
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'


#
# Java portion of the Makefile
#

# (some paths)
LIB=${realpath lib}
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/research/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:lib/stanford-corenlp-caseless-models-current.jar:lib/stanford-corenlp-models-current.jar
if SCALA_2_11
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar:${LIB}/postgresql.jar:${LIB}/demo/gson.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/config-1.2.0.jar
else
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar:${LIB}/postgresql.jar:${LIB}/demo/gson.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/typesafe-config.jar
endif
export TEST_CP=${CP}:${LIB}/test/scalatest_2.11-2.1.6.jar:${LIB}/test/junit-4.11.jar:${LIB}/test/hamcrest-core-1.3.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar

# (some targets)
src/org/goobs/truth/Messages.java: src/Messages.proto
	$(MAKE) -C src org/goobs/truth/Messages.java

src/naturalli_client.jar: $(wildcard src/org/goobs/truth/*.scala) $(wildcard src/org/goobs/truth/*.java) $(wildcard src/org/goobs/truth/scripts/*.scala) $(wildcard src/org/goobs/truth/conf/*.conf) src/org/goobs/truth/Messages.java
	$(MAKE) -C src naturalli_client.jar

test/src/test_client.jar: $(wildcard test/src/org/goobs/truth/*.scala) $(wildcard test/src/org/goobs/truth/*.java) $(wildcard test/src/org/goobs/truth/*.dat) $(wildcard test/src/edu/stanford/nlp/natlog/*.java) $(wildcard test/src/org/goobs/truth/*.java) 
	$(MAKE) -C src naturalli_client.jar
	$(MAKE) -C test/src test_client.jar

# (some helpers)
client: src/naturalli_client.jar
	${SCALA_HOME}/bin/scala -cp ${CP}:src/naturalli_client.jar -J-mx4g org.goobs.truth.Client

java_test: src/naturalli_client.jar test/src/test_client.jar
	export CLASSPATH=""
	@echo "[test] junit *.java"
if HAVE_ANT
	ant test
else
	@echo "  (could not find ant -- running directly)"
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:${SCALA_HOME}/lib/scala-library.jar:test/src/test_client.jar:src/naturalli_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.natlog.GaborMonoTest org.goobs.truth.QuantifierTest
	@echo "[test] scalatest *.scala"
	@${SCALA_HOME}/bin/scala -cp ${TEST_CP}:test/src/test_client.jar:src/naturalli_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.scalatest.tools.Runner -u test -R test/src/test_client.jar -o -w org.goobs.truth
endif
	@echo "[test] RegressionTest"
	@${SCALA_HOME}/bin/scala -cp ${TEST_CP}:test/src/test_client.jar:src/naturalli_client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.goobs.truth.RegressionTest
