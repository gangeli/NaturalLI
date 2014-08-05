#include <limits.h>

#include "gtest/gtest.h"
#include "Map.h"

class MapTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    smallMap = new HashIntMap(5);
  }

  virtual void TearDown() {
    delete smallMap;
  }
  
  HashIntMap* smallMap;
};

TEST_F(MapTest, MapEntrySize) {
  EXPECT_EQ(8, sizeof(map_entry));
}

TEST_F(MapTest, Creation) { }

TEST_F(MapTest, Buckets) {
  EXPECT_EQ(5, smallMap->buckets());
}

TEST_F(MapTest, Size) {
  EXPECT_EQ(0, smallMap->size());
  smallMap->put(0, 42, 1001);
  EXPECT_EQ(1, smallMap->size());
}

TEST_F(MapTest, Sum) {
  EXPECT_EQ(0, smallMap->sum());
  smallMap->put(0, 42, 1001);
  EXPECT_EQ(1001, smallMap->sum());
  smallMap->put(3, 44, 1002);
  EXPECT_EQ(2003, smallMap->sum());
}

TEST_F(MapTest, Map) {
  // Initialize
  uint64_t value;
  smallMap->put(0, 42, 1001);
  smallMap->put(3, 44, 1002);
  // Check gets
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  EXPECT_TRUE(smallMap->get(3, 44, &value));
  EXPECT_EQ(1002, value);
  // Map
  auto fn = [](uint64_t count) -> uint64_t { 
    return count + 5;
  };
  smallMap->mapValues(fn);
  // Re-check gets
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1006, value);
  EXPECT_TRUE(smallMap->get(3, 44, &value));
  EXPECT_EQ(1007, value);
}

TEST_F(MapTest, SimplePutGet) {
  smallMap->put(0, 42, 1001);
  uint64_t value;
  // (should contain right value)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  // (should not contain wrong value)
  EXPECT_FALSE(smallMap->get(1, 43, &value));
}

TEST_F(MapTest, ZeroPut) {
  uint64_t value;
  EXPECT_FALSE(smallMap->get(0, 42, &value));
  smallMap->put(0, 42, 0);
  // (should contain right value)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(0, value);
}

TEST_F(MapTest, Increment) {
  uint64_t value;
  EXPECT_FALSE(smallMap->get(0, 42, &value));
  smallMap->increment(0, 42, 5);
  // (should increment from zero)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(5, value);
  // (increment again)
  smallMap->increment(0, 42, 7);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(12, value);
}

TEST_F(MapTest, IncrementWithCap) {
  uint64_t value;
  EXPECT_FALSE(smallMap->get(0, 42, &value));
  smallMap->increment(0, 42, 5, 10);
  // (should increment from zero)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(5, value);
  // (increment again)
  smallMap->increment(0, 42, 7, 10);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(10, value);
}

TEST_F(MapTest, Overwrite) {
  smallMap->put(0, 42, 1001);
  uint64_t value;
  // Initial value
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  // Overwrite
  smallMap->put(0, 42, 4242);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(4242, value);
}

TEST_F(MapTest, Checksum) {
  smallMap->put(0, 42, 1001);
  uint64_t value;
  // (should contain right value)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  // (should not contain wrong value)
  EXPECT_FALSE(smallMap->get(0, 43, &value));
}

TEST_F(MapTest, FillMap) {
  smallMap->put(0, 42, 1001);
  smallMap->put(1, 43, 1002);
  smallMap->put(2, 44, 1003);
  smallMap->put(3, 45, 1004);
  uint64_t value;
  // (should contain right values)
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  EXPECT_TRUE(smallMap->get(1, 43, &value));
  EXPECT_EQ(1002, value);
  EXPECT_TRUE(smallMap->get(2, 44, &value));
  EXPECT_EQ(1003, value);
  EXPECT_TRUE(smallMap->get(3, 45, &value));
  EXPECT_EQ(1004, value);
  // (should not contain wrong value)
  EXPECT_FALSE(smallMap->get(4, 46, &value));
}

TEST_F(MapTest, FillMapModAround) {
  smallMap->put(1, 42, 1001);
  smallMap->put(2, 43, 1002);
  smallMap->put(3, 44, 1003);
  smallMap->put(4, 45, 1004);
  uint64_t value;
  // (should contain right values)
  EXPECT_TRUE(smallMap->get(1, 42, &value));
  EXPECT_EQ(1001, value);
  EXPECT_TRUE(smallMap->get(2, 43, &value));
  EXPECT_EQ(1002, value);
  EXPECT_TRUE(smallMap->get(3, 44, &value));
  EXPECT_EQ(1003, value);
  EXPECT_TRUE(smallMap->get(4, 45, &value));
  EXPECT_EQ(1004, value);
  // (this should loop around to bucket 0, and establish that it's false)
  EXPECT_FALSE(smallMap->get(1, 50, &value));
}

TEST_F(MapTest, CopyConstructor) {
  smallMap->put(0, 42, 1001);
  uint64_t value;
  // Initial value
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(1001, value);
  // Copy
  HashIntMap other(*smallMap);
  EXPECT_TRUE(other.get(0, 42, &value));
  EXPECT_EQ(1001, value);
  // Change
  smallMap->put(0, 42, 4242);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(4242, value);
  // Make sure it didn't change other
  EXPECT_TRUE(other.get(0, 42, &value));
  EXPECT_EQ(1001, value);
}

TEST_F(MapTest, LargeDatum) {
  uint64_t value;
  // (0x1 << 32) - 1
  uint64_t uint32Limit = (0x1l << 32) - 1;
  smallMap->put(0, 42, uint32Limit);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(uint32Limit, value);
  // (clear)
  smallMap->put(0, 42, 0);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(0, value);
  // (0x1 << 32)
  uint64_t uint32Overflow = (0x1l << 32);
  smallMap->put(0, 42, uint32Overflow);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(uint32Overflow, value);
  // (clear)
  smallMap->put(0, 42, 0);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(0, value);
  // (0x1 << 42) - 1
  uint64_t barelyOK = (0x1l << 38) - 1;
  smallMap->put(0, 42, barelyOK);
  EXPECT_TRUE(smallMap->get(0, 42, &value));
  EXPECT_EQ(barelyOK, value);
}

TEST_F(MapTest, LargeChecksum) {
  uint64_t value;
  // (0x1 << 22) - 1
  uint32_t checksumLimit = (0x1l << 25) - 1;
  smallMap->put(0, checksumLimit, 10);
  EXPECT_TRUE(smallMap->get(0, checksumLimit, &value));
  EXPECT_EQ(10, value);
  // (0x1 << 22) - 2
  // (should not be contained -- we never added it)
  uint32_t checksumUnderlimit = (0x1l << 25) - 2;
  EXPECT_FALSE(smallMap->get(0, checksumUnderlimit, &value));
  // (0x1 << 22)
  // (overflows the checksum -- false positive containment
  uint64_t checksumOverflow = ((0x1l << 25) - 1) | (0x1l << 26);
  EXPECT_TRUE(smallMap->get(0, checksumOverflow, &value));
  EXPECT_EQ(10, value);
}
