#include <limits.h>
#include <bitset>

#include "gtest/gtest.h"

#include "Types.h"

using namespace std;

TEST(TypesTest, TaggedWord) {
  for (int monotonicity = 0; monotonicity < 4; ++monotonicity) {
    for (int sense = 0; sense < 32; ++sense) {
      for (int word = 0; word < 1000; ++word) {
        tagged_word w = getTaggedWord(word, sense, monotonicity);
        ASSERT_EQ(monotonicity, w.monotonicity);
        ASSERT_EQ(sense, w.sense);
        ASSERT_EQ(word, w.word);
      }
      for (int word = (0x1 << VOCABULARY_ENTROPY) - 1; word >= (0x1 << VOCABULARY_ENTROPY) - 1000; --word) {
        tagged_word w = getTaggedWord(word, sense, monotonicity);
        ASSERT_EQ(monotonicity, w.monotonicity);
        ASSERT_EQ(sense, w.sense);
        ASSERT_EQ(word, w.word);
      }
    }
  }
}

TEST(TypesTest, PackedTypeSizes) {
  EXPECT_EQ(4, sizeof(tagged_word));
}
