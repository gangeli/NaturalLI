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
           toString(graph, searchType, path->source(searchType));
  }
}
