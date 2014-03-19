#!/usr/bin/env bash
#

set -e

echo "-- SETUP --"
rm lib/stanford-corenlp-*
ln -s $HOME/stanford-corenlp-* lib/

echo "-- MAKE --"
make clean
make all

echo "-- TESTS --"
valgrind --leak-check=yes dist/test_server
sleep 120
make test

echo "SUCCESS!"
exit 0
