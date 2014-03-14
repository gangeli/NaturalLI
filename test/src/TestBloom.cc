#include <limits.h>

#include "gtest/gtest.h"
#include "Bloom.h"


class BloomTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
  }

  virtual void TearDown() {
  }
  
  BloomFilter filter;

};

// Make sure we're doing something reasonable
TEST_F(BloomTest, Basics) {
  // Some random case
  uint32_t buffer[4]{ 0x1, 0x3, 0x345, 0x45656 };
  filter.add(buffer, 4);
  EXPECT_TRUE(filter.contains(buffer, 4));
  buffer[0] = 0x2;
  EXPECT_FALSE(filter.contains(buffer, 4));
}

// Some regression tests from the false positives set
TEST_F(BloomTest, Regressions) {
  uint32_t buffer[4]{ 0x2, 0x0, 0x0, 0x0 };
  filter.add(buffer, 4);
  EXPECT_TRUE(filter.contains(buffer, 4));
}

// Make sure we don't have too many overlaps
TEST_F(BloomTest, FewFalsePositives) {
  uint16_t incr = 32;  // set to 206 to test 1.8 billion facts
  uint32_t total = incr * incr * incr * incr;
  // Populate the filter
  uint32_t buffer[4]{ 0x0, 0x0, 0x0, 0x0 };
  for (uint16_t a = 0; a < incr; ++a) {
//    printf("Populate Pass; high bit: %u\n", a);
    buffer[0] = a;
    for (uint16_t b = 0; b < incr; ++b) {
      buffer[1] = b;
      for (uint16_t c = 0; c < incr; ++c) {
        buffer[2] = c;
        for (uint16_t d = 0; d < incr; ++d) {
          buffer[3] = d;
          filter.add(buffer, 4);
          if (!filter.contains(buffer, 4)) {
//            printf("a=%u b=%u c=%u d=%u\n", a, b, c, d);
          }
          ASSERT_TRUE(filter.contains(buffer, 4));  // crash early
        }
      }
    }
  }
  // Make sure we have no false negatives
  for (uint16_t a = 0; a < incr; ++a) {
//    printf("False Negative Pass; high bit: %u\n", a);
    buffer[0] = a;
    for (uint16_t b = 0; b < incr; ++b) {
      buffer[1] = b;
      for (uint16_t c = 0; c < incr; ++c) {
        buffer[2] = c;
        for (uint16_t d = 0; d < incr; ++d) {
          buffer[3] = d;
          EXPECT_TRUE(filter.contains(buffer, 4));
        }
      }
    }
  }
  // Make sure we have few false positives
  uint32_t falsePositives = 0;
  for (uint16_t a = incr; a < 2*incr; ++a) {
//    printf("False Positive Pass; high bit: %u\n", a);
    buffer[0] = a;
    for (uint16_t b = incr; b < 2*incr; ++b) {
      buffer[1] = b;
      for (uint16_t c = incr; c < 2*incr; ++c) {
        buffer[2] = c;
        for (uint16_t d = incr; d < 2*incr; ++d) {
          buffer[3] = d;
          if (filter.contains(buffer, 4)) { falsePositives += 1; }
        }
      }
    }
  }
  printf("False positives in Bloom filter: %u / %u\n", falsePositives, total);
  EXPECT_LT(falsePositives, 100);
}
