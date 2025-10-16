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

package android.server.wm.app;

/**
 * Application of WM test app. This must NOT be used for the test itself as it will crash the test
 * app when it is not responsive.
 */
public class CrashUnresponsiveAppTestApplication extends TestApplication {

    private static final int SIGABRT = 6;

    @Override
    int signalToSendOnUnresponsive() {
        // Crash the test app to dump all threads to tombstone as '/data/tombstones/tombstone_*'
        return SIGABRT;
    }
}
