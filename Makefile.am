current_dir := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
etc := "${current_dir}/etc"

SUBDIRS = src test/src
ACLOCAL_AMFLAGS=-I m4
EXTRA_DIST = README.md \
             doxygen.conf \
             models/ConceptNet/emnlp2014_final.tab \
             qry.jar run \
             build.xml \
             lib/jaws.jar \
             lib/gson-2.3.1.jar \
             lib/test/hamcrest-core-1.3.jar \
             lib/test/junit-4.11.jar \
             rc \
             etc/depInsert2rel.tab \
             etc/operators.tab \
             etc/pp.sql \
             etc/mkGraph.sh \
             test/run_testcases.sh \
             test/run_perfcase.sh \
             test/run_inet.py \
             test/data \
						 etc/WordNet-3.1
             

clean-local:
	rm -f java.hprof.txt
	rm -f *.gcno
	rm -f *.gcda
	rm -f src/naturalli_preprocess.jar
	rm -f test/src/naturalli_preprocess_test.jar
	rm -f naturalli_test.junit.xml
	rm -f naturalli_itest.junit.xml
	rm -f test/naturalli_test.junit.xml
	rm -f test/naturalli_itest.junit.xml
	rm -f test/src/naturalli_test.junit.xml
	rm -f test/src/naturalli_itest.junit.xml
	rm -rf build/
	rm -rf test/build/
	bash -c 'for file in `find . -name "*.class"`; do rm -f $$file; done'
	bash -c 'for file in `find . -name "TEST-*.xml"`; do rm -f $$file; done'


#
# Java portion of the Makefile
#

# (some paths)
LIB=${realpath lib}
# TODO(gabor) remove dependenc on more/ and research/
JAVANLP=${CORENLP}:${CORENLP_MODELS}:${CORENLP_CASELESS_MODELS}:/afs/cs.stanford.edu/u/angeli/workspace/nlp/projects/more/classes:/home/gabor/workspace/nlp/projects/more/classes:/home/gabor/workspace/nlp/projects/research/classes:/afs/cs.stanford.edu/u/angeli/workspace/nlp/projects/research/classes
# ((export classpath))
if SCALA_2_11
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/gson-2.3.1.jar:${LIB}/demo/joda-time.jar:${LIB}/demo/javamail-1.4.5.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/config-1.2.0.jar
else
  export CP=${JAVANLP}:${LIB}/corenlp-scala.jar:${LIB}/scripts/sim-1.0.jar:${LIB}/jaws.jar:${LIB}/trove.jar:${LIB}/gson-2.3.1.jar:${LIB}/demo/joda-time.jar:${LIB}/demo/javamail-1.4.5.jar:${LIB}/demo/servlet-api.jar:${LIB}/scripts/lucene-core-4.7.0.jar:${LIB}/scripts/lucene-analyzers-common-4.7.0.jar:${SCALA_HOME}/lib/typesafe-config.jar
endif
export TEST_CP=${CP}:${LIB}/test/scalatest_2.11-2.1.6.jar:${LIB}/test/junit-4.11.jar:${LIB}/test/hamcrest-core-1.3.jar:${LIB}/stanford-corenlp-models-current.jar:${LIB}/stanford-corenlp-caseless-models-current.jar

# (some tasks)
etc/graphData/:
	wget -O ${etc}/graphData.tar.gz 'https://www.dropbox.com/s/l8hm7v60mopihwr/naturalli_raw_graph_data.tar.gz?dl=1'
	cd etc && tar xvfz graphData.tar.gz

etc/.pp_affinity: etc/pp.sql
#if HAVE_GPSQL
#	psql -h ${GP_HOST} -p ${GP_PORT} -U ${GP_USER} ${GP_DB} -f ${etc}/pp.sql
#	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
#	  -c "COPY (SELECT * FROM pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
#	  | gzip > ${etc}/pp.tab.gz
#	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
#	  -c "COPY (SELECT * FROM subj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
#	  | gzip > ${etc}/subj_pp.tab.gz
#	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
#	  -c "COPY (SELECT * FROM subj_obj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
#	  | gzip > ${etc}/subj_obj_pp.tab.gz
#	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
#	  -c "COPY (SELECT * FROM subj_pp_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
#	  | gzip > ${etc}/subj_pp_pp.tab.gz
#	psql -h ${GP_HOST} -U ${GP_USER} -p ${GP_PORT} ${GP_DB} \
#	  -c "COPY (SELECT * FROM subj_pp_obj_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
#	  | gzip > ${etc}/subj_pp_obj.tab.gz
#	touch ${etc}/.pp_affinity
#else
	@echo "Could not find Greenplum; downloading attachment models instead."
	wget -O ${etc}/pp.tab.gz 'https://www.dropbox.com/s/tgqwwcvoaspmzj3/pp.tab.gz?dl=1'
	wget -O ${etc}/subj_obj_pp.tab.gz 'https://www.dropbox.com/s/4sajae63s25i7uw/subj_obj_pp.tab.gz?dl=1'
	wget -O ${etc}/subj_pp_obj.tab.gz 'https://www.dropbox.com/s/xkw9moy0g91x5zv/subj_pp_obj.tab.gz?dl=1'
	wget -O ${etc}/subj_pp_pp.tab.gz 'https://www.dropbox.com/s/1ed2u0elmx977jh/subj_pp_pp.tab.gz?dl=1'
	wget -O ${etc}/subj_pp.tab.gz 'https://www.dropbox.com/s/a89ax0mfce7j2sv/subj_pp.tab.gz?dl=1'
	wget -O ${etc}/obj.tab.gz 'https://www.dropbox.com/s/bxzrobeh64ts2cw/obj.tab.gz?dl=1'
	touch ${etc}/.pp_affinity
