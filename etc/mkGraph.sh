#!/bin/bash
#
set -e
  
VOCAB=vocab.tab
PRIV=privative.tab

#
# Create vocabulary
#
echo "Creating vocabulary (in $VOCAB)..."
cat graphData/* |\
  sed -e 's/_/ /g' |\
  awk -F'	' '{ print $1 "\t" $3 }' |\
  tr '	' '\n' |\
  sort | uniq |\
  awk '{printf("%d\t%s\n", NR + 63, $0)}' > $VOCAB

#
# Create privatives
#
echo "Creating privatives (in $PRIV)..."
PRIVATIVE_WORDS=`echo "^(" \
     "believed|" \
     "debatable|" \
     "disputed|" \
     "dubious|" \
     "hypothetical|" \
     "impossible|" \
     "improbable|" \
     "plausible|" \
     "putative|" \
     "questionable|" \
     "so called|" \
     "supposed|" \
     "suspicious|" \
     "theoretical|" \
     "uncertain|" \
     "unlikely|" \
     "would - be|" \
     "apparent|" \
     "arguable|" \
     "assumed|" \
     "likely|" \
     "ostensible|" \
     "possible|" \
     "potential|" \
     "predicted|" \
     "presumed|" \
     "probable|" \
     "seeming|" \
     "anti|" \
     "fake|" \
     "fictional|" \
     "fictitious|" \
     "imaginary|" \
     "mythical|" \
     "phony|" \
     "false|" \
     "artificial|" \
     "erroneous|" \
     "mistaken|" \
     "mock|" \
     "pseudo|" \
     "simulated|" \
     "spurious|" \
     "deputy|" \
     "faulty|" \
     "virtual|" \
     "doubtful|" \
     "erstwhile|" \
     "ex|" \
     "expected|" \
     "former|" \
     "future|" \
     "onetime|" \
     "past|" \
     "proposed" \
     ")	[0-9]+" | sed -e 's/| /|/g'`

cat graphData/*.txt |\
  sed -e 's/_/ /g' |\
  awk -F'	' '{ print $1 "\t" $2 }' |\
  egrep "$PRIVATIVE_WORDS" |\
  sort | uniq |\
  sed -e 's/_/ /g' |\
  awk -F'	' '
      FNR == NR {
          assoc[ $2 ] = $1;
          next;
      }
      FNR < NR {
          if ( $1 in assoc ) {
              $1 = assoc[ $1 ]
          }
          print
      }
  ' $VOCAB - | sed -e 's/ /\t/g' > $PRIV

#
# Creating edge types
#
echo "Creating edge types (in edgeTypes.tab)..."
TMP=`mktemp`
for file in `find graphData -name "*.txt"`; do
  echo $file |\
    sed -r -e 's/.*\/edge_(.*)_[anrvs].txt/\1/g' |\
    sed -r -e 's/.*\/edge_(.*).txt/\1/g'
done > $TMP
echo "quantifier_up" >> $TMP
echo "quantifier_down" >> $TMP
echo "quantifier_negate" >> $TMP
echo "quantifier_reword" >> $TMP
echo "angle_nn" >> $TMP
cat $TMP | sort | uniq | awk '{ printf("%d\t%s\n", NR - 1, $0) }' > edgeTypes.tab
rm $TMP

#
# Create graph
#
function index() {
  FILE=`mktemp`
  cat $1 | sed -e 's/_/ /g' > $FILE
  awk -F'	' '
      FNR == NR {
          assoc[ $2 ] = $1;
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
  ' $VOCAB $FILE |\
    sed -e 's/ /\t/g'
  rm $FILE
}

function indexAll() {
  for file in `find graphData -name "*.txt"`; do
    type=`echo $file |\
      sed -r -e 's/.*\/edge_(.*)_[anrvs].txt/\1/g' |\
      sed -r -e 's/.*\/edge_(.*).txt/\1/g'`
    index $file | sed -e "s/^/$type	/g" |\
      awk '
        FNR == NR {
            assoc[ $2 ] = $1;
            next;
        }
        FNR < NR {
          $1 = assoc[ $1 ];
          print $2 " " $3 " " $4 " " $5 " " $1 " " $6
        } ' edgeTypes.tab - |\
      sed -e 's/ /	/g'
  done
}

echo "Indexing edges..."
indexAll | gzip > graph.tab.gz
echo "DONE"

rm -f $VOCAB.gz
gzip $VOCAB
rm -f $PRIV.gz
gzip $PRIV
