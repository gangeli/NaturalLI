#include "Utils.h"

#include <string>
#include <algorithm>

#include "Types.h"

using namespace std;

const vector<tagged_word> lemursHaveTails() {
  vector<tagged_word> fact;
  fact.push_back(LEMUR);
  fact.push_back(HAVE);
  fact.push_back(TAIL);
  return fact;
}

const vector<tagged_word> animalsHaveTails() {
  vector<tagged_word> animals;
  animals.push_back(ANIMAL);
  animals.push_back(HAVE);
  animals.push_back(TAIL);
  return animals;
}

const vector<tagged_word> catsHaveTails() {
  vector<tagged_word> cats;
  cats.push_back(CAT);
  cats.push_back(HAVE);
  cats.push_back(TAIL);
  return cats;
}

const vector<tagged_word> someDogsChaseCats() {
  vector<tagged_word> cats;
  cats.push_back(SOME);
  cats.push_back(DOG);
  cats.push_back(CHASE);
  cats.push_back(CAT);
  return cats;
}

string toString(const Graph& graph, const tagged_word* fact, const uint8_t factLength) {
  string gloss = "";
  for (int i = 0; i < factLength; ++i) {
    monotonicity m = fact[i].monotonicity;
    string marker = "";
    if (m == MONOTONE_DOWN) { marker = "[v]"; }
    if (m == MONOTONE_UP) { marker = "[^]"; }
    string sense = to_string((long long int) fact[i].sense);
    gloss = gloss + (gloss == "" ? "" : " ") + marker + graph.gloss(fact[i]) + "_" + sense;
  }
  return gloss;
}

string toString(const Graph& graph, SearchType& searchType, const Path* path) {
  if (path == NULL) {
    return "<start>";
  } else {
    return toString(graph, path->fact, path->factLength) +
           "; from\n  " +
           toString(graph, searchType, path->parent);
  }
}

std::string toString(const Graph& graph, const Tree& tree) {
  tagged_word buffer[tree.length];
  for (uint8_t i = 0; i < tree.length; ++i) {
    buffer[i] = tree.token(i);
  }
  return toString(graph, buffer, tree.length);
}

std::string toString(const Graph& graph, const Tree& tree, const SearchNode& path) {
  string str = "";
  str += std::to_string(path.factHash()) + ": ";
  for (uint8_t i = 0; i < tree.length; ++i) {
    if (i == path.tokenIndex()) {
    string sense = to_string((long long int) path.token().sense);
      str += "*" + string(graph.gloss(path.token())) + "_" + sense + " ";
    } else if (path.isDeleted(i)) {
      str += "-- ";
    } else {
      str += string(graph.gloss(tree.token(i))) + " ";
    }
  }
  return str;
}

string toString(const edge_type& edge) {
  switch (edge) {
    case WORDNET_UP                   : return "WORDNET_UP";
    case WORDNET_DOWN                 : return "WORDNET_DOWN";
    case WORDNET_NOUN_ANTONYM         : return "WORDNET_NOUN_ANTONYM";
    case WORDNET_NOUN_SYNONYM         : return "WORDNET_NOUN_SYNONYM";
    case WORDNET_VERB_ANTONYM         : return "WORDNET_VERB_ANTONYM";
    case WORDNET_ADJECTIVE_ANTONYM    : return "WORDNET_ADJECTIVE_ANTONYM";
    case WORDNET_ADVERB_ANTONYM       : return "WORDNET_ADVERB_ANTONYM";
    case WORDNET_ADJECTIVE_PERTAINYM  : return "WORDNET_ADJECTIVE_PERTAINYM";
    case WORDNET_ADVERB_PERTAINYM     : return "WORDNET_ADVERB_PERTAINYM";
    case WORDNET_ADJECTIVE_RELATED    : return "WORDNET_ADJECTIVE_RELATED";
    case ANGLE_NN                     : return "ANGLE_NN";
    case FREEBASE_UP                  : return "FREEBASE_UP";
    case FREEBASE_DOWN                : return "FREEBASE_DOWN";
    case MORPH_FUDGE_NUMBER           : return "MORPH_FUDGE_NUMBER";
    case SENSE_REMOVE                 : return "SENSE_REMOVE";
    case SENSE_ADD                    : return "SENSE_ADD";
    case ADD_NOUN                     : return "ADD_NOUN";
    case ADD_VERB                     : return "ADD_VERB";
    case ADD_ADJ                      : return "ADD_ADJ";
    case ADD_NEGATION                 : return "ADD_NEGATION";
    case ADD_EXISTENTIAL              : return "ADD_EXISTENTIAL";
    case ADD_QUANTIFIER_OTHER         : return "ADD_QUANTIFIER_OTHER";
    case ADD_UNIVERSAL                : return "ADD_UNIVERSAL";
    case ADD_OTHER                    : return "ADD_?";
    case DEL_NOUN                     : return "DEL_NOUN";
    case DEL_VERB                     : return "DEL_VERB";
    case DEL_ADJ                      : return "DEL_ADJ";
    case DEL_NEGATION                 : return "DEL_NEGATION";
    case DEL_EXISTENTIAL              : return "DEL_EXISTENTIAL";
    case DEL_QUANTIFIER_OTHER         : return "DEL_QUANTIFIER_OTHER";
    case DEL_UNIVERSAL                : return "DEL_UNIVERSAL";
    case DEL_OTHER                    : return "DEL_?";
    case QUANTIFIER_UP                : return "QUANTIFIER_UP";
    case QUANTIFIER_DOWN              : return "QUANTIFIER_DOWN";
    case QUANTIFIER_NEGATE            : return "QUANTIFIER_NEGATE";
    case QUANTIFIER_REWORD            : return "QUANTIFIER_REWORD";
    default: return "UNK_EDGE_TYPE";
  }
}

