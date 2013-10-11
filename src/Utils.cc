#include "Utils.h"

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

string toString(const Graph* graph, const vector<word> fact) {
  string gloss = "";
  for (vector<word>::const_iterator it = fact.begin(); 
       it != fact.end();
       ++it) {
    gloss = gloss + (gloss == "" ? "" : " ") + graph->gloss(*it);
  }
  return gloss;
}

string toString(const Graph* graph, const Path* path) {
  if (path == NULL) {
    return "<start>";
  } else {
    return toString(graph, path->fact) +
           "; from\n  " +
           toString(graph, path->source());
  }
}
