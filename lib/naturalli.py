#!/usr/bin/env python
#

import sys
PYTHON3 = None
try:
  from urllib.request import urlopen
  from urllib.parse import urlencode
  sys.stderr.write("[naturalli.py]: Using Python 3\n")
except:
  PYTHON3=False
  pass
try:
  from urllib import urlopen
  from urllib import urlencode
  sys.stderr.write("[naturalli.py]: Using Python 2\n")
except:
  PYTHON3=True
  pass
import json

"""
  A question converted to a statement, with the associated
  truth value.
"""
class Statement:
  def __init__(self, text, focus, truth, answer):
    self.text = text
    self.focus = focus
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
  def __init__(self, query, focus, numHits, results, maxScore):
    self.query = query
    self.focus = focus
    self.numHits = numHits
    self.results = results
    self.results = [r['text'][0] for r in results]
    self.scoresRelative = [float(r['score'] / maxScore) for r in results]
    self.scores = [float(r['score']) for r in results]

  def __getitem__(self, k):
    return self.results[k]

  def __len__(self):
    return len(self.results)
  
  def __str__(self):
    return 'QueryResult(' + str(self.numHits) + ', ' + self.results[0] + ')'

  def topScore(self):
    if len(self.scores) > 0:
      return self.scores[0]
    else:
      return 0.0
  
  def averageScore(self):
    sumV = 0.0
    for score in self.scores:
      sumV += score
    if len(self.scores) == 0:
      return sumV
    else:
      return sumV / float(len(self.scores))


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
    pendingAnswer = ""
    # For each line...
    for line in content:
      line = line.strip()
      if line == '':
        # ... If it's a newline, register the statement
        example = Statement(lastLine, pendingAnswer, True, pendingAnswer)
        if lastLine.startswith('TRUE: '):
          example = Statement(lastLine[6:], pendingAnswer, True, pendingAnswer)
        elif lastLine.startswith('FALSE: '):
          example = Statement(lastLine[7:], pendingAnswer, False, pendingAnswer)
        elif lastLine.startswith('UNK: '):
          example = Statement(lastLine[5:], pendingAnswer, False, pendingAnswer)
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
  Read a data file formatted in the classifier training format.
  That is, a TSV file with columns:
    0. The ID of the question
    1. The truth value of the conclusion given the premise.
    2. The premise.
    3. The conclusion (hypothesis).

  @param inStream An input stream to read from -- this can be an open file,
         or a stream like stdin.

  @return A List of Tuples, with elements (truth, premise, conclusion).
"""
def readClassifierData(inStream):
  lines = []
  for line in inStream.readlines():
    line = line.strip();
    fields = line.split('\t')
    exID = fields[0]
    truth = True if fields[1].lower() == 'true' else False
    premise = fields[2]
    hypothesis = fields[3]
    yield (truth, premise, hypothesis)

"""
  Query Solr at the given URL with the given query

  @param url The URL to query Solr at, as a String.
  @param query The query, as a String.
  @param focus The focus of the query; for example, the answer to the multiple
               choice question. This can be empty if there is no focus.

  @return A QueryResult object representing the result of this query.
"""
def query(url, query, focus, rows=10):
  # Run query
  f = urlopen(url + '?' + 
      urlencode({'wt': 'json', 'fl': 'id text score', 'q': query.replace(':', '\:'), 'rows': (rows + 3)}))
  data = json.loads(f.read().decode('utf8', 'ignore').encode('ascii', 'ignore'))
  # Check for errors
  if not 'response' in data:
    raise Exception('Bad response for query: %s' % query)
  # Filter exact match sentences
  results = [r for r in data['response']['docs'] if r['text'][0].encode('ascii', 'ignore').replace(' ', '') != query.replace(' ', '')][0:rows]
  # Create query result
  return QueryResult(
      query, 
      focus,
      data['response']['numFound'], 
      results,
      float(data['response']['maxScore']))


"""
  A decorator for a collection which displays a progress bar as you 
  iterate through it.

  @param iterable The collection to wrap.
  @param description The description to print while iterating.

  @return An Iterable (i.e., this class) which wraps the passed
          Iterable.
"""
class withProgress:
  def __init__(self, iterable, description="Progress"):
    self.iterable = iterable
    self.description = description
  
  def __getitem__(self, k):
    if k == len(self.iterable) or len(self.iterable) < 100 or k % (len(self.iterable) / 100) == 0:
      sys.stderr.write("\r%s... %d%%" % (self.description, 100.0 * float(k+1) / float(len(self.iterable))))
      sys.stderr.flush()
    if k == len(self.iterable):
      sys.stderr.write("\n")
    return self.iterable[k]

  def __len__(self):
    return len(self.iterable)


"""
  Compute the mode of a list of elements.

  @param lst The list of elements to compute the mode of.
"""
def mode(lst):
  counts = {}
  for elem in lst:
    if elem not in counts:
      counts[elem] = 0
    counts[elem] += 1
  argmax = None
  maxV = -1.0
  for key in counts:
    if counts[key] > maxV:
      maxV = counts[key]
      argmax = key
  return argmax
