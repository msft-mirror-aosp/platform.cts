/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.regex.Pattern;

public class CodecTestBase {
    static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    static final long Q_DEQ_TIMEOUT_US = 5000; // block at most 5ms while looking for io buffers
    static final int RETRY_LIMIT = 100; // max poll counter before test aborts and returns error
    static final String CODEC_FILTER_KEY = "codec-filter";
    static final String CODEC_PREFIX_KEY = "codec-prefix";
    static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    static Pattern codecFilter;
    static String codecPrefix;
    static String mediaTypePrefix;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        codecPrefix = args.getString(CODEC_PREFIX_KEY);
        mediaTypePrefix = args.getString(MEDIA_TYPE_PREFIX_KEY);
        String codecFilterStr = args.getString(CODEC_FILTER_KEY);
        if (codecFilterStr != null) {
            codecFilter = Pattern.compile(codecFilterStr);
        }
    }
}

