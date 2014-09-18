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

std::string toString(const Graph& graph, const Tree& tree, const SynPath& path) {
  string str = "";
  str += std::to_string(path.factHash()) + ": ";
  for (uint8_t i = 0; i < tree.length; ++i) {
    if (i == path.tokenIndex()) {
      str += "*" + string(graph.gloss(path.token())) + " ";
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
      function = FUNCTION_INDEPENDENCE;
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
  if (lower == "acomp") { return 1; }
  if (lower == "advcl") { return 2; }
  if (lower == "advmod") { return 3; }
  if (lower == "amod") { return 4; }
  if (lower == "appos") { return 5; }
  if (lower == "aux") { return 6; }
  if (lower == "auxpass") { return 7; }
  if (lower == "cc") { return 8; }
  if (lower == "ccomp") { return 9; }
  if (lower == "conj") { return 10; }
  if (lower == "cop") { return 11; }
  if (lower == "csubj") { return 12; }
  if (lower == "csubjpass") { return 13; }
  if (lower == "dep") { return 14; }
  if (lower == "det") { return 15; }
  if (lower == "discourse") { return 16; }
  if (lower == "dobj") { return 17; }
  if (lower == "expl") { return 18; }
  if (lower == "goeswith") { return 19; }
  if (lower == "iobj") { return 20; }
  if (lower == "mark") { return 21; }
  if (lower == "mwe") { return 22; }
  if (lower == "neg") { return 23; }
  if (lower == "nn") { return 24; }
  if (lower == "npadvmod") { return 25; }
  if (lower == "nsubj") { return 26; }
  if (lower == "nsubjpass") { return 27; }
  if (lower == "num") { return 28; }
  if (lower == "number") { return 29; }
  if (lower == "parataxis") { return 30; }
  if (lower == "pcomp") { return 31; }
  if (lower == "pobj") { return 32; }
  if (lower == "poss") { return 33; }
  if (lower == "posseive") { return 34; }
  if (lower == "preconj") { return 35; }
  if (lower == "predet") { return 36; }
  if (lower == "prep") { return 37; }
  if (lower == "prt") { return 38; }
  if (lower == "punct") { return 39; }
  if (lower == "quantmod") { return 40; }
  if (lower == "rcmod") { return 41; }
  if (lower == "root") { return 42; }
  if (lower == "tmod") { return 43; }
  if (lower == "vmod") { return 44; }
  if (lower == "xcomp") { return 45; }
  return 0;
}
  
string dependencyGloss(const uint8_t& indexed) {
  if (indexed == 1) { return "acomp"; }
  if (indexed == 2) { return "advcl"; }
  if (indexed == 3) { return "advmod"; }
  if (indexed == 4) { return "amod"; }
  if (indexed == 5) { return "appos"; }
  if (indexed == 6) { return "aux"; }
  if (indexed == 7) { return "auxpass"; }
  if (indexed == 8) { return "cc"; }
  if (indexed == 9) { return "ccomp"; }
  if (indexed == 10) { return "conj"; }
  if (indexed == 11) { return "cop"; }
  if (indexed == 12) { return "csubj"; }
  if (indexed == 13) { return "csubjpass"; }
  if (indexed == 14) { return "dep"; }
  if (indexed == 15) { return "det"; }
  if (indexed == 16) { return "discourse"; }
  if (indexed == 17) { return "dobj"; }
  if (indexed == 18) { return "expl"; }
  if (indexed == 19) { return "goeswith"; }
  if (indexed == 20) { return "iobj"; }
  if (indexed == 21) { return "mark"; }
  if (indexed == 22) { return "mwe"; }
  if (indexed == 23) { return "neg"; }
  if (indexed == 24) { return "nn"; }
  if (indexed == 25) { return "npadvmod"; }
  if (indexed == 26) { return "nsubj"; }
  if (indexed == 27) { return "nsubjpass"; }
  if (indexed == 28) { return "num"; }
  if (indexed == 29) { return "number"; }
  if (indexed == 30) { return "parataxis"; }
  if (indexed == 31) { return "pcomp"; }
  if (indexed == 32) { return "pobj"; }
  if (indexed == 33) { return "poss"; }
  if (indexed == 34) { return "posseive"; }
  if (indexed == 35) { return "preconj"; }
  if (indexed == 36) { return "predet"; }
  if (indexed == 37) { return "prep"; }
  if (indexed == 38) { return "prt"; }
  if (indexed == 39) { return "punct"; }
  if (indexed == 40) { return "quantmod"; }
  if (indexed == 41) { return "rcmod"; }
  if (indexed == 42) { return "root"; }
  if (indexed == 43) { return "tmod"; }
  if (indexed == 44) { return "vmod"; }
  if (indexed == 45) { return "xcomp"; }
  return "???";
}


