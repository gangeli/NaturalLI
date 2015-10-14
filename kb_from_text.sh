#!/bin/bash
#

make src/naturalli_preprocess.jar

java -Dwordnet.database.dir=etc/WordNet-3.1/dict \
  -cp stanford-corenlp-3.5.3.jar:stanford-corenlp-models-current.jar:lib/jaws.jar:src/naturalli_preprocess.jar \
  edu.stanford.nlp.naturalli.ProcessPremise |\
  src/hash_tree  |\
  src/write_kb $1
