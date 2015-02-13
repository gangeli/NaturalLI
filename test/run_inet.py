#!/usr/bin/env python3
#

from socket import *
from threading import Lock
from concurrent.futures import ThreadPoolExecutor
import sys
import json

TCP_IP = '127.0.0.1'
if len(sys.argv) > 1:
  TCP_IP = sys.argv[1]
TCP_PORT = 1337
if len(sys.argv) > 2:
  TCP_PORT = int(sys.argv[2])
PARALLELISM=4
if len(sys.argv) > 3:
  PARALLELISM = int(sys.argv[3])

# A bit of help text
print( "Connecting to %s:%u on %u threads" % (TCP_IP, TCP_PORT, PARALLELISM) )
print( "Enter a number of optional premises, separated by newlines," )
print( "followed by a query and two newlines. Or, pipe one of the test" )
print( "case files in test/data/ ." )
print( "For example:" )
print( "" )
print( "All cats have tails." )
print( "Some animals have tails." )
print( "" )
print( "> 'Some animals have tails.' is true (0.999075)" )
print( "" )
print( "vv Your input here (^D to exit) vv" )

# Evaluation variables
RESULT_LOCK = Lock()
numGuessAndCorrect = 0
numGuessTrue       = 0
numGoldTrue        = 0
numCorrect         = 0
numStrictCorrect   = 0
numTotal           = 0

"""
  Run a query on the server, with an optional premise set.
  @param premises A list of premises, each of which is a String.
  @param query A query, as a String, and optionally prepended with either
               'TRUE: ' or 'FALSE: ' to communicate a value judgment to
               the server.
  @return True if the query succeeded, else False.
"""
def query(premises, query):
  # We want these variables
  global RESULT_LOCK
  global numGuessAndCorrect
  global numGuessTrue
  global numGoldTrue
  global numCorrect
  global numStrictCorrect
  global numTotal
  
  # Open a socket
  s = socket(AF_INET, SOCK_STREAM)
  s.connect((TCP_IP, TCP_PORT))

  # Write the premises + query
  for premise in premises:
    s.send((premise.strip() + "\n").encode("ascii"))
  s.send((query.strip() + '\n\n').encode("ascii"))
  gold = None
  booleanGold = True
  if query.startswith('TRUE: '):
    query = query[len("TRUE: "):]
    gold = 'true'
    booleanGold = True
  elif query.startswith('FALSE: '):
    query = query[len("FALSE: "):]
    gold = 'false'
    booleanGold = False
  elif query.startswith('UNK: '):
    query = query[len("UNK: "):]
    gold = 'unknown'
    booleanGold = False

  # Read the response
  rawResponse = s.recv(32768).decode("ascii");

  # Parse pass/fail judgments
  didPass=True
  if rawResponse.startswith("PASS: "):
    rawResponse = rawResponse[len("PASS: "):]
    didPass = True
  elif rawResponse.startswith("FAIL: "):
    rawResponse = rawResponse[len("FAIL: "):]
    didPass = False

  # Parse JSON
  response = json.loads(rawResponse)
  if response['success']:
    # Materialize JSON
    numResults  = response['numResults']  # Int
    totalTicks  = response['totalTicks']  # Int
    probability = response['truth']       # Double
    bestPremise = response['bestPremise'] # String
    path        = response['path']        # List
    # Output result
    guess = 'unknown'
    booleanGuess = False
    if probability > 0.5:
      guess = 'true'
      booleanGuess = True
    elif probability < 0.5:
      guess = 'false'
    if gold != None:
      pfx = "FAIL:"
      if gold == guess:
        pfx = "PASS:"
      print ("> %s '%s' is %s (%f)" % (pfx, query, guess, probability))
    else:
      print ("> '%s' is %s (%f)" % (query, guess, probability))
    # Compute results
    RESULT_LOCK.acquire()
    if gold != None:
      numTotal += 1;
      if booleanGuess == booleanGold:
        numCorrect += 1
      if guess == gold:
        numStrictCorrect += 1
      if booleanGuess and booleanGold:
        numGuessAndCorrect += 1
      if booleanGuess:
        numGuessTrue += 1
      if booleanGold:
        numGoldTrue += 1
    RESULT_LOCK.release()
  else:
    print ("Query failed: %s" % response['message'])
    return False
  
  # Socket should be closed already, but just in case...
  s.close()
  return True


lines = []
with ThreadPoolExecutor(max_workers=PARALLELISM) as threads:
  while True:
    line = sys.stdin.readline()
    if not line:
      break;
    if line.strip().startswith('#'):
      continue
    if (line == '\n'):
      # Parse the lines
      lines.reverse()
      if len(lines) == 0:
        continue
      elif len(lines) == 1:
        queryStr = lines[0]
        premises = []
      else:
        queryStr, *premises = lines
      lines = []
      # Start the query (async)
      threads.submit(query, premises, queryStr)
#      query(premises, queryStr)  # single threaded
    else:
      lines.append(line.strip());


# Compute scores
p      = 1.0 if numGuessTrue == 0 else (float(numGuessAndCorrect) / float(numGuessTrue))
r      = 0.0 if numGoldTrue == 0 else (float(numGuessAndCorrect) / float(numGoldTrue))
f1     = 2.0 * p * r / (p + r)
accr   = 0.0 if numTotal == 0 else (float(numCorrect) / float(numTotal))
strict = 0.0 if numTotal == 0 else (float(numStrictCorrect) / float(numTotal))

# Print scores
if numTotal > 0:
  print("--------------------")
  print("P:        %.3g" % p)
  print("R:        %.3g" % r)
  print("F1:       %.3g" % f1)
  print("Accuracy: %.3g" % accr)
  print("3-class:  %.3g" % strict)
  print("")
  print("(Total):  %d"   % numTotal)
  print("--------------------")
