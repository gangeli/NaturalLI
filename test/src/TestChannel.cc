#include <limits.h>
#include <config.h>
#include <string>

#include "gtest/gtest.h"
#include "Channel.h"
#include "SynSearch.h"

using namespace moodycamel;
using namespace std;


// ----------------------------------------------
// Channel
// ----------------------------------------------

TEST(ChannelTest, CreationAndSize) {
  Channel<int> queue(100);
  EXPECT_EQ(88, sizeof(queue));
}

TEST(ChannelTest, PushPoll) {
  ScoredSearchNode path, out;
  Channel<ScoredSearchNode> c(1024);
  EXPECT_FALSE(c.poll(&out));
  EXPECT_TRUE(c.push(path));
  EXPECT_TRUE(c.poll(&out));
}

TEST(ChannelTest, Capacity) {
  ScoredSearchNode path, out;
  Channel<ScoredSearchNode> c(16);
  for (uint8_t i = 0; i < 31; ++i) {
    EXPECT_TRUE(c.push(path));
  }
  ASSERT_FALSE(c.push(path));
  for (uint8_t i = 0; i < 31; ++i) {
    EXPECT_TRUE(c.poll(&out));
  }
  ASSERT_FALSE(c.poll(&out));
}

TEST(ChannelTest, RandomAccessPattern) {
  srand (42);
  ScoredSearchNode path, out;
  Channel<ScoredSearchNode> c(16);
  uint8_t size = 0;
  for (uint32_t i = 0; i < 10000; ++i) {
    if (size == 31) {
      ASSERT_FALSE(c.push(path));
      ASSERT_TRUE(c.poll(&out));
      size -= 1;
    } else if (size == 0) {
      ASSERT_FALSE(c.poll(&out));
      ASSERT_TRUE(c.push(path));
      size += 1;
    } else if (rand() % 2) {
      ASSERT_TRUE(c.push(path));
      size += 1;
    } else {
      ASSERT_TRUE(c.poll(&out));
      size -= 1;
    }
  }
}

TEST(ChannelTest, CorrectValues) {
  // (variables)
  Tree a(string("20852\t2\tnsubj\n") +
         string("60042\t0\troot\n") +
         string("125248\t2\tdobj"));
  Tree b(string("73918\t2\tnsubj\n") +
         string("60042\t0\troot\n") +
         string("125248\t2\tdobj"));
  ScoredSearchNode pathA(SearchNode(a), 0.0f);
  ScoredSearchNode pathB(SearchNode(b), 0.0f);
  ScoredSearchNode out;
  Channel<ScoredSearchNode> c(1024);
  // (push)
  ASSERT_TRUE(c.push(pathA));
  ASSERT_TRUE(c.push(pathB));
  // (poll)
  ASSERT_TRUE(c.poll(&out));
  EXPECT_EQ(pathA.node.factHash(), out.node.factHash());
  ASSERT_TRUE(c.poll(&out));
  EXPECT_EQ(pathB.node.factHash(), out.node.factHash());
  // (nothing at the end)
  EXPECT_FALSE(c.poll(&out));
  EXPECT_EQ(pathB.node.factHash(), out.node.factHash());  // out not written
}
