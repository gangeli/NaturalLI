#!/bin/bash
#

INPUT_DIR='/scr/nlp/data/openie/output/ollie1.0.1'
export TMPDIR="/$HOST/scr1/angeli/tmp"

PGUSER=gabor
PGPORT=4243
PGHOST=john0
PGDATABASE=truth

OUT_OPENIE_UNNORMALIZED='output/ollie_unnormalized.tab'
OUT_OPENIE_INDEXED='output/ollie_indexed.tab'
OUT_OPENIE_INDEXED_HEADS='output/ollie_indexed_heads.tab'

JOBS=6

echo "---------------"
echo "PLEASE READ ME:"
echo "---------------"
echo "This script will take a long time. In addition, it will create"
echo "a rather large number of files taking up a lot of space"
echo "IN THE CURRENT DIRECTORY."
echo ""
echo "Created Files:"
echo "  $OUT_OPENIE_UNNORMALIZED.bz2:  All extractions in a single file"
echo "  $OUT_OPENIE_INDEXED.bz2:       The indexed facts"
echo "  $OUT_OPENIE_INDEXED_HEADS.bz2: The indexed facts, with only head words"
echo ""
echo "Temporary Files:"
echo "  $OUT_OPENIE_UNNORMALIZED:  All extractions in a single file (unzipped)"
echo "  $OUT_OPENIE_INDEXED:       The indexed facts (unzipped)"
echo "  $OUT_OPENIE_INDEXED_HEADS: The indexed facts with only head words (unzipped)"
echo ""
echo "Input:"
echo "  $INPUT_DIR: All files in this directory will be used as input"
echo ""
read -p "Hit ENTER to proceeed"

echo ""
echo "[00] START"

TMP_SED_CASED=`mktemp`
TMP_SED_CASELESS=`mktemp`
WORDS=`mktemp`

function cleanup {
  echo "[99] Cleaning up..."
  rm -f $TMP_SED_CASED
  rm -f $TMP_SED_CASELESS
  rm -f $WORDS
}
trap cleanup EXIT


# ###########################
# AGGREGATE FACTS
# ###########################
#echo "[10] Sort+Uniq Facts ($OUT_OPENIE_UNNORMALIZED)"
#echo "$INPUT_DIR" | grep "reverb" > /dev/null
#if [ "$?" == "0" ]; then
#  # ReVerb
#  # ------
#  echo "  (using ReVerb input)"
#  set -e
#  mkdir -p output
#  zcat `find $INPUT_DIR/ -name "*.reverb.gz"` |\
#    ( parallel --jobs $JOBS --gnu --pipe cut -f 3-5 ) |\
#    perl -pe 's/([0-9]+)/length($1)/gei' | sed -r -e 's/([0-9]+)/num_\1/g' |\
#    ( parallel --jobs $JOBS --gnu --pipe --files sort | parallel -Xj1 sort -m {} ';' rm {}) |\
#    uniq -c |\
#    sed -e 's/^ *\([0-9][0-9]*\) /\1\t/' > $OUT_OPENIE_UNNORMALIZED
#else
#  echo "$INPUT_DIR" | grep "ollie" > /dev/null
#  if [ "$?" == "0" ]; then
#    # Ollie
#    # ------
#    echo "  (using Ollie input)"
#    set -e
#    mkdir -p output
#    find $INPUT_DIR -type f -name "*.ollie.gz" -print |\
#      xargs zcat |\
#      ( parallel --gnu --pipe cut -f 2-4 ) |\
#      perl -pe 's/([0-9]+)/length($1)/gei' | sed -r -e 's/([0-9]+)/num_\1/g' |\
#      ( parallel --gnu --pipe --files sort | parallel -Xj1 sort -m {} ';' rm {}) |\
#      uniq -c |\
#      sed -e 's/^ *\([0-9][0-9]*\) /\1\t/' > $OUT_OPENIE_UNNORMALIZED
#  else
#    echo "UNKNOWN INPUT TYPE"
#    exit 1
#  fi
#fi

# ###########################
# CREATE REPLACEMENT PATTERNS
# ###########################
echo "[20] Create Case Sensitive Replacements ($TMP_SED_CASED)"
/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "COPY (SELECT * FROM word) TO stdout DELIMITER E'\t';" > $WORDS
cat $WORDS |\
  awk -F'\t' '{ print $0 "\t" length($2) }' |\
  (parallel --gnu --pipe --files sort | parallel --gnu -Xj1 sort -m {} ';' rm {}) |\
  sed '$d' |\
  awk -F'\t' '{ print "s/"$2"/"$1"/g" }' > $TMP_SED_CASED

