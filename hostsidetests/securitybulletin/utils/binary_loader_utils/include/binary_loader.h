/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef BINARY_LOADER_H
#define BINARY_LOADER_H

#include <dlfcn.h>
#include <link.h>
#include <string>

class BinaryLoader {
public:
    BinaryLoader(const std::string absoluteBinPath);
    ~BinaryLoader();
    uintptr_t getFunctionAddress(uintptr_t functionOffset);

private:
    const char* binPath;
    uintptr_t baseAddress;
    void* binHandle;
    static int callback(struct dl_phdr_info* info, size_t /* size */, void* data);
};

#endif // BINARY_LOADER_H
