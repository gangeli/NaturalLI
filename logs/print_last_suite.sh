#!/bin/bash
#
SCRIPT=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)/`basename "${BASH_SOURCE[0]}"`
DIR=`dirname $SCRIPT`

COUNT=`ls "$DIR/last_suite" | wc -l`
echo "$COUNT"
for i in `seq 3 $COUNT`; do 
  echo "--$i--"; 
  cat "$DIR/last_suite/$i/_rerun.sh" | grep 'naturalli.use'; 
  cat "$DIR/last_suite/$i/_rerun.sh" | grep "data'"; 
  cat "$DIR/last_suite/$i/_rerun.sh" | grep "model'"; 
  tail -n 2 "$DIR/last_suite/$i/redwood.log" | head -n 1; 
  echo "-----"; 
  echo ""; 
done | sed -r \
  -e 's/-.*\/0\/.*/overlap+lucene/g' \
  -e 's/-.*\/1\/.*/overlap/g' \
  -e 's/-.*\/2\/.*/lucene/g'