echo "[30] Create Case In-sensitive Replacements ($TMP_SED_CASELESS)"
cat $WORDS |\
  grep '.*[A-Z].*' |\
  grep -v '[0-9]' |\
  tr '[:upper:]' '[:lower:]' |\
  awk -F'\t' '{ print $0 "\t" length($2) }' |\
  (parallel --jobs $JOBS --gnu --pipe --files sort | parallel --jobs $JOBS --gnu -Xj1 sort -m {} ';' rm {}) |\
  sed '$d' |\
  awk -F'\t' '{ print "s/"$2"/"$1"/g" }' > $TMP_SED_CASELESS


# ###########################
# INDEX FACTS
# ###########################
#echo "[40] Index Facts ($OUT_OPENIE_INDEXED)"
#bunzip2 $OUT_OPENIE_UNNORMALIZED.bz2 &> /dev/null || true
#cat $OUT_OPENIE_UNNORMALIZED |\
#  grep -a -P '^[\x01-\x7f]+$' |\
#  cut -f 2-4 |\
#  sed -e 's/\t/ /g'  -e 's/\. //g' -e 's/["\(\)]//g' -e 's/[0-9]+//g' |\
#  ( parallel --jobs $JOBS --gnu --block 100M --pipe ./lemmatize ) |\
#  ( parallel --jobs $JOBS --gnu --pipe ./fastsed $TMP_SED_CASED )    |\
#  ( parallel --jobs $JOBS --gnu --pipe ./fastsed $TMP_SED_CASELESS ) |\
#  sed -e 's/[ 	]+/ /g' |\
#  paste $OUT_OPENIE_UNNORMALIZED - |\
#  ( parallel --jobs $JOBS --gnu --pipe cut -f 1,5) |\
#  egrep '^[0-9]+	[0-9][0-9 ]*$' > $OUT_OPENIE_INDEXED

echo "[45] Index Facts ($OUT_OPENIE_INDEXED_HEADS)"
bunzip2 $OUT_OPENIE_UNNORMALIZED.bz2 &> /dev/null || true
cat $OUT_OPENIE_UNNORMALIZED |\
  grep -a -P '^[\x01-\x7f]+$' |\
  cut -f 2-4 |\
  sed -e 's/\. //g' -e 's/["\(\)]//g' -e 's/[0-9]+//g' |\
  ( parallel --jobs $JOBS --gnu --block 100M --pipe ./simplify ) |\
  sed -e 's/\t/ /g' |\
  ( parallel --jobs $JOBS --gnu --pipe ./fastsed $TMP_SED_CASED )    |\
  ( parallel --jobs $JOBS --gnu --pipe ./fastsed $TMP_SED_CASELESS ) |\
  sed -e 's/[ 	]+/ /g' |\
  paste $OUT_OPENIE_UNNORMALIZED - |\
  ( parallel --jobs $JOBS --gnu --pipe cut -f 1,5) |\
  egrep '^[0-9]+	[0-9][0-9 ]*$' > $OUT_OPENIE_INDEXED_HEADS

# ###########################
# IMPORT TO PSQL
# ###########################
#echo "[50] Import to Postgres (from $OUT_OPENIE_INDEXED)"
#/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "DROP TABLE IF EXISTS fact_new;"
#/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE TABLE fact_new(gloss INTEGER[], weight INTEGER);"
#cat $OUT_OPENIE_INDEXED | grep -v '		' | sed -r -e 's/\s+$//g' -e 's/ +/"",""/g' -e 's/([0-9]+)\t(.*)/"{""\2""}","\1"/g' |\
#  /u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "COPY fact_new FROM STDIN DELIMITER ',' CSV;"
#/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE INDEX fact_new_weight ON fact_new USING BTREE (weight);"
#/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE INDEX fact_new_gloss ON fact_new USING GIN (gloss);"

echo "[55] Import to Postgres (from $OUT_OPENIE_INDEXED_HEADS)"
/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "DROP TABLE IF EXISTS fact_heads_new;"
/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE TABLE fact_heads_new(gloss INTEGER[], weight INTEGER);"
cat $OUT_OPENIE_INDEXED_HEADS | grep -v '		' | sed -r -e 's/\s+$//g' -e 's/ +/"",""/g' -e 's/([0-9]+)\t(.*)/"{""\2""}","\1"/g' |\
  /u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "COPY fact_heads_new FROM STDIN DELIMITER ',' CSV;"
/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE INDEX fact_heads_new_weight ON fact_heads_new USING BTREE (weight);"
/u/nlp/packages/pgsql/bin/psql -U $PGUSER -p $PGPORT -h $PGHOST $PGDATABASE -c "CREATE INDEX fact_heads_new_gloss ON fact_heads_new USING GIN (gloss);"


# ###########################
# BZIP
# ###########################
echo "[60] BZIP $OUT_OPENIE_UNNORMALIZED"
bzip2 $OUT_OPENIE_UNNORMALIZED
echo "[61] BZIP $OUT_OPENIE_INDEXED"
bzip2 $OUT_OPENIE_INDEXED
echo "[62] BZIP $OUT_OPENIE_INDEXED_HEADS"
bzip2 $OUT_OPENIE_INDEXED_HEADS
