SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4

clean-local:
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda
	rm -f src/client.jar
	rm -f test/test_client.jar
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

LIB=${realpath lib}
JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/classes:${JAVANLP_HOME}/projects/research/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:${JAVANLP_HOME}/projects/scala-2.10/classes:lib/stanford-corenlp-caseless-models-current.jar:lib/stanford-corenlp-models-current.jar
export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/protobuf.jar:${LIB}/postgresql.jar:${LIB}/typesafe-config.jar:${LIB}/demo/gson.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar
export TEST_CP=${CP}:${LIB}/test/scalatest.jar:${LIB}/test/junit-4.11.jar:${LIB}/test/hamcrest-core-1.3.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar

src/org/goobs/truth/Messages.java: src/Messages.proto
	$(MAKE) -C src org/goobs/truth/Messages.java

src/client.jar: $(wildcard src/org/goobs/truth/*.scala) $(wildcard src/org/goobs/truth/*.java) $(wildcard src/org/goobs/truth/scripts/*.scala) $(wildcard src/org/goobs/truth/conf/*.conf) src/org/goobs/truth/Messages.java
	$(MAKE) -C src client.jar

client: src/client.jar
	${SCALA_HOME}/bin/scala -cp ${CP}:src/client.jar -J-mx4g org.goobs.truth.Client

test/test_client.jar: $(wildcard test/src/org/goobs/truth/*.scala) $(wildcard test/src/org/goobs/truth/*.java) $(wildcard test/src/org/goobs/truth/*.dat) $(wildcard test/src/edu/stanford/nlp/natlog/*.java) $(wildcard test/src/org/goobs/truth/*.java) 
	$(MAKE) -C src client.jar
	@echo "[test] Compiling (${JDK_HOME}/bin/javac)..."
	@${JDK_HOME}/bin/javac -d test/src -cp ${TEST_CP}:src/client.jar:${SCALA_HOME}/lib/scala-library.jar  `find . -name "*.java"`
	@echo "[test] Compiling (${SCALA_HOME}/bin/scalac)..."
	@${SCALA_HOME}/bin/scalac -feature -deprecation -d test/src -cp ${TEST_CP}:src/client.jar `find test/src -name "*.scala"` `find test/src -name "*.java"`
	jar cf test/test_client.jar -C test/src .
	jar uf test/test_client.jar -C src .
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'

java_test: src/client.jar test/test_client.jar
	@echo "[test] junit *.java"
if HAVE_ANT
	@echo "  (using ant)"
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:${SCALA_HOME}/lib/scala-library.jar:test/test_client.jar:src/client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.natlog.GaborMonoTest org.goobs.truth.QuantifierTest
else
	@echo "  (could not find ant -- running directly)"
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:${SCALA_HOME}/lib/scala-library.jar:test/test_client.jar:src/client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.natlog.GaborMonoTest org.goobs.truth.QuantifierTest
endif
	endif
	@echo "[test] scalatest *.scala"
	@${SCALA_HOME}/bin/scala -cp ${TEST_CP}:test/test_client.jar:src/client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.scalatest.tools.Runner -u test/test_scala.xml -R test/test_client.jar -o -w org.goobs.truth
	@echo "[test] RegressionTest"
	@${SCALA_HOME}/bin/scala -cp ${TEST_CP}:test/test_client.jar:src/client.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -J-mx4g org.goobs.truth.RegressionTest

server: src/truth