#endif

etc/clauseSplitterModel.ser.gz:
	wget -O ${etc}/clauseSplitterModel.ser.gz 'https://www.dropbox.com/s/cbh3jrzs81m404o/clauseSplitterModel.ser.gz?dl=1'

etc/.mk_graph: etc/graphData/ etc/mkGraph.sh
	bash -c 'cd ${etc} && ./mkGraph.sh'
	source ${current_dir}/rc && validateGraph
	touch ${etc}/.mk_graph

# (script targets)
etc/pp.tab.gz: etc/.pp_affinity
etc/subj_pp.tab.gz: etc/.pp_affinity
etc/subj_obj_pp.tab.gz: etc/.pp_affinity
etc/subj_pp_pp.tab.gz: etc/.pp_affinity
etc/subj_pp_obj.tab.gz: etc/.pp_affinity
etc/obj.tab.gz: etc/.pp_affinity
etc/graph.tab.gz: etc/.mk_graph
etc/vocab.tab.gz: etc/.mk_graph
etc/sense.tab.gz: etc/.mk_graph
etc/privative.tab.gz: etc/.mk_graph
etc/edgeTypes.tab: etc/.mk_graph

etc/naturalli_models.jar: etc/clauseSplitterModel.ser.gz etc/pp.tab.gz etc/subj_pp.tab.gz etc/subj_obj_pp.tab.gz etc/subj_pp_pp.tab.gz etc/subj_pp_obj.tab.gz etc/obj.tab.gz
	$(eval TMP := $(shell mktemp -d))
	mkdir -p ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/subj_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/subj_obj_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/subj_pp_pp.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/subj_pp_obj.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/obj.tab.gz ${TMP}/edu/stanford/nlp/naturalli
	cp ${etc}/clauseSplitterModel.ser.gz ${TMP}/edu/stanford/nlp/naturalli
	jar cvf ${etc}/naturalli_models.jar -C ${TMP} .

clean_models:
	rm -f ${etc}/pp.tab.gz ${etc}/subj_pp.tab.gz ${etc}/subj_obj_pp.tab.gz \
		    ${etc}/subj_pp_pp.tab.gz ${etc}/subj_pp_obj.tab.gz ${etc}/obj.tab.gz \
				${etc}/graph.tab.gz ${etc}/vocab.tab.gz ${etc}/sense.tab.gz ${etc}/privative.tab.gz \
				${etc}/naturalli_models.jar

etc/.have_models: etc/pp.tab.gz etc/edgeTypes.tab etc/naturalli_models.jar
	touch ${etc}/.have_models


# (some targets)
src/naturalli_preprocess.jar: $(wildcard src/edu/stanford/nlp/naturalli/*.java) $(wildcard src/edu/stanford/nlp/naturalli/entail/*.java) etc/.have_models
	$(MAKE) -C src naturalli_preprocess.jar

src/naturalli.war: src/naturalli_preprocess.jar $(wildcard src/edu/stanford/nlp/naturalli/demo/*.java) src/edu/stanford/nlp/naturalli/demo/WEB-INF/naturalli.xml etc/.have_models
	$(MAKE) -C src naturalli.war

test/src/naturalli_preprocess_test.jar: $(wildcard test/src/edu/stanford/nlp/naturalli/*.java) $(wildcard test/data/*) etc/.have_models
	$(MAKE) -C src naturalli_preprocess.jar
	$(MAKE) -C test/src naturalli_preprocess_test.jar


# (some helpers)
java_test: src/naturalli_preprocess.jar test/src/naturalli_preprocess_test.jar etc/.have_models
	export CLASSPATH=""
	@echo "[test] junit *.java"
if HAVE_ANT
	@echo "[test] (using ant)"
	CLASSPATH="" CORENLP_JAR="${CORENLP}" CORENLP_MODELS_JAR="${CORENLP_MODELS}" CORENLP_CASELESS_MODELS_JAR="${CORENLP_CASELESS_MODELS}" NATURALLI_MODELS="${NATURALLI_MODELS}" ant test
else
	@echo "  (could not find ant -- running directly)"
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/naturalli_preprocess_test.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=${etc}/WordN${etc}3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.PreprocessITest
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/naturalli_preprocess_test.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=${etc}/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.QuantifierScopeITest
	@${JDK_HOME}/bin/java -cp ${TEST_CP}:test/src/naturalli_preprocess_test.jar:src/naturalli_preprocess.jar -Dwordnet.database.dir=${etc}/WordNet-3.1/dict -mx4g org.junit.runner.JUnitCore edu.stanford.nlp.naturalli.WSDTest
endif
