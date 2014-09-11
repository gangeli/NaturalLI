#include "SynSearch.h"

using namespace std;

inline syn_path_data mkSynPathData(
    const uint64_t&    factHash,
    const uint8_t&     index,
    const bool&        validity,
    const uint32_t&    deleteMask,
    const tagged_word& currentToken) {
  syn_path_data dat;
  dat.factHash = factHash;
  dat.index = index;
  dat.validity = validity;
  dat.deleteMask = deleteMask;
  dat.currentToken = currentToken;
  return dat;
}
  
SynPath::SynPath(const float& costIfTrue, const float& costIfFalse)
    : costIfTrue(costIfTrue), costIfFalse(costIfFalse), backpointer(NULL),
      data(mkSynPathData(42l, 255, false, 42, getTaggedWord(0, 0, 0))) { }

SynPath::SynPath()
    : costIfTrue(0.0), costIfFalse(0.0), backpointer(NULL),
      data(mkSynPathData(42l, 255, false, 42, getTaggedWord(0, 0, 0))) { }

SynPath::SynPath(const SynPath& from)
    : costIfTrue(from.costIfTrue), costIfFalse(from.costIfFalse),
      backpointer(from.backpointer),
      data(from.data) { }
