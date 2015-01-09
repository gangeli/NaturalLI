#include <limits.h>

#include <config.h>
#include "gtest/gtest.h"
#include "GZip.h"

using namespace std;

class GZipTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    iter = new GZIterator("../data/unittest_gzip.tab.gz");
    ASSERT_FALSE(iter == NULL);
  }

  virtual void TearDown() {
    delete iter;
  }

  GZIterator* iter;
};

TEST_F(GZipTest, HasNext) {
  ASSERT_TRUE(iter->hasNext());
}

TEST_F(GZipTest, CanRead) {
  ASSERT_TRUE(iter->hasNext());
  GZRow row = iter->next();
  ASSERT_EQ(2, row.size());
  EXPECT_EQ("1.1", string(row[0]));
  EXPECT_EQ("1.2", string(row[1]));
}

TEST_F(GZipTest, CanReadVariableLengthLines) {
  ASSERT_TRUE(iter->hasNext());
  GZRow row = iter->next();
  EXPECT_EQ(2, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  ASSERT_EQ(1, row.size());
  EXPECT_EQ("2.1", string(row[0]));
}

TEST_F(GZipTest, CanReadEmptyLines) {
  ASSERT_TRUE(iter->hasNext());
  GZRow row = iter->next();
  EXPECT_EQ(2, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  EXPECT_EQ(1, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  ASSERT_EQ(0, row.size());
}

TEST_F(GZipTest, CanFinishFile) {
  ASSERT_TRUE(iter->hasNext());
  GZRow row = iter->next();
  EXPECT_EQ(2, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  EXPECT_EQ(1, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  EXPECT_EQ(0, row.size());
  ASSERT_TRUE(iter->hasNext()); row = iter->next();
  ASSERT_EQ(3, row.size());
  EXPECT_EQ("4.1", string(row[0]));
  EXPECT_EQ("4.2", string(row[1]));
  EXPECT_EQ("4.3", string(row[2]));
}
