#!/bin/bash
#

mv corpus.tab corpus.tab.bak

echo "barrons-sentences.txt"
cat barrons-sentences.txt |\
  awk -F'\t' '{ print "barrons-" $1 "\t" $2 }' \
  >> corpus.tab

echo "c12k-biology-sentences.txt"
cat ck12-biology-sentences.txt |\
  awk -F'\t' '{ print "ck12_biology-" $1 "\t" $2 }' \
  >> corpus.tab

echo "CurrentWebCorpus-allSources-v1.txt"
cat CurrentWebCorpus-allSources-v1.txt |\
  awk -F'\t' '{ print "currentweb-" NR "\t" $1 }' \
  >> corpus.tab

echo "dictionary-sentences.txt"
cat dictionary-sentences.txt |\
  awk -F'\t' '{ print "dictionary-" $1 "\t" $2 }' |\
  sed -r \
    -e 's/<term>([^<]+)<\/term>/\1/g' \
    -e 's/<.*:/ is/g' \
    -e 's/"//g' \
  >> corpus.tab

echo "simplewiki-science-sentences.txt"
export CLASSPATH="$CLASSPATH:/home/gabor/workspace/naturalli/src/naturalli_preprocess.jar"
export CLASSPATH="$CLASSPATH:/home/gabor/staging/stanford-srparser-models-current.jar"
cat simplewiki-science-sentences.txt |\
  sed -r -e 's/\.([0-9])\t/.0\1\t/g' |\
  sort -n |\
  java -mx3g edu.stanford.nlp.naturalli.ExpandCoreference |\
  awk -F'\t' '{ print "simplewiki_science-" $1 "\t" $2 }'
  >> corpus.tab

echo "simplewiki-barrons-sentences.txt"
cat simplewiki-barrons-sentences.txt |\
  sed -r -e 's/\.([0-9])\t/.0\1\t/g' |\
  sort -n |\
  java -mx3g edu.stanford.nlp.naturalli.ExpandCoreference |\
  awk -F'\t' '{ print "simplewiki_barrons-" $1 "\t" $2 }'
  >> corpus.tab

rm corpus.tab.bak
