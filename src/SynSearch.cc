#include <cstring>
#include <sstream>
#include <thread>

#include "SynSearch.h"
#include "Channel.h"
#include "Utils.h"

using namespace std;
using namespace moodycamel;

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
    if (item.length() > 0) {
      length += 1;
    }
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
    fprintf(stderr, "ERROR: Bad monotonicity marker: %s; assuming flat: ", field.c_str());
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
    if (line.length() == 0) { continue; }
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
        case 3:  // Word Sense
          data[lineI].sense = atoi(field.c_str());
          break;
        case 4:  // Subject monotonicity
          if (field != "-") {
            isQuantifier = true;
            stringToMonotonicity(field, &subjMono, &subjType);
          }
          break;
        case 5:  // Subject span
          if (field != "-") {
            uint8_t begin, end;
            stringToSpan(field, &begin, &end);
            span.subj_begin = begin; span.subj_end = end;
          }
          break;
        case 6:  // Object monotonicity
          if (field != "-") {
            stringToMonotonicity(field, &objMono, &objType);
          } else {
            objMono = MONOTONE_FLAT;
            objType = QUANTIFIER_TYPE_NONE;
          }
          break;
        case 7:  // Object span
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
              fprintf(stderr, "WARNING: Too many quantifiers; skipping at word %u\n", lineI);
            }
          }
          break;
      }
      fieldI += 1;
    }
    if (fieldI != 3 && fieldI != 8) {
      fprintf(stderr, "ERROR: Bad number of CoNLL fields in line (expected 3 or 7, was %u): %s\n", fieldI, line.c_str());
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
      onSubject[i] = false;
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
//      fprintf(stderr, "Calling visitor() on quantifier %u: %u-%u %u-%u :: %u %u; %u %u\n",
//        argmin, 
//        quantifierSpans[argmin].subj_begin, 
//        quantifierSpans[argmin].subj_end,
//        quantifierSpans[argmin].obj_begin,
//        quantifierSpans[argmin].obj_end,
//        quantifierMonotonicities[argmin].subj_type,
//        quantifierMonotonicities[argmin].subj_mono,
//        quantifierMonotonicities[argmin].obj_type,
//        quantifierMonotonicities[argmin].obj_mono
//        );
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
  fprintf(stderr, "No root found in tree!\n");
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
//  fprintf(stderr, "Mutating %u; dep=%u -> %u  gov=%u\n", index, oldWord, newWord, governor);
  newHash ^= hashEdge(edgeInto(index, oldWord, governor));
  newHash ^= hashEdge(edgeInto(index, newWord, governor));
  // Fix outgoing dependencies
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
//      fprintf(stderr, "  Also re-hashing word at %u; dep=%u  gov=%u -> %u\n", i,
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
//        fprintf(stderr, "Deleting word at %u; dep=%u  gov=%u\n", i, deletionWord, governor);
        // Case: we are deleting the root of the deletion chunk
        newHash ^= hashEdge(edgeInto(i, deletionWord, governor));
      } else {
//        fprintf(stderr, "Deleting word at %u; dep=%u  gov=%u\n", i, edgeInto(i).dependent, edgeInto(i).governor);
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
      case FUNCTION_EQUIVALENT:
      case FUNCTION_INDEPENDENCE:
        return true;
      case FUNCTION_ALTERNATION:  // negate anyways, just at high cost
      case FUNCTION_NEGATION:
      case FUNCTION_COVER:
        return false;
      default:
        fprintf(stderr, "Unknown function: %u", projectedRelation);
        std::exit(1);
        break;
    }
  } else {
    switch (projectedRelation) {
      case FUNCTION_FORWARD_ENTAILMENT:
      case FUNCTION_REVERSE_ENTAILMENT:
      case FUNCTION_EQUIVALENT:
      case FUNCTION_INDEPENDENCE:
        return false;
      case FUNCTION_COVER:  // negate anyways, just at high cost
      case FUNCTION_NEGATION:
      case FUNCTION_ALTERNATION:
        return true;
      default:
        fprintf(stderr, "Unknown function: %u", projectedRelation);
        std::exit(1);
        break;
    }
  }
  fprintf(stderr, "Reached end of reverseTransition() function -- this shouldn't happen!\n");
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        default: fprintf(stderr, "Unknown projectivity type for quantifier: %u", quantifierType); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
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
            default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
          }
          break;
        default: fprintf(stderr, "Unknown projectivity type for quantifier: %u", quantifierType); std::exit(1); break;
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
        default: fprintf(stderr, "Unknown lexical relation: %u", lexicalFunction); std::exit(1); break;
      }
      break;
    default:
      fprintf(stderr, "Invalid monotonicity: %u", monotonicity);
      std::exit(1);
      break;
  }
  fprintf(stderr, "Reached end of project() function -- this shouldn't happen!\n");
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
// UTILITIES
// ----------------------------------------------

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
