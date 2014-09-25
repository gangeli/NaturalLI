#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Utils.h"

using namespace std;

// ----------------------------------------------
// PATH ELEMENT (SEARCH NODE)
// ----------------------------------------------
inline syn_path_data mkSearchNodeData(
    const uint64_t&    factHash,
    const uint8_t&     index,
    const bool&        truth,
    const uint32_t&    deleteMask,
    const tagged_word& currentToken,
    const ::word       governor) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.truth = truth;
  dat.deleteMask = deleteMask;
  dat.currentToken = currentToken;
  dat.governor = governor;
  return dat;
}

SearchNode::SearchNode()
    : backpointer(0),
      data(mkSearchNodeData(42l, 255, false, 42, getTaggedWord(0, 0, 0), TREE_ROOT_WORD)) {  }

SearchNode::SearchNode(const SearchNode& from)
    : backpointer(from.backpointer),
      data(from.data) {
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}
  
SearchNode::SearchNode(const Tree& init)
    : backpointer(0),
      data(mkSearchNodeData(init.hash(), init.root(), true, 
                         0x0, init.token(init.root()), TREE_ROOT_WORD)) {
  memcpy(this->quantifierMonotonicities, init.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}
  
//
// SearchNode() ''mutate constructor
//
SearchNode::SearchNode(const SearchNode& from, const uint64_t& newHash,
                 const tagged_word& newToken,
                 const bool& newTruthValue,
                 const uint32_t& backpointer)
    : backpointer(backpointer),
      data(mkSearchNodeData(newHash, from.data.index, newTruthValue,
                         from.data.deleteMask, newToken,
                         from.data.governor)) {
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}

//
// SearchNode() ''delete constructor
//
SearchNode::SearchNode(const SearchNode& from, const uint64_t& newHash,
          const bool& newTruthValue,
          const uint32_t& addedDeletions, const uint32_t& backpointer)
    : backpointer(backpointer),
      data(mkSearchNodeData(newHash, from.data.index, newTruthValue,
                         addedDeletions | from.data.deleteMask, from.data.currentToken,
                         from.data.governor)) { 
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}

//
// SearchNode() ''move index constructor
//
SearchNode::SearchNode(const SearchNode& from, const Tree& tree,
                 const uint8_t& newIndex, const uint32_t& backpointer)
    : backpointer(backpointer),
      data(mkSearchNodeData(from.data.factHash, newIndex, from.data.truth, 
                         from.data.deleteMask, tree.token(newIndex), 
                         tree.token(tree.governor(newIndex)).word)) { 
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}

// ----------------------------------------------
// DEPENDENCY TREE
// ----------------------------------------------

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

            
void stringToMonotonicity(string field, monotonicity* mono, quantifier_type* type) {
  if (field == "monotone") {
    *mono = MONOTONE_UP; *type = QUANTIFIER_TYPE_NONE;
  } else if (field == "additive") {
    *mono = MONOTONE_UP; *type = QUANTIFIER_TYPE_ADDITIVE;
  } else if (field == "multiplicative") {
    *mono = MONOTONE_UP; *type = QUANTIFIER_TYPE_MULTIPLICATIVE;
  } else if (field == "additive-multiplicative") {
    *mono = MONOTONE_UP; *type = QUANTIFIER_TYPE_BOTH;
  } else if (field == "antitone") {
    *mono = MONOTONE_DOWN; *type = QUANTIFIER_TYPE_NONE;
  } else if (field == "anti-additive") {
    *mono = MONOTONE_DOWN; *type = QUANTIFIER_TYPE_ADDITIVE;
  } else if (field == "anti-multiplicative") {
    *mono = MONOTONE_DOWN; *type = QUANTIFIER_TYPE_MULTIPLICATIVE;
  } else if (field == "anti-additive-multiplicative") {
    *mono = MONOTONE_DOWN; *type = QUANTIFIER_TYPE_BOTH;
  } else if (field == "nonmonotone") {
    *mono = MONOTONE_FLAT; *type = QUANTIFIER_TYPE_NONE;
  } else {
    printf("ERROR: Bad monotonicity marker: %s; assuming flat: ", field.c_str());
  }
}

void stringToSpan(string field, uint8_t* begin, uint8_t* end) {
  stringstream spanStream(field);
  string spanElem;
  bool isFirst = true;
  while (getline(spanStream, spanElem, '-')) {
    if (isFirst) {
      *begin = atoi(spanElem.c_str()) - 1;
      isFirst = false;
    } else {
      *end = atoi(spanElem.c_str()) - 1;
    }
  }
}

Tree::Tree(const string& conll) 
      : length(computeLength(conll)),
        numQuantifiers(0) {
  // Variables
  stringstream lineStream(conll);
  string line;
  uint8_t lineI = 0;
  monotonicity subjMono;
  monotonicity objMono;
  quantifier_type subjType;
  quantifier_type objType;
  quantifier_span span;
  // Parse CoNLL
  while (getline(lineStream, line, '\n')) {
    bool isQuantifier = false;
    stringstream fieldsStream(line);
    string field;
    uint8_t fieldI = 0;
    while (getline(fieldsStream, field, '\t')) {
      switch (fieldI) {
        case 0:  // Word (as integer)
          data[lineI].word = atoi(field.c_str());
          data[lineI].sense = 0;
          break;
        case 1:  // Governor
          data[lineI].governor = atoi(field.c_str());
          if (data[lineI].governor == 0) {
            data[lineI].governor = TREE_ROOT;
          } else {
            data[lineI].governor -= 1;
          }
          break;
        case 2:  // Label
          data[lineI].relation = indexDependency(field.c_str());
          break;
        case 3:  // Subject monotonicity
          if (field != "-") {
            isQuantifier = true;
            stringToMonotonicity(field, &subjMono, &subjType);
          }
          break;
        case 4:  // Subject span
          if (field != "-") {
            uint8_t begin, end;
            stringToSpan(field, &begin, &end);
            span.subj_begin = begin; span.subj_end = end;
          }
          break;
        case 5:  // Object monotonicity
          if (field != "-") {
            stringToMonotonicity(field, &objMono, &objType);
          } else {
            objMono = MONOTONE_FLAT;
            objType = QUANTIFIER_TYPE_NONE;
          }
          break;
        case 6:  // Object span
          if (field != "-") {
            uint8_t begin, end;
            stringToSpan(field, &begin, &end);
            span.obj_begin = begin; span.obj_end = end;
          } else {
            span.obj_begin = 0;
            span.obj_end = 0;
          }
          // Register quantifier
          if (isQuantifier) {
            if (!registerQuantifier(span, subjType, objType, subjMono, objMono)) {
              printf("WARNING: Too many quantifiers; skipping at word %u\n", lineI);
            }
          }
          break;
      }
      fieldI += 1;
    }
    if (fieldI != 3 && fieldI != 7) {
      printf("ERROR: Bad number of CoNLL fields in line (expected 3 or 7, was %u): %s\n", fieldI, line.c_str());
    }
    lineI += 1;
  }
}
  
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
void Tree::foreachQuantifier(
      const uint8_t& index,
      std::function<void(quantifier_type,monotonicity)> visitor) const {
  // Variables
  uint8_t distance[MAX_QUANTIFIER_COUNT];
  memset(distance, 0, MAX_QUANTIFIER_COUNT * sizeof(uint8_t));
  bool valid[MAX_QUANTIFIER_COUNT];
  memset(valid, 0, MAX_QUANTIFIER_COUNT * sizeof(uint8_t));
  bool onSubject[MAX_QUANTIFIER_COUNT];
  // Collect statistics
  for (uint8_t i = 0; i < MAX_QUANTIFIER_COUNT; ++i) {
    if (i >= numQuantifiers) { break; }
    if (index >= quantifierSpans[i].subj_begin && index < quantifierSpans[i].subj_end) {
      onSubject[i] = true;
      valid[i] = true;
      const uint8_t d1 = index - quantifierSpans[i].subj_begin;
      const uint8_t d2 = quantifierSpans[i].subj_end - index;
      distance[i] = (d1 < d2) ? d1 : d2;
    } else if (index >= quantifierSpans[i].obj_begin && index < quantifierSpans[i].obj_end) {
      valid[i] = true;
      const uint8_t d1 = index - quantifierSpans[i].obj_begin;
      const uint8_t d2 = quantifierSpans[i].obj_end - index;
      distance[i] = (d1 < d2) ? d1 : d2;
    }
  }
  // Run foreach
  bool somethingValid = true;
  while (somethingValid) {
    somethingValid = false;
    uint8_t argmin = 255;
    uint8_t min = 255;
    // (get smallest distance from token)
    for (uint8_t i = 0; i < MAX_QUANTIFIER_COUNT; ++i) {
      if (valid[i]) {
        somethingValid = true;
        if (distance[i] < min) {
          min = distance[i];
          argmin = i;
        }
      }
    }
    // (call visitor)
    if (somethingValid) {
      if (onSubject[argmin]) {
        visitor(quantifierMonotonicities[argmin].subj_type, quantifierMonotonicities[argmin].subj_mono);
      } else {
        visitor(quantifierMonotonicities[argmin].obj_type, quantifierMonotonicities[argmin].obj_mono);
      }
    }
    // (update state)
    valid[argmin] = false;
  }
}
#pragma GCC pop_options  // matches push_options above
  
//
// Tree::dependents()
//
void Tree::dependents(const uint8_t& index,
      const uint8_t& maxChildren,
      uint8_t* childrenIndices, 
      dep_label* childrenRelations, 
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
    return fnv_64a_buf(const_cast<dependency_edge*>(&edge), sizeof(dependency_edge), FNV1_64_INIT);
  #else
    return fnv_64a_buf(&edge, sizeof(dependency_edge), FNV1_64_INIT);
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
      newHash ^= hashEdge(edgeInto(i, data[i].word, oldWord));
      newHash ^= hashEdge(edgeInto(i, data[i].word, newWord));
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
  
//
// Tree::projectLexicalRelation()
//
natlog_relation Tree::projectLexicalRelation( const uint8_t& index,
                                              const natlog_relation& lexicalRelation) const {
  const dep_tree_word& token = data[index];
  natlog_relation outputRelation = lexicalRelation;
  foreachQuantifier(index, [&outputRelation] (quantifier_type type, monotonicity mono) mutable -> void {
    outputRelation = project(mono, type, outputRelation);
  });
  return outputRelation;
} 


// ----------------------------------------------
// CHANNEL
// ----------------------------------------------
  
bool Channel::push(const ScoredSearchNode& value) {
  if (data.pushPointer == data.pollPointer) {
    // Case: empty buffer
    data.buffer[data.pushPointer] = value;
    data.pushPointer += 1;
    return true;
  } else {
    // Case: something in buffer
    const uint16_t available =
      ((data.pollPointer + CHANNEL_BUFFER_LENGTH) - data.pushPointer) % CHANNEL_BUFFER_LENGTH - 1;
    if (available > 0) {
      data.buffer[data.pushPointer] = value;
      data.pushPointer = (data.pushPointer + 1) % CHANNEL_BUFFER_LENGTH;
      return true;
    } else {
      return false;
    }
  }
}

bool Channel::poll(ScoredSearchNode* output) {
  const uint16_t available =
    ((data.pushPointer + CHANNEL_BUFFER_LENGTH) - data.pollPointer) % CHANNEL_BUFFER_LENGTH;
  if (available > 0) {
  }
  if (available > 0) {
    *output = data.buffer[data.pollPointer];
    data.pollPointer = (data.pollPointer + 1) % CHANNEL_BUFFER_LENGTH;
    return true;
  } else { 
    return false;
  }
}


// ----------------------------------------------
// NATURAL LOGIC
// ----------------------------------------------

//
// reverseTransition()
//
bool reverseTransition(const bool& endState,
                       const natlog_relation projectedRelation) {
  if (endState) {
    switch (projectedRelation) {
      case FUNCTION_FORWARD_ENTAILMENT:
      case FUNCTION_REVERSE_ENTAILMENT:
      case FUNCTION_ALTERNATION:
      case FUNCTION_EQUIVALENT:
      case FUNCTION_INDEPENDENCE:
        return true;
      case FUNCTION_NEGATION:
      case FUNCTION_COVER:
        return false;
      default:
        printf("Unknown function: %u", projectedRelation);
        std::exit(1);
        break;
    }
  } else {
    switch (projectedRelation) {
      case FUNCTION_FORWARD_ENTAILMENT:
      case FUNCTION_REVERSE_ENTAILMENT:
      case FUNCTION_COVER:
      case FUNCTION_EQUIVALENT:
      case FUNCTION_INDEPENDENCE:
        return false;
      case FUNCTION_NEGATION:
      case FUNCTION_ALTERNATION:
        return true;
      default:
        printf("Unknown function: %u", projectedRelation);
        std::exit(1);
        break;
    }
  }
  printf("Reached end of reverseTransition() function -- this shouldn't happen!\n");
  std::exit(1);
  return false;
}

//
// project()
//
uint8_t project(const monotonicity& monotonicity,
                const quantifier_type& quantifierType,
                const natlog_relation& lexicalFunction) {
  switch (monotonicity) {
    case MONOTONE_UP:
      switch (quantifierType) {
        case QUANTIFIER_TYPE_NONE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_ALTERNATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_COVER: return FUNCTION_INDEPENDENCE;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_ADDITIVE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_COVER;
            case FUNCTION_ALTERNATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_COVER: return FUNCTION_COVER;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_MULTIPLICATIVE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_ALTERNATION;
            case FUNCTION_ALTERNATION: return FUNCTION_ALTERNATION;
            case FUNCTION_COVER: return FUNCTION_INDEPENDENCE;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_BOTH:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_NEGATION;
            case FUNCTION_ALTERNATION: return FUNCTION_ALTERNATION;
            case FUNCTION_COVER: return FUNCTION_COVER;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        default: printf("Unknown projectivity type for quantifier: %u", quantifierType); std::exit(1); break;
      }
    case MONOTONE_DOWN:
      switch (quantifierType) {
        case QUANTIFIER_TYPE_NONE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_ALTERNATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_COVER: return FUNCTION_INDEPENDENCE;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_ADDITIVE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_ALTERNATION;
            case FUNCTION_ALTERNATION: return FUNCTION_INDEPENDENCE;
            case FUNCTION_COVER: return FUNCTION_ALTERNATION;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_MULTIPLICATIVE:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_COVER;
            case FUNCTION_ALTERNATION: return FUNCTION_COVER;
            case FUNCTION_COVER: return FUNCTION_INDEPENDENCE;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        case QUANTIFIER_TYPE_BOTH:
          switch (lexicalFunction) {
            case FUNCTION_EQUIVALENT: return FUNCTION_EQUIVALENT;
            case FUNCTION_FORWARD_ENTAILMENT: return FUNCTION_REVERSE_ENTAILMENT;
            case FUNCTION_REVERSE_ENTAILMENT: return FUNCTION_FORWARD_ENTAILMENT;
            case FUNCTION_NEGATION: return FUNCTION_NEGATION;
            case FUNCTION_ALTERNATION: return FUNCTION_COVER;
            case FUNCTION_COVER: return FUNCTION_ALTERNATION;
            case FUNCTION_INDEPENDENCE: return FUNCTION_INDEPENDENCE;
            default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        default: printf("Unknown projectivity type for quantifier: %u", quantifierType); std::exit(1); break;
      }
      break;
    case MONOTONE_FLAT:
      switch (lexicalFunction) {
        case FUNCTION_EQUIVALENT:
          return FUNCTION_EQUIVALENT;
        case FUNCTION_FORWARD_ENTAILMENT:
        case FUNCTION_REVERSE_ENTAILMENT:
        case FUNCTION_NEGATION:
        case FUNCTION_ALTERNATION:
        case FUNCTION_COVER:
        case FUNCTION_INDEPENDENCE:
          return FUNCTION_INDEPENDENCE;
        default: printf("Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
      }
      break;
    default:
      printf("Invalid monotonicity: %u", monotonicity);
      std::exit(1);
      break;
  }
  printf("Reached end of project() function -- this shouldn't happen!\n");
  std::exit(1);
  return 255;
}

//
// SynSearch::mutationCost()
//
float SynSearchCosts::mutationCost(const Tree& tree,
                                   const uint8_t& index,
                                   const uint8_t& edgeType,
                                   const bool& endTruthValue,
                                   bool* beginTruthValue) const {
  const natlog_relation lexicalRelation = edgeToLexicalFunction(edgeType);
  const float lexicalRelationCost
    = mutationLexicalCost[edgeType];
  const natlog_relation projectedFunction
    = tree.projectLexicalRelation(index, lexicalRelation);
  *beginTruthValue = reverseTransition(endTruthValue, projectedFunction);
  const float transitionCost
    = ((*beginTruthValue) ? transitionCostFromTrue : transitionCostFromFalse)[projectedFunction];
  return lexicalRelationCost + transitionCost;
}

//
// SynSearch::insertionCost()
//
float SynSearchCosts::insertionCost(const Tree& tree,
                                    const uint8_t& governorIndex,
                                    const dep_label& dependencyLabel,
                                    const ::word& dependent,
                                    const bool& endTruthValue,
                                    bool* beginTruthValue) const {
  const natlog_relation lexicalRelation
    = dependencyInsertToLexicalFunction(dependencyLabel, dependent);
  const float lexicalRelationCost
    = insertionLexicalCost[dependencyLabel];
  const natlog_relation projectedFunction
    = tree.projectLexicalRelation(governorIndex, lexicalRelation);
  *beginTruthValue = reverseTransition(endTruthValue, projectedFunction);
  const float transitionCost
    = ((*beginTruthValue) ? transitionCostFromTrue : transitionCostFromFalse)[projectedFunction];
  return lexicalRelationCost + transitionCost;
}


//
// createStrictCosts()
//
SynSearchCosts* createStrictCosts(const float& smallConstantCost,
                                  const float& okCost,
                                  const float& badCost) {
  SynSearchCosts* costs = new SynSearchCosts();
  // Set Constant Costs
  for (uint8_t i = 0; i < NUM_MUTATION_TYPES; ++i) {
    costs->mutationLexicalCost[i] = smallConstantCost;
  }
  for (uint8_t i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    costs->insertionLexicalCost[i] = smallConstantCost;
    costs->deletionLexicalCost[i] = smallConstantCost;
  }
  // Set NatLog
  costs->transitionCostFromTrue[FUNCTION_EQUIVALENT] = okCost;
  costs->transitionCostFromTrue[FUNCTION_FORWARD_ENTAILMENT] = okCost;
  costs->transitionCostFromTrue[FUNCTION_REVERSE_ENTAILMENT] = badCost;
  costs->transitionCostFromTrue[FUNCTION_NEGATION] = okCost;
  costs->transitionCostFromTrue[FUNCTION_ALTERNATION] = okCost;
  costs->transitionCostFromTrue[FUNCTION_COVER] = badCost;
  costs->transitionCostFromTrue[FUNCTION_INDEPENDENCE] = badCost;
  costs->transitionCostFromFalse[FUNCTION_EQUIVALENT] = okCost;
  costs->transitionCostFromFalse[FUNCTION_FORWARD_ENTAILMENT] = badCost;
  costs->transitionCostFromFalse[FUNCTION_REVERSE_ENTAILMENT] = okCost;
  costs->transitionCostFromFalse[FUNCTION_NEGATION] = okCost;
  costs->transitionCostFromFalse[FUNCTION_ALTERNATION] = badCost;
  costs->transitionCostFromFalse[FUNCTION_COVER] = okCost;
  costs->transitionCostFromFalse[FUNCTION_INDEPENDENCE] = badCost;
  return costs;
}



// ----------------------------------------------
// SEARCH ALGORITHM
// ----------------------------------------------

//
// Print the current time
//
void printTime(const char* format) {
  char s[128];
  time_t t = time(NULL);
  struct tm* p = localtime(&t);
  strftime(s, 1000, format, p);
  printf("%s", s);
}

//
// Create the input options for a search
//
syn_search_options SynSearchOptions(
    const uint32_t& maxTicks,
    const float& costThreshold,
    const bool& stopWhenResultFound,
    const bool& silent) {
  syn_search_options opts;
  opts.maxTicks = maxTicks;
  opts.costThreshold = costThreshold;
  opts.stopWhenResultFound = stopWhenResultFound;
  opts.silent = silent;
  return opts;
}

//
// A helper to evict the cache. This is rather expensive.
//
inline void EVICT_CACHE() {
  void* __ptr = malloc(L3_CACHE_SIZE); 
  memset(__ptr, 0x0, L3_CACHE_SIZE); 
  free(__ptr);
}

//
// Handle push/pop to the priority queue
//
void priorityQueueWorker(
    Channel* enqueueChannel, Channel* dequeueChannel, 
    bool* timeout, bool* pqEmpty, const syn_search_options& opts) {
  // Variables
  uint64_t idleTicks = 0;
  KNHeap<float,SearchNode> pq(
    std::numeric_limits<float>::infinity(),
    -std::numeric_limits<float>::infinity());
  float key;
  ScoredSearchNode value;

  // Main loop
  while (!(*timeout)) {
    bool somethingHappened = false;

    // Enqueue
    while (enqueueChannel->poll(&value)) {
      pq.insert(value.cost, value.node);
      somethingHappened = true;
    }

    // Dequeue
    if (!pq.isEmpty()) {
      *pqEmpty = false;
      pq.deleteMin(&key, &(value.node));
      while (!dequeueChannel->push(value)) {
        idleTicks += 1;
        if (idleTicks % 1000000 == 0) { 
          EVICT_CACHE();
          if (!opts.silent) { printTime("[%c] "); printf("  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
          if (*timeout) { 
            if (!opts.silent) {
              printTime("[%c] ");
              printf("  |PQ Dirty Timeout| idleTicks=%lu\n", idleTicks);
            }
            return;
          }
        }
      }
      somethingHappened = true;
    } else {
      if (!(*pqEmpty)){ 
        *pqEmpty = true;
        EVICT_CACHE();
      }
    }

    // Debug
    if (!somethingHappened) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        EVICT_CACHE();
        if (!opts.silent) {printTime("[%c] "); printf("  |PQ Idle| idle=%luM\n", idleTicks / 1000000); }
      }
    }
  }

  // Return
  if (!opts.silent) {
    printTime("[%c] ");
    printf("  |PQ Normal Return| idleTicks=%lu\n", idleTicks);
  }
}


//
// Handle creating children for the priority queue
//
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
void pushChildrenWorker(
    Channel* enqueueChannel, Channel* dequeueChannel,
    SearchNode* history, uint64_threadsafe_t* historySize,
    const SynSearchCosts* costs,
    bool* timeout, bool* pqEmpty,
    const syn_search_options& opts, const Graph* graph, const Tree& tree,
    uint64_t* ticks) {
  // Variables
  uint64_t idleTicks = 0;
  *ticks = 0;
  uint8_t  dependentIndices[8];
  natlog_relation  dependentRelations[8];
  uint8_t memorySize = 0;
  SearchNode memory[SEARCH_CYCLE_MEMORY];
  ScoredSearchNode scoredNode;
  // Main Loop
  while (*ticks < opts.maxTicks) {
    // Dequeue an element
    while (!dequeueChannel->poll(&scoredNode)) {
      idleTicks += 1;
      if (idleTicks % 1000000 == 0) { 
        if (!opts.silent) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM  seemsDone=%u\n", *ticks / 1000, idleTicks / 1000000, *pqEmpty); }
        // Check if the priority queue is empty
        if (*pqEmpty) {
          // (evict the cache)
          EVICT_CACHE();
          // (try to poll again)
          if (dequeueChannel->poll(&scoredNode)) { break; }
          // (priority queue really is empty)
          if (!opts.silent) { printTime("[%c] "); printf("  |PQ Empty| ticks=%lu\n", *ticks); }
          *timeout = true;
          return;
        }
      }
    }
    
    // Register the dequeue'd element
    const SearchNode& node = scoredNode.node;
#if SEARCH_CYCLE_MEMORY!=0
    memory[0] = history[node.getBackpointer()];
    memorySize = 1;
    for (uint8_t i = 1; i < SEARCH_CYCLE_MEMORY; ++i) {
      if (memory[i - 1].getBackpointer() != 0) {
        memory[i] = history[memory[i - 1].getBackpointer()];
        memorySize = i + 1;
      }
    }
    const SearchNode& parent = history[node.getBackpointer()];
#endif
    const uint32_t myIndex = historySize->value;
//    printf("%u>> %s (points to %u)\n", 
//      myIndex, toString(*graph, tree, node).c_str(),
//      node.getBackpointer());
    history[myIndex] = node;
    historySize->value += 1;
    *ticks += 1;
    if (!opts.silent && (*ticks) % 1000000 == 0) { 
      printTime("[%c] "); 
      printf("  |CF Progress| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000);
    }

    // PUSH 1: Mutations
    uint32_t numEdges;
    const edge* edges = graph->incomingEdgesFast(node.token(), &numEdges);
    for (uint32_t edgeI = 0; edgeI < numEdges; ++edgeI) {
      bool newTruthValue;
      const float cost = costs->mutationCost(
          tree, node.tokenIndex(), edges[edgeI].type,
          node.truthState(), &newTruthValue);
      if (isinf(cost)) { continue; }
      // (compute new fields)
      const uint64_t newHash = tree.updateHashFromMutation(
          node.factHash(), node.tokenIndex(), node.token().word,
          node.governor(), edges[edgeI].source
        );
      const tagged_word newToken = getTaggedWord(
          edges[edgeI].source,
          edges[edgeI].source_sense,
          node.token().monotonicity);
      // (create child)
      const SearchNode mutatedChild(node, newHash, newToken,
                                    newTruthValue, myIndex);
      // (push child)
#if SEARCH_CYCLE_MEMORY!=0
      bool isNewChild = true;
      for (uint8_t i = 0; i < memorySize; ++i) {
//        printf("  CHECK %lu  vs %lu\n", mutatedChild.factHash(), memory[i].factHash());
        isNewChild &= (mutatedChild != memory[i]);
      }
      if (isNewChild) {
#endif
      while (!enqueueChannel->push(ScoredSearchNode(mutatedChild, cost))) {
        idleTicks += 1;
        if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
      }
//      printf("  push mutation %s\n", toString(*graph, tree, mutatedChild).c_str());
#if SEARCH_CYCLE_MEMORY!=0
      }
#endif
    }
  
    // INTERM: Get Children
    uint8_t numDependents;
    tree.dependents(node.tokenIndex(), 8, dependentIndices,
                    dependentRelations, &numDependents);
    
    for (uint8_t dependentI = 0; dependentI < numDependents; ++dependentI) {
      const uint8_t& dependentIndex = dependentIndices[dependentI];

      // PUSH 2: Deletions
      if (node.isDeleted(dependentIndex)) { continue; }
      bool newTruthValue;
      const float cost = costs->insertionCost(
            tree, node.tokenIndex(), tree.relation(dependentIndex),
            tree.word(dependentIndex), node.truthState(), &newTruthValue);
      if (!isinf(cost)) {
        // (compute deletion)
        uint32_t deletionMask = tree.createDeleteMask(dependentIndex);
        uint64_t newHash = tree.updateHashFromDeletions(
            node.factHash(), dependentIndex, tree.token(dependentIndex).word,
            node.token().word, deletionMask);
        // (create child)
        const SearchNode deletedChild(node, newHash,
                                      newTruthValue, deletionMask, myIndex);
        // (push child)
        while (!enqueueChannel->push(ScoredSearchNode(deletedChild, cost))) {
          idleTicks += 1;
          if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
        }
//        printf("  push deletion %s\n", toString(*graph, tree, deletedChild).c_str());
      }

      // PUSH 3: Index Move
      // (create child)
      const SearchNode indexMovedChild(node, tree, dependentIndex,
                                    myIndex);
      // (push child)
      while (!enqueueChannel->push(ScoredSearchNode(indexMovedChild, scoredNode.cost))) {  // Copy the cost
        idleTicks += 1;
        if (!opts.silent && idleTicks % 1000000 == 0) { printTime("[%c] "); printf("  |CF Idle| ticks=%luK  idle=%luM\n", *ticks / 1000, idleTicks / 1000000); }
      }
//      printf("  push index move %s\n", toString(*graph, tree, indexMovedChild).c_str());
    }
  }

  if (!opts.silent) {
    printTime("[%c] ");
    printf("  |CF Normal Return| ticks=%lu  idle=%lu\n", *ticks, idleTicks);
  }
  *timeout = true;
  EVICT_CACHE();
}
#pragma GCC pop_options  // matches push_options above

//
// Handle the fact lookup
//
void factLookupWorker(
    vector<vector<SearchNode>>* matches, std::function<bool(uint64_t)> contains,
    const SearchNode* history, const uint64_threadsafe_t* historySize, 
    bool* searchDone, const syn_search_options& opts) {
  // Variables
  uint32_t onPrice = 0;
  uint64_t idleTicks = 0l;


  while (!(*searchDone)) {
    while (onPrice < historySize->value) {
      const SearchNode& node = history[onPrice];
      if (node.truthState() && contains(node.factHash())) {
        bool unique = true;
        for (auto iter = matches->begin(); iter != matches->end(); ++iter) {
          vector<SearchNode> path = *iter;
          SearchNode endOfPath = path.back();
          if (endOfPath.factHash() == node.factHash()) {
            unique = false;
          }
        }
        if (unique) {
          // Add this path to the result
          // (get the complete path)
          vector<SearchNode> path;
          path.push_back(node);
          if (node.getBackpointer() != 0) {
            SearchNode head = node;
            while (head.getBackpointer() != 0) {
              path.push_back(head);
              head = history[head.getBackpointer()];
            }
          }
          // (reverse the path)
          std::reverse(path.begin(), path.end());
          // (add to the results list)
          matches->push_back(path);
        }
      }
      onPrice += 1;
    }

    // Debug print
    idleTicks += 1;
    if (!opts.silent && idleTicks % 1000000 == 0) { 
      printTime("[%c] "); 
      printf("  |Lookup Idle| idle=%luM\n", idleTicks / 1000000);
      EVICT_CACHE();
    }
  }

  // Return
  if (!opts.silent) {
    printTime("[%c] "); 
    printf("  |Lookup normal return| resultCount=%lu  idle=%lu\n", 
           matches->size(), idleTicks);
  }
}

//
// The entry method for searching
//
syn_search_response SynSearch(
    const Graph* mutationGraph, 
    const btree::btree_set<uint64_t>& db,
    const Tree* input, const SynSearchCosts* costs,
    const syn_search_options& opts) {
  syn_search_response response;
  // Debug print parameters
  if (opts.maxTicks > 1000000000) {
    printTime("[%c] ");
    printf("ERROR: Max number of ticks is too large: %u\n", opts.maxTicks);
    response.totalTicks = 0;
    return response;
  }
  if (!opts.silent) {
    printTime("[%c] ");
    printf("|BEGIN SEARCH| fact='%s'\n", toString(*mutationGraph, *input).c_str());
  }

  // Allocate some shared variables
  // (from the tree cache space)
  bool* cacheSpace = (bool*) malloc(3*sizeof(bool));
  bool* timeout = cacheSpace;
  *timeout = false;
  bool* pqEmpty = cacheSpace + 1;
  *pqEmpty = false;
  bool* searchDone = cacheSpace + 2;
  *searchDone = false;
  // (communication)
  Channel* enqueueChannel = threadsafeChannel();
  Channel* dequeueChannel = threadsafeChannel();
  SearchNode* history = (SearchNode*) malloc(opts.maxTicks * sizeof(SearchNode));
  uint64_threadsafe_t* historySize = malloc_uint64_threadsafe_t();

  // (sanity check)
  if (((uint64_t) searchDone) + 1 >= ((uint64_t) input) + sizeof(Tree)) {
    printf("Allocated too much into the cache memory area!\n");
    std::exit(1);
  }

  // Enqueue the first element
  // (to the fringe)
  if (!dequeueChannel->push(ScoredSearchNode(SearchNode(*input), 0.0f))) {
    printf("Could not push root!?\n");
    std::exit(1);
  }
  // (to the history)
  history[0] = SearchNode(*input);
  historySize->value += 1;

  // Start threads
  // (priority queue)
  std::thread priorityQueueThread(priorityQueueWorker, 
      enqueueChannel, dequeueChannel, timeout, pqEmpty, opts);
  // (child creator)
  std::thread pushChildrenThread(pushChildrenWorker, 
      enqueueChannel, dequeueChannel, 
      history, historySize,
      costs,
      timeout, pqEmpty,
      opts, mutationGraph, *input, &(response.totalTicks));
  // (database lookup)
  std::function<bool(uint64_t)> lookupFn = [&db](uint64_t value) -> bool {
    auto iter = db.find( value );
    return iter != db.end();
  };
  std::thread lookupThread(factLookupWorker, 
      &(response.paths), lookupFn, history, historySize, searchDone, opts);
      

  // Join threads
  if (!opts.silent) {
    printTime("[%c] ");
    printf("AWAITING JOIN...\n");
  }
  // (priority queue)
  if (!opts.silent) { printTime("[%c] "); printf("  Priority queue...\n"); }
  priorityQueueThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    printf("    Priority queue joined.\n");
  }
  // (push children)
  if (!opts.silent) { printTime("[%c] "); printf("  Child factory...\n"); }
  pushChildrenThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    printf("    Child factory joined.\n");
  }
  // (database lookup)
  // (note: this must come after the above two have joined)
  *searchDone = true;
  EVICT_CACHE();
  if (!opts.silent) { printTime("[%c] "); printf("  Database lookup...\n"); }
  lookupThread.join();
  if (!opts.silent) {
    printTime("[%c] ");
    printf("    Database lookup joined.\n");
  }

  // Clean up
  delete enqueueChannel;
  delete dequeueChannel;
  free(history);
  free(historySize);
  free(cacheSpace);
  
  // Return
  EVICT_CACHE();
  return response;
}
