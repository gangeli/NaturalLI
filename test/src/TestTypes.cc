#include <limits.h>
#include <bitset>

#include "gtest/gtest.h"

#include "Types.h"

using namespace std;

TEST_F(TypesTest, TaggedWord) {
  for (int monotonicity = 0; monotonicity < 4; ++monotonicity) {
    for (int sense = 0; sense < 32; ++sense) {
      for (int word = 0; word < 1000; ++word) {
        tagged_word w = getTaggedWord(word, sense, monotonicity);
        ASSERT_EQ(monotonicity, getMonotonicity(w));
        ASSERT_EQ(sense, getSense(w));
        ASSERT_EQ(word, getWord(w));
      }
      for (int word = (0x1 << 25) - 1; word >= (0x1 << 25) - 1000; --word) {
        tagged_word w = getTaggedWord(word, sense, monotonicity);
        ASSERT_EQ(monotonicity, getMonotonicity(w));
        ASSERT_EQ(sense, getSense(w));
        ASSERT_EQ(word, getWord(w));
      }
    }
  }
}
