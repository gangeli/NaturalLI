#include <cstring>
#include <sstream>
#include <thread>

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
  
SynPath::SynPath(const Tree& init)
    : costIfTrue(0.0f), costIfFalse(0.0f),
      backpointer(NULL),
      data(mkSynPathData(42l, 255, false, 42, getTaggedWord(0, 0, 0)))
//      data(mkSynPathData(  // TODO(gabor)
//      ));
  {
}
  

// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

//
// Tree::Tree(params)
//
Tree::Tree(const uint8_t&     length, 
           const tagged_word* words,
           const uint8_t*     governors,
           const uint8_t*     relations)
       : length(length),
         availableCacheLength(34 + (MAX_QUERY_LENGTH - length) * sizeof(dep_tree_word)) {
  // Set trivial fields
  for (uint8_t i = 0; i < length; ++i) {
    data[i].word = words[i];
    data[i].governor = governors[i];
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
          data[lineI].governor = atoi(field.c_str());
          if (data[lineI].governor == 0) {
            data[lineI].governor = TREE_ROOT;
          } else {
            data[lineI].governor -= 1;
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
// Tree::dependents()
//
void Tree::dependents(const uint8_t& index,
      const uint8_t& maxChildren,
      uint8_t* childrenIndices, 
      uint8_t* childrenRelations, 
      uint8_t* childrenLength) const {
  *childrenLength = 0;
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
      childrenRelations[(*childrenLength)] = data[i].relation;
      childrenIndices[(*childrenLength)++] = i;  // must be last line in block
      if (*childrenLength >= maxChildren) { return; }
    }
  }
}
  
//
// Tree::createDeleteMask()
//
uint32_t Tree::createDeleteMask(const uint8_t& root) const {
  uint32_t bitmask = TREE_DELETE(0x0, root);
  bool cleanPass = false;
  while (!cleanPass) {
    cleanPass = true;
    for (uint8_t i = 0; i < length; ++i) {
      if (!TREE_IS_DELETED(bitmask, i) &&
          data[i].governor != TREE_ROOT &&
          TREE_IS_DELETED(bitmask, data[i].governor)) {
        bitmask = TREE_DELETE(bitmask, i);
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
    if (data[i].governor == TREE_ROOT) {
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
    if (data[i].governor != rhs.data[i].governor) { return false; }
    if (data[i].relation != rhs.data[i].relation) { return false; }
  }
  return true;
}

//
// Tree::hash()
//
inline uint64_t mix( const uint64_t u ) {
  uint64_t v = u * 3935559000370003845l + 2691343689449507681l;
  v ^= v >> 21;
  v ^= v << 37;
  v ^= v >>  4;
  v *= 4768777513237032717l;
  v ^= v << 20;
  v ^= v >> 41;
  v ^= v <<  5;
  return v;
}

inline uint64_t hashEdge(const dependency_edge& edge) {
#if TWO_PASS_HASH!=0
  #ifdef __GNUG__
    return fnv_64a_buf(const_cast<dependency_edge*>(&edge), sizeof(edge), FNV1_64_INIT);
  #else
    return fnv_64a_buf(&edge, sizeof(edge), FNV1_64_INIT);
  #endif
#else
    uint64_t edgeHash;
    memcpy(&edgeHash, &edge, sizeof(uint64_t));
    return mix(edgeHash);
#endif
}

  
uint64_t Tree::hash() const {
  uint64_t value = 0x0;
  for (uint8_t i = 0; i < length; ++i) {
    value ^= hashEdge(edgeInto(i));
  }
  return value;
}
  
//
// Tree::updateHashFromMutation()
//
uint64_t Tree::updateHashFromMutation(
                                      const uint64_t& oldHash,
                                      const uint8_t& index, 
                                      const ::word& oldWord,
                                      const ::word& governor,
                                      const ::word& newWord) const {
  uint64_t newHash = oldHash;
  // Fix incoming dependency
//  printf("Mutating %u; dep=%u -> %u  gov=%u\n", index, oldWord, newWord, governor);
  newHash ^= hashEdge(edgeInto(index, oldWord, governor));
  newHash ^= hashEdge(edgeInto(index, newWord, governor));
  // Fix outgoing dependencies
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
//      printf("  Also re-hashing word at %u; dep=%u  gov=%u -> %u\n", i,
//              data[i].word.word, oldWord, newWord);
      newHash ^= hashEdge(edgeInto(i, data[i].word.word, oldWord));
      newHash ^= hashEdge(edgeInto(i, data[i].word.word, newWord));
    }
  }
  // Return
  return newHash;
}

//
// Tree::updateHashFromDeletion()
//
uint64_t Tree::updateHashFromDeletions(
                                       const uint64_t& oldHash,
                                       const uint8_t& deletionIndex, 
                                       const ::word& deletionWord,
                                       const ::word& governor,
                                       const uint32_t& newDeletions) const {
  uint64_t newHash = oldHash;
  for (uint8_t i = 0; i < length; ++i) {
    if (TREE_IS_DELETED(newDeletions, i)) {
      if (i == deletionIndex) {
//        printf("Deleting word at %u; dep=%u  gov=%u\n", i, deletionWord, governor);
        // Case: we are deleting the root of the deletion chunk
        newHash ^= hashEdge(edgeInto(i, deletionWord, governor));
      } else {
//        printf("Deleting word at %u; dep=%u  gov=%u\n", i, edgeInto(i).dependent, edgeInto(i).governor);
        // Case: we are deleting an entire edge
        newHash ^= hashEdge(edgeInto(i));
      }
    }
  }
  return newHash;
}
  
  
// ----------------------------------------------
// SEARCH ALGORITHM
// ----------------------------------------------

void priorityQueueWorker(
    SynPath* enqueueBuffer, uint16_t* enqueuePointer, uint16_t* enqueueUpperBound,
    SynPath* dequeueBuffer, uint16_t* dequeuePointer, uint16_t* dequeueUpperBound) {
}

void pushChildrenWorker(
    SynPath* enqueueBuffer, uint16_t* enqueuePointer, uint16_t* enqueueUpperBound,
    SynPath* dequeueBuffer, uint16_t* dequeuePointer, uint16_t* dequeueUpperBound) {
}



syn_search_response SynSearch(Graph* mutationGraph, Tree* input) {
  SynPath* enqueueBuffer = (SynPath*) malloc(CACHE_LINE_SIZE * 8 * sizeof(SynPath*));
  SynPath* dequeueBuffer = (SynPath*) malloc(CACHE_LINE_SIZE * sizeof(SynPath*));

  // Allocate some shared variables
  uint16_t* cacheSpace = (uint16_t*) input->cacheSpace();
  uint16_t* enqueuePointer = cacheSpace;
  *enqueuePointer = 0;
  uint16_t* dequeuePointer = cacheSpace + 1;
  *dequeuePointer = 0;
  uint16_t* processEnqueuePointer = cacheSpace + 2;
  *processEnqueuePointer = 0;
  uint16_t* processDequeuePointer = cacheSpace + 3;
  *processDequeuePointer = 0;
  // (sanity check)
  if (((uint64_t) processDequeuePointer) >= ((uint64_t) input) + sizeof(Tree)) {
    printf("Allocated too much into the cache memory area!\n");
    std::exit(1);
  }

  // Enqueue the first element
  new(dequeueBuffer) SynPath(*input);
  *dequeuePointer += 1;

  // Start threads
  std::thread priorityQueueThread(priorityQueueWorker, 
      enqueueBuffer, enqueuePointer, processEnqueuePointer,
      dequeueBuffer, dequeuePointer, processDequeuePointer);
  
  std::thread pushChildrenThread(pushChildrenWorker, 
      enqueueBuffer, processEnqueuePointer, enqueuePointer,
      dequeueBuffer, processDequeuePointer, dequeuePointer);
      

  // Join threads
  priorityQueueThread.join();
  pushChildrenThread.join();
  
  syn_search_response response;
  // TODO(gabor)
  return response;
}
