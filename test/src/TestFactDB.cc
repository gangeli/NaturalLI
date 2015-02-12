#include <limits.h>
#include <bitset>

#include "gtest/gtest.h"

#include "FactDB.h"

using namespace std;
using namespace btree;

//
// Read From Memory
//
TEST(FactDBTest, ReadFromIntStream) {
//    return kb.find(value) != kb.end() || auxKB.find(value) != auxKB.end();
  uint64_t stream[] = { 42l, 43l, 44l };
  const btree_set<uint64_t>* kb = readKB(stream, 3);
  EXPECT_FALSE(kb->find(41l) != kb->end());
  EXPECT_TRUE(kb->find(42l) != kb->end());
  EXPECT_TRUE(kb->find(43l) != kb->end());
  EXPECT_TRUE(kb->find(44l) != kb->end());
  EXPECT_FALSE(kb->find(45l) != kb->end());
  delete kb;
}
