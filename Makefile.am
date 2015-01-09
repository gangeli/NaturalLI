SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4
EXTRA_DIST = README.md doxygen.conf \
             models/ConceptNet/emnlp2014_final.tab \
             qry.jar run \
             build.xml \
             lib/jaws.jar \
             lib/test/hamcrest-core-1.3.jar lib/test/junit-4.11.jar

clean-local:
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda
	rm -f src/naturalli_preprocess.jar
	rm -f test/src/test_preprocess.jar
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'
	bash -c 'for file in `find . -name "TEST-*.xml"`; do rm -f $$file; done'


#
# Java portion of the Makefile
#

# (some paths)
LIB=${realpath lib}
JAVANLP=${CORENLP}:${CORENLP_MODELS}:${CORENLP_CASELESS_MODELS}
# ((export classpath))
if SCALA_2_11
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/scripts/gson-2.2.4.jar:${LIB}/demo/joda-time.jar:${LIB}/demo/javamail-1.4.5.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/config-1.2.0.jar
else
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/scripts/gson-2.2.4.jar:${LIB}/demo/joda-time.jar:${LIB}/demo/javamail-1.4.5.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/typesafe-config.jar
endif
export TEST_CP=${CP}:${LIB}/test/scalatest_2.11-2.1.6.jar:${LIB}/test/junit-4.11.jar:${LIB}/test/hamcrest-core-1.3.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar

# (some tasks)
etc/.pp_affinity: etc/pp.sql
	psql -h ${GP_HOST} -p ${GP_PORT} -U ${GP_USER} ${GP_DB} -f etc/pp.sql
	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
	  -c "COPY (SELECT * FROM pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
	  | gzip > etc/pp.tab.gz
	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
	  -c "COPY (SELECT * FROM subj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
	  | gzip > etc/subj_pp.tab.gz
	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
	  -c "COPY (SELECT * FROM subj_obj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
	  | gzip > etc/subj_obj_pp.tab.gz
	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
	  -c "COPY (SELECT * FROM subj_pp_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
	  | gzip > etc/subj_pp_pp.tab.gz
	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
	  -c "COPY (SELECT * FROM subj_pp_obj_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
	  | gzip > etc/subj_pp_obj.tab.gz
	touch etc/.pp_affinity

etc/.mk_graph: $(wildcard etc/graphData/*.txt)  etc/mkGraph.sh
	cd etc && ./mkGraph.sh
	touch etc/.mk_graph

# (script targets)
etc/pp.tab.gz: etc/.pp_affinity
etc/subj_pp.tab.gz: etc/.pp_affinity
etc/subj_obj_pp.tab.gz: etc/.pp_affinity
etc/subj_pp_pp.tab.gz: etc/.pp_affinity
etc/subj_pp_obj.tab.gz: etc/.pp_affinity
etc/graph.tab.gz: etc/.mk_graph
etc/vocab.tab.gz: etc/.mk_graph
etc/privative.tab.gz: etc/.mk_graph
etc/edgeTypes.tab: etc/.mk_graph

etc/naturalli_models.jar: etc/clauseSearcherModel.ser.gz etc/pp.tab.gz etc/subj_pp.tab.gz etc/subj_obj_pp.tab.gz etc/subj_pp_pp.tab.gz etc/subj_pp_obj.tab.gz
	$(eval TMP := $(shell mktemp -d))
	mkdir -p ${TMP}/edu/stanford/nlp/naturalli
	cp etc/pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp etc/subj_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp etc/subj_obj_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp etc/subj_pp_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp etc/subj_pp_obj.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp etc/clauseSearcherModel.ser.gz ${TMP}/edu/stanford/nlp/naturalli
	jar cvf naturalli_models.jar -C ${TMP} .
	touch etc/naturalli_models.jar  # make sure this is the last thing we touch

clean_models:
	rm -f etc/pp.tab.gz etc/subj_pp.tab.gz etc/subj_obj_pp.tab.gz \
		    etc/subj_pp_pp.tab.gz etc/subj_pp_obj.tab.gz \
				etc/graph.tab.gz etc/vocab.tab.gz \
				etc/naturalli_models.jar

# (some targets)
src/naturalli_preprocess.jar: $(wildcard src/edu/stanford/nlp/naturalli/*.java)
	$(MAKE) -C src naturalli_preprocess.jar

src/naturalli.war: src/naturalli_preprocess.jar $(wildcard src/edu/stanford/nlp/naturalli/demo/*.java) src/edu/stanford/nlp/naturalli/demo/WEB-INF/naturalli.xml
	$(MAKE) -C src naturalli.war

test/src/test_preprocess.jar: $(wildcard test/src/edu/stanford/nlp/naturalli/*.java) $(wildcard test/data/*)
	$(MAKE) -C src naturalli_preprocess.jar
	$(MAKE) -C test/src test_preprocess.jar


# (some helpers)
preprocess: src/naturalli_preprocess.jar
	${SCALA_HOME}/bin/scala -cp ${CP}:src/naturalli_preprocess.jar -J-mx4g edu.stanford.nlp.naturalli.Preprocess

java_test: src/naturalli_preprocess.jar test/src/test_preprocess.jar
	export CLASSPATH=""
	@echo "[test] junit *.java"
if HAVE_ANT
	@echo "[test] (using ant)"
	CLASSPATH="" CORENLP_JAR="${CORENLP}" CORENLP_MODELS_JAR="${CORENLP_MODELS}" CORENLP_CASELESS_MODELS_JAR="${CORENLP_CASELESS_MODELS}" NATURALLI_MODELS="${NATURALLI_MODELS}" ant test
else
	@echo "  (could not find ant -- running directly)"
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/test_preprocess.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.PreprocessITest
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/test_preprocess.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.QuantifierScopeITest
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/test_preprocess.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=etc/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.WSDTest
endif
