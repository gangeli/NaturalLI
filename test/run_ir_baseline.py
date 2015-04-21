#!/usr/bin/env python
#
# Run an IR baseline, where a query is run for each candidate,
# and the result which has the best Lucene score and the best
# lexical overlap is taken as the answer to the question.
#
# @see lib/naturalli.py for a lot of the common utilities.
#
# @author Gabor
#

import argparse
import sys
import os
from math import exp,log

sys.path.append(os.path.dirname(os.path.realpath(__file__)) + '/../lib/')
from naturalli import *


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

  tokA = [x.lower() for x in strA.split(' ') if x != 'what' and x != 'why' and x != 'how']
  tokB = [x.lower() for x in strB.split(' ') if x != 'what' and x != 'why' and x != 'how']
  try:
    from stemming.porter2 import stem
    tokA = [stem(x) for x in tokA]
    tokB = [stem(x) for x in tokB]
  except:
    pass
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

"""
  Compute a weighted average score of the overlap between a question and
  the answer, based on the top search results.
  The scores are degraded in two ways:
    (1) If there is no overlap between the question and the result, the
        score is immediately degraded by half
    (2) In addition, as we go down the results list, we degrade the score
        exponentially (though with a low coefficient)

  @param question The question being asked about, as a String.
  @param statement The statement we are evaluating, as a Statement.
  @param result The result of the query, as a QueryResult.
  @param stopwords The stop word set, as a Set.

  @return A score for this statement being the answer to the question,
          as a Float.
"""
def averageScore(question, statement, result, stopwords):
  sumScore = 0.0
  for resultI in range(len(result)):
    score = 0.0
    # Case: take the overlap between the candidate and the result
    score = overlapMeasure(statement.answer, result[resultI], stopwords)
#    if overlapMeasure(question, result[resultI], stopwords) == 0.0:
#      # Case: the result has nothing to do with the question
#      score *= 1.00
    # Degrade the score a bit
    score *= exp(- 10.0 * float(resultI) / float(len(result)))
#    score *= result.scores[resultI]
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
#  results = [query(solrURL, statement.answer + ' ' + example.question) for statement in example]

  # Average the scores of the top results, with degredation
  # as you go down the results
  maxV = 0.0
  argmax = 0
  for candidateI in range(len(example)):
    lexScore = averageScore(example.question, example[candidateI], results[candidateI], stopwords)
    solrScore = results[candidateI].topScore()
#    solrScore = 1.0
#    aveSolrScore = results[candidateI].averageScore() / float(len(results[candidateI]))
    aveSolrScore = 0.0
    score = pow(lexScore, 2.0) * solrScore + aveSolrScore
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
  for example in withProgress(examples, 'Running queries'):
    guessIndex = guess(solrURL, example, stopwords)
    if guessIndex == example.correctIndex:
      numCorrect += 1

  # Print the accuracy
  print("")
  print("")
  print("Accuracy: " + '{0:.4g}%'.format(100.0 * float(numCorrect) / numTotal))
