/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "AHardwareBuffer_test"
//#define LOG_NDEBUG 0

#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <android/hardware_buffer.h>
#include <gtest/gtest.h>

#define BAD_VALUE -EINVAL
#define NO_ERROR 0

static ::testing::AssertionResult BuildHexFailureMessage(uint64_t expected,
        uint64_t actual, const char* type) {
    std::ostringstream ss;
    ss << type << " 0x" << std::hex << actual
            << " does not match expected " << type << " 0x" << std::hex
            << expected;
    return ::testing::AssertionFailure() << ss.str();
}

static ::testing::AssertionResult BuildFailureMessage(uint32_t expected,
        uint32_t actual, const char* type) {
    return ::testing::AssertionFailure() << "Buffer " << type << " do not match"
            << ": " << actual << " != " << expected;
}

static ::testing::AssertionResult CheckAHardwareBufferMatchesDesc(
        AHardwareBuffer* abuffer, const AHardwareBuffer_Desc& desc) {
    AHardwareBuffer_Desc bufferDesc;
    AHardwareBuffer_describe(abuffer, &bufferDesc);
    if (bufferDesc.width != desc.width)
        return BuildFailureMessage(desc.width, bufferDesc.width, "widths");
    if (bufferDesc.height != desc.height)
        return BuildFailureMessage(desc.height, bufferDesc.height, "heights");
    if (bufferDesc.layers != desc.layers)
        return BuildFailureMessage(desc.layers, bufferDesc.layers, "layers");
    if (bufferDesc.usage != desc.usage)
        return BuildHexFailureMessage(desc.usage, bufferDesc.usage, "usage");
    if (bufferDesc.format != desc.format)
        return BuildFailureMessage(desc.format, bufferDesc.format, "formats");
    return ::testing::AssertionSuccess();
}

// Test that passing in NULL values to allocate works as expected.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_FailsWithNullInput) {
    AHardwareBuffer* buffer;
    AHardwareBuffer_Desc desc;

    memset(&desc, 0, sizeof(AHardwareBuffer_Desc));

    int res = AHardwareBuffer_allocate(&desc, NULL);
    EXPECT_EQ(BAD_VALUE, res);
    res = AHardwareBuffer_allocate(NULL, &buffer);
    EXPECT_EQ(BAD_VALUE, res);
    res = AHardwareBuffer_allocate(NULL, NULL);
    EXPECT_EQ(BAD_VALUE, res);
}

// Test that passing in NULL values to allocate works as expected.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_BlobFormatRequiresHeight1) {
    AHardwareBuffer* buffer;
    AHardwareBuffer_Desc desc = {};

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_RARELY;
    desc.format = AHARDWAREBUFFER_FORMAT_BLOB;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(BAD_VALUE, res);

    desc.height = 1;
    res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
    buffer = NULL;
}

// Test that allocate can create an AHardwareBuffer correctly.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
    buffer = NULL;

    desc.width = 4;
    desc.height = 12;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.format = AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM;
    res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
}

TEST(AHardwareBufferTest, AHardwareBuffer_describe_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);

    AHardwareBuffer_Desc expected_desc;
    memset(&expected_desc, 0, sizeof(AHardwareBuffer_Desc));
    AHardwareBuffer_describe(NULL, &desc);
    EXPECT_EQ(0U, expected_desc.width);
    AHardwareBuffer_describe(buffer, NULL);
    EXPECT_EQ(0U, expected_desc.width);
    AHardwareBuffer_describe(buffer, &desc);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));

    AHardwareBuffer_release(buffer);
}

struct ClientData {
    int fd;
    AHardwareBuffer* buffer;
    ClientData(int fd_in, AHardwareBuffer* buffer_in)
            : fd(fd_in), buffer(buffer_in) {}
};

static void* clientFunction(void* data) {
    ClientData* pdata = reinterpret_cast<ClientData*>(data);
    int err = AHardwareBuffer_sendHandleToUnixSocket(pdata->buffer, pdata->fd);
    EXPECT_EQ(NO_ERROR, err);
    close(pdata->fd);
    return 0;
}

TEST(AHardwareBufferTest, AHardwareBuffer_SendAndRecv_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    // Test that an invalid buffer fails.
    int err = AHardwareBuffer_sendHandleToUnixSocket(NULL, 0);
    EXPECT_EQ(BAD_VALUE, err);
    err = 0;
    err = AHardwareBuffer_sendHandleToUnixSocket(buffer, 0);
    EXPECT_EQ(BAD_VALUE, err);

    // Allocate the buffer.
    err = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, err);

    int fds[2];
    err = socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, fds);

    // Launch a client that will send the buffer back.
    ClientData data(fds[1], buffer);
    pthread_t thread;
    ASSERT_EQ(0, pthread_create(&thread, NULL, clientFunction, &data));

    // Receive the buffer.
    err = AHardwareBuffer_recvHandleFromUnixSocket(fds[0], NULL);
    EXPECT_EQ(BAD_VALUE, err);

    AHardwareBuffer* received = NULL;
    err = AHardwareBuffer_recvHandleFromUnixSocket(fds[0], &received);
    EXPECT_EQ(NO_ERROR, err);
    ASSERT_TRUE(received != NULL);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(received, desc));

    void* ret_val;
    ASSERT_EQ(0, pthread_join(thread, &ret_val));
    ASSERT_EQ(NULL, ret_val);
    close(fds[0]);

    AHardwareBuffer_release(buffer);
    AHardwareBuffer_release(received);
}

TEST(AHardwareBufferTest, AHardwareBuffer_Lock_and_Unlock_Succeed) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_CPU_READ_RARELY;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    // Test that an invalid buffer fails.
    int err = AHardwareBuffer_lock(NULL, 0, -1, NULL, NULL);
    EXPECT_EQ(BAD_VALUE, err);
    err = 0;

    // Allocate the buffer.
    err = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, err);
    void* bufferData = NULL;
    err = AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY, -1,
          NULL, &bufferData);
    EXPECT_EQ(NO_ERROR, err);
    EXPECT_TRUE(bufferData != NULL);
    int32_t fence = -1;
    err = AHardwareBuffer_unlock(buffer, &fence);

    AHardwareBuffer_release(buffer);
}
