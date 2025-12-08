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
package android.sharesheet.cts;

import static android.Manifest.permission.BIND_TO_TAP_TO_SHARE_SERVICE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.chooser.Flags;
import android.service.chooser.TapToShareClient;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_TAP_TO_SHARE)
@RunWith(AndroidJUnit4.class)
public class CtsSharesheetTapToShareServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final ComponentName SERVICE_COMPONENT =
            new ComponentName("android.sharesheet.cts",
                    "android.sharesheet.cts.CtsSharesheetTapToShareService");
    private static final ComponentName OUT_OF_PROCESS_SERVICE_COMPONENT =
            new ComponentName("android.sharesheet.cts.outofprocess",
                    "android.sharesheet.cts.outofprocess.CtsSharesheetOutOfProcessTapToShareService");
    private static final int TIMEOUT_MS = 5000;

    private Context mContext;
    private TapToShareClient mClient;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mClient = new TapToShareClient(mContext);
        CtsSharesheetTapToShareService.reset();
    }

    @Test
    public void testStartSession_withoutPermission_throwsSecurityException() throws Exception {
        final CountDownLatch connectionFailedLatch = new CountDownLatch(1);
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        TapToShareClient.SessionListener listener = new TapToShareClient.SessionListener() {
            @Override
            public void onDeviceTapped() {
                fail("onDeviceTapped should not be called");
            }

            @Override
            public void onConnectionFailed(@NonNull Exception e) {
                exceptionRef.set(e);
                connectionFailedLatch.countDown();
            }
        };

        mClient.startSession(
                OUT_OF_PROCESS_SERVICE_COMPONENT, /* referrer= */ null, mExecutor, listener);

        assertTrue("onConnectionFailed not called in time",
                connectionFailedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull("Exception should not be null", exceptionRef.get());
        assertTrue("Exception should be a SecurityException",
                exceptionRef.get() instanceof SecurityException);
    }

    @Test
    public void testStartSession_withPermission_succeeds() throws Exception {
        final CountDownLatch connectionFailedLatch = new CountDownLatch(1);
        TapToShareClient.SessionListener listener = new TapToShareClient.SessionListener() {
            @Override
            public void onDeviceTapped() {}

            @Override
            public void onConnectionFailed(@NonNull Exception e) {
                fail("onConnectionFailed should not be called: " + e);
                connectionFailedLatch.countDown();
            }
        };
        try (PermissionContext permissionContext = TestApis.permissions().withPermission(
                BIND_TO_TAP_TO_SHARE_SERVICE)) {
            // This call should succeed.
            mClient.startSession(
                    OUT_OF_PROCESS_SERVICE_COMPONENT, /* referrer= */ null, mExecutor, listener);
            // A short wait to allow for any async connection failure.
            connectionFailedLatch.await(200, TimeUnit.MILLISECONDS);
        } finally {
            mClient.endSession();
        }
    }

    @Test
    public void testStartSession_shouldTriggerOnSessionStart() throws Exception {
        TapToShareClient.SessionListener listener = new TapToShareClient.SessionListener() {
            @Override
            public void onDeviceTapped() {}

            @Override
            public void onConnectionFailed(@NonNull Exception e) {
                fail("onConnectionFailed should not be called: " + e);
            }
        };
        try {
            mClient.startSession(SERVICE_COMPONENT, /* referrer= */ null, mExecutor, listener);
            CtsSharesheetTapToShareService service =
                    CtsSharesheetTapToShareService.awaitService(TIMEOUT_MS);

            assertTrue("onSessionStart timeout", service.awaitSessionStart(TIMEOUT_MS));
        } finally {
            mClient.endSession();
        }
    }

    @Test
    public void testDoTapToShare_shouldTriggerOnDeviceTapped() throws Exception {
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        TapToShareClient.SessionListener listener = new TapToShareClient.SessionListener() {
            @Override
            public void onDeviceTapped() {
                listenerLatch.countDown();
            }

            @Override
            public void onConnectionFailed(@NonNull Exception e) {
                fail("onConnectionFailed should not be called: " + e);
            }
        };

        try {
            mClient.startSession(SERVICE_COMPONENT, /* referrer= */ null, mExecutor, listener);
            CtsSharesheetTapToShareService service =
                    CtsSharesheetTapToShareService.awaitService(TIMEOUT_MS);

            assertTrue("onSessionStart timeout", service.awaitSessionStart(TIMEOUT_MS));

            service.doTapToShare();

            assertTrue("Listener not notified in time",
                    listenerLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mClient.endSession();
        }
    }

    @Test
    public void testEndSession_shouldTriggerOnSessionEnd() throws Exception {
        TapToShareClient.SessionListener listener = new TapToShareClient.SessionListener() {
            @Override
            public void onDeviceTapped() {}

            @Override
            public void onConnectionFailed(@NonNull Exception e) {
                fail("onConnectionFailed should not be called: " + e);
            }
        };
        CtsSharesheetTapToShareService service;
        try {
            mClient.startSession(SERVICE_COMPONENT, /* referrer= */ null, mExecutor, listener);
            service = CtsSharesheetTapToShareService.awaitService(TIMEOUT_MS);

            assertTrue("onSessionStart timeout", service.awaitSessionStart(TIMEOUT_MS));
        } finally {
            mClient.endSession();
        }
        assertTrue("onSessionEnd should be called", service.awaitSessionEnd(TIMEOUT_MS));
    }
}