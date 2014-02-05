#include "Utils.h"

using namespace std;

const vector<word> lemursHaveTails() {
  vector<word> fact;
  fact.push_back(2479928);
  fact.push_back(3844);
  fact.push_back(14221);
  return fact;
}

const vector<word> animalsHaveTails() {
  vector<word> animalsHaveTails;
  animalsHaveTails.push_back(3701);
  animalsHaveTails.push_back(3844);
  animalsHaveTails.push_back(14221);
  return animalsHaveTails;
}

const vector<word> catsHaveTails() {
  vector<word> catsHaveTails;
  catsHaveTails.push_back(27970);
  catsHaveTails.push_back(3844);
  catsHaveTails.push_back(14221);
  return catsHaveTails;
}

string toString(Graph& graph, const word* fact, const uint8_t factLength) {
  string gloss = "";
  for (int i = 0; i < factLength; ++i) {
    gloss = gloss + (gloss == "" ? "" : " ") + graph.gloss(fact[i]);
  }
  return gloss;
}

string toString(Graph& graph, SearchType& searchType, const Path* path) {
  if (path == NULL) {
    return "<start>";
  } else {
    return toString(graph, path->fact, path->factLength) +
           "; from\n  " +
           toString(graph, searchType, path->parent);
  }
}

std::string toString(edge_type& edge) {
  switch (edge) {
    case WORDNET_UP                   : return "WORDNET_UP";
    case WORDNET_DOWN                 : return "WORDNET_DOWN";
    case WORDNET_NOUN_ANTONYM         : return "WORDNET_NOUN_ANTONYM";
    case WORDNET_VERB_ANTONYM         : return "WORDNET_VERB_ANTONYM";
    case WORDNET_ADJECTIVE_ANTONYM    : return "WORDNET_ADJECTIVE_ANTONYM";
    case WORDNET_ADVERB_ANTONYM       : return "WORDNET_ADVERB_ANTONYM";
    case WORDNET_ADJECTIVE_PERTAINYM  : return "WORDNET_ADJECTIVE_PERTAINYM";
    case WORDNET_ADVERB_PERTAINYM     : return "WORDNET_ADVERB_PERTAINYM";
    case WORDNET_ADJECTIVE_RELATED    : return "WORDNET_ADJECTIVE_RELATED";
    case ANGLE_NN                     : return "ANGLE_NN";
    case FREEBASE_UP                  : return "FREEBASE_UP";
    case FREEBASE_DOWN                : return "FREEBASE_DOWN";
    case MORPH_TO_LEMMA               : return "MORPH_TO_LEMMA";
    case MORPH_FROM_LEMMA             : return "MORPH_FROM_LEMMA";
    case MORPH_FUDGE_NUMBER           : return "MORPH_FUDGE_NUMBER";
    default: return "UNK_EDGE_TYPE";
  }
}
