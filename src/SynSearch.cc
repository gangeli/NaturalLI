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
    const ::word&      governor,
    const uint64_t& backpointer) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.truth = truth;
  dat.deleteMask = deleteMask;
  dat.currentWord = currentToken.word;
  dat.currentSense = currentToken.sense;
  dat.governor = governor;
  dat.backpointer = backpointer;
  return dat;
}

inline syn_path_data mkSearchNodeData(
    const uint64_t&    factHash,
    const uint8_t&     index,
    const bool&        truth,
    const uint32_t&    deleteMask,
    const ::word&      currentWord,
    const uint8_t&     currentSense,
    const ::word&      governor,
    const uint64_t& backpointer) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.truth = truth;
  dat.deleteMask = deleteMask;
  dat.currentWord = currentWord;
  dat.currentSense = currentSense;
  dat.governor = governor;
  dat.backpointer = backpointer;
  return dat;
}

SearchNode::SearchNode()
    : data(mkSearchNodeData(42l, 255, false, 42, getTaggedWord(0, 0, 0), TREE_ROOT_WORD, 0)) {  }

SearchNode::SearchNode(const SearchNode& from)
    : data(from.data) {
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}
  
SearchNode::SearchNode(const Tree& init)
    : data(mkSearchNodeData(init.hash(), init.root(), true, 
                         0x0, init.token(init.root()), TREE_ROOT_WORD, 0)) {
  memcpy(this->quantifierMonotonicities, init.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}
 
SearchNode::SearchNode(const Tree& init, const bool& assumedInitialTruth)
    : data(mkSearchNodeData(init.hash(), init.root(), assumedInitialTruth, 
                         0x0, init.token(init.root()), TREE_ROOT_WORD, 0)) {
  memcpy(this->quantifierMonotonicities, init.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}
 
SearchNode::SearchNode(const Tree& init, const uint8_t& index)
    : data(mkSearchNodeData(init.hash(), index, true, 
                         0x0, init.token(index), init.word(init.governor(index)), 0)) {
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
    : data(mkSearchNodeData(newHash, from.data.index, newTruthValue,
                         from.data.deleteMask, newToken,
                         from.data.governor, backpointer)) {
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}

//
// SearchNode() ''delete constructor
//
SearchNode::SearchNode(const SearchNode& from, const uint64_t& newHash,
          const bool& newTruthValue,
          const uint32_t& addedDeletions, const uint32_t& backpointer)
    : data(mkSearchNodeData(newHash, from.data.index, newTruthValue,
                         addedDeletions | from.data.deleteMask, 
                         from.data.currentWord, from.data.currentSense,
                         from.data.governor, backpointer)) { 
  memcpy(this->quantifierMonotonicities, from.quantifierMonotonicities,
    MAX_QUANTIFIER_COUNT * sizeof(quantifier_monotonicity));
}

//
// SearchNode() ''move index constructor
//
SearchNode::SearchNode(const SearchNode& from, const Tree& tree,
                 const uint8_t& newIndex, const uint32_t& backpointer)
    : data(mkSearchNodeData(from.data.factHash, newIndex, from.data.truth, 
                         from.data.deleteMask, tree.token(newIndex), 
                         tree.token(tree.governor(newIndex)).word, backpointer)) { 
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
  
  // Initialize cached variables
  for (uint8_t tokenI = 0; tokenI < MAX_QUERY_LENGTH; ++tokenI) {
    memset(quantifiersInScope[tokenI], MAX_QUANTIFIER_COUNT, MAX_QUANTIFIER_COUNT * sizeof(uint8_t));
    populateQuantifiersInScope(tokenI);
  }
}

//
// Tree::foreachQuantifier()
//
void Tree::foreachQuantifier(
      const uint8_t& index,
      std::function<void(const quantifier_type,const monotonicity)> visitor) const {
  for (uint8_t i = 0; i < MAX_QUANTIFIER_COUNT; ++i) {
    // Get the quantifier in scipe
    uint8_t quantifier = this->quantifiersInScope[index][i];
    if (quantifier >= MAX_QUANTIFIER_COUNT) { return; }
    // Check if it's the subject or object
    const quantifier_span& span = this->quantifierSpans[quantifier];
    bool onSubject = index < span.subj_end && index >= span.subj_begin;
    // Visit the quantifier
    if (onSubject) {
      visitor(this->quantifierMonotonicities[quantifier].subj_type, 
              this->quantifierMonotonicities[quantifier].subj_mono);
    } else {
      visitor(this->quantifierMonotonicities[quantifier].obj_type,
              this->quantifierMonotonicities[quantifier].obj_mono);
    }
  }
}

//
// Tree::populateQuantifiersInScope()
//
#pragma GCC push_options  // matches pop_options below
#pragma GCC optimize ("unroll-loops")
void Tree::populateQuantifiersInScope(const uint8_t index) {
  // Variables
  uint8_t distance[MAX_QUANTIFIER_COUNT];
  memset(distance, 0, MAX_QUANTIFIER_COUNT * sizeof(uint8_t));
  bool valid[MAX_QUANTIFIER_COUNT];
  memset(valid, 0, MAX_QUANTIFIER_COUNT * sizeof(uint8_t));
  // Collect statistics
  for (uint8_t i = 0; i < MAX_QUANTIFIER_COUNT; ++i) {
    if (i >= numQuantifiers) { break; }
    if (index >= quantifierSpans[i].subj_begin && index < quantifierSpans[i].subj_end) {
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
  uint8_t outIndex = 0;
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
      this->quantifiersInScope[index][outIndex] = argmin;
      outIndex += 1;
    }
    // (update state)
    valid[argmin] = false;
  }
  this->quantifiersInScope[index][outIndex] = MAX_QUANTIFIER_COUNT;
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

inline uint64_t hashEdge(dependency_edge edge) {
  // Collapse relations which are 'equivalent'
  dep_label  originalRel = edge.relation;
  if (edge.relation == DEP_NEG) { 
    edge.relation = DEP_DET;
  }
  // Hash edge
#if TWO_PASS_HASH!=0
  return fnv_64a_buf(&edge, sizeof(dependency_edge), FNV1_64_INIT);
#else
  uint64_t edgeHash;
  memcpy(&edgeHash, &edge, sizeof(uint64_t));
  return mix(edgeHash);
#endif
  // Revert relation
  edge.relation = originalRel;
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
  newHash ^= hashEdge(edgeInto(index, oldWord, governor));
  newHash ^= hashEdge(edgeInto(index, newWord, governor));
  // Fix outgoing dependencies
  for (uint8_t i = 0; i < length; ++i) {
    if (data[i].governor == index) {
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
        // Case: we are deleting the root of the deletion chunk
        newHash ^= hashEdge(edgeInto(i, deletionWord, governor));
      } else {
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
natlog_relation Tree::projectLexicalRelation( const SearchNode& currentNode,
                                              const natlog_relation& lexicalRelation) const {
  const uint8_t index = currentNode.tokenIndex();
  const dep_tree_word& token = data[index];
  natlog_relation outputRelation = lexicalRelation;
  for (uint8_t i = 0; i < MAX_QUANTIFIER_COUNT; ++i) {
    // Get the quantifier in scope
    uint8_t quantifier = this->quantifiersInScope[index][i];
    if (quantifier >= MAX_QUANTIFIER_COUNT) { break; }
    // Check if it's the subject or object
    const quantifier_span& span = this->quantifierSpans[quantifier];
    bool onSubject = index < span.subj_end && index >= span.subj_begin;
    // Visit the quantifier
    if (onSubject) {
      const uint8_t type = currentNode.quantifierMonotonicities[quantifier].subj_type;
      const uint8_t mono = currentNode.quantifierMonotonicities[quantifier].subj_mono;
      outputRelation = project(mono, type, outputRelation);
    } else {
      const uint8_t type = currentNode.quantifierMonotonicities[quantifier].obj_type;
      const uint8_t mono = currentNode.quantifierMonotonicities[quantifier].obj_mono;
      outputRelation = project(mono, type, outputRelation);
    }
  }
  return outputRelation;
}


// ----------------------------------------------
// NATURAL LOGIC
// ----------------------------------------------
natlog_relation dependencyInsertToLexicalFunction(const dep_label& dep,
                                                  const ::word& dependent) {
  switch (dep) {
    case DEP_ACOMP: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_ADVCL: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_ADVMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_AMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_APPOS: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_AUX: return FUNCTION_INDEPENDENCE;            // He left -/-> he should leave
    case DEP_AUXPASS: return FUNCTION_INDEPENDENCE;        // Some cat adopts -/-> some cat got adopted
    case DEP_CC: return FUNCTION_REVERSE_ENTAILMENT;       // match DEP_CONJ
    case DEP_CCOMP: return FUNCTION_INDEPENDENCE;          // interesting project here... "he said x" -> "x"?
    case DEP_CONJ: return FUNCTION_REVERSE_ENTAILMENT;     // match DEP_CC
    case DEP_COP: return FUNCTION_INDEPENDENCE;
    case DEP_CSUBJ: return FUNCTION_INDEPENDENCE;          // don't drop subjects.
    case DEP_CSUBJPASS: return FUNCTION_INDEPENDENCE;      // as above
    case DEP_DEP: return FUNCTION_INDEPENDENCE;
    case DEP_DET: return FUNCTION_EQUIVALENT;              // TODO(gabor) better treatment of generics?
    case DEP_DISCOURSE: return FUNCTION_EQUIVALENT;
    case DEP_DOBJ: return FUNCTION_INDEPENDENCE;           // don't drop objects.
    case DEP_EXPL: return FUNCTION_EQUIVALENT;             // though we shouldn't see this...
    case DEP_GOESWITH: return FUNCTION_EQUIVALENT;         // also shouldn't see this
    case DEP_IOBJ: return FUNCTION_REVERSE_ENTAILMENT;     // she gave me a raise -> she gave a raise
    case DEP_MARK: return FUNCTION_INDEPENDENCE;
    case DEP_MWE: return FUNCTION_INDEPENDENCE;            // shouldn't see this
    case DEP_NEG: return FUNCTION_NEGATION;
    case DEP_NN: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_NPADVMOD: return FUNCTION_INDEPENDENCE;       // not sure about this one
    case DEP_NSUBJ: return FUNCTION_INDEPENDENCE;
    case DEP_NSUBJPASS: return FUNCTION_INDEPENDENCE;
    case DEP_NUM: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_NUMBER: return FUNCTION_INDEPENDENCE;
    case DEP_PARATAXIS: return FUNCTION_INDEPENDENCE;      // or, reverse?
    case DEP_PCOMP: return FUNCTION_INDEPENDENCE;          // though, not so in collapsed dependencies
    case DEP_POBJ: return FUNCTION_INDEPENDENCE;           // must delete whole preposition
    case DEP_POSS: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_POSSEIVE: return FUNCTION_INDEPENDENCE;       // see DEP_POSS
    case DEP_PRECONJ: return FUNCTION_INDEPENDENCE;        // FORBIDDEN to see this
    case DEP_PREDET: return FUNCTION_INDEPENDENCE;         // FORBIDDEN to see this
    case DEP_PREP: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_PRT: return FUNCTION_INDEPENDENCE;
    case DEP_PUNCT: return FUNCTION_EQUIVALENT;
    case DEP_QUANTMOD: return FUNCTION_FORWARD_ENTAILMENT;
    case DEP_RCMOD: return FUNCTION_FORWARD_ENTAILMENT;    // "there are great tennors --rcmod--> who are modest"
    case DEP_ROOT: return FUNCTION_INDEPENDENCE;           // err.. never delete
    case DEP_TMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_VMOD: return FUNCTION_REVERSE_ENTAILMENT;
    case DEP_XCOMP: return FUNCTION_INDEPENDENCE;
    default:
      fprintf(stderr, "No such dependency label: %u\n", dep);
      std::exit(1);
      return 255;
  }
}

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
// transition()
//
bool transition(const bool& startState,
                const natlog_relation projectedRelation) {
  if (startState) {
    switch (projectedRelation) {
      case FUNCTION_FORWARD_ENTAILMENT:
      case FUNCTION_REVERSE_ENTAILMENT:
      case FUNCTION_EQUIVALENT:
      case FUNCTION_INDEPENDENCE:
        return true;
      case FUNCTION_ALTERNATION:
      case FUNCTION_NEGATION:
      case FUNCTION_COVER:  // negate anyways, just at high cost
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
      case FUNCTION_COVER:
      case FUNCTION_NEGATION:
      case FUNCTION_ALTERNATION:  // negate anyways, just at high cost
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
                                   const SearchNode& currentNode,
                                   const uint8_t& edgeType,
                                   const bool& endTruthValue,
                                   bool* beginTruthValue) const {
  const natlog_relation lexicalRelation = edgeToLexicalFunction(edgeType);
  const float lexicalRelationCost = mutationLexicalCost[edgeType];
  const natlog_relation projectedFunction
    = tree.projectLexicalRelation(currentNode, lexicalRelation);
  *beginTruthValue = reverseTransition(endTruthValue, projectedFunction);
  const float transitionCost
    = ((*beginTruthValue) ? transitionCostFromTrue : transitionCostFromFalse)[projectedFunction];
  return lexicalRelationCost + transitionCost;
}

//
// SynSearch::insertionCost()
//
float SynSearchCosts::insertionCost(const Tree& tree,
                                    const SearchNode& governor,
                                    const dep_label& dependencyLabel,
                                    const ::word& dependent,
                                    const bool& endTruthValue,
                                    bool* beginTruthValue) const {
  const natlog_relation lexicalRelation
    = dependencyInsertToLexicalFunction(dependencyLabel, dependent);
  const float lexicalRelationCost
    = insertionLexicalCost[dependencyLabel];
  const natlog_relation projectedFunction
    = tree.projectLexicalRelation(governor, lexicalRelation);
  *beginTruthValue = reverseTransition(endTruthValue, projectedFunction);
  const float transitionCost
    = ((*beginTruthValue) ? transitionCostFromTrue : transitionCostFromFalse)[projectedFunction];
  return lexicalRelationCost + transitionCost;
}

//
// SynSearch::deletionCost()
//
float SynSearchCosts::deletionCost(const Tree& tree,
                                   const SearchNode& governor,
                                   const dep_label& dependencyLabel,
                                   const ::word& dependent,
                                   const bool& beginTruthValue,
                                   bool* endTruthValue) const {
  const natlog_relation lexicalRelation
    = dependencyDeleteToLexicalFunction(dependencyLabel, dependent);
  const float lexicalRelationCost
    = insertionLexicalCost[dependencyLabel];
  const natlog_relation projectedFunction
    = tree.projectLexicalRelation(governor, lexicalRelation);
  *endTruthValue = transition(endTruthValue, projectedFunction);
  const float transitionCost
    = (beginTruthValue ? transitionCostFromTrue : transitionCostFromFalse)[projectedFunction];
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


//
// ForwardPartialSearch
//
void ForwardPartialSearch(
    const BidirectionalGraph* mutationGraph,
    const Tree* input,
    std::function<void(const SearchNode&)> callback) {
  // Initialize Search
  vector<SearchNode> fringe;
  fringe.push_back(SearchNode(*input));
  uint8_t  dependentIndices[8];
  natlog_relation  dependentRelations[8];
  SynSearchCosts* guidingCosts = strictNaturalLogicCosts();
  bool newTruthValue;
  btree::btree_set<uint64_t> seen;

  // Run Search
  while (fringe.size() > 0) {
    const SearchNode node = fringe.back();
    fringe.pop_back();
    if (seen.find(node.factHash()) == seen.end()) { 
//      fprintf(stderr, "  %s\n", toString(*(mutationGraph->impl), *input, node).c_str());
      callback(node); 
    }
    seen.insert(node.factHash());

    // Valid Mutations
    const vector<edge> edges = mutationGraph->outgoingEdges(node.token());
    for (auto iter = edges.begin(); iter != edges.end(); ++iter) {
      // TODO(gabor) NER raising would go here
    }

    // Valid Deletions
    uint8_t numDependents;
    input->dependents(node.tokenIndex(), 8, dependentIndices,
                    dependentRelations, &numDependents);
    for (uint8_t dependentI = 0; dependentI < numDependents; ++dependentI) {
      const uint8_t& dependentIndex = dependentIndices[dependentI];
      if (node.isDeleted(dependentIndex)) { continue; }
      const float cost = guidingCosts->deletionCost(
            *input, node, input->relation(dependentIndex),
            input->word(dependentIndex), node.truthState(), &newTruthValue);
      // Deletion (optional)
      if (newTruthValue && !isinf(cost)) {  // TODO(gabor) handle negation deletions?
        // (compute)
        uint32_t deletionMask = input->createDeleteMask(dependentIndex);
        uint64_t newHash = input->updateHashFromDeletions(
            node.factHash(), dependentIndex, input->token(dependentIndex).word,
            node.word(), deletionMask);
        // (create child)
        const SearchNode deletedChild(node, newHash, newTruthValue, deletionMask, 0);
        fringe.push_back(deletedChild);
      }
      // Move (always)
      const SearchNode indexMovedChild(node, *input, dependentIndex, 0);
      fringe.push_back(indexMovedChild);
    }
  }

  delete guidingCosts;
}


