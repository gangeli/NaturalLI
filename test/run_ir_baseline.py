#!/usr/bin/env python3
#

import argparse
import json
import sys
from urllib.request import urlopen
from urllib.parse import urlencode
from math import exp

"""
  A question converted to a statement, with the associated
  truth value.
"""
class Statement:
  def __init__(self, text, truth, answer):
    self.text = text
    self.truth = truth
    if answer == None:
      self.answer = text
    else:
      self.answer = answer

  def text():
    return text

  def truth():
    return truth

  def __str__(self):
    return str(self.truth) + ": " + self.answer

"""
  A group of statements, of which one and exactly one is true.
  This is effectively a multiple choice question group.
"""
class StatementGroup:
  def __init__(self, question, statements):
    self.question = question
    self.statements = statements
    self.correctIndex = None
    for i in range(len(self.statements)):
      if self.statements[i].truth:
        if self.correctIndex != None:
          raise Exception("Multiple correct indices for group!")
        self.correctIndex = i

  def __str__(self):
    return 'StatementGroup(' + str(len(self.statements)) + ', ' + self.question + ')'

  def __getitem__(self, k):
    return self.statements[k]

  def __len__(self):
    return len(self.statements)

  def correct(self):
    return self.statements[self.correctIndex].text

  def incorrect(self):
    rtn = []
    for i in range(len(self.statements)):
      if i != self.correctIndex:
        rtn.append(self.statements[i].text)
    return rtn

"""
  The result of a Lucene (Solr) query.
  Contains the query, the total number of hits, and the top
  results returned from the initial query.
"""
class QueryResult:
  def __init__(self, query, numHits, results):
    self.query = query
    self.numHits = numHits
    self.results = []
    for result in results:
      self.results.append(result['body'][0])

  def __getitem__(self, k):
    return self.results[k]

  def __len__(self):
    return len(self.results)
  
  def __str__(self):
    return 'QueryResult(' + str(self.numHits) + ', ' + self.results[0] + ')'

"""
  Parse the command line arguments
"""
def parseargs():
  parser = argparse.ArgumentParser(description=
      'Run the IR baseline on one of the test files (see test/data)')
  parser.add_argument('--host', metavar='host', default='localhost',
                      help='The hostname where Solr is running (default: localhost).')
  parser.add_argument('--port', metavar='port', default=8983,
                      type=int,
                      help='The port where Solr is running (default: 8983).')
  parser.add_argument('--collection', metavar='collection', required=True,
                      help='The Solr collection to query.')
  parser.add_argument('--stopwords', metavar='stopwords',
                      help='A stop word file with stop words to ignore.')
  parser.add_argument('file', metavar='files', nargs='+',
                      help='The test files to run on.')
  
  return parser.parse_args()

"""
  Read the data from a file, formatted like the other test files.
  A statement is the last line of a group before a double newline.
  Statement groups are separated by a comment beginning with
  '#Q: ' and then the question being asked. The literal answers can be
  marked with comments beginning with '#A: '.

  @param path A String representing the file path to read.

  @return A list of StatementGroups representing the multiple choice
          questions in this file. If these are not multiple choice
          questions, a single StatementGroup will be returned.
"""
def readData(path):
  # The examples, and example groups
  examples = []
  groups = []
  lastQuestion = None
  with open(path) as f:
    # Read the file
    content = f.readlines()
    lastLine = None
    pendingAnswer = None
    # For each line...
    for line in content:
      line = line.strip()
      if line == '':
        # ... If it's a newline, register the statement
        example = Statement(lastLine, True, pendingAnswer)
        if lastLine.startswith('TRUE: '):
          example = Statement(lastLine[6:], True, pendingAnswer)
        elif lastLine.startswith('FALSE: '):
          example = Statement(lastLine[7:], False, pendingAnswer)
        elif lastLine.startswith('UNK: '):
          example = Statement(lastLine[5:], False, pendingAnswer)
        examples.append(example)
        pendingAnswer = None
      elif line.startswith('#A: '):
        # ... If it's an answer marker, register the answer.
        pendingAnswer = line[4:]
      elif line.startswith('#Q: '):
        # ... If it's a question marker, register the question
        if len(examples) > 0:
          groups.append(StatementGroup(lastQuestion, examples))
        lastQuestion = line[4:]
        examples = []
      lastLine = line
  # Return the groups
  if len(examples) > 0:
    groups.append(StatementGroup(lastQuestion, examples))
  return groups

"""
  Query Solr at the given URL with the given query

  @param url The URL to query Solr at, as a String.
  @param query The query, as a String.

  @return A QueryResult object representing the result of this query.
"""
def query(url, query):
  f = urlopen(url + '?' + urlencode({'wt': 'json', 'q': query}))
  data = json.loads(f.read().decode('utf8'))
  return QueryResult(query, data['response']['numFound'], data['response']['docs'])


