#!/usr/bin/env python
#

import argparse
import sys
import os
import csv
import time
import math

sys.path.append(os.path.dirname(os.path.realpath(__file__)) + '/../../lib/')
from naturalli import *

"""
  Parse the command line arguments
"""
def parseargs():
  parser = argparse.ArgumentParser(description=
      'Convert the output of a turk task into a set of training examples for the classifier.')
  parser.add_argument('--count', metavar='count', default=8,
                      type=int,
                      help='The number of queries per HIT. This should match the count in run-queries.py')
  parser.add_argument('--turker', metavar='turker', required=True,
                      help='The turker to review.')
  parser.add_argument('file', metavar='files', nargs='*',
                      help='The turker files to grep through.')
  
  return parser.parse_args()
        
def writeLine(truth, premise, conclusion):
  print("%d\t%s\t%s\t%s" % (0, 'True' if truth else 'False', premise, conclusion))

"""
  Process a given turk file, writing an output in the Classifier data format.

  @param f The file to process
  @param count The number of queries in a HIT. This should match the value 
               used when creating the HIT.
  @param negativesForPremise A map from a premise to negative hypothesis from
                             that premise.
  @param workerStats An object for collecting statistics on a worker.
"""
def process(f, count, turker):
  reader = csv.DictReader(f, delimiter=',', quotechar='"')
  for row in reader:
    if row['WorkerId'] == turker:
      for i in range(count):
        premise = row['Input.premise_' + str(i)]
        conclusion = row['Input.hypothesis_' + str(i)]
        answer = row['Answer.ex' + str(i)]
        if answer == 'Forward':
          print("%s\n  =>  %s" % (premise, conclusion))
        elif answer == 'Backward':
          print("%s\n  <=> %s" % (premise, conclusion))
        elif answer == 'Both':
          print("%s\n  <=  %s" % (premise, conclusion))
        elif answer == 'Neither':
          print("%s\n  </> %s" % (premise, conclusion))
        else:
          raise Exception("Unknown answer: %s" % answer)

"""
  The entry point of the program.
"""
if __name__ == "__main__":
  # Parse the arguments
  opts = parseargs()

  for filename in opts.file:
    with open(filename, 'r') as f:
      process(f, opts.count, opts.turker)
