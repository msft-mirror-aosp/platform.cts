/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.pm.cts;

import static android.Manifest.permission.GET_INTENT_SENDER_INTENT;
import static android.Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.IntentFilter.BLOCK_NULL_ACTION_INTENTS;
import static android.security.Flags.FLAG_BLOCK_NULL_ACTION_INTENTS;
import static android.security.Flags.FLAG_ENFORCE_INTENT_FILTER_MATCH;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.strictmode.UnsafeIntentLaunchViolation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(AndroidJUnit4.class)
public class SaferIntentTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;
    private ArrayList<BroadcastReceiver> mRegisteredReceiverList;
    private LinkedBlockingQueue<StrictMode.ViolationInfo> mViolations;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS_CHANGEID = 161252188;
    private static final long IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID = 229362273;

    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";
    private static final String SERVICE_NAME = "android.content.pm.cts.TestPmService";
    private static final String RECEIVER_NAME = "android.content.pm.cts.PmTestReceiver";

    private static final String EXPORTED_ACTION = "android.intent.action.cts.EXPORTED_ACTION";
    private static final String NON_EXPORTED_ACTION =
            "android.intent.action.cts.NON_EXPORTED_ACTION";

    private static final String NON_EXISTENT_ACTION_NAME = "android.intent.action.cts.NON_EXISTENT";
    private static final String RESOLUTION_TEST_PKG_NAME =
            "android.content.cts.IntentResolutionTest";
    private static final String ACTION_RECEIVING_INTENT = "android.content.cts.RECEIVING_INTENT";
    private static final String SELECTOR_ACTION_NAME = "android.intent.action.SELECTORTEST";
    private static final String FILE_PROVIDER_AUTHORITY = "android.content.cts.fileprovider";
    private static final String RESOLUTION_TEST_ACTION_NAME =
            "android.intent.action.RESOLUTION_TEST";

    @Before
    public void setup() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
        mRegisteredReceiverList = new ArrayList<>();
        mViolations = new LinkedBlockingQueue<>();
        StrictMode.setViolationLogger(mViolations::offer);
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() ->
                        CompatChanges.removePackageOverrides(mContext.getPackageName(),
                                Set.of(ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS_CHANGEID,
                                        IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID,
                                        BLOCK_NULL_ACTION_INTENTS)),
                OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD);
        for (BroadcastReceiver receiver : mRegisteredReceiverList) {
            mContext.unregisterReceiver(receiver);
        }
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);
        StrictMode.setViolationLogger(null);
    }

    private void setCompatOverride(long changeId, boolean enable) {
        var override = Map.of(changeId, new PackageOverride.Builder().setEnabled(enable).build());
        SystemUtil.runWithShellPermissionIdentity(
                () -> CompatChanges.putPackageOverrides(mContext.getPackageName(), override),
                OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD);
    }

    private void enableStrictMode() {
        var policy = new StrictMode.VmPolicy.Builder()
                .detectUnsafeIntentLaunch()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(policy);
    }

    private void assertViolation(boolean b) throws InterruptedException {
        StrictMode.ViolationInfo v = mViolations.poll(5, TimeUnit.SECONDS);
        // No other violations queued up
        assertTrue(mViolations.isEmpty());
        if (b) {
            assertNotNull(v);
            assertTrue(UnsafeIntentLaunchViolation.class.isAssignableFrom(v.getViolationClass()));
        } else {
            assertNull(v);
        }
    }

    @Test
    public void testStartInternalExportedActivity() {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        Intent intent = new Intent(EXPORTED_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Test
    public void testStartInternalNonExportedActivity() throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, false);
        enableStrictMode();

        Intent intent = new Intent(NON_EXPORTED_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        // Strict mode should still catch the unsafe usage
        assertViolation(true);

        // Enable the feature
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        assertThrows(ActivityNotFoundException.class, () -> mContext.startActivity(intent));
        assertViolation(true);

        // Switching to explicit should work properly
        intent.setPackage(mContext.getPackageName());
        mContext.startActivity(intent);
        assertViolation(false);
    }

    @Test
    public void testBroadcastInternalExportedRuntimeReceiver()
            throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        var latch = new CountDownLatch(1);
        var receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        var filter = new IntentFilter(EXPORTED_ACTION);
        mContext.registerReceiver(receiver, filter, RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        mContext.sendBroadcast(new Intent(EXPORTED_ACTION));
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testBroadcastInternalNonExportedRuntimeReceiver()
            throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, false);
        enableStrictMode();

        var receiver = new BroadcastReceiver() {
            private CountDownLatch mLatch;
            @Override
            public void onReceive(Context context, Intent intent) {
                mLatch.countDown();
            }
        };
        var filter = new IntentFilter(NON_EXPORTED_ACTION);
        mContext.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        var intent = new Intent(NON_EXPORTED_ACTION);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        // Strict mode should still catch the unsafe usage
        assertViolation(true);

        // Enable the feature
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertFalse(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertViolation(true);

        // Switching to explicit should work properly
        intent.setPackage(mContext.getPackageName());
        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertViolation(false);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testEnforceIntentToMatchIntentFilter() {
        setCompatOverride(ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS_CHANGEID, true);

        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);
        final var activityFlags = PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DEFAULT_ONLY);

        Intent intent = new Intent();
        List<ResolveInfo> results;

        /* Non-component intent tests */

        intent.setPackage(RESOLUTION_TEST_PKG_NAME);

        // Package intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(2 /* TestPmActivity and TestPmActivityWithDefault */, results.size());
        // MATCH_DEFAULT_ONLY will change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1 /* TestPmActivityWithDefault */, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Package intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        /* Component intent tests */

        intent = new Intent();
        ComponentName comp;

        // Component intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        // MATCH_DEFAULT_ONLY shall NOT change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Component intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // More comprehensive intent matching tests
        intent = new Intent();
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME + "2");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setType("*/*");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setData(Uri.parse("http://example.com"));
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setDataAndType(Uri.parse("http://example.com"), "*/*");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
        File file = new File(mContext.getFilesDir(), "test.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        Uri uri = FileProvider.getUriForFile(mContext, FILE_PROVIDER_AUTHORITY, file);
        intent.setData(uri);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
        file.delete();
        intent.addCategory(Intent.CATEGORY_APP_BROWSER);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // Component intents with non-matching intent filter on our own package
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(mContext.getPackageName(), ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(mContext.getPackageName(), SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(mContext.getPackageName(), RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(RESOLUTION_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        // MATCH_DEFAULT_ONLY shall NOT change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Matching intent and non-matching selector
        selector.setAction(NON_EXISTENT_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        /* Pending Intent tests */

        var authority = RESOLUTION_TEST_PKG_NAME + ".provider";
        Bundle b = mContext.getContentResolver().call(authority, "", null, null);
        assertNotNull(b);
        PendingIntent pi = b.getParcelable("pendingIntent", PendingIntent.class);
        assertNotNull(pi);
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(GET_INTENT_SENDER_INTENT);
        try {
            intent = pi.getIntent();
            // It should be a non-matching intent, which cannot be resolved in our package
            results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
            assertEquals(0, results.size());
            // However, querying on behalf of the pending intent creator should work properly
            results = pi.queryIntentComponents(0);
            assertEquals(1, results.size());
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(
                new ComponentName("android", "com.android.internal.app.ResolverActivity"));
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {
        }
    }

    @Test
    public void testLegacyIntentFilterMatching() {
        setCompatOverride(ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS_CHANGEID, false);

        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);

        Intent intent = new Intent();
        List<ResolveInfo> results;
        ComponentName comp;

        /* Component explicit intent tests */

        // Explicit intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(RESOLUTION_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testNullActionMatching() {
        final var activityFlags = PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DEFAULT_ONLY);
        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);

        // Create a package explicit intent with null action
        Intent intent = new Intent();
        intent.setPackage(RESOLUTION_TEST_PKG_NAME);
        List<ResolveInfo> results;

        // Test legacy behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, false);

        // Null action intent should match
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertFalse(results.isEmpty());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertFalse(results.isEmpty());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertFalse(results.isEmpty());

        // Test new behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, true);

        // Null action intent should not match
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertTrue(results.isEmpty());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertTrue(results.isEmpty());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertTrue(results.isEmpty());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testRegisterReceiverNullActionMatching() throws InterruptedException {
        final var receiver = new BroadcastReceiver() {
            private CountDownLatch mLatch;

            @Override
            public void onReceive(Context context, Intent intent) {
                mLatch.countDown();
            }
        };
        final var filter = new IntentFilter("action");
        filter.addDataScheme("https");
        mContext.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        mRegisteredReceiverList.add(receiver);

        // Create an intent with null action
        final var intent = new Intent()
                .setPackage(mContext.getPackageName())
                .setData(Uri.parse("https://www.google.com"))
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);

        // Test legacy behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, false);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));

        // Test new behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, true);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertFalse(receiver.mLatch.await(5, TimeUnit.SECONDS));
    }

    private static class IntentRetriever extends BroadcastReceiver {
        private CountDownLatch mLatch;
        private Intent mIntent;

        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            mLatch.countDown();
        }
    }

    private IntentRetriever setupMismatchFlagTest() {
        // Explicitly disable the enforcement to test non-matching intents
        setCompatOverride(ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS_CHANGEID, false);

        final var receiver = new IntentRetriever();
        final var filter = new IntentFilter(ACTION_RECEIVING_INTENT);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        return receiver;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testActivityIntentMismatchFlag() throws InterruptedException {
        final var receiver = setupMismatchFlagTest();

        // Set up base intent with non matching action
        var intent = new Intent(NON_EXISTENT_ACTION_NAME)
                .setClassName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startActivity(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertTrue(receiver.mIntent.isMismatchingFilter());
        Thread.sleep(500);

        // Set up base intent with matching action
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startActivity(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());
        Thread.sleep(500);

        // Package intents should always match
        intent = new Intent(RESOLUTION_TEST_ACTION_NAME)
                .setPackage(RESOLUTION_TEST_PKG_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startActivity(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());
        Thread.sleep(500);

        // Test whether the flag is cleared when matching
        intent.addExtendedFlags(Intent.EXTENDED_FLAG_FILTER_MISMATCH);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startActivity(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testServiceIntentMismatchFlag() throws InterruptedException {
        final var receiver = setupMismatchFlagTest();

        // Exempt target app so we can start its services
        SystemUtil.runShellCommand("cmd deviceidle whitelist +" + RESOLUTION_TEST_PKG_NAME);

        // Set up base intent with non matching action
        var intent = new Intent(NON_EXISTENT_ACTION_NAME)
                .setClassName(RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startService(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertTrue(receiver.mIntent.isMismatchingFilter());

        // Set up base intent with matching action
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startService(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());

        // Package intents should always match
        intent = new Intent(RESOLUTION_TEST_ACTION_NAME)
                .setPackage(RESOLUTION_TEST_PKG_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startService(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());

        // Test whether the flag is cleared when matching
        intent.addExtendedFlags(Intent.EXTENDED_FLAG_FILTER_MISMATCH);

        receiver.mLatch = new CountDownLatch(1);
        mContext.startService(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testBroadcastIntentMismatchFlag() throws InterruptedException {
        final var receiver = setupMismatchFlagTest();

        // Set up base intent with non matching action
        var intent = new Intent(NON_EXISTENT_ACTION_NAME)
                .setClassName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertTrue(receiver.mIntent.isMismatchingFilter());

        // Set up base intent with matching action
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());

        // Package intents should always match
        intent = new Intent(RESOLUTION_TEST_ACTION_NAME)
                .setPackage(RESOLUTION_TEST_PKG_NAME);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());

        // Test whether the flag is cleared when matching
        intent.addExtendedFlags(Intent.EXTENDED_FLAG_FILTER_MISMATCH);

        receiver.mLatch = new CountDownLatch(1);
        mContext.sendBroadcast(intent);
        assertTrue(receiver.mLatch.await(5, TimeUnit.SECONDS));
        assertFalse(receiver.mIntent.isMismatchingFilter());
    }
}
