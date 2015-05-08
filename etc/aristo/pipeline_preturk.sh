#!/bin/bash
#

set -e

etc/aristo/turk_10_run-queries.py \
    --host jonsson.stanford.edu \
    --collection aristo \
    test/data/perfcase_aristo_train.examples \
    test/data/perfcase_aristo_barrons.examples \
      > etc/aristo/turk_15_rawset.0.tab | \
  etc/aristo/turk_20_create-csv.py \
    --out etc/aristo/turk_30_input.0.csv

head -n 3432 etc/aristo/turk_15_rawset.0.tab \
    > etc/aristo/aristo_train.tab
echo "Last line: `tail -n 1 etc/aristo/aristo_train.tab`"