string toString(const time_t& elapsedTime) {
  // Compute fields
  uint64_t seconds = elapsedTime / CLOCKS_PER_SEC;
  uint64_t minutes = seconds / 60;
  seconds = seconds % 60;
  uint64_t hours = minutes / 60;
  minutes = minutes % 60;
  uint64_t days = hours / 24;
  hours = hours % 24;
  // Create string
  char buffer[128];
  if (days == 0 && hours == 0 && minutes == 0) {
    snprintf(buffer, 127, "%lus", seconds);
  } else if (days == 0 && hours == 0) {
    snprintf(buffer, 127, "%lum %lus", minutes, seconds);
  } else if (days == 0) {
    snprintf(buffer, 127, "%luh %lum %lus", hours, minutes, seconds);
  } else {
    snprintf(buffer, 127, "%lud %luh %lum %lus", days, hours, minutes, seconds);
  }
  // Return
  return string(buffer);
}

inference_function edge2function(const edge_type& type) {
  inference_function function;
  switch (type) {
    case WORDNET_UP: 
    case FREEBASE_UP: 
    case QUANTIFIER_UP:
    case DEL_NOUN:
    case DEL_VERB:
    case DEL_ADJ:
    case DEL_OTHER:
      function = FUNCTION_FORWARD_ENTAILMENT;
      break;
    case WORDNET_DOWN: 
    case FREEBASE_DOWN: 
    case QUANTIFIER_DOWN:
    case ADD_NOUN:
    case ADD_VERB:
    case ADD_ADJ:
    case ADD_OTHER:
      function = FUNCTION_REVERSE_ENTAILMENT;
      break;
    case WORDNET_NOUN_ANTONYM: 
    case WORDNET_VERB_ANTONYM: 
    case WORDNET_ADJECTIVE_ANTONYM: 
    case WORDNET_ADVERB_ANTONYM: 
      function = FUNCTION_ALTERNATION;
      break;
    case WORDNET_NOUN_SYNONYM: 
    case WORDNET_ADJECTIVE_RELATED: 
    case QUANTIFIER_REWORD:
    case ANGLE_NN: 
    case MORPH_FUDGE_NUMBER: 
    case SENSE_REMOVE: 
    case SENSE_ADD: 
      function = FUNCTION_EQUIVALENT;
      break;
    case WORDNET_ADJECTIVE_PERTAINYM:
    case WORDNET_ADVERB_PERTAINYM:
      function = FUNCTION_EQUIVALENT;
      break;
    case ADD_NEGATION:
    case DEL_NEGATION:
      function = FUNCTION_NEGATION;
      break;
    case QUANTIFIER_NEGATE:
      function = FUNCTION_NEGATION;
      break;
    // The weird cases...
    case ADD_EXISTENTIAL:
    case ADD_UNIVERSAL:
    case ADD_QUANTIFIER_OTHER:
    case DEL_EXISTENTIAL:
    case DEL_UNIVERSAL:
    case DEL_QUANTIFIER_OTHER:
    case NULL_EDGE_TYPE:
      function = FUNCTION_EQUIVALENT;
      break;
    default:
      printf("Unknown edge type: %u\n", type);
      std::exit(1);
      break;
  }
  return function;
}

