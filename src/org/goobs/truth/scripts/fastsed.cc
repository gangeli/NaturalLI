#include <stdio.h>
#include <stdlib.h>

#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <unordered_map>

#include <boost/algorithm/string.hpp>
#include <boost/tokenizer.hpp>

using namespace std;

unordered_map<string, uint32_t> PATTERNS;

struct entry {
  uint64_t index;
  uint64_t gloss_begin;
  uint64_t gloss_length;
};

uint32_t split(const std::string s, string* buffer) {
    boost::char_separator<char> sep(" ", "", boost::keep_empty_tokens);
    boost::tokenizer<boost::char_separator<char> > tok(s, sep);
    int i = 0;
    for(boost::tokenizer<boost::char_separator<char> >::iterator beg= tok.begin(); beg!=tok.end(); ++beg) {
      buffer[i] = *beg;
      i += 1;
    }
    return i;
}

int main(int argc, char** argv) {
  // Read Input
  if (argc < 2) {
    cout << "Usage: sed <expressions_file>" << endl;
    return 1;
  }
  char index_str[32];
  string line;
  vector<struct entry> entries;

  ifstream infile(argv[1]);
  line.reserve(1024);
  uint64_t buffer_index = 0;
  while (std::getline(infile, line)) {
    const char* cline = line.c_str();
    std::size_t slash_index = line.substr(0, line.length() - 3).find_last_of("/");
    std::size_t length = line.length();
    string gloss = line.substr(2, slash_index - 2);
    string index = line.substr(slash_index + 1, length - 2 - (slash_index + 1));
    PATTERNS[gloss] = atoi(index.c_str());
  }


  // Run sed
  string tokens[1024];
  string buffer;
  buffer.reserve(4096);
  while (getline(cin, line)) {
    // Variables we'll need
    uint32_t num_tokens = split(line, tokens);
    bool found[num_tokens];
    memset(found, false, num_tokens);
    uint32_t indices[num_tokens];

    // Index substrings of the original string
    for (int length = num_tokens; length > 0; --length) {
      for (int begin = 0; begin <= num_tokens - length; ++begin) {
        // Make sure we haven't consumed this span yet
        bool do_search = true;
        for (int k = begin; k < begin + length; ++k) {
          if (found[k]) { do_search = false; break; }
        }
        if (!do_search) { continue; }
        // Create key
        buffer.clear();
        buffer.append(tokens[begin]);
        for (int k = begin + 1; k < begin + length; ++k) {
          buffer.push_back(' ');
          buffer.append(tokens[k]);
        }
        // Check key
        unordered_map<string,uint32_t>::iterator it = PATTERNS.find(buffer);
        if (it != PATTERNS.end()) {
          uint32_t index = it->second;
          for (int k = begin; k < begin + length; ++k) {
            indices[k] = index;
            found[k] = true;
          }
        }
      }
    }
    
    // Collapse indices
    uint32_t last_index = 0;
    for (int k = 0; k < num_tokens; ++k) {
      if (!found[k]) {
        cout << tokens[k];
        last_index = 0;
      } else if (last_index != indices[k]) {
        cout << indices[k];
        last_index = indices[k];
      }
      if (k < num_tokens - 1) {
        cout << ' ';
      }
    }
    cout << endl;
  }
}
