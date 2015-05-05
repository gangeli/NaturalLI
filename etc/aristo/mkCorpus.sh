#!/bin/bash
#

set -o errexit

if [ -d corpora ]; then
  mkdir corpora
fi

if [ -e corpora/aristo.tab ]; then
  mv corpora/aristo.tab corpora/aristo.tab.bak
fi
touch corpora/aristo.tab

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

# Resolve coreference + grok the files
echo "barrons-sentences.txt"
generalRead rawcorpora/barrons-sentences.txt |\
  awk -F'\t' '{ print "barrons-" $1 "\t" $2 }' \
  >> corpora/aristo.tab

echo "ck12-biology-sentences.txt"
generalRead rawcorpora/ck12-biology-sentences.txt |\
  awk -F'\t' '{ print "c12k_biology-" $1 "\t" $2 }' \
  >> corpora/aristo.tab

echo "CurrentWebCorpus-allSources-v1.txt"
generalRead rawcorpora/CurrentWebCorpus-allSources-v1.txt |\
  awk -F'\t' '{ print "currentweb-" NR "\t" $1 }' \
  >> corpora/aristo.tab

echo "dictionary-sentences.txt"
TMP=`mktemp`
cat rawcorpora/dictionary-sentences.txt |\
  sed -r \
    -e 's/<term>([^<]+)<\/term>/\1/g' \
    -e 's/<.*:/ is/g' \
    -e 's/"//g' > "$TMP"
echo "Dictionary written to $TMP"
generalRead "$TMP" |\
  awk -F'\t' '{ print "dictionary-" $1 "\t" $2 }' \
  >> corpora/aristo.tab
rm "$TMP"

echo "simplewiki-science-sentences.txt"
generalRead rawcorpora/simplewiki-science-sentences.txt |\
  awk -F'\t' '{ print "simplewiki_science-" $1 "\t" $2 }' \
  >> corpora/aristo.tab

echo "simplewiki-barrons-sentences.txt"
generalRead rawcorpora/simplewiki-barrons-sentences.txt |\
  awk -F'\t' '{ print "simplewiki_barrons-" $1 "\t" $2 }' \
  >> corpora/aristo.tab

# Clean up the corpus
mv corpora/aristo.tab corpora/aristo.unicode.nouniq.tab

cat corpora/aristo.unicode.nouniq.tab |\
  perl -pe 's/[^[:ascii:]]//g' \
    > corpora/aristo.ascii.nouniq.tab

cat corpora/aristo.ascii.nouniq.tab |\
  sort -u -t'	' -k2 \
    > aristo.tab

# Clean up the backup
if [ -e corpora/aristo.tab.bak ]; then
  rm -f corpora/aristo.tab.bak
fi


