#!/bin/bash
#

make src/naturalli_client.jar

java -Dwordnet.database.dir=etc/WordNet-3.1/dict \
  -cp $CLASSPATH:src/naturalli_client.jar \
  edu.stanford.nlp.naturalli.Preprocess |\
  src/naturalli_mkkb
