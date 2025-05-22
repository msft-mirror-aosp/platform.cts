/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.content.MockBuggyProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test system behavior of a buggy provider.
 *
 * <p>see @{@link MockBuggyProvider}
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class BuggyProviderTest {

    @Test
    public void testGetTypeDoesntCrashSystem() {
        // ensure the system doesn't crash when a provider takes too long to respond
        try {
            ActivityManager.getService()
                    .getMimeTypeFilterAsync(
                            MockBuggyProvider.CONTENT_URI,
                            UserHandle.USER_CURRENT,
                            new RemoteCallback(result -> {}));
        } catch (Exception e) {
            assertWithMessage("Unexpected exception while fetching type: " + e.getMessage()).fail();
        }
    }

    @Test
    public void testGetTypeViaResolverDoesntCrashSystem() {
        // ensure the system doesn't crash when a provider takes too long to respond
        ContentResolver resolver =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getContentResolver();
        try {
            resolver.getType(MockBuggyProvider.CONTENT_URI);
        } catch (Exception e) {
            assertWithMessage("Unexpected exception while fetching type: " + e.getMessage()).fail();
        }
    }
}
