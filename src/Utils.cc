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
    string sense = to_string((long long int) fact[i].sense);
    gloss = gloss + (gloss == "" ? "" : " ") + marker + graph.gloss(fact[i]) + "_" + sense;
  }
  return gloss;
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
    case HYPERNYM                     : return "HYPERNYM";
    case HYPONYM                      : return "HYPONYM";
    case ANTONYM                      : return "WORDNET_NOUN_ANTONYM";
    case SYNONYM                      : return "WORDNET_NOUN_SYNONYM";
    case ANGLE_NN                     : return "ANGLE_NN";
    case SENSE_REMOVE                 : return "SENSE_REMOVE";
    case SENSE_ADD                    : return "SENSE_ADD";
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
