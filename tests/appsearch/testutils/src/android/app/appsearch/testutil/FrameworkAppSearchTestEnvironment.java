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
// @exportToGMSCore:skipFile()
package android.app.appsearch.testutil;

import android.app.appsearch.annotation.CanIgnoreReturnValue;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.SystemUtil;

import java.util.Objects;

/**
 * Contains utility methods for the Framework implementation of AppSearch tests.
 *
 * @hide
 */
public class FrameworkAppSearchTestEnvironment implements AppSearchTestEnvironment {

    @Override
    @NonNull
    public String getTestAppRootFolder() {
        return "/data/local/tmp/cts/appsearch/";
    }

    @Override
    @NonNull
    @CanIgnoreReturnValue
    public String runShellCommand(@NonNull String command) {
        Objects.requireNonNull(command);
        return SystemUtil.runShellCommand(command);
    }
}
