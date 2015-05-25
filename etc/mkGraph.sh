#!/bin/bash
#

set -e
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
GENERIC_TMP="`mktemp`"

  
VOCAB="$DIR/vocab.tab"
SENSE="$DIR/sense.tab"
PRIV="$DIR/privative.tab"

GRAPH_DATA='graphData'
  
# Delete files which will be auto-generated
# Having the old versions of these around mess with
# detecting the edge types.
rm -f "$DIR/$GRAPH_DATA/edge_quantifier_reword.txt"
rm -f "$DIR/$GRAPH_DATA/edge_quantifier_negate.txt"
rm -f "$DIR/$GRAPH_DATA/edge_quantifier_up.txt"
rm -f "$DIR/$GRAPH_DATA/edge_quantifier_down.txt"

#
# Create vocabulary
#
echo "Creating vocabulary (in $VOCAB)..."
TMP=`mktemp`
cat "$DIR/$GRAPH_DATA/table_lemma_defn.txt" |\
  awk -F'	' '{ print $1 }' |\
  sed -e 's/_/ /g' > "$TMP"

zcat "$DIR/$GRAPH_DATA/glove.6B.50d.txt.gz" |\
  awk '{ print $1 }' |\
  sed -r -e 's/^[0-9\.]+$/--num--/g' >> "$TMP"

cat "$DIR/operators.tab" |\
  grep -v '^$' | grep -v '^#' | grep -v '__implicit' |\
  awk -F'	' '{ print $3 }' >> "$TMP"

echo '--num--' >> "$TMP"

cat "$TMP" | sort | uniq |\
  awk '{printf("%d\t%s\n", NR + 63, $0)}' > "$VOCAB"
rm "$TMP"

#
# Create sense mapping
#
echo "Creating sense mapping (in $SENSE)..."

cat "$DIR/$GRAPH_DATA/table_lemma_defn.txt" |\
  sed -r -e 's/[^	]+\.([a-z])\.[0-9]+/\1/g' |\
  awk -F'	' '{ print $1 "\t" $2 "\t" $4 "\t" $3 }' |\
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
          print $1 "\t" $2 "\t" $3 "\t" $4
      }
  ' "$VOCAB" - |\
  sed -r -e 's/location$/n/' -e 's/a$/j/' > "$SENSE"

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

cat "$DIR/$GRAPH_DATA/edge_*.txt" |\
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
  ' "$VOCAB" - | sed -e 's/ /\t/g' > "$PRIV"

#
# Creating edge types
#
echo "Creating edge types (in $DIR/edgeTypes.tab)..."
TMP=`mktemp`
cat "$DIR/$GRAPH_DATA/"edge_*.txt | awk -F'\t' '{ print $3 }' | sort | uniq > "$TMP"
echo "quantup" >> "$TMP"
echo "quantdown" >> "$TMP"
echo "quantnegate" >> "$TMP"
echo "quantreword" >> "$TMP"
echo "nn" >> "$TMP"
cat "$TMP" | sort | uniq | awk '{ printf("%d\t%s\n", NR - 1, $0) }' > "$DIR/edgeTypes.tab"
rm "$TMP"

#
# Handle quantifiers
#
echo "Creating quantifier edges..."

# (quantifier reword) -- we hash them to the same thing anyways
#cat <<EOF > $GENERIC_TMP
#import fileinput
#synonyms = {}
#for line in fileinput.input():
#  fields = line.strip().split("\t");
#  if not fields[0] in  synonyms:
#    synonyms[fields[0]] = []
#  synonyms[fields[0]].append(fields[1]);
#for key in synonyms:
#  values = synonyms[key]
#  for source in values:
#    for sink in values:
#      if source != sink:
#        print ("%s\t0\tquantreword\t%s\t0\t1.0" % (source, sink))
#EOF
#cat $DIR/operators.tab |\
#  grep -v '^$' | grep -v '^#' | grep -v '__implicit' |\
#  awk -F'	' '{ print $1 "\t" $3 }' |\
#  python $GENERIC_TMP > $DIR/$GRAPH_DATA/edge_quantifier_reword.txt

# (antonyms)
cat <<EOF > "$GENERIC_TMP"
import fileinput
synonyms = {}
for line in fileinput.input():
  fields = line.strip().split("\t");
  if not fields[0] in  synonyms:
    synonyms[fields[0]] = []
  synonyms[fields[0]].append(fields[1]);
antonyms = [ ["all", "no"], ["no", "all"],
             ["some", "no"], ["no", "some"],
             ["most", "no"], ["no", "most"],
             ["all", "not_all"], ["not_all", "all"] ]
for [classA, classB] in antonyms:
  for source in synonyms[classA]:
    for sink in synonyms[classB]:
      print ("%s\t0\tquantnegate\t%s\t0\t1.0" % (source, sink))
EOF
cat "$DIR/operators.tab" |\
  grep -v '^$' | grep -v '^#' | grep -v '__implicit' |\
  awk -F'	' '{ print $1 "\t" $3 }' |\
  python "$GENERIC_TMP" > "$DIR/$GRAPH_DATA/edge_quantifier_negate.txt"

