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

package android.app.appsearch.testutil;

import android.app.appsearch.annotation.CanIgnoreReturnValue;

import androidx.annotation.NonNull;

/**
 * An interface which exposes environment specific methods for AppSearch tests.
 *
 * @hide
 */
public interface AppSearchTestEnvironment {

    /** Returns the package name of the process that runs the built-in indexers. */
    @NonNull
    String getIndexerPackageName();

    /** Returns the root folder where test apps can be installed. */
    @NonNull
    String getTestAppRootFolder();

    /**
     * Runs an ADB shell command. This can be used to install/uninstall a test app.
     *
     * @return The output of the shell command.
     */
    @NonNull
    @CanIgnoreReturnValue
    String runShellCommand(@NonNull String command);
}
