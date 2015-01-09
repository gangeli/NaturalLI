#!/bin/bash
#
set -e
  
VOCAB=vocab.tab

echo "Creating vocabulary (in $VOCAB)..."
cat graphData/* |\
  sed -e 's/_/ /g' |\
  awk -F'	' '{ print $1 "\t" $3 }' |\
  tr '	' '\n' |\
  sort | uniq |\
  awk '{printf("%s\t%d\n", $0, NR + 63)}' > $VOCAB

function index() {
  FILE=`mktemp`
  cat $1 | sed -e 's/_/ /g' > $FILE
  awk -F'	' '
      FNR == NR {
          assoc[ $1 ] = $2;
          next;
      }
      FNR < NR {
          for ( i = 1; i <= NF; i+=2 ) {
              if ( $i in assoc ) {
                  $i = assoc[ $i ]
              }
          }
          print
      }
  ' $VOCAB $FILE | sed -e 's/ /\t/g'
  rm $FILE
}

function indexAll() {
  for file in `find graphData -name "*.txt"`; do
    type=`echo $file |\
      sed -r -e 's/.*\/edge_(.*)_[anrvs].txt/\1/g' |\
      sed -r -e 's/.*\/edge_(.*).txt/\1/g'`
    index $file | sed -e "s/^/$type	/g"
  done
}

echo "Indexing edges..."
indexAll | gzip > graph.tab.gz
echo "DONE"

gzip $VOCAB
