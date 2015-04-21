#!/usr/bin/env python
#

import argparse
import sys
import os
from math import exp,log
import json

sys.path.append(os.path.dirname(os.path.realpath(__file__)) + '/../../lib/')
from naturalli import *

def parseargs():
  parser = argparse.ArgumentParser(description=
      'Convert the SNLI corpus to a TSV we can pass into TrainEntailment.')
  parser.add_argument('file', metavar='files', nargs='+',
                      help='The SNLI jsonl files to run on.')
  return parser.parse_args()

def toTrilean(s):
  if s == 'entailment':
    return 'True'
  elif s == 'contradiction':
    return 'False'
  elif s == 'neutral':
    return 'Unknown'
  else:
    raise Exception("Unknown trilean value: %s" % s)
  
if __name__ == "__main__":
  opts = parseargs()
  i = 0
  for path in opts.file:
    with open(path) as f:
      for line in withProgress(f.readlines(), 'Reading JSON'):
        data = json.loads(line.decode('utf-8'))
        print("%d\t%s\t%s\t%s" % (
          i,
          toTrilean(mode(data['annotator_labels'])),
          data['sentence1'].encode('utf-8'),
          data['sentence2_corrected'].encode('utf-8')
          ))
        i += 1
  sys.stderr.write("Done.\n")
