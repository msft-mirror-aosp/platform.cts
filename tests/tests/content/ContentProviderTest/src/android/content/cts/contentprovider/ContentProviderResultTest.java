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

package android.content.cts.contentprovider;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentProviderResult;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class ContentProviderResultTest {
    private static final Uri TEST_URI = Uri.EMPTY;
    private static final Bundle TEST_BUNDLE = Bundle.EMPTY;
    private static final int COUNT = 42;

    @Test
    public void testUri() {
        ContentProviderResult contentProviderResult = new ContentProviderResult(TEST_URI);
        assertThat(contentProviderResult.uri).isEqualTo(TEST_URI);
        assertThat(contentProviderResult.count).isNull();
        assertThat(contentProviderResult.extras).isNull();
        assertThat(contentProviderResult.exception).isNull();
    }

    @Test
    public void testCount() {
        ContentProviderResult contentProviderResult = new ContentProviderResult(COUNT);
        assertThat(contentProviderResult.count).isEqualTo(COUNT);
        assertThat(contentProviderResult.uri).isNull();
        assertThat(contentProviderResult.extras).isNull();
        assertThat(contentProviderResult.exception).isNull();
    }

    @Test
    public void testExtras() {
        ContentProviderResult contentProviderResult = new ContentProviderResult(TEST_BUNDLE);
        assertThat(contentProviderResult.extras).isEqualTo(TEST_BUNDLE);
        assertThat(contentProviderResult.uri).isNull();
        assertThat(contentProviderResult.count).isNull();
        assertThat(contentProviderResult.exception).isNull();
    }

    @Test
    public void testException() {
        Exception testException = new IllegalArgumentException();
        ContentProviderResult contentProviderResult = new ContentProviderResult(testException);
        assertThat(contentProviderResult.exception).isEqualTo(testException);
        assertThat(contentProviderResult.uri).isNull();
        assertThat(contentProviderResult.count).isNull();
        assertThat(contentProviderResult.extras).isNull();
    }
}