# (quantifier down)
cat <<EOF > "$GENERIC_TMP"
import fileinput
synonyms = {}
for line in fileinput.input():
  fields = line.strip().split("\t");
  if not fields[0] in  synonyms:
    synonyms[fields[0]] = []
  synonyms[fields[0]].append(fields[1]);
up = [ ["all", "some"], ["most", "some"] ]
for [classA, classB] in up:
  for source in synonyms[classA]:
    for sink in synonyms[classB]:
      print ("%s\t0\tquantup\t%s\t0\t1.0" % (source, sink))
EOF
cat "$DIR/operators.tab" |\
  grep -v '^$' | grep -v '^#' | grep -v '__implicit' |\
  awk -F'	' '{ print $1 "\t" $3 }' |\
  python "$GENERIC_TMP" > "$DIR/$GRAPH_DATA/edge_quantifier_up.txt"

# (quantifier up)
cat <<EOF > "$GENERIC_TMP"
import fileinput
synonyms = {}
for line in fileinput.input():
  fields = line.strip().split("\t");
  if not fields[0] in  synonyms:
    synonyms[fields[0]] = []
  synonyms[fields[0]].append(fields[1]);
down = [ ["some", "all"], ["some", "most"] ]
for [classA, classB] in down:
  for source in synonyms[classA]:
    for sink in synonyms[classB]:
      print ("%s\t0\tquantdown\t%s\t0\t1.0" % (source, sink))
EOF
cat "$DIR/operators.tab" |\
  grep -v '^$' | grep -v '^#' | grep -v '__implicit' |\
  awk -F'	' '{ print $1 "\t" $3 }' |\
  python "$GENERIC_TMP" > "$DIR/$GRAPH_DATA/edge_quantifier_down.txt"


#
# Create graph
#

# Index a given file ($1) using cat program ($2), and write the output to stdout
function index() {
  FILE=`mktemp`
  pv --progress --timer --eta --rate "$1" |\
    "$2" |\
    egrep -v '^[0-9]+	[0-9]+'  |\
    egrep -v '^[^	]+	[0-9]+	[0-9]+'  |\
    egrep -v '		'  |\
    sort | uniq | sed -e 's/_/ /g' \
      > "$FILE"
  awk -F'\t' '
      FNR == NR {
          assoc[ $2 ] = $1;
          next;
      }
      FNR < NR {
        if ( $1 in assoc ) {
          $1 = assoc[ $1 ]
        } else {
          $1 = "NOVOCAB"
        }
        if ( $2 > 31 ) {
          $2 = 31
        }
        if ( $4 in assoc ) {
          $4 = assoc[ $4 ]
        } else {
          $4 = "NOVOCAB"
        }
        if ( $5 > 31 ) {
          $5 = 31
        }
        if ( $6 < 0.0 ) {
          $6 = 0.0
        }
        print
      }
  ' "$VOCAB" "$FILE" |\
    grep -v 'NOVOCAB' |\
    sed -e 's/ /\t/g'
  rm "$FILE"
}

# index, but with cat as the program
function indexCat() {
  index "$1" cat
}

# index, but with bzcat as the program
function indexBZ() {
  index "$1" bzcat
}

# Index all of the graph spec files
function indexAll() {
  # (index the txt files)
  for file in `find $DIR/$GRAPH_DATA -name "edge_*.txt"`; do
    echo "$file" 1>&2
    indexCat "$file" |\
      awk '
        FNR == NR {
            assoc[ $2 ] = $1;
            next;
        }
        FNR < NR {
          $3 = assoc[ $3 ];
          printf $1 " " $2 " " $4 " " $5 " " $3 " " "%f\n", $6
        } ' "$DIR/edgeTypes.tab" - |\
      sed -e 's/ /	/g'
  done
  # (index the nearest neighbors file)
  echo "$DIR/$GRAPH_DATA/edge_nn.txt.bz2" 1>&2
  indexBZ "$DIR/$GRAPH_DATA/edge_nn.txt.bz2" |\
    awk '
      FNR == NR {
          assoc[ $2 ] = $1;
          next;
      }
      FNR < NR {
        $3 = assoc[ $3 ];
        printf $1 " " $2 " " $4 " " $5 " " $3 " " "%f\n", $6
        printf $4 " " $5 " " $1 " " $2 " " $3 " " "%f\n", $6
      } ' "$DIR/edgeTypes.tab" - |\
    sed -e 's/ /	/g'
}

# Put everything together into a single (validated) gz file.
echo "Indexing edges (in $DIR/graph.tab.gz)..."
indexAll |\
  sort | uniq |\
  gzip \
  > "$DIR/graph.tab.gz"
echo "DONE"
#  egrep -v '0\.0+$' |\
#  egrep '[0-9]+	[0-9]+	[0-9]+	[0-9]+	[0-9]+	[0-9\.]+' |\

# Compress some other stuff
rm -f "$VOCAB.gz"
gzip "$VOCAB"
rm -f "$SENSE.gz"
gzip "$SENSE"
rm -f "$PRIV.gz"
gzip "$PRIV"

rm -f $GENERIC_TMP
touch "$DIR/.mk_graph"
