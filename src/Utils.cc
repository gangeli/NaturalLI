#include "Utils.h"

#include <string>

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
    gloss = gloss + (gloss == "" ? "" : " ") + marker + graph.gloss(fact[i]);
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

inference_function edge2function(const edge_type& type) {
  inference_function function;
  switch (type) {
    case WORDNET_UP: 
    case FREEBASE_UP: 
    case DEL_NOUN:
    case DEL_VERB:
    case DEL_ADJ:
    case DEL_OTHER:
      function = FUNCTION_FORWARD_ENTAILMENT;
      break;
    case WORDNET_DOWN: 
    case FREEBASE_DOWN: 
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
    case QUANTIFIER_NEGATE:
    case ADD_NEGATION:
    case DEL_NEGATION:
      function = FUNCTION_NEGATION;
      break;
    // The weird cases...
    case ADD_EXISTENTIAL:
    case ADD_UNIVERSAL:
    case ADD_QUANTIFIER_OTHER:
    case DEL_EXISTENTIAL:
    case DEL_UNIVERSAL:
    case DEL_QUANTIFIER_OTHER:
    // I think something special actually happens with these
    case QUANTIFIER_WEAKEN:
    case QUANTIFIER_STRENGTHEN:
    case NULL_EDGE_TYPE:
      function = FUNCTION_EQUIVALENT;
      break;
    default:
      printf("Unknown edge type: %u", type);
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
