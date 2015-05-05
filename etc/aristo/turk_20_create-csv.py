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
      'Convert a file output by turk_10_run-queries.py into a CSV suitable for uploading to mturk.')
  parser.add_argument('--count', metavar='count', default=8,
                      type=int,
                      help='The number of queries per HIT. This should match the count in run-queries.py')
  parser.add_argument('--out', metavar='out', required=True,
                      help='The output CSV file to write to.')
  parser.add_argument('file', metavar='files', nargs='*',
                      help='The files to run on; if none are given, run off of stdin.')
  
  return parser.parse_args()

"""
  Write a single line of the CSV, based on a set of examples.

  @param examples A list of (truth, premise, hypothesis) lists.
                  This should be a bounded, fixed length == opts.count
  @param writer The CSV writer to write to.

  @return (void)
"""
def writeLine(examples, writer):
  fields = []
  for i in range(len(examples)):
    fields.append(examples[i][0])
    fields.append(examples[i][1])
    fields.append(examples[i][2])
  writer.writerow(fields)

"""
  Process an entire intput file or stream

  @param inFile The file (or stream) to read from.
                This should be formatted like the training examples for
                the classifier.
  @param count The number of examples to include in each CSV line.
  @param writer The CSV writer to write to.

  @return (void)
"""
def process(inFile, count, writer):
  lines = []
  for truth, premise, hypothesis in readClassifierData(inFile):
    if truth:
      lines.append([str(truth), premise, hypothesis])
      if len(lines) == count:
        writeLine(lines, writer);
        lines = []

"""
  The entry point of the program.
"""
if __name__ == "__main__":
  # Parse the arguments
  opts = parseargs()
  with open(opts.out, 'wb') as f:
    writer = csv.writer(f)
    # Write the CSV header
    header = []
    for i in range(opts.count):
      header.append('truth_' + str(i))
      header.append('premise_' + str(i))
      header.append('hypothesis_' + str(i))
    writer.writerow(header)
    # Process the files
    if len(opts.file) == 0:
      sys.stderr.write("Reading from stdin...\n")
      process(sys.stdin, opts.count, writer)
    else:
      for filename in opts.file:
        sys.stderr.write("Reading %s...\n" % filename)
        with open(filename, 'r') as f:
          process(f, opts.count, writer)