/**
 * Print a demangled stack backtrace of the caller function to FILE* out.
 *
 * FROM:
 *
 * stacktrace.h (c) 2008, Timo Bingmann from http://idlebox.net/
 * published under the WTFPL v2.0
 */
void print_stacktrace(FILE *out, unsigned int max_frames)
{
    fprintf(out, "stack trace:\n");

    // storage array for stack trace address data
    void* addrlist[max_frames+1];

    // retrieve current stack addresses
    int addrlen = backtrace(addrlist, sizeof(addrlist) / sizeof(void*));

    if (addrlen == 0) {
	fprintf(out, "  <empty, possibly corrupt>\n");
	return;
    }

    // resolve addresses into strings containing "filename(function+address)",
    // this array must be free()-ed
    char** symbollist = backtrace_symbols(addrlist, addrlen);

    // allocate string which will be filled with the demangled function name
    size_t funcnamesize = 256;
    char* funcname = (char*)malloc(funcnamesize);

    // iterate over the returned symbol lines. skip the first, it is the
    // address of this function.
    for (int i = 1; i < addrlen; i++)
    {
	char *begin_name = 0, *begin_offset = 0, *end_offset = 0;

	// find parentheses and +address offset surrounding the mangled name:
	// ./module(function+0x15c) [0x8048a6d]
	for (char *p = symbollist[i]; *p; ++p)
	{
	    if (*p == '(')
		begin_name = p;
	    else if (*p == '+')
		begin_offset = p;
	    else if (*p == ')' && begin_offset) {
		end_offset = p;
		break;
	    }
	}

	if (begin_name && begin_offset && end_offset
	    && begin_name < begin_offset)
	{
	    *begin_name++ = '\0';
	    *begin_offset++ = '\0';
	    *end_offset = '\0';

	    // mangled name is now in [begin_name, begin_offset) and caller
	    // offset in [begin_offset, end_offset). now apply
	    // __cxa_demangle():

	    int status;
	    char* ret = abi::__cxa_demangle(begin_name,
					    funcname, &funcnamesize, &status);
	    if (status == 0) {
		funcname = ret; // use possibly realloc()-ed string
		fprintf(out, "  %s : %s+%s\n",
			symbollist[i], funcname, begin_offset);
	    }
	    else {
		// demangling failed. Output function name as a C function with
		// no arguments.
		fprintf(out, "  %s : %s()+%s\n",
			symbollist[i], begin_name, begin_offset);
	    }
	}
	else
	{
	    // couldn't parse the line? print the whole line.
	    fprintf(out, "  %s\n", symbollist[i]);
	}
    }

    free(funcname);
    free(symbollist);
}

uint8_t indexDependency(const string& dependencyAsString) {
  string lower = dependencyAsString;
  std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
  if (lower == "acomp") { return DEP_ACOMP; }
  if (lower == "advcl") { return DEP_ADVCL; }
  if (lower == "advmod") { return DEP_ADVMOD; }
  if (lower == "amod") { return DEP_AMOD; }
  if (lower == "appos") { return DEP_APPOS; }
  if (lower == "aux") { return DEP_AUX; }
  if (lower == "auxpass") { return DEP_AUXPASS; }
  if (lower == "cc") { return DEP_CC; }
  if (lower == "ccomp") { return DEP_CCOMP; }
  if (lower == "conj") { return DEP_CONJ; }
  if (lower == "cop") { return DEP_COP; }
  if (lower == "csubj") { return DEP_CSUBJ; }
  if (lower == "csubjpass") { return DEP_CSUBJPASS; }
  if (lower == "dep") { return DEP_DEP; }
  if (lower == "det") { return DEP_DET; }
  if (lower == "discourse") { return DEP_DISCOURSE; }
  if (lower == "dobj") { return DEP_DOBJ; }
  if (lower == "expl") { return DEP_EXPL; }
  if (lower == "goeswith") { return DEP_GOESWITH; }
  if (lower == "iobj") { return DEP_IOBJ; }
  if (lower == "mark") { return DEP_MARK; }
  if (lower == "mwe") { return DEP_MWE; }
  if (lower == "neg") { return DEP_NEG; }
  if (lower == "nn") { return DEP_NN; }
  if (lower == "npadvmod") { return DEP_NPADVMOD; }
  if (lower == "nsubj") { return DEP_NSUBJ; }
  if (lower == "nsubjpass") { return DEP_NSUBJPASS; }
  if (lower == "num") { return DEP_NUM; }
  if (lower == "number") { return DEP_NUMBER; }
  if (lower == "parataxis") { return DEP_PARATAXIS; }
  if (lower == "pcomp") { return DEP_PCOMP; }
  if (lower == "pobj") { return DEP_POBJ; }
  if (lower == "poss") { return DEP_POSS; }
  if (lower == "posseive") { return DEP_POSSEIVE; }
  if (lower == "preconj") { return DEP_PRECONJ; }
  if (lower == "predet") { return DEP_PREDET; }
  if (lower == "prep") { return DEP_PREP; }
  if (lower == "prt") { return DEP_PRT; }
  if (lower == "punct") { return DEP_PUNCT; }
  if (lower == "quantmod") { return DEP_QUANTMOD; }
  if (lower == "rcmod") { return DEP_RCMOD; }
  if (lower == "root") { return DEP_ROOT; }
  if (lower == "tmod") { return DEP_TMOD; }
  if (lower == "vmod") { return DEP_VMOD; }
  if (lower == "xcomp") { return DEP_XCOMP; }
  return 0;
}
  
