#!/bin/bash
#

set -e

etc/aristo/turk_50_grok-turk-output.py \
  --host jonsson.stanford.edu \
  --collection aristo \
  --negativeSource etc/aristo/turk_15_rawset.0.tab \
  --filterTurkers A1AQK667NBERJ1,A6IVYRP8FAIS,A1OFS99W7B2RET \
  etc/aristo/turk_40_output.0.csv \
    > etc/aristo/turk_90_trainset.tab

etc/aristo/query-negatives.py \
  --host jonsson.stanford.edu \
  --collection aristo \
  --negativesPerQuery 10 \
  test/data/perfcase_aristo_train.examples \
    >> etc/aristo/turk_90_trainset.tab

