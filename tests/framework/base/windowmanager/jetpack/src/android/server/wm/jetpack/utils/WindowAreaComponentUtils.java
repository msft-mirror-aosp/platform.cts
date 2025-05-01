/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.jetpack.utils;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.io.IOException;

/** Utility methods used by the WindowAreaComponent tests. */
public class WindowAreaComponentUtils {

    /** Timeout to use on a physical device. */
    private static final int DEVICE_TIMEOUT = 2000;

    /** Timeout to use on a cuttlefish device. */
    private static final int CUTTLEFISH_TIMEOUT = 5000;

    /** Checks if the provided {@code condition} is true within the timeout period. */
    public static void waitAndAssert(PollingCheck.PollingCheckCondition condition) {
        try {
            waitFor(MediaUtils.onCuttlefish() ? CUTTLEFISH_TIMEOUT : DEVICE_TIMEOUT, condition);
        } catch (IOException e) {
            throw new AssertionError("Cuttlefish check failed", e);
        }
    }
}
