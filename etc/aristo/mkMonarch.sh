#!/bin/bash
#
# Create the Monarch dataset, for now without training.
#

set -o nounset
set -o errexit
set -o xtrace

# Load the prerequisite environment variables
TASK_FILE="${BASH_SOURCE[0]}"
if [[ "$TASK_FILE" != /* ]]; then TASK_FILE="`pwd`/$TASK_FILE"; fi
MYDIR=$( cd "$( dirname "$TASK_FILE" )" && pwd )
ROOTDIR="$MYDIR/../.."

#
# Create the perfcase files
#
"$MYDIR/monarch2perfcase" \
  "$MYDIR/monarch.raw.tab" \
  train \
    > "$ROOTDIR/test/data/perfcase_monarch_train.examples"
"$MYDIR/monarch2perfcase" \
  "$MYDIR/monarch.raw.tab" \
  dev \
    > "$ROOTDIR/test/data/perfcase_monarch_dev.examples"
"$MYDIR/monarch2perfcase" \
  "$MYDIR/monarch.raw.tab" \
  test \
    > "$ROOTDIR/test/data/perfcase_monarch_test.examples"

#
# Create the classifier files
#
"$MYDIR/turk_10_run-queries.py" \
    --host jonsson.stanford.edu \
    --collection aristo \
    "$ROOTDIR/test/data/perfcase_monarch_train.examples" \
      > "$ROOTDIR/etc/aristo/eval_monarch_train_allcorpora.tab"
"$MYDIR/turk_10_run-queries.py" \
    --host jonsson.stanford.edu \
    --collection aristo \
    "$ROOTDIR/test/data/perfcase_monarch_dev.examples" \
      > "$ROOTDIR/etc/aristo/eval_monarch_dev_allcorpora.tab"
"$MYDIR/turk_10_run-queries.py" \
    --host jonsson.stanford.edu \
    --collection aristo \
    "$ROOTDIR/test/data/perfcase_monarch_test.examples" \
      > "$ROOTDIR/etc/aristo/eval_monarch_test_allcorpora.tab"

