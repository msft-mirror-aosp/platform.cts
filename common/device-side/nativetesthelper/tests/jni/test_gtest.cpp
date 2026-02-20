/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <gtest/gtest.h>

TEST(NativeTestHelperTest, TrueIsTrue) {
    EXPECT_TRUE(true);
}

TEST(NativeTestHelperTest, FalseIsFalse) {
    EXPECT_FALSE(false);
}

TEST(NativeTestHelperTest, SkippitySkipSkip) {
    GTEST_SKIP();
}

TEST(NativeTestHelperTest, SingleFailure) {
    ASSERT_EQ("42", "The Answer");
}

TEST(NativeTestHelperTest, MultipleFailures) {
    ADD_FAILURE() << "First failure";
    ADD_FAILURE() << "Second failure";
    EXPECT_EQ(1, 2);
}