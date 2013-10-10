#include <limits.h>

#include "gtest/gtest.h"
#include "TestUtils.h"
#include "FactDB.h"


class MockFactDBTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    facts = ReadMockFactDB();
  }

  virtual void TearDown() {
    delete facts;
  }

  FactDB* facts;
};

// Make sure the correct fact is in there
TEST_F(MockFactDBTest, HasCatsHaveTails) {
  EXPECT_TRUE(facts->contains(catsHaveTails()));
}

// Make sure the incorrect facts are not in there
TEST_F(MockFactDBTest, DoesNotHaveOtherFacts) {
  EXPECT_FALSE(facts->contains(lemursHaveTails()));
  EXPECT_FALSE(facts->contains(animalsHaveTails()));
}
