#!/usr/bin/env bash
#

set -e
set -o xtrace

export MAXMEM_GB=6
export JAVANLP_HOME=${JAVANLP_HOME-/home/gabor/workspace/nlp}
echo "JavaNLP at: $JAVANLP_HOME"
export SCALA_HOME=${SCALA_HOME-/home/gabor/programs/scala}
echo "Scala at: $SCALA_HOME"

function configure() {
  ./configure \
    --with-scala=$SCALA_HOME \
    --with-java=/usr/lib/jvm/java-8-oracle \
    --with-corenlp=$HOME/stanford-corenlp.jar \
    --with-corenlp-models=$HOME/stanford-corenlp-models-current.jar \
    --with-corenlp-caseless-models=$HOME/stanford-corenlp-caseless-models-current.jar \
    --enable-debug FACT_MAP_SIZE=27 $@
  make clean
}

echo "-- CLEAN --"
git clean -f
./autogen.sh

echo "-- MAKE --"
configure
make all check TESTS_ENVIRONMENT=true 

echo "-- C++ TESTS --"
test/src/test_server --gtest_output=xml:test/test_server.junit.xml

echo "-- MAKE DIST --"
configure
make -j4 dist
tar xfz `find . -name "naturalli-*.tar.gz"`
cd `find . -type d -name "naturalli-*"`
configure
make all -j4 check
cd ..
rm -r `find . -type d -name "naturalli-*"`

echo "-- C++ SPECIAL TESTS --"
echo "(no debugging)"
configure --disable-debug
make -j4 check
echo "(two pass hash)"
configure TWO_PASS_HASH=1
make -j4 check
echo "(search cycle memory)"
configure SEARCH_CYCLE_MEMORY=1; make -j4 check
configure SEARCH_CYCLE_MEMORY=2; make -j4 check
configure SEARCH_CYCLE_MEMORY=5; make -j4 check
echo "(back to default)"
configure  # reconfigure to default
make -j4 all

echo "-- COVERAGE --"
cd src/
rm -f naturalli_server-Messages.pb.gcda
rm -f naturalli_server-Messages.pb.gcno
rm -f naturalli_server-Messages.pb.h.gcno
rm -f naturalli_server-Messages.pb.h.gcda
gcovr -r . --html --html-details -o /var/www/naturalli/coverage/index.html
gcovr -r . --xml -o coverage.xml
cd ..

echo "SUCCESS!"
exit 0
