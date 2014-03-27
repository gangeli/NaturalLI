#include "Trie.h"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sstream>

#include "Utils.h"
#include "Postgres.h"

using namespace std;
using namespace btree;

//
// Trie::~Trie
//
Trie::~Trie() {
  for (btree_map<word,Trie*>::iterator iter = children.begin(); iter != children.end(); ++iter) {
    delete iter->second;
  }
}

//
// Trie::add
//
void Trie::add(edge* elements, uint8_t length) {
  // Corner cases
  if (length == 0) { return; }  // this case shouldn't actually happen normally...
  // Register child
  btree_map<word,Trie*>::iterator childIter = children.find( getWord(elements[0].sink) );
  Trie* child = NULL;
  if (childIter == children.end()) {
    child = new Trie();
    children[getWord(elements[0].sink)] = child;
  } else {
    child = childIter->second;
  }
  // Register information about child
  child->registerEdge(elements[0]);
  // Recursive call
  if (length == 1) {
    child->isLeaf = true;  // Mark this as a leaf node
  } else {
    child->add(&(elements[1]), length - 1);
  }
}

//
// Trie::contains
//
const bool Trie::contains(const tagged_word* query, const uint8_t queryLength,
                          vector<edge>* insertions) {
  insertions[0] = vector<edge>();
  
  if (queryLength == 0) {
    // Case: we're at the end of the query
    if (!isLeaf) {
      // ... add all possible completions
      btree_map<word,Trie*>::iterator iter;
      for (iter = children.begin(); iter != children.end(); ++iter) {
        Trie* child = iter->second;
        edge buffer[4];
        uint8_t numEdges = child->getEdges(buffer);
        for (int i = 0; i < numEdges; ++i) {
          buffer[i].sink = iter->first;
          insertions[i].push_back(buffer[i]);
        }
      }
    }
    // ... return whether the fact exists
    return isLeaf;
  } else {
    // Case: we're in the middle of the query
    btree_map<word,Trie*>::iterator childIter = children.find( getWord(query[0]) );
    if (childIter == children.end()) {
      // ... end of prefix match; add candidates
      for (btree_map<word,Trie*>::iterator iter = children.begin(); iter != children.end(); ++iter) {
        Trie* child = iter->second;
        if (child->children.size() > 0) {
          edge buffer[4];
          uint8_t numEdges = child->getEdges(buffer);
          for (int i = 0; i < numEdges; ++i) {
            buffer[i].sink = iter->first;
            insertions[i].push_back(buffer[i]);
          }
        }
      }
      return false;
    } else {
      return childIter->second->contains(&(query[1]), queryLength - 1, &(insertions[1]));
    }
  }
}


//
// ReadFactTrie
//
FactDB* ReadFactTrie(const uint64_t maxFactsToRead) {
  Trie* facts = new Trie();
  char query[127];

  // Read valid insertions
  printf("Reading valid insertions...\n");
  btree_map<word,vector<edge>> word2senses;
  // (query)
  snprintf(query, 127, "SELECT DISTINCT (sink) sink, sink_sense, type FROM %s WHERE source=0 AND sink<>0 ORDER BY type;", PG_TABLE_EDGE.c_str());
  PGIterator wordIter = PGIterator(query);
  uint32_t numValidInsertions = 0;
  while (wordIter.hasNext()) {
    // Get fact
    PGRow row = wordIter.next();
    // Create edge
    edge e;
    e.sink  = atoi(row[0]);
    e.sense = atoi(row[1]);
    e.type  = atoi(row[2]);
    e.cost  = 1.0f;
    // Register edge
    word2senses[e.sink].push_back(e);
    numValidInsertions += 1;
  }
  printf("  Done. Can insert %u words\n", numValidInsertions);


  // Read facts
  printf("Reading facts...\n");
  // (query)
  if (maxFactsToRead == std::numeric_limits<uint64_t>::max()) {
    snprintf(query, 127,
             "SELECT gloss, weight FROM %s ORDER BY weight DESC;",
             PG_TABLE_FACT.c_str());
  } else {
    snprintf(query, 127,
             "SELECT gloss, weight FROM %s ORDER BY weight DESC LIMIT %lu;",
             PG_TABLE_FACT.c_str(),
             maxFactsToRead);
  }
  PGIterator iter = PGIterator(query);
  uint64_t i = 0;
  edge buffer[256];
  uint8_t bufferLength;
  while (iter.hasNext()) {
    // Get fact
    PGRow row = iter.next();
    const char* gloss = row[0];
    uint32_t weight = atoi(row[1]);
    if (weight < MIN_FACT_COUNT) { break; }
    // Parse fact
    uint32_t glossLength = strnlen(gloss, 1024);
    stringstream stream (&gloss[1]);
    bufferLength = 0;
    while( stream.good() ) {
      // Parse the word
      string substr;
      getline( stream, substr, ',' );
      word w = atoi(substr.c_str());
      // Register the word
      btree_map<word,vector<edge>>::iterator iter = word2senses.find( buffer[bufferLength].sink );
      if (iter == word2senses.end() || iter->second.size() == 0) {
        buffer[bufferLength].sink  = w;
        buffer[bufferLength].sense = 0;
        buffer[bufferLength].type  = 0;
        buffer[bufferLength].cost  = 1.0f;
      } else {
        buffer[bufferLength] = iter->second[0];
      }
      if (bufferLength >= MAX_FACT_LENGTH) { break; }
    }
    // Add fact
    if (weight >= MIN_FACT_COUNT) {
      // Add 'canonical' version
      facts->add(buffer, bufferLength);
      // Add word sense variants
      for (int i = 0; i < bufferLength; ++i) {
        btree_map<word,vector<edge>>::iterator iter = word2senses.find( buffer[i].sink );
        if (iter != word2senses.end() && iter->second.size() > 1) {
          for (int sense = 1; sense < iter->second.size(); ++sense) {
            buffer[i] = iter->second[sense];
            facts->add(buffer, bufferLength);
          }
        }
      }
    }
    // Debug
    i += 1;
    if (i % 10000000 == 0) {
      printf("  loaded %luM facts\n", i / 1000000);
    }
  }

  // Return
  printf("  done reading the fact database (%lu facts read)\n", i);
  return facts;
}