string dependencyGloss(const uint8_t& indexed) {
  if (indexed == DEP_ACOMP) { return "acomp"; }
  if (indexed == DEP_ADVCL) { return "advcl"; }
  if (indexed == DEP_ADVMOD) { return "advmod"; }
  if (indexed == DEP_AMOD) { return "amod"; }
  if (indexed == DEP_APPOS) { return "appos"; }
  if (indexed == DEP_AUX) { return "aux"; }
  if (indexed == DEP_AUXPASS) { return "auxpass"; }
  if (indexed == DEP_CC) { return "cc"; }
  if (indexed == DEP_CCOMP) { return "ccomp"; }
  if (indexed == DEP_CONJ) { return "conj"; }
  if (indexed == DEP_COP) { return "cop"; }
  if (indexed == DEP_CSUBJ) { return "csubj"; }
  if (indexed == DEP_CSUBJPASS) { return "csubjpass"; }
  if (indexed == DEP_DEP) { return "dep"; }
  if (indexed == DEP_DET) { return "det"; }
  if (indexed == DEP_DISCOURSE) { return "discourse"; }
  if (indexed == DEP_DOBJ) { return "dobj"; }
  if (indexed == DEP_EXPL) { return "expl"; }
  if (indexed == DEP_GOESWITH) { return "goeswith"; }
  if (indexed == DEP_IOBJ) { return "iobj"; }
  if (indexed == DEP_MARK) { return "mark"; }
  if (indexed == DEP_MWE) { return "mwe"; }
  if (indexed == DEP_NEG) { return "neg"; }
  if (indexed == DEP_NN) { return "nn"; }
  if (indexed == DEP_NPADVMOD) { return "npadvmod"; }
  if (indexed == DEP_NSUBJ) { return "nsubj"; }
  if (indexed == DEP_NSUBJPASS) { return "nsubjpass"; }
  if (indexed == DEP_NUM) { return "num"; }
  if (indexed == DEP_NUMBER) { return "number"; }
  if (indexed == DEP_PARATAXIS) { return "parataxis"; }
  if (indexed == DEP_PCOMP) { return "pcomp"; }
  if (indexed == DEP_POBJ) { return "pobj"; }
  if (indexed == DEP_POSS) { return "poss"; }
  if (indexed == DEP_POSSEIVE) { return "posseive"; }
  if (indexed == DEP_PRECONJ) { return "preconj"; }
  if (indexed == DEP_PREDET) { return "predet"; }
  if (indexed == DEP_PREP) { return "prep"; }
  if (indexed == DEP_PRT) { return "prt"; }
  if (indexed == DEP_PUNCT) { return "punct"; }
  if (indexed == DEP_QUANTMOD) { return "quantmod"; }
  if (indexed == DEP_RCMOD) { return "rcmod"; }
  if (indexed == DEP_ROOT) { return "root"; }
  if (indexed == DEP_TMOD) { return "tmod"; }
  if (indexed == DEP_VMOD) { return "vmod"; }
  if (indexed == DEP_XCOMP) { return "xcomp"; }
  return "???";
}


