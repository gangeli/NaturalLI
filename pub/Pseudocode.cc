// TODO(gabor) data structures:
//   -- query
//   -- response
//   -- "path" of edits


// Populate Tables
createTable("words");
for ( (word, key) : readWordsFromPostgres()) {
  write("words", key, word);
}

createTable("edges");
for ( line : readEdgeFile().getLines().map( _.split("\t") ) ) {
  write("edges", line.head, line.tail);
}

createTable("facts");
for ( (id, left_arg, relation, right_arg, count) : readFactsFromPostgres()) {
  word[] fact = arraycat(left_arg, relation, right_arg);
  write("facts", fact, id);
}

// Effectively a recursive reverse function
path constructPath(SearchElem* endNode) {
  if (endNode == NULL) {
    return new Vector<word[]>()
  }
  Vector<word[]> pathSoFar = constructPath(endNode.backpointer);
  pathSoFar.push_back(endNode.fact);
  return pathSoFar;
}

struct {
  word[] fact;
  SearchElem* backpointer;
  char edgeType;
  // TODO(near future) -- "valid natlog inference" marker
} SearchElem;

char ADD_WORD = 127;
char DEL_WORD = 128;

typedef path = Vector<word[]>;
typedef word = int;
typedef edge_type = char;

// Run BFS
path handleQuery(word[] query, int timeout) {
  // TODO(near future) eventually, priority queue
  //   - cost will be computed dynamically
  // TODO(near future) cache of seen things
  Queue<SearchElem> fringe = new Queue<SearchElem>();
  Vector<path> responses = new Vector<path>();
  fringe.push(new SearchElem(query, NULL));
  int numElementsPopped = 0;
  while (!fringe.isEmpty() && numElementsPopped < timeout) {
    // get the next intermediate fact to look at
    SearchElem* candidate = fringe.pop();
    numElementsPopped += 1;
    // if the fact exists, add it as a response
    if (read("facts", candidate->fact) != NULL) {
      responses.push_back( constructPath(candidate) );
    }
    // either way, add all the node's children to the fringe
    // case: do mutations (e.g., cat -> feline)
    for (int wordI = 0; wordI < candidate->fact.size(); ++wordI) {  // for each word that can mutate
      // Find all mutations of the words at candidate->fact[wordI]
      Vector<Triple<word, edge_type, float>> edges = deserialize(read("edges", candidate->fact[wordI]));
      // Add mutated facts
      for ( (word, edgeType) : edges ) {
        word[] newFact = copyMemory(candidate->fact);
        newFact[wordI] = candidate->fact[wordI];
        fringe.push(new SearchElem(newFact, candidate, edgeType));  // happens # edges times
      }
    }
    // case: add insertions (not implemented yet because they're hard)
    for (word candidateWord : candidate->fact) {
      for (int indexToAdd = 0; indexToAdd < candidate->fact.size(); ++indexToAdd) {
        for (word : valid_words_to_insert) {
          // create a new fact
          // ...
          fringe.push(newFact, candidate, ADD_WORD);
        }
      }
    }
    // case: add deletions; e.g., "the blue cat" -> "the cat"
    for (int indexToRemove = 0; indexToRemove < candidate->fact.size(); ++indexToRemove) {
      word[] newFact = new word[candidate->fact.size() - 1];
      for (int i = 0; i < indexToRemove; ++i) {
        newFact[i] = candidate->fact[i];  // here, we just copy candidate->fact
      }
      for (int i = indexToRemove + 1; i < candidate->fact.size(); ++i) {
        newFact[i - 1] = candidate->fact[i]; // here, we shift candidate->fact left by one
      }
      // add the fact to the fringe
      fringe.push(new SearchElem(newFact, candidate, DEL_WORD));  // this will happen fact.size() times
    }
  }
  return responses;
}



int main() {
  while (something_more_principled_than_while_true) {
    int[] queryFact = awaitQuery(client);
    client.send( handleQuery(queryFact) );
  }
}
