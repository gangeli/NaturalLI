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
  A class encapsulating a HIT done by a worker.
"""
class HIT:
  def __init__(self, acceptTime, submitTime, durationMS, judgments):
    self.acceptTime = acceptTime
    self.submitTime = submitTime
    self.duration   = durationMS
    self.responses  = judgments

"""
  A class for collecting information on a worker, e.g., to find spammers.
"""
class WorkerStats:
  def __init__(self):
    self.workers = {}

  def registerHIT(self, workerID, acceptTime, submitTime, startTime, endTime, judgments):
    if not workerID in self.workers:
      self.workers[workerID] = []
    start = long(startTime) if startTime != 'N/A' and startTime != 'English' else long(endTime) + 1
    self.workers[workerID].append(HIT(
      time.strptime(acceptTime, "%a %b %d %H:%M:%S %Z %Y"),
      time.strptime(submitTime, "%a %b %d %H:%M:%S %Z %Y"),
      long(endTime) - start,
      judgments))

  def averageTimePerHIT(self, workerID):
    sumTime = 0
    denom = 0
    for hit in self.workers[workerID]:
      if hit.duration >= 0:
        sumTime += hit.duration
        denom += 1
    return 0.0 if denom == 0 else float(sumTime) / float(denom)
  
  def numHITs(self, workerID):
    return len(self.workers[workerID])
  
  def percentInstructionsRead(self, workerID):
    sumNoRead = 0
    for hit in self.workers[workerID]:
      if hit.duration < 0:
        sumNoRead += 1
    return 1.0 - float(sumNoRead) / float(len(self.workers[workerID]))
  
  def entropyOfResponses(self, workerID):
    # Compute the distribution
    p = {}
    for hit in self.workers[workerID]:
      for response in hit.responses:
        if not response in p:
          p[response] = 0
        p[response] += 1
    # Normalize the distribution
    Z = 0
    for key in p:
      Z += p[key]
    for key in p:
      p[key] = float(p[key]) / float(Z)
    # Compute the entropy
    entropy = 0.0
    for key in p:
      entropy -= ( p[key] * math.log(p[key]) )
    return entropy
  
  def printSuspiciousWorkers(self):
    turkers = [Turker(wid, self.averageTimePerHIT(wid), self.numHITs(wid), self.percentInstructionsRead(wid), self.entropyOfResponses(wid)) for wid in self.workers]
    for turker in sorted(turkers, key=lambda turker: -turker.score()):
      sys.stderr.write ("%f:  %s\n" % (turker.score(), turker))


class Turker:
  def __init__(self, workerID, averageTime, numHITs, percentRead, entropy):
    self.workerID = workerID
    self.averageTime = long(averageTime)
    self.numHITs = int(numHITs)
    self.percentRead = float(percentRead)
    self.entropy = float(entropy)

  """
    A metric for how good we think this Turker is.
  """
  def score(self):
    if self.percentRead > 0.0 and self.numHITs < 5:
      return 100.0
    else:
      return self.percentRead + min(1.3862943611198906 - self.entropy, self.entropy)

  def __str__(self):
    return self.workerID + " {numHITs: %d  aveTime: %d  percentRead: %f  entropy: %f}" % (
        self.numHITs, self.averageTime, self.percentRead, self.entropy)





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
  parser.add_argument('--filterTurkers', metavar='filterTurkers',
                      help='The turkers to filter from the results. See view-turkers.py, and the statistics at the bottom of this script')
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
  @param workerStats An object for collecting statistics on a worker.
  @param filteredWorkers A set of workers which have been filtered out.
"""
def process(f, count, negativesForPremise, workerStats, filteredWorkers):
  reader = csv.DictReader(f, delimiter=',', quotechar='"')
  allPremises = {}
  for row in reader:
    if not row['WorkerId'] in filteredWorkers:
      anySupport = False
      judgments = []
      for i in range(count):
        premise = row['Input.premise_' + str(i)]
        conclusion = row['Input.hypothesis_' + str(i)]
        if not premise in allPremises:
          allPremises[premise] = True
        
        answer = row['Answer.ex' + str(i)]
        judgments.append(answer)
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
      
      # Register worker
      workerStats.registerHIT(
          row['WorkerId'],
          row['AcceptTime'],
          row['SubmitTime'],
          row['Answer.start_time'],
          row['Answer.end_time'],
          judgments)
  
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

  # Read the filtered turkers
  filteredWorkers = {}
  for turker in opts.filterTurkers.split(","):
    filteredWorkers[turker] = True

  # Grok the file
  workerStats = WorkerStats()
  if len(opts.file) == 0:
    sys.stderr.write("Reading from stdin...\n")
    process(sys.stdin, opts.count, negativesForPremise, workerStats)
  else:
    for filename in opts.file:
      sys.stderr.write("Reading %s...\n" % filename)
      with open(filename, 'r') as f:
        process(f, opts.count, negativesForPremise, workerStats, filteredWorkers)
  sys.stderr.write ('Done processing files.\n')

  # Print worker stats
  sys.stderr.write ('\n\nTurker Statistics\n----------------\n')
  workerStats.printSuspiciousWorkers()



