#!/bin/bash
#

set -o nounset
set -o errexit
set -o xtrace

MYDIR=$( cd "$( dirname "$TASK_FILE" )" && pwd )

if [ ! -d "$MYDIR/corpora" ]; then
  mkdir "$MYDIR/corpora"
fi

if [ -e "$MYDIR/corpora/aristo.tab" ]; then
  mv "$MYDIR/corpora/aristo.tab" "$MYDIR/corpora/aristo.tab.bak"
fi
touch "$MYDIR/corpora/aristo.tab"

export CLASSPATH="$CLASSPATH:$HOME/workspace/naturalli/src/naturalli_preprocess.jar"
export CLASSPATH="$CLASSPATH:/u/nlp/data/StanfordCoreNLPModels/stanford-srparser-models-current.jar"

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
    java -mx20g edu.stanford.nlp.naturalli.ExpandCoreference
}

# Resolve coreference + grok the files
echo "barrons-sentences.txt"
generalRead "$MYDIR/rawcorpora/barrons-sentences.txt" |\
  awk -F'\t' '{ print "barrons-" $1 "\t" $2 }' \
  >> "$MYDIR/corpora/aristo.tab"

echo "ck12-biology-sentences.txt"
generalRead "$MYDIR/rawcorpora/ck12-biology-sentences.txt" |\
  awk -F'\t' '{ print "c12k_biology-" $1 "\t" $2 }' \
  >> "$MYDIR/corpora/aristo.tab"

echo "CurrentWebCorpus-allSources-v1.txt"
generalRead "rawcorpora/CurrentWebCorpus-allSources-v1.txt" |\
  awk -F'\t' '{ print "currentweb-" NR "\t" $1 }' \
  >> "$MYDIR/corpora/aristo.tab"

echo "dictionary-sentences.txt"
TMP=`mktemp`
cat "$MYDIR/rawcorpora/dictionary-sentences.txt" |\
  sed -r \
    -e 's/<term>([^<]+)<\/term>/\1/g' \
    -e 's/<.*:/ is/g' \
    -e 's/"//g' > "$TMP"
echo "Dictionary written to $TMP"
generalRead "$TMP" |\
  awk -F'\t' '{ print "dictionary-" $1 "\t" $2 }' \
  >> "$MYDIR/corpora/aristo.tab"
rm "$TMP"

echo "simplewiki-science-sentences.txt"
generalRead "$MYDIR/rawcorpora/simplewiki-science-sentences.txt" |\
  awk -F'\t' '{ print "simplewiki_science-" $1 "\t" $2 }' \
  >> "$MYDIR/corpora/aristo.tab"

echo "simplewiki-barrons-sentences.txt"
generalRead "$MYDIR/rawcorpora/simplewiki-barrons-sentences.txt" |\
  awk -F'\t' '{ print "simplewiki_barrons-" $1 "\t" $2 }' \
  >> "$MYDIR/corpora/aristo.tab"

# Clean up the corpus
cat "$MYDIR/corpora/aristo.tab" |\
  egrep -v '.{1000}.*' \
    > "$MYDIR/corpora/aristo.unicode.nouniq.tab"

cat "$MYDIR/corpora/aristo.unicode.nouniq.tab" |\
  perl -pe 's/[^[:ascii:]]//g' \
    > "$MYDIR/corpora/aristo.ascii.nouniq.tab"

cat "$MYDIR/corpora/aristo.ascii.nouniq.tab" |\
  sort -u -t'	' -k2 \
    > "$MYDIR/corpora/aristo.tab"

# Clean up the backup
if [ -e "$MYDIR/corpora/aristo.tab.bak" ]; then
  rm -f "$MYDIR/corpora/aristo.tab.bak"
fi


