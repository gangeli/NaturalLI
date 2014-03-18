#!/usr/bin/env bash
#

set -e

echo "-- SETUP --"
rm lib/stanford-corenlp-*
ln -s $HOME/stanford-corenlp-* lib/

echo "-- MAKE --"
make clean
make all

echo "-- START SERVER --"
dist/server &

echo "-- TESTS --"
valgrind --leak-check=yes dist/test_server
sleep 120
make test

echo "-- SHUTDOWN SERVER --"
java -cp $JAVANLP_HOME/projects/core/classes:lib/protobuf.jar:dist/test_client.jar org.goobs.truth.scripts.ShutdownServer

echo "SUCCESS!"
exit 0
