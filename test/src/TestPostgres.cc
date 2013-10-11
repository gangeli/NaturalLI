#include <limits.h>

#include "gtest/gtest.h"
#include "Config.h"
#include "Postgres.h"


// Ensure that we can issue a query
TEST(PostgresTest, CanIssueQuery) {
  PGIterator results = PGIterator("SELECT 1;");
}

// Ensure that our simple query has results returned
TEST(PostgresTest, SELECT_ONE_HasResults) {
  PGIterator results = PGIterator("SELECT 1;");
  EXPECT_TRUE(results.hasNext());
}

// Ensure that our simple query has the right result returned
TEST(PostgresTest, SELECT_ONE_CanGetResults) {
  PGIterator results = PGIterator("SELECT 1;");
  ASSERT_TRUE(results.hasNext());
  EXPECT_EQ(string("1"), string(results.next()[0]));
  EXPECT_FALSE(results.hasNext());
}

// Ensure proper semantics for reading a table
TEST(PostgresTest, EdgeTypeIndexerHasCorrectEntries) {
  for (uint32_t skip = 1; skip < 4; ++skip) {
    PGIterator results = PGIterator(
        "SELECT * FROM edge_type_indexer;",
        skip);
    // 0: wordnet_up
    ASSERT_TRUE(results.hasNext());
    PGRow term = results.next();
    EXPECT_EQ(string("0"), string(term[0]));
    EXPECT_EQ(string("wordnet_up"), string(term[1]));
    // 1: wordnet_up
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("1"), string(term[0]));
    EXPECT_EQ(string("wordnet_down"), string(term[1]));
    // 2: wordnet_noun_antonym
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("2"), string(term[0]));
    EXPECT_EQ(string("wordnet_noun_antonym"), string(term[1]));
    // Iterate over the rest
    for (int i = 3; i < 10; ++i) {
      ASSERT_TRUE(results.hasNext());
      results.next();
    }
    while (results.hasNext()) {
      results.next();
    }
    EXPECT_FALSE(results.hasNext());
  }
}

// Test gloss
TEST(PostgresTest, WordIndexerHasCorrectEntries) {
  for (uint32_t skip = 1; skip < 4; ++skip) {
    PGIterator results = PGIterator(
        "SELECT * FROM word_indexer ORDER BY index ASC LIMIT 10;",
        skip);
    // 0: wordnet_up
    ASSERT_TRUE(results.hasNext());
    PGRow term = results.next();
    EXPECT_EQ(string("0"), string(term[0]));
    EXPECT_EQ(string("cephalopod"), string(term[1]));
    // 1: wordnet_up
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("1"), string(term[0]));
    EXPECT_EQ(string("mollusk"), string(term[1]));
    // 2: wordnet_noun_antonym
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("2"), string(term[0]));
    EXPECT_EQ(string("mollusc"), string(term[1]));
  }
}

TEST(PostgresTest, CountQuery) {
  EXPECT_EQ(18858251,
    atoi(PGIterator("SELECT COUNT(*) FROM word_indexer;").next()[0]));

}
