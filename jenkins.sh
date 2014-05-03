#!/usr/bin/env bash
#

set -e
export MAXMEM_GB=6
JAVANLP_HOME=${JAVANLP_HOME-/home/gabor/workspace/nlp}

echo "-- SETUP --"
rm lib/stanford-corenlp-*
ln -s $HOME/stanford-corenlp-* lib/

echo "-- MAKE --"
./configure \
  --with-scala=/home/gabor/programs/scala \
  --with-java=/usr/lib/jvm/java-7-oracle \
  --enable-debug
make clean
make all check TESTS_ENVIRONMENT=true 

echo "-- TEST --"
#test/src/test_server --gtest_output=xml:test/test_server.junit.xml
#test/src/itest_server --gtest_output=xml:test/itest_server.junit.xml
make java_test

echo "-- COVERAGE --"
gcovr -x -r src -e ".+\.test\.cc" > build/coverage.xml

echo "-- Document --"
doxygen doxygen.conf

echo "SUCCESS!"
exit 0
