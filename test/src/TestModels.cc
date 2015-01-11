#include <limits.h>

#include "gtest/gtest.h"

#include "Models.h"

using namespace std;

TEST(ModelsTest, DependencyGloss) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_NE("???", dependencyGloss(i));
  }
  EXPECT_EQ("???", dependencyGloss(NUM_DEPENDENCY_LABELS));
}

TEST(ModelsTest, DependencyIndex) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_EQ(i, indexDependency(dependencyGloss(i)));
  }
  EXPECT_EQ(0, indexDependency(dependencyGloss(NUM_DEPENDENCY_LABELS)));
}
