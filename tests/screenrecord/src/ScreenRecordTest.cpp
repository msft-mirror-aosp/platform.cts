/*
 * Copyright (C) 2025 The Android Open Source Project
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
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <string>

class ScreenRecordTest : public ::testing::Test {
protected:
    static constexpr const char* kVideoPath = "/data/local/tmp/test_screenrecord.mp4";

    void SetUp() override {
        ASSERT_NE(0, access(kVideoPath, F_OK))
                << "Precondition failed: Test file already exists at " << kVideoPath
                << ". The test environment must be clean before starting.";
    }

    void TearDown() override {
        EXPECT_EQ(0, remove(kVideoPath)) << "TearDown failed to clean up test file: " << kVideoPath;
    }
};

TEST_F(ScreenRecordTest, RecordsVideoAndChecksFileExists) {
    std::string command = std::string("screenrecord --time-limit 2 ") + kVideoPath;

    ASSERT_EQ(0, system(command.c_str()))
            << "The 'screenrecord' command failed to execute successfully.";

    ASSERT_EQ(0, access(kVideoPath, F_OK))
            << "The output video file was not created at: " << kVideoPath;
}

int main(int argc, char** argv) {
    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
