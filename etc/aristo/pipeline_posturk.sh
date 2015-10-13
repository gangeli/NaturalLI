#!/bin/bash
#

set -e

# Load the prerequisite environment variables
TASK_FILE="${BASH_SOURCE[0]}"
if [[ "$TASK_FILE" != /* ]]; then TASK_FILE="`pwd`/$TASK_FILE"; fi
MYDIR=$( cd "$( dirname "$TASK_FILE" )" && pwd )
ROOTDIR="$MYDIR/../.."

"$ROOTDIR/etc/aristo/turk_50_grok-turk-output.py" \
  --host jonsson.stanford.edu \
  --collection aristo \
  --negativeSource "$ROOTDIR/etc/aristo/turk_15_rawset.0.tab" \
  --filterTurkers A1AQK667NBERJ1,A6IVYRP8FAIS,A1OFS99W7B2RET \
  "$ROOTDIR/etc/aristo/turk_40_output.0.csv" \
    > "$ROOTDIR/etc/aristo/turk_90_trainset.tab"

"$ROOTDIR/etc/aristo/query-negatives.py" \
  --host jonsson.stanford.edu \
  --collection aristo \
  --negativesPerQuery 10 \
  "$ROOTDIR/test/data/perfcase_aristo_train.examples" \
    >> "$ROOTDIR/etc/aristo/turk_90_trainset.tab"

