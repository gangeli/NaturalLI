#!/bin/bash
#

WORDS='/scr/nlp/data/openie/aux/vocab.tab'
INPUT_DIR='/scr/nlp/data/openie/output/reverb1.4'
TMP_DIR='/jagupard3/scr1/angeli/tmp'

OUT_OPENIE_UNNORMALIZED='output/openie_unnormalized.tab.bz2'
OUT_OPENIE_INDEXED='output/openie_indexed.tab.bz2'

echo "-------"
echo "NOTICE:"
echo "-------"
echo "This script will take a long time. In addition, it will create"
echo "a rather large number of files taking up a lot of space"
echo "IN THE CURRENT DIRECTORY."
echo ""
echo "Created Files:"
echo "  $OUT_OPENIE_UNNORMALIZED: All extractions in a single file"
echo "  $OUT_OPENIE_INDEXED:      The indexed facts"
echo ""
read -p "Hit ENTER to proceeed"

echo ""
echo "[00] START"

TMP_SED_CASED=`mktemp`
TMP_SED_CASELESS=`mktemp`

set -e
function cleanup {
  echo "[99] Cleaning up..."
  rm  -f $TMP_SED_CASED
  rm  -f $TMP_SED_CASELESS
}
trap cleanup EXIT


# ###########################
# AGGREGATE FACTS
# ###########################
echo "[10] Sort+Uniq Facts ($OUT_OPENIE_UNNORMALIZED)"
mkdir -p output
zcat `find $INPUT_DIR/ -name "*.reverb.gz"` |\
  cut -f 3-5 |\
  perl -pe 's/([0-9]+)/length($1)/gei' | sed -r -e 's/([0-9]+)/num_\1/g' |\
  (parallel --gnu --pipe --files sort | parallel -Xj1 sort -m {} ';' rm {}) |\
  uniq -c |\
  sed -e 's/^ *\([0-9][0-9]*\) /\1\t/' |\
  ( parallel --gnu --pipe bzip2 ) > $OUT_OPENIE_UNNORMALIZED

# ###########################
# CREATE REPLACEMENT PATTERNS
# ###########################
echo "[20] Create Case Sensitive Replacements ($TMP_SED_CASED)"
cat $WORDS |\
  awk -F'\t' '{ print $0 "\t" length($2) }' |\
  (parallel --gnu --pipe --files sort | parallel --gnu -Xj1 sort -m {} ';' rm {}) |\
  sed '$d' |\
  awk -F'\t' '{ print "s/"$2"/"$1"/g" }' > $TMP_SED_CASED

echo "[30] Create Case In-sensitive Replacements ($TMP_SED_CASELESS)"
cat $WORDS |\
  grep '.*[A-Z].*' |\
  tr '[:upper:]' '[:lower:]' |\
  awk -F'\t' '{ print $0 "\t" length($2) }' |\
  (parallel --gnu --pipe --files sort | parallel --gnu -Xj1 sort -m {} ';' rm {}) |\
  sed '$d' |\
  awk -F'\t' '{ print "s/"$2"/"$1"/g" }' > $TMP_SED_CASELESS


# ###########################
# INDEX FACTS
# ###########################
echo "[40] Index Facts ($OUT_OPENIE_INDEXED)"
( parallel --gnu --pipe bzcat $OUT_OPENIE_UNNORMALIZED ) |\
  cut -f 2-4 |\
  ( parallel --gnu --pipe sed -e 's/\t/ /g'  -e 's/\. //g' -e 's/["\(\)]//g' -e 's/[0-9]+//g' ) |\
  ( parallel --gnu --jobs 4 --pipe ./fastsed $TMP_SED_CASED ) |\
  ( parallel --gnu --jobs 4 --pipe ./fastsed $TMP_SED_CASELESS ) |\
  sed -e 's/ +/ /g' |\
  paste $OUT_OPENIE_UNNORMALIZED - |\
  cut -f 1,5 |\
  ( parallel --gnu --pipe egrep '^[0-9]+       [0-9 ]*$' ) |\
  ( parallel --gnu --pipe bzip2 ) > $OUT_OPENIE_INDEXED