"""
  Take the overlap between two token lists, ignoring stop words.
  This will return a normalized value between 0 and 1, defined as
  (a \intersect b) / min(|a|, |b|).

  @param tokA The tokens of A, as a List.
  @param tokB The tokens of B, as a List.
  @param stopwords The stopwords to ignore, as a Set.

  @return A normalized overlap metric between 0 and 1, as a Float.
"""
def naiveOverlap(tokA, tokB, stopwords):
  # Create sets
  setA = set(tokA) - stopwords
  if len(setA) == 0:
    setA = set(tokA)
  setB = set(tokB) - stopwords
  if len(setB) == 0:
    setB = set(tokB)
  # Compute overlap
  return float(len(setA & setB)) / float(min(len(setA), len(setB)))

"""
  Call the appropriate overlap measure between two strings.
  This serves to (1) hold common computations between overlap
  measures, and (2) route the control flow to the right measure.

  @param strA The first String, usually the candidate answer.
  @param strB The second String, usually the query result.

  @return An overlap measure, as a Float.
"""
def overlapMeasure(strA, strB, stopwords):
  # Split and lowercase tokens
  tokA = [x.lower() for x in strA.split(' ')]
  tokB = [x.lower() for x in strB.split(' ')]
  overlap = naiveOverlap(tokA, tokB, stopwords)
  return overlap

"""
  Find the best candidate at the k-th search result.

  @param example The example we are processing (as a StatementGroup).
  @param results A list of QueryResult objects, one for each statement
                 in the example.
  @param stopwords A set of stop words, as a Set.
  @param k The kth result to consider.

  @return The index of the guessed choice, as an Integer.
"""
def guessAtLevel(example, results, stopwords, k):
  argmax = None
  maxScore = -1.0
  for i in range(len(example)):
    statement = example[i]
    result = results[i]
    score = overlapMeasure(statement.answer, result[k], stopwords)
    if score > maxScore:
      maxScore = score
      argmax = i
  return argmax


def averageScore(question, statement, result, stopwords):
  sumScore = 0.0
  for resultI in range(len(result)):
    score = 0.0
    # Case: take the overlap between the candidate and the result
    score = overlapMeasure(statement.answer, result[resultI], stopwords)
    if overlapMeasure(question, result[resultI], stopwords) == 0.0:
      # Case: the result has nothing to do with the question
      score *= 0.5
    # Degrade the score a bit
    score *= exp(- 4.0 * float(resultI) / float(len(result)))
#    score *= (1.0 - float(resultI) / float(len(result)))
    sumScore += score
  return sumScore / float(len(result))


"""
  Guess the answer, given a query backend and an example.

  @param solrURL The URL of the Solr instance to query, as a String.
  @param example The example to guess on, as a StatementGroup.
  @param stopwords A set of stop words, as a Set.

  @return The guessed index of the correct answer, as an Integer.
"""
def guess(solrURL, example, stopwords):
  results = [query(solrURL, statement.text) for statement in example]

  # Average the scores of the top results, with degredation
  # as you go down the results
  maxV = 0.0
  argmax = 0
  for candidateI in range(len(example)):
    score = averageScore(example.question, example[candidateI], results[candidateI], stopwords)
    if score > maxV:
      maxV = score
      argmax = candidateI
  return argmax

#  Alternative, take the top result only
#  level = 0
#  correctIndex = guessAtLevel(example, results, stopwords, level)
#  while correctIndex == None and level < 5:
#    level += 1
#    correctIndex = guessAtLevel(example, results, stopwords, level)
#  if correctIndex == None:
#    return 0
#  else:
#    return correctIndex


#  Alternative: vote amongst top $k$ results
#  guesses = {}
#  for level in range(1):
#    guess = guessAtLevel(example, results, stopwords, level)
#    if not guess in guesses:
#      guesses[guess] = 0
#    guesses[guess] += 1
#  maxV = 0.0
#  argmax = 0
#  for key in guesses:
#    if guesses[key] > maxV:
#      maxV = guesses[key]
#      argmax = key
#  return argmax


"""
  The entry point of the program.
"""
if __name__ == "__main__":
  # Parse the arguments
  opts = parseargs()
  solrURL = 'http://' + opts.host + ':' + str(opts.port) + '/solr/' + opts.collection + '/select/'
  
  # Read stop words
  stopwords = set()
  if opts.stopwords != None:
    with open(opts.stopwords) as f:
      # Read the file
      stopwords = set([x.strip() for x in f.readlines()])
      print("Read " + str(len(stopwords)) + " stop words")

  # Read the examples
  examples = []
  for exampleFile in opts.file:
    examples.extend(readData(exampleFile))

  # Process he examples
  numCorrect = 0
  numTotal = float(len(examples))
  for i in range(len(examples)):
    sys.stdout.write("\rRunning queries... %d%%" % (100.0 * float(i+1) / numTotal))
    sys.stdout.flush()
    example = examples[i]
    guessIndex = guess(solrURL, example, stopwords)
    if guessIndex == example.correctIndex:
      numCorrect += 1

  # Print the accuracy
  print("")
  print("")
  print("Accuracy: " + '{0:.3g}'.format(float(numCorrect) / numTotal))
