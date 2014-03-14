#include "Trie.h"

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

const bool Trie::contains(const tagged_word* query, const uint8_t queryLength, 
                          tagged_word* canInsert, uint8_t* canInsertLength) {
  if (queryLength == 0) {
    if (*canInsertLength > 0) {
      uint8_t i = 0;
      for (map<word,Trie>::iterator iter = children.begin(); 
           iter != children.end();
           ++iter) {
        canInsert[i] = iter->first;
        i += 1;
        if (i > *canInsertLength) { break; }
      }
      *canInsertLength = i;
    }
    return isLeaf();
  } else {
    map<word,Trie>::iterator iter = children.find( getWord(query[0]) );
    if (iter == children.end()) {
      return false;
    } else {
      Trie& child = iter->second;
      return child.contains(&query[1], queryLength - 1, canInsert, canInsertLength);
    }
  }
}



Trie* ReadFactTrie() {
  printf("Reading facts...\n");
  // Read facts
  Trie* facts = new Trie();
  // (query)
  char factQuery[127];
  snprintf(factQuery, 127, "SELECT gloss, weight FROM %s ORDER BY weight DESC;", PG_TABLE_FACT.c_str());
  PGIterator iter = PGIterator(factQuery);
  uint64_t i = 0;
  word buffer[256];
  uint8_t bufferLength;
  while (iter.hasNext()) {
    // Get fact
    PGRow row = iter.next();
    char* gloss = row[0];
    uint32_t weight = atoi(row[1]);
    if (weight < MIN_FACT_COUNT) { break; }
    // Parse fact
    uint32_t glossLength = strnlen(gloss, 1024);
    gloss[glossLength - 1] = '\0';
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
    facts->add(buffer, bufferLength);
    // Debug
    i += 1;
    if (i % 10000000 == 0) {
      printf("loaded %luM facts\n", i / 1000000);
    }
  }

  // Return
  printf("%s\n", "Done reading the fact database.");
  return facts;
}
