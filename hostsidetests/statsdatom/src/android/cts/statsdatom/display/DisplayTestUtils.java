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

package android.cts.statsdatom.display;


import java.util.concurrent.TimeUnit;

public class DisplayTestUtils {

    public static final String DISPLAY_TEST_PKG = "android.display.cts";
    public static final String DISPLAY_TEST_APK = "CtsDisplayTestCases.apk";
    public static final String TEST_CLASS_DISPLAY_EVENT = "android.display.cts.DisplayEventTest";
    public static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
}
