#include <limits.h>

#include <config.h>
#include "gtest/gtest.h"
#include "Postgres.h"
#include "Types.h"

using namespace std;

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
        "SELECT * FROM edge_type;",
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
  for (uint32_t skip = 1; skip < 5; ++skip) {
    PGIterator results = PGIterator(
        "SELECT * FROM word ORDER BY index ASC LIMIT 100;",
        skip);
    // 0: none
    ASSERT_TRUE(results.hasNext());
    PGRow term = results.next();
    EXPECT_EQ(string("0"), string(term[0]));
    EXPECT_EQ(string("__none__"), string(term[1]));
    // 1: unk
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("1"), string(term[0]));
    EXPECT_EQ(string("__unk__"), string(term[1]));
    for (uint32_t i = 0; i < 10; ++i) { 
      ASSERT_TRUE(results.hasNext());
      term = results.next();
    }
    // 12: 'hood
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("12"), string(term[0]));
    EXPECT_EQ(string("` hood"), string(term[1]));
    // 13: vicinity
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("13"), string(term[0]));
    EXPECT_EQ(string("'s gravenhage"), string(term[1]));
    // 14: locality
    ASSERT_TRUE(results.hasNext());
    term = results.next();
    EXPECT_EQ(string("14"), string(term[0]));
    EXPECT_EQ(string("` tween"), string(term[1]));
  }
}

inline void checkWordIndex(const string& gloss, uint32_t expected) {
  char buffer[128];
  sprintf(buffer, "SELECT index FROM word WHERE gloss='%s';",
          gloss.c_str());
  PGIterator results = PGIterator(buffer);
  ASSERT_TRUE(results.hasNext());
  PGRow term = results.next();
  EXPECT_EQ(expected, atoi(term[0]));
}

// Test gloss
TEST(PostgresTest, WordIndexerMatchesDefineTags) {
  checkWordIndex("lemur",  LEMUR.word);
  checkWordIndex("animal", ANIMAL.word);
  checkWordIndex("potto",  POTTO.word);
  checkWordIndex("cat",    CAT.word);
  checkWordIndex("have",   HAVE.word);
  checkWordIndex("tail",   TAIL.word);
}

TEST(PostgresTest, CountQuery) {
  EXPECT_EQ(NUM_EDGE_TYPES,
    atoi(PGIterator("SELECT COUNT(*) FROM edge_type;").next()[0]));

}
