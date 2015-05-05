#!/usr/bin/env python
#

import argparse
import sys
import os
import csv

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
  parser.add_argument('--negativeSource', metavar='negativeSource',
                      help='A NaturalLI formatted test file that can be used to sample negatives.')
  parser.add_argument('file', metavar='files', nargs='*',
                      help='The files to run on; if none are given, run off of stdin.')
  
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
"""
def process(f, count, negativesForPremise):
  reader = csv.DictReader(f, delimiter=',', quotechar='"')
  allPremises = {}
  for row in reader:
    anySupport = False
    for i in range(count):
      premise = row['Input.premise_' + str(i)]
      conclusion = row['Input.hypothesis_' + str(i)]
      if not premise in allPremises:
        allPremises[premise] = True
      
      answer = row['Answer.ex' + str(i)]
      if answer == 'Forward':
        writeLine(True, premise, conclusion)
        anySupport = True
      elif answer == 'Backward':
        writeLine(True, conclusion, premise)
        anySupport = True
      elif answer == 'Both':
        writeLine(True, premise, conclusion)
        writeLine(True, conclusion, premise)
        anySupport = True
      elif answer == 'Neither':
        writeLine(False, premise, conclusion)
      else:
        raise Exception("Unknown answer: %s" % answer)
      
    # There should be at least one supporting premise for each question
    if not anySupport:
      sys.stderr.write("No support for question: %s\n" % row['Input.hypothesis_0'])
  
  for premise in allPremises:
    if premise in negativesForPremise:
      for negativeConclusion in negativesForPremise[premise]:
        writeLine(False, premise, negativeConclusion)
    

"""
  The entry point of the program.
"""
if __name__ == "__main__":
  # Parse the arguments
  opts = parseargs()
  
  # Read the negatives
  negativesForPremise = {}
  if opts.negativeSource:
    sys.stderr.write ('Reading negatives from %s...\n' % opts.negativeSource)
    with open(opts.negativeSource, 'r') as negativeSource:
      for truth, premise, hypothesis in readClassifierData(negativeSource):
        if not truth:
          if not premise in negativesForPremise:
            negativesForPremise[premise] = []
          if not hypothesis in negativesForPremise[premise]:
            negativesForPremise[premise].append(hypothesis)
  sys.stderr.write ('Done reading negatives (%d).\n' % len(negativesForPremise))

  # Grok the file
  if len(opts.file) == 0:
    sys.stderr.write("Reading from stdin...\n")
    process(sys.stdin, opts.count, negativesForPremise)
  else:
    for filename in opts.file:
      sys.stderr.write("Reading %s...\n" % filename)
      with open(filename, 'r') as f:
        process(f, opts.count, negativesForPremise)
  sys.stderr.write ('Done processing files.\n')



