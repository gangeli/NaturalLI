#include <limits.h>
#include <bitset>
#include <ctime>

#include "gtest/gtest.h"

#include "Types.h"
#include "Utils.h"

using namespace std;

class UtilsTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    graph = ReadMockGraph();
  }

  virtual void TearDown() {
    delete graph;
  }

  Graph* graph;
  std::vector<tagged_word> lemursHaveTails_;
};

TEST_F(UtilsTest, ToStringTree) {
  Tree tree(string("20852\t2\tnsubj\n") +
            string("60042\t0\troot\n") +
            string("125248\t2\tdobj"));
  EXPECT_EQ(string("[^]cat_0 [^]have_0 [^]tail_0"), toString(*graph, tree));
}

TEST_F(UtilsTest, ToStringTime) {
  EXPECT_EQ(string("0s"), toString(0l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1s"), toString(1l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("59s"), toString(59l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1m 0s"), toString(60l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("1m 1s"), toString(61l * CLOCKS_PER_SEC));
  EXPECT_EQ(string("2h 20m 3s"), toString((2l*60l*60l + 20l*60l + 3l) * CLOCKS_PER_SEC));
  EXPECT_EQ(string("10d 2h 20m 3s"), toString((10l*24l*60l*60l + 2l*60l*60l + 20l*60l + 3l) * CLOCKS_PER_SEC));
}

TEST_F(UtilsTest, DependencyGloss) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_NE("???", dependencyGloss(i));
  }
  EXPECT_EQ("???", dependencyGloss(NUM_DEPENDENCY_LABELS));
}

TEST_F(UtilsTest, DependencyIndex) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_EQ(i, indexDependency(dependencyGloss(i)));
  }
  EXPECT_EQ(0, indexDependency(dependencyGloss(NUM_DEPENDENCY_LABELS)));
}

TEST_F(UtilsTest, FastATOI) {
  EXPECT_EQ(0, fast_atoi("0"));
  EXPECT_EQ(1, fast_atoi("1"));
  EXPECT_EQ(104235, fast_atoi("104235"));
  for (uint8_t shift = 0; shift < 31; ++shift) {
    uint32_t i = (1l << shift);
    char buffer[32];
    snprintf(buffer, 31, "%u", i);
    EXPECT_EQ(i, fast_atoi(buffer));
    EXPECT_EQ(atoi(buffer), fast_atoi(buffer));
  }
}

TEST_F(UtilsTest, FastATOIPeculiarities) {
  // Parsing from PSQL is a bit of a pain, and it makes it easier
  // if we can just accept the final '}' and discard it in atoi()
  EXPECT_EQ(104235, fast_atoi("104235}"));
}

//TEST_F(UtilsTest, FastATOISpeed) {  // should pass without asserts
//  // Allocate buffers
//  const uint32_t count = 10000000;
//  char* strings[count];
//  for (uint32_t i = 0; i < count; ++i) {
//    strings[i] = (char*) malloc(16 * sizeof(char));
//    snprintf(strings[i], 15, "%u", i);
//  }
//
//  // Fast ATOI
//  time_t fastAtoiStart = clock();
//  for (uint32_t i = 0; i < count; ++i) {
//    EXPECT_EQ(i, fast_atoi(strings[i]));
//  }
//  time_t fastAtoiStop = clock();
//
//  // ATOI
//  time_t atoiStart = clock();
//  for (uint32_t i = 0; i < count; ++i) {
//    EXPECT_EQ(i, atoi(strings[i]));
//  }
//  time_t atoiStop = clock();
//
//  // Timing
//  printf("fast=%u; atoi=%u\n",
//    (fastAtoiStop - fastAtoiStart),
//    (atoiStop - atoiStart));
//  EXPECT_LE(fastAtoiStop - fastAtoiStart,
//            atoiStop - atoiStart);
//  
//  // Clean up
//  for (uint32_t i = 0; i < 10000; ++i) {
//    free(strings[i]);
//  }
//}
