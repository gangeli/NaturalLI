#!/bin/bash
#

set -e

# Create the Turk pipeline
etc/aristo/turk_10_run-queries.py \
    --host jonsson.stanford.edu \
    --collection aristo \
    test/data/perfcase_aristo_train.examples \
    test/data/perfcase_aristo_barrons.examples \
      > etc/aristo/turk_15_rawset.0.tab | \
  etc/aristo/turk_20_create-csv.py \
    --out etc/aristo/turk_30_input.0.csv

# Create the training corpus with all document sources
etc/aristo/turk_10_run-queries.py \
    --host jonsson.stanford.edu \
    --collection aristo \
    test/data/perfcase_aristo_train.examples \
      > etc/aristo/eval_train_allcorpora.tab

# Create the training corpus with only the Barrons study guide
etc/aristo/turk_10_run-queries.py \
    --host jonsson.stanford.edu \
    --collection aristo_barrons \
    test/data/perfcase_aristo_train.examples \
      > etc/aristo/eval_train_barrons.tab
