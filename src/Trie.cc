#include "Trie.h"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sstream>

#include "Utils.h"
#include "Postgres.h"

using namespace std;

void Trie::add(word* elements, uint8_t length) {
  if (length == 0) { 
    setIsLeaf(true);
  } else {
    children[elements[0]].add( &elements[1], length - 1);
  }
}
  
void Trie::add(word* elements, edge_type* edges, uint8_t length) {
  if (length == 0) { 
    setIsLeaf(true);
  } else {
    children[elements[0]].add( &elements[1], &edges[1], length - 1);
    children[elements[0]].setEdgeType(edges[0]);
  }
}

const bool Trie::contains(const tagged_word* query, const uint8_t queryLength, 
                          tagged_word* canInsert, edge_type* canInsertEdge,
                          uint8_t* canInsertLength) {
  if (queryLength == 0) {
    if (*canInsertLength > 0) {
      uint8_t i = 0;
      for (map<word,Trie>::iterator iter = children.begin(); 
           iter != children.end();
           ++iter) {
        canInsert[i] = iter->first;
        canInsertEdge[i] = iter->second.getEdgeType();
        i += 1;
        if (i > *canInsertLength) { break; }
      }
      *canInsertLength = i;
    }
    return isLeaf();
  } else {
    map<word,Trie>::iterator iter = children.find( getWord(query[0]) );
    if (iter == children.end()) {
      *canInsertLength = 0;
      return false;
    } else {
      Trie& child = iter->second;
      return child.contains(&query[1], queryLength - 1, canInsert, canInsertEdge, canInsertLength);
    }
  }
}

const uint8_t TrieFactDB::filterAndSort(const tagged_word* elements, uint8_t length, word* buffer,
                                        edge_type* edges) {
  uint8_t filteredLength = 0;
  for (int i = 0; i < length; ++i) {
    const word w = getWord(elements[i]);
    unordered_map<word,edge_type>::iterator it = this->validInsertions.find(w);
    if (it != this->validInsertions.end()) {
      buffer[filteredLength] = w;
      filteredLength += 1;
    }
  }
  // Sort
  sort(buffer, buffer + filteredLength);
  // Fill edges
  for (int i = 0; i < filteredLength; ++i) {
    unordered_map<word,edge_type>::iterator it = this->validInsertions.find(buffer[i]);
    if (it != this->validInsertions.end()) {
      edges[i] = it->second;
    }
  }
  return filteredLength;
}


void TrieFactDB::add(word* elements, uint8_t length, uint32_t weight) {
  // Register fact
  facts.add(elements, length);
  // Register completion
  if (weight >= MIN_COMPLETION_W) {
    word buffer[256];
    edge_type edges[256];
    const uint8_t filteredLength = filterAndSort(elements, length, buffer, edges);
    completions.add(buffer, edges, filteredLength);
  }
}
  
void TrieFactDB::addValidInsertion(const word& word, const edge_type& edge) {
  this->validInsertions[word] = edge;
}

const bool TrieFactDB::contains(const tagged_word* query, const uint8_t queryLength, 
                                tagged_word* canInsert, edge_type* canInsertEdge, 
                                uint8_t* canInsertLength) {
  // Check children
  if (*canInsertLength > 0) {
    // Create sorted query
    word buffer[256];
    edge_type edges[256];
    const uint8_t filteredLength = filterAndSort(query, queryLength, buffer, edges);
    // Check completions
    completions.contains(buffer, filteredLength, canInsert, canInsertEdge, canInsertLength);
  }
  // Check ordered containment
  return facts.contains(query, queryLength);
}


FactDB* ReadFactTrie(const uint64_t maxFactsToRead) {
  printf("facts to read: %lu\n", maxFactsToRead);
  TrieFactDB* facts = new TrieFactDB();
  char query[127];

  // Read valid insertions
  printf("Reading valid insertions...\n");
  // (query)
  snprintf(query, 127, "SELECT DISTINCT (sink) sink, type FROM %s WHERE source=0 AND sink<>0 ORDER BY type;", PG_TABLE_EDGE.c_str());
  PGIterator wordIter = PGIterator(query);
  uint32_t numValidInsertions = 0;
  while (wordIter.hasNext()) {
    // Get fact
    PGRow row = wordIter.next();
    facts->addValidInsertion(atoi(row[0]), atoi(row[1]));
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
  word buffer[256];
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
      string substr;
      getline( stream, substr, ',' );
      buffer[bufferLength] = atoi(substr.c_str());
      bufferLength += 1;
      if (bufferLength == 255) { break; }
    }
    // Add fact
    facts->add(buffer, bufferLength, weight);
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
