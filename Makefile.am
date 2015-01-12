SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4
EXTRA_DIST = README.md \
						 doxygen.conf \
             models/ConceptNet/emnlp2014_final.tab \
             qry.jar run \
             build.xml \
             lib/jaws.jar \
             lib/test/hamcrest-core-1.3.jar \
						 lib/test/junit-4.11.jar \
						 test/data/unittest_gzip.tab.gz \
						 rc \
						 etc/depInsert2rel.tab \
						 etc/operators.tab \
						 etc/mkGraph.sh

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
etc/graphData/:
	wget -O etc/graphData.tar.gz 'https://www.dropbox.com/s/l8hm7v60mopihwr/naturalli_raw_graph_data.tar.gz?dl=1'
	cd etc && tar xvfz graphData.tar.gz

etc/.pp_affinity: etc/pp.sql
if HAVE_GPSQL
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
else
	@echo "Could not find Greenplum; downloading attachment models instead."
	wget -O etc/pp.tab.gz 'https://www.dropbox.com/s/tgqwwcvoaspmzj3/pp.tab.gz?dl=1'
	wget -O etc/subj_obj_pp.tab.gz 'https://www.dropbox.com/s/4sajae63s25i7uw/subj_obj_pp.tab.gz?dl=1'
	wget -O etc/subj_pp_obj.tab.gz 'https://www.dropbox.com/s/xkw9moy0g91x5zv/subj_pp_obj.tab.gz?dl=1'
	wget -O etc/subj_pp_pp.tab.gz 'https://www.dropbox.com/s/1ed2u0elmx977jh/subj_pp_pp.tab.gz?dl=1'
	wget -O etc/subj_pp.tab.gz 'https://www.dropbox.com/s/a89ax0mfce7j2sv/subj_pp.tab.gz?dl=1'
endif

etc/clauseSearcherModel.ser.gz:
	wget -O etc/clauseSearcherModel.ser.gz https://www.dropbox.com/s/s6v1v7wp7el4ytm/clauseSearcherModel.ser.gz?dl=1

etc/.mk_graph: etc/graphData/ etc/mkGraph.sh
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
etc/sense.tab.gz: etc/.mk_graph
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
	jar cvf etc/naturalli_models.jar -C ${TMP} .

clean_models:
	rm -f etc/pp.tab.gz etc/subj_pp.tab.gz etc/subj_obj_pp.tab.gz \
		    etc/subj_pp_pp.tab.gz etc/subj_pp_obj.tab.gz \
				etc/graph.tab.gz etc/vocab.tab.gz etc/sense.tab.gz etc/privative.tab.gz \
				etc/naturalli_models.jar

.have_models: etc/pp.tab.gz etc/edgeTypes.tab etc/naturalli_models.jar
	touch .have_models


# (some targets)
src/naturalli_preprocess.jar: $(wildcard src/edu/stanford/nlp/naturalli/*.java) .have_models
	$(MAKE) -C src naturalli_preprocess.jar

src/naturalli.war: src/naturalli_preprocess.jar $(wildcard src/edu/stanford/nlp/naturalli/demo/*.java) src/edu/stanford/nlp/naturalli/demo/WEB-INF/naturalli.xml .have_models
	$(MAKE) -C src naturalli.war

test/src/test_preprocess.jar: $(wildcard test/src/edu/stanford/nlp/naturalli/*.java) $(wildcard test/data/*) .have_models
	$(MAKE) -C src naturalli_preprocess.jar
	$(MAKE) -C test/src test_preprocess.jar


# (some helpers)
java_test: src/naturalli_preprocess.jar test/src/test_preprocess.jar .have_models
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
