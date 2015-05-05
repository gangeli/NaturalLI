Aristo Auxilliary Files + Scripts
=================================

This folder contains auxilliary files and scripts for processing the Aristo
4th grade science exam corpus.


Corpus Processing
-----------------

As a prerequisite for this portion, you should have the following files in a
directory `rawcorpora` under this directory:

  * `barrons-sentences.txt`
  * `ck12-biology-sentences.txt`
  * `CurrentWebCorpus-allSources-v1.txt`
  * `dictionary-sentences.txt`
  * `simplewiki-barrons-sentences.txt`
  * `simplewiki-science-sentences.txt`
  * `SimpleWiktionary.20140114.txt`

Then, from this directory, you can create a corpus file as a TSV with two fields:
the id of the sentence and a coreference-resolved surface form of a sentence.
This is made with the script:

        mkCorpus.sh

and will produce files -- roughly 300 MB in total:

  * `corpora/aristo.tab`: The uniq'd, ascii version of the corpus.
  * `corpora/aristo.ascii.nouniq.tab`: The ascii version of the corpus, including
    duplicates.
  * `corpora/aristo.unicode.nouniq.tab`: The original version of the corpus, including
    duplicates and including awkward non-ascii characters.


Mturk Pipeline
----------

The following was used to create the Turk CSV, and then the training data from
that.
All of these should be run from the project root.
Also, note that the pre-turk pipeline is basically guaranteed to not work unless
you're me running on one of my machines.

  1. __pipeline_preturk.sh__: This runs the requisite queries from the input 
     sentences, and generates the turk CSV. The CSV is generated into
     `turk_30_input.X.csv`, which should be renamed to the appropriate versioning.

  2. __pipeline_postturk.sh__: This runs the processing on 
     sentences, and generates the turk CSV.
     This produces a file `turk_90_trainset.tab`, which corresponds to the training
     set to be used for the classifier.


Other Utilities
----------

A few other useful utilities in the folder:

  * `query-negatives.py`: Given a test file in NaturalLI format (i.e., true / false
    labeled examples, as in `test/data`), query the false examples for candidate
    false premises. This is useful for, e.g., augmenting a training set with
    additional negative examples.
