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

package android.nativeservice;

import android.nativeservice.INativeServiceListener;

// An interface defining methods provided by the native service used for test.
// This interface is not oneway to support synchronous calls that return values.
interface INativeServiceWrapper {
    // Register a listener to this service. Usually a listener corresponds to a service connection.
    oneway void registerListener(in INativeServiceListener listener);
    // Check if the preloaded library is loaded.
    boolean isLibraryMarkedPreloaded();
    // get the parent pid
    int getParentPid();
}
