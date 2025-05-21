/*
 * Copyright 2025 The Android Open Source Project
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

#ifndef MEGA_COMMON_JAVANATIVEFLOATFIFO_H
#define MEGA_COMMON_JAVANATIVEFLOATFIFO_H

#include <atomic>
#include <memory>
#include <stdint.h>
#include <sys/sysinfo.h>

#include "oboe/FifoBuffer.h"

class JavaNativeFloatFifo : public oboe::FifoBuffer {
public:
    JavaNativeFloatFifo(uint8_t *dataStorageAddress,
                        uint32_t capacityInFrames);

    int32_t getAvailableToWrite() {
        return (int32_t)getBufferCapacityInFrames() - getFullFramesAvailable();
    }

private:
    std::atomic<uint64_t>  mReadCounter{0};
    std::atomic<uint64_t>  mWriteCounter{0};
};

#endif //MEGA_COMMON_JAVANATIVEFLOATFIFO_H
