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
import static android.provider.Settings.Secure.TAP_EVENT_SERVICE_COMPONENT;
import static android.provider.Settings.Secure.TAP_SHARE_FULFILLMENT_ACTIVITY_COMPONENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.junit.Assume.assumeFalse;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
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
import org.junit.After;
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
    private static final ComponentName GESTURE_EXCHANGE_ACTIVITY_COMPONENT =
            new ComponentName("android.sharesheet.cts",
                    "android.sharesheet.cts.CtsGestureExchangeActivity");
    private static final String GESTURE_EXCHANGE_MIME_TYPE = "test/tap_to_share_cts";
    private static final int TIMEOUT_MS = 5000;

    private Context mContext;
    private PackageManager mPackageManager;
    private TapToShareClient mClient;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        mClient = new TapToShareClient(mContext);
        CtsSharesheetTapToShareService.reset();
    }

    @After
    public void tearDown() throws Exception {
        // Make sure the service is destroyed to avoid interference with other tests
        CtsSharesheetTapToShareService service = CtsSharesheetTapToShareService.getInstance();
        if (service != null) {
            assertTrue("Service should have been destroyed",
                    service.awaitDestroy(TIMEOUT_MS));
        }
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
    public void testPerformTapToShare_shouldTriggerOnDeviceTapped() throws Exception {
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

            service.performTapToShare();

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

    @Test
    public void testTapToShare_launchesFulfillmentActivity() throws Exception {
        assumeHandheldDevice();

        final CountDownLatch intentLatch = new CountDownLatch(1);
        final AtomicReference<Intent> receivedIntent = new AtomicReference<>();
        CtsGestureExchangeActivity.setOnIntentReceivedConsumer(intent -> {
            receivedIntent.set(intent);
            intentLatch.countDown();
        });

        ContentResolver resolver = mContext.getContentResolver();
        String oldService = Settings.Secure.getString(resolver, TAP_EVENT_SERVICE_COMPONENT);
        String oldActivity = Settings.Secure.getString(resolver,
                TAP_SHARE_FULFILLMENT_ACTIVITY_COMPONENT);
        Settings.Secure.putString(resolver, TAP_EVENT_SERVICE_COMPONENT,
            SERVICE_COMPONENT.flattenToString());
        Settings.Secure.putString(resolver, TAP_SHARE_FULFILLMENT_ACTIVITY_COMPONENT,
            GESTURE_EXCHANGE_ACTIVITY_COMPONENT.flattenToString());
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(GESTURE_EXCHANGE_MIME_TYPE);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Test");
            Intent chooser = Intent.createChooser(shareIntent, "Share test");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            TestApis.activities().startActivity(chooser);

            CtsSharesheetTapToShareService service =
                    CtsSharesheetTapToShareService.awaitService(TIMEOUT_MS);
            assertTrue("onSessionStart timeout", service.awaitSessionStart(TIMEOUT_MS));
            service.performTapToShare();

            assertTrue("Fulfillment activity not launched in time",
                    intentLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertNotNull(receivedIntent.get());
            assertEquals(Intent.ACTION_SEND, receivedIntent.get().getAction());
            assertEquals(GESTURE_EXCHANGE_MIME_TYPE, receivedIntent.get().getType());
            assertEquals("Test", receivedIntent.get().getStringExtra(Intent.EXTRA_TEXT));
        } finally {
            Settings.Secure.putString(resolver, TAP_EVENT_SERVICE_COMPONENT, oldService);
            Settings.Secure.putString(resolver, TAP_SHARE_FULFILLMENT_ACTIVITY_COMPONENT, oldActivity);
            mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }

    private void assumeHandheldDevice() {
        assumeFalse("This test only runs on handheld devices with a standard Sharesheet",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_PC));
    }
}