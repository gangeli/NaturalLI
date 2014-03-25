#!/usr/bin/env bash
#

set -e

echo "-- SETUP --"
rm lib/stanford-corenlp-*
ln -s $HOME/stanford-corenlp-* lib/

echo "-- MAKE --"
./configure --with-scala=/home/gabor/programs/scala
make clean
make all

echo "-- TEST --"
make check
test/server/itest_server
make java_test

echo "-- COVERAGE --"
gcovr -x -r src -e ".+\.test\.cc" > build/coverage.xml

echo "SUCCESS!"
exit 0
