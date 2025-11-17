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

package android.content.cts;

import static android.security.Flags.FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SENDMULTIPLE_IMAGECAPTURE_ACTIONS;
import static android.security.Flags.FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SEND_ACTION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppGlobals;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.StrictMode;
import android.os.strictmode.ImplicitUriPermissionGrantViolation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class ActivityLaunchUriPermissionTest {

    private Context mContext;

    private Instrumentation mInstrumentation;

    private IntentRetriever mRetriever;

    private static LinkedBlockingQueue<StrictMode.ViolationInfo> sViolations;
    private static StrictMode.VmPolicy sOldVmPolicy;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";

    private static final String RESOLUTION_TEST_PKG_NAME =
            "android.content.cts.IntentResolutionTest";

    private static final String ACTION_RECEIVING_INTENT = "android.content.cts.RECEIVING_INTENT";

    private static final List<String> TEST_APPS = List.of(RESOLUTION_TEST_PKG_NAME);

    private static final String TAG = ActivityLaunchUriPermissionTest.class.getSimpleName();

    static class IntentRetriever extends BroadcastReceiver {
        Intent mIntent;

        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }

        boolean waitOnReceive() {
            final long startTime = System.currentTimeMillis();
            final long timeoutMilliseconds = 1500;

            while (mIntent == null
                    && (System.currentTimeMillis() - startTime) < timeoutMilliseconds) {
                SystemUtil.waitForBroadcasts();
            }

            if (mIntent != null) {
                return true;
            } else {
                Log.w(TAG, "Timeout: Intent not received within " + timeoutMilliseconds + "ms.");
                return false;
            }
        }

        void reset() {
            mIntent = null;
        }
    }

    @BeforeClass
    public static void setupClass() {
        sOldVmPolicy = StrictMode.getVmPolicy();
        sViolations = new LinkedBlockingQueue<>();
        StrictMode.setViolationLogger(sViolations::offer);
        enableStrictMode();
    }

    @AfterClass
    public static void tearDownClass() {
        StrictMode.setVmPolicy(sOldVmPolicy);
        StrictMode.setViolationLogger(null);
    }

    @Before
    public void setup() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();

        for (String testApp : TEST_APPS) {
            // Bring test app out of the stopped state so that it can receive broadcasts
            SystemUtil.runWithShellPermissionIdentity(
                    () ->
                            AppGlobals.getPackageManager()
                                    .setPackageStoppedState(
                                            testApp, false, Process.myUserHandle().getIdentifier()),
                    Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
            // Exempt test app so we can start its services
            SystemUtil.runShellCommand("cmd deviceidle whitelist +" + testApp);
        }

        mRetriever = new IntentRetriever();
        final var filter = new IntentFilter(ACTION_RECEIVING_INTENT);
        mContext.registerReceiver(mRetriever, filter, Context.RECEIVER_EXPORTED);
        sViolations.clear();
    }

    @After
    public void tearDown() throws Exception {
        for (String testApp : TEST_APPS) {
            // Remove app from whitelist
            SystemUtil.runShellCommand("cmd deviceidle whitelist -" + testApp);
        }
        mContext.unregisterReceiver(mRetriever);
    }

    private static boolean isStrictModeFeatureEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
                && android.security.Flags.strictModeViolationForImplicitUriGrantsEnabled();
    }

    private static void enableStrictMode() {
        if (!isStrictModeFeatureEnabled()) {
            return;
        }
        var policy =
                new StrictMode.VmPolicy.Builder()
                        .detectImplicitUriPermissionGrant()
                        .penaltyLog()
                        .build();
        StrictMode.setVmPolicy(policy);
    }

    private void assertViolation(int expectedViolationCount) throws InterruptedException {
        if (!isStrictModeFeatureEnabled()) {
            return;
        }
        for (int i = 0; i < expectedViolationCount; i++) {
            StrictMode.ViolationInfo violation = sViolations.poll(5, TimeUnit.SECONDS);
            assertNotNull(violation);
            assertEquals(
                    ImplicitUriPermissionGrantViolation.class.getName(),
                    violation.getViolationClass().getName());
        }
        // Ensure there are no more violations
        assertTrue(
                "Expected " + expectedViolationCount + " violations, but more were found.",
                sViolations.isEmpty());
    }

    private Intent createImageCaptureActionIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri uri =
                Uri.parse(
                        "content://"
                            + "android.content.cts.fileprovider/root/data/data/android.content.cts"
                            + ".MockApplication/poc");
        intent.putExtra("output", uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        return intent;
    }

    private Intent createSendMultipleActionIntent() {
        ArrayList<Uri> uriList = new ArrayList<>();
        uriList.add(Uri.parse("data:text/plain;charset=utf-8," + Uri.encode("text1")));
        uriList.add(Uri.parse("data:text/plain;charset=utf-8," + Uri.encode("text2")));

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        return intent;
    }

    private Intent createSendActionIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        Uri uri = Uri.parse("content://com.example.myapp.fileprovider/my_images/shared_image.jpg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        return intent;
    }

    private boolean hasReadPermission(int flags) {
        return (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == Intent.FLAG_GRANT_READ_URI_PERMISSION;
    }

    private boolean hasWritePermission(int flags) {
        return (flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                == Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    }

    private Intent startActivityAndGetReceivedIntent(Intent intent) {
        mRetriever.reset();
        mContext.startActivity(intent);
        assertTrue(mRetriever.waitOnReceive());
        return mRetriever.mIntent;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SENDMULTIPLE_IMAGECAPTURE_ACTIONS)
    public void testImplicitGrantForImageCaptureActionRestricted() throws InterruptedException {
        Intent intent = createImageCaptureActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, receivedIntent.getAction());
        assertFalse(hasReadPermission(receivedIntent.getFlags()));
        assertFalse(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SENDMULTIPLE_IMAGECAPTURE_ACTIONS)
    public void testImplicitGrantForSendMultipleActionRestricted() throws InterruptedException {
        Intent intent = createSendMultipleActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND_MULTIPLE, mRetriever.mIntent.getAction());
        assertFalse(hasReadPermission(receivedIntent.getFlags()));
        assertFalse(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SEND_ACTION)
    public void testImplicitGrantForSendActionRestricted() throws InterruptedException {
        Intent intent = createSendActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND, receivedIntent.getAction());
        assertFalse(hasReadPermission(receivedIntent.getFlags()));
        assertFalse(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(1);
    }

    @Test
    @RequiresFlagsDisabled(
            FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SENDMULTIPLE_IMAGECAPTURE_ACTIONS)
    public void testImplicitGrantForImageCaptureActionUnRestricted() throws InterruptedException {
        Intent intent = createImageCaptureActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, receivedIntent.getAction());
        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertTrue(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(2);
    }

    @Test
    @RequiresFlagsDisabled(
            FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SENDMULTIPLE_IMAGECAPTURE_ACTIONS)
    public void testImplicitGrantForSendMultipleActionUnRestricted() throws InterruptedException {
        Intent intent = createSendMultipleActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND_MULTIPLE, mRetriever.mIntent.getAction());
        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertFalse(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(1);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_IMPLICIT_URI_GRANTS_RESTRICTED_FOR_SEND_ACTION)
    public void testImplicitGrantForSendActionUnRestricted() throws InterruptedException {
        Intent intent = createSendActionIntent();
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND, receivedIntent.getAction());
        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertFalse(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(1);
    }

    @Test
    public void testExplicitGrantForImageCaptureAction() throws InterruptedException {
        Intent intent = createImageCaptureActionIntent();
        intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, receivedIntent.getAction());

        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertTrue(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(0);
    }

    @Test
    public void testExplicitGrantForSendMultipleAction() throws InterruptedException {
        Intent intent = createSendMultipleActionIntent();
        intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND_MULTIPLE, mRetriever.mIntent.getAction());

        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertTrue(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(0);
    }

    @Test
    public void testExplicitGrantForSendAction() throws InterruptedException {
        Intent intent = createSendActionIntent();
        intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent receivedIntent = startActivityAndGetReceivedIntent(intent);

        assertEquals(Intent.ACTION_SEND, receivedIntent.getAction());

        assertTrue(hasReadPermission(receivedIntent.getFlags()));
        assertTrue(hasWritePermission(receivedIntent.getFlags()));
        assertViolation(0);
    }

    @Test
    public void testStrictModePermit() throws InterruptedException {
        assumeTrue(isStrictModeFeatureEnabled());

        var builder =
                new StrictMode.VmPolicy.Builder().detectImplicitUriPermissionGrant().penaltyLog();

        builder.permitImplicitUriPermissionGrant();
        StrictMode.setVmPolicy(builder.build());

        startActivityAndGetReceivedIntent(createSendActionIntent());
        assertViolation(0);
    }

    @Test
    public void testStrictModeDetectAll() throws InterruptedException {
        assumeTrue(isStrictModeFeatureEnabled());

        StrictMode.VmPolicy sOldVmPolicy = StrictMode.getVmPolicy();
        try {
            var builder = new StrictMode.VmPolicy.Builder().detectAll().penaltyLog();
            StrictMode.setVmPolicy(builder.build());
            assertTrue(StrictMode.vmImplicitUriPermissionGrantEnabled());
        } finally {
            StrictMode.setVmPolicy(sOldVmPolicy);
        }
    }
}
