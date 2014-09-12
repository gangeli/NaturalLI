#include <cstring>
#include <sstream>

#include "SynSearch.h"
#include "Utils.h"


using namespace std;

// ----------------------------------------------
// PATH ELEMENT (SEARCH NODE)
// ----------------------------------------------
inline syn_path_data mkSynPathData(
    const uint64_t&    factHash,
    const uint8_t&     index,
    const bool&        validity,
    const uint32_t&    deleteMask,
    const tagged_word& currentToken) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.validity = validity;
  dat.deleteMask = deleteMask;
  dat.currentToken = currentToken;
  return dat;
}

SynPath::SynPath()
    : costIfTrue(0.0), costIfFalse(0.0), backpointer(NULL),
      data(mkSynPathData(42l, 255, false, 42, getTaggedWord(0, 0, 0))) { }

SynPath::SynPath(const SynPath& from)
    : costIfTrue(from.costIfTrue), costIfFalse(from.costIfFalse),
      backpointer(from.backpointer),
      data(from.data) { }
  

// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

//
// Tree::Tree(params)
//
Tree::Tree(const uint8_t&     length, 
           const tagged_word* words,
           const uint8_t*     parents,
           const uint8_t*     relations)
       : length(length),
         availableCacheLength(34 + (MAX_QUERY_LENGTH - length) * sizeof(dep_tree_word)) {
  // Set trivial fields
  for (uint8_t i = 0; i < length; ++i) {
    data[i].word = words[i];
    data[i].parent = parents[i];
    data[i].relation = relations[i];
  }
  // Zero out cache
  memset(cacheSpace(), 42, availableCacheLength * sizeof(uint8_t));
}
  
//
// Tree::Tree(conll)
//
uint8_t computeLength(const string& conll) {
  uint8_t length = 0;
  stringstream ss(conll);
  string item;
  while (getline(ss, item, '\n')) {
    length += 1;
  }
  return length;
}

Tree::Tree(const string& conll) 
      : length(computeLength(conll)),
         availableCacheLength(34 + (MAX_QUERY_LENGTH - length) * sizeof(dep_tree_word)) {
  stringstream lineStream(conll);
  string line;
  uint8_t lineI = 0;
  while (getline(lineStream, line, '\n')) {
    stringstream fieldsStream(line);
    string field;
    uint8_t fieldI = 0;
    while (getline(fieldsStream, field, '\t')) {
      switch (fieldI) {
        case 0:
          data[lineI].word = getTaggedWord(atoi(field.c_str()), 0, 0);
          break;
        case 1:
          data[lineI].parent = atoi(field.c_str());
          if (data[lineI].parent == 0) {
            data[lineI].parent = TREE_ROOT;
          } else {
            data[lineI].parent -= 1;
          }
          break;
        case 2:
          data[lineI].relation = indexDependency(field.c_str());
          break;
      }
      fieldI += 1;
    }
    if (fieldI != 3) {
      printf("Bad number of CoNLL fields in line (expected 3): %s\n", line.c_str());
      std::exit(1);
    }
    lineI += 1;
  }
  // Zero out cache
  memset(cacheSpace(), 42, availableCacheLength * sizeof(uint8_t));
}
  
//
// Tree::children()
//
void Tree::children(const uint8_t& index,
      uint8_t* childrenIndices, 
      uint8_t* childrenRelations, 
      uint8_t* childrenLength) const {
  *childrenLength = 0;
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].parent == index) {
      childrenRelations[(*childrenLength)] = data[i].relation;
      childrenIndices[(*childrenLength)++] = i;  // must be last line in block
    }
  }
}
  
//
// Tree::createDeleteMask()
//
uint32_t Tree::createDeleteMask(const uint8_t& root) const {
  uint32_t bitmask = 0x1 << root;
  bool cleanPass = false;
  while (!cleanPass) {
    cleanPass = true;
    for (uint8_t i = 0; i < length; ++i) {
      if (!TREE_IS_DELETED(bitmask, i) &&
          TREE_IS_DELETED(bitmask, data[i].parent)) {
        TREE_DELETE(bitmask, i);
        cleanPass = false;
      }
    }
  }
  return bitmask;
}
  
//
// Tree::root()
//
uint8_t Tree::root() const {
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].parent == TREE_ROOT) {
      return i;
    }
  }
  printf("No root found in tree!\n");
  std::exit(1);
  return 255;
}
  
//
// Tree::operator==()
//
bool Tree::operator==(const Tree& rhs) const {
  if (length != rhs.length) { return false; }
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].word != rhs.data[i].word) { return false; }
    if (data[i].parent != rhs.data[i].parent) { return false; }
    if (data[i].relation != rhs.data[i].relation) { return false; }
  }
  return true;
}
