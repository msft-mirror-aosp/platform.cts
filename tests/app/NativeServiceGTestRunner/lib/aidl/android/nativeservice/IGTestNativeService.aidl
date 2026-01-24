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

package android.nativeservice;

import android.os.ParcelFileDescriptor;

/**
 * Interface for running GTests within a native service.
 */
interface IGTestNativeService {
    /**
     * Runs a GTest suite.
     * @param pfd A ParcelFileDescriptor to write the test results to.
     * @param args The arguments to pass to the GTest suite.
     */
    void runGTest(in ParcelFileDescriptor pfd, in List<String> args);
}
