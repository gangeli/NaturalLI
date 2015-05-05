#!/bin/bash
#

set -o errexit

if [ -e corpus.tab ]; then
  mv corpus.tab corpus.tab.bak
fi
touch corpus.tab

export CLASSPATH="$CLASSPATH:/home/gabor/workspace/naturalli/src/naturalli_preprocess.jar"
export CLASSPATH="$CLASSPATH:/home/gabor/staging/stanford-srparser-models-current.jar"

function generalRead() {
  cat $1 |\
    sed -r -e 's/(\.|^)([0-9])([\.\t])/\10\2\3/g' |\
    sed -r -e 's/\.([0-9])([\.\t])/.0\1\2/g' |\
    sed -r -e 's/^\t/00\t/g' |\
    sed -r -e 's/^([0-9]+)\t/\1.00\t/g' |\
    sed -r -e 's/^([0-9]+\.[0-9]+)\t/\1.00\t/g' |\
    sed -r -e 's/^([0-9]+\.[0-9]+\.[0-9]+)\t/\1.00\t/g' |\
    sed -r -e 's/^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)\t/\1.00\t/g' |\
    sed -r -e 's/^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)\t/\1.00\t/g' |\
    sed -r -e 's/^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)\t/\1.00\t/g' |\
    java -mx4g edu.stanford.nlp.naturalli.ExpandCoreference
}


echo "barrons-sentences.txt"
generalRead barrons-sentences.txt |\
  awk -F'\t' '{ print "barrons-" $1 "\t" $2 }' \
  >> corpus.tab

echo "ck12-biology-sentences.txt"
generalRead ck12-biology-sentences.txt |\
  awk -F'\t' '{ print "c12k_biology-" $1 "\t" $2 }' \
  >> corpus.tab

echo "CurrentWebCorpus-allSources-v1.txt"
generalRead CurrentWebCorpus-allSources-v1.txt |\
  awk -F'\t' '{ print "currentweb-" NR "\t" $1 }' \
  >> corpus.tab

echo "dictionary-sentences.txt"
TMP=`mktemp`
cat dictionary-sentences.txt |\
  sed -r \
    -e 's/<term>([^<]+)<\/term>/\1/g' \
    -e 's/<.*:/ is/g' \
    -e 's/"//g' > "$TMP"
echo "Dictionary written to $TMP"
generalRead "$TMP" |\
  awk -F'\t' '{ print "dictionary-" $1 "\t" $2 }' \
  >> corpus.tab
rm "$TMP"

echo "simplewiki-science-sentences.txt"
generalRead simplewiki-science-sentences.txt |\
  awk -F'\t' '{ print "simplewiki_science-" $1 "\t" $2 }' \
  >> corpus.tab

echo "simplewiki-barrons-sentences.txt"
generalRead simplewiki-barrons-sentences.txt |\
  awk -F'\t' '{ print "simplewiki_barrons-" $1 "\t" $2 }' \
  >> corpus.tab

if [ -e corpus.tab.bak ]; then
  rm -f corpus.tab.bak
fi
