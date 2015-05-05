#!/bin/bash
#

etc/aristo/turk_50_grok-turk-output.py \
  --negativeSource etc/aristo/turk_15_rawset.0.tab \
  etc/aristo/turk_40_output.0.csv \
    > etc/aristo/turk_90_trainset.tab
