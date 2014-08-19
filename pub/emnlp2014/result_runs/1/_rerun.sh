#!/bin/sh
#
# Automatically generated with Qry (https://github.com/gangeli/qry)

# Restore working directory
cd /juice/scr20/scr/angeli/workspace/naturalli-emnlp
# Manage Git
if [ `git rev-parse --verify HEAD` != "7f921b49ff58a3ce11116acb461f4fd09b0df8e9" ]; then echo "WARNING: Git revision has changed from 7f921b49ff58a3ce11116acb461f4fd09b0df8e9"; fi
mkdir -p /juice/scr20/scr/angeli/workspace/naturalli-emnlp/runs/1/_rerun
# Run Program
'java'\
	'-cp' 'src/naturalli_client.jar:test/src/test_client.jar:/user/angeli/workspace/nlp//projects/core/classes:/user/angeli/workspace/nlp//projects/core/lib/joda-time.jar:/user/angeli/workspace/nlp//projects/core/lib/jollyday-0.4.7.jar:/user/angeli/workspace/nlp//projects/more/classes:/user/angeli/workspace/nlp//projects/more/lib/BerkeleyParser.jar:/user/angeli/workspace/nlp//projects/research/classes:/user/angeli/workspace/nlp//projects/research/lib/reverb.jar:/user/angeli/workspace/nlp//projects/research/lib/postgresql.jar:/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-models-current.jar:/u/nlp/data/StanfordCoreNLPModels/stanford-corenlp-caseless-models-current.jar:/juice/u21/u/nlp/packages/scala/scala-2.11.0/lib/scala-library.jar:/juice/u21/u/nlp/packages/scala/scala-2.11.0/lib/config-1.2.0.jar:/juice/u21/u/nlp/packages/scala/scala-2.11.0/lib/scala-xml_2.11-1.0.1.jar:lib/corenlp-scala.jar:lib/trove.jar:lib/jaws.jar:lib/scripts/sim-1.0.jar:lib/protobuf.jar'\
	'-Xmx8G' \
	'-Xss32m' \
	'-XX:MaxPermSize=256M' \
	'-Dwordnet.database.dir=etc/WordNet-3.1/dict' \
	'-server' \
	'-ea' 'org.goobs.truth.Evaluate'\
	'-learn.model.start' '0'\
	'-learn.train' '{conceptnet_mturk_train}'\
	'-learn.online.iterations' '1000000'\
	'-server.backup.discount' '0.9'\
	'-server.backup.host' 'localhost'\
	'-psql.password' ''\
	'-psql.port' '5432'\
	'-log.captureStreams' 'true'\
	'-learn.model.dir' '/home/gabor/workspace/truth/models/ConceptNet/'\
	'-log.channels.debug' 'false'\
	'-server.backup.threads' '16'\
	'-server.backup.port' '1339'\
	'-server.main.host' 'john10'\
	'-server.backup.do' 'false'\
	'-learn.test' '{conceptnet_mturk_test}'\
	'-psql.username' 'angeli'\
	'-log.collapse' 'none'\
	'-evaluate.allowunk' 'false'\
	'-psql.db' 'naturalli'\
	'-log.neatExit' 'true'\
	'-server.main.port' '1337'\
	'-evaluate.model' 'models/ConceptNet/offline.tab'\
	'-learn.prCurve' 'logs/lastPR.dat'\
	'-search.timeout' '100000'\
	'-learn.offline.sigma' '100'\
	'-log.channels.width' '20'\
	'-server.main.threads' '16'\
	'-learn.online.sgd.nu' '1'\
	'-learn.iterations' '10000'\
	'-learn.offline.passes' '10'\
	'-psql.host' 'jonsson'\
	'-search.allowlookup' 'false'\
	'-log.file' 'logs/lastrun.log'\
	'-search.allowlookup' 'false'\
	'-evaluate.model' 'models/ConceptNet-save/offline.tab'\
	'-log.file' '/juice/scr20/scr/angeli/workspace/naturalli-emnlp/runs/1/_rerun/redwood.log'