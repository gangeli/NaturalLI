#!/usr/bin/env python
#

import argparse
import sys
import os
from math import exp,log

sys.path.append(os.path.dirname(os.path.realpath(__file__)) + '/../../lib/')
from naturalli import *

"""
  Parse the command line arguments
"""
def parseargs():
  parser = argparse.ArgumentParser(description=
      'Run the IR queries to get negatives over a given test file (in NaturalLI format)')
  parser.add_argument('--host', metavar='host', default='localhost',
                      help='The hostname where Solr is running (default: localhost).')
  parser.add_argument('--port', metavar='port', default=8983,
                      type=int,
                      help='The port where Solr is running (default: 8983).')
  parser.add_argument('--collection', metavar='collection', required=True,
                      help='The Solr collection to query.')
  parser.add_argument('--negativesPerQuery', metavar='negativesPerQuery', default=1,
                      type=int,
                      help='The number of results to treat as negatives for each query (default: 1).')
  parser.add_argument('file', metavar='files', nargs='+',
                      help='The test files to run on, in NaturalLI format.')
  
  return parser.parse_args()

"""
  The entry point of the program.
"""
if __name__ == "__main__":
  # Parse the arguments
  opts = parseargs()
  solrURL = 'http://' + opts.host + ':' + str(opts.port) + '/solr/' + opts.collection + '/select/'

  # Read the examples
  examples = []
  for exampleFile in opts.file:
    examples.extend(readData(exampleFile))

  # Run queries
  qI = 0
  for group in withProgress(examples, 'Running queries'):
    for statement in group:
      if not statement.truth:
        results = query(solrURL, statement.text, opts.negativesPerQuery)
        for result in results:
          print("%d\t%s\t%s\t%s" % (
                qI,
                'False',
                result,
                statement.text
               ))
    qI += 1
