#!/bin/bash
#
# Run the sql commands to scrape the prepositional attachment
# affinities for various syntactic patterns.
#
# This assumes as a prerequisite the following tables in the given
# postgres instance:
#  - syngrams_edge
#  - syngrams_biedge
#  - syngrams_triedge
#
# @see http://commondatastorage.googleapis.com/books/syntactic-ngrams/index.html

set -x

HOST=julie0

# Run the SQL script
#psql -h $HOST -p 4242 -U angeli angeli -f pp.sql

# Dump the results
psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > pp.tab.gz

psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM subj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > subj_pp.tab.gz

psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM subj_obj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > subj_obj_pp.tab.gz

psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM subj_obj_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > subj_obj_pp.tab.gz

psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM subj_pp_pp_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > subj_pp_pp.tab.gz

psql -h julie0 -U angeli -p 4242 \
  -c "COPY (SELECT * FROM subj_pp_obj_check WHERE positive_count > 100 AND percent > 0.1 ORDER BY percent DESC) TO STDOUT WITH NULL AS ''"\
  | gzip > subj_pp_obj.tab.gz
