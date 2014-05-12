There's a bunch of nonsense in here.
The two files you care about are:

  * Training data: `reverb_train.tab`
  * Test data: `reverb_test.tab`

Each of these four columns: the first column is whether Turkers judged
this fact to be "plausible" or "implausible".
The last three columns in turn are the tokenized ReVerb fact, as
a (subject, relation, object) triple.

The other files are just for converting from the raw keys used
in the 2013 CoNLL paper (http://stanford.edu/~angeli/papers/2013-conll-truth.pdf)
for Cassandra, and a more readable tab separated format for use by real people.

In particular, the pipeline is as follows:

  * Begin with `reverb_raw_train.mturk.out.csv` and `reverb_raw_test.mturk.out.csv`.
  * Convert these (via the process described in the CoNLL paper) to positive
    and negative keys: `reverb_[correct|wrong]_[train|test].keys`.
  * Create a key to sentence mapping from the mturk output files. This is
    stored in `reverb.keymap`
  * Use this keymap and the keys files to create the tab files you care about.
    This script is `mkTabFiles.py`.
