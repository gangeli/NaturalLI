#include <limits.h>

#include "gtest/gtest.h"

#include "Models.h"

using namespace std;

//
// Dependency Gloss
//
TEST(ModelsTest, DependencyGloss) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_NE("???", dependencyGloss(i));
  }
  EXPECT_EQ("???", dependencyGloss(NUM_DEPENDENCY_LABELS));
}

//
// Dependency Index
//
TEST(ModelsTest, DependencyIndex) {
  for (int i = 0; i < NUM_DEPENDENCY_LABELS; ++i) {
    EXPECT_EQ(i, indexDependency(dependencyGloss(i)));
  }
  EXPECT_EQ(NUM_DEPENDENCY_LABELS, indexDependency(dependencyGloss(NUM_DEPENDENCY_LABELS)));
}

//
// Dependency Index Manual Sanity Check
//
TEST(ModelsTest, ParticlarDependencyIndices) {
  EXPECT_EQ(DEP_ACOMP, indexDependency("acomp"));
  EXPECT_EQ(DEP_NSUBJ, indexDependency("nsubj"));
  EXPECT_EQ(NUM_DEPENDENCY_LABELS, indexDependency("???"));
  EXPECT_EQ(NUM_DEPENDENCY_LABELS, indexDependency("foobarbaz"));
}
