/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.encryptionapp;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.os.StrictMode.ViolationInfo;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.strictmode.CredentialProtectedWhileLockedViolation;
import android.os.strictmode.ImplicitDirectBootViolation;
import android.os.strictmode.Violation;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.TestUtils;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class EncryptionAppTest extends InstrumentationTestCase {
    private static final String TAG = "EncryptionAppTest";

    private static final String KEY_BOOT = "boot";

    private static final String TEST_PKG = "com.android.cts.encryptionapp";
    private static final String TEST_ACTION = "com.android.cts.encryptionapp.TEST";

    private static final String OTHER_PKG = "com.android.cts.splitapp";

    private static final int BOOT_TIMEOUT_SECONDS = 150;
    private static final int UNLOCK_SCREEN_START_TIME_SECONDS = 10;

    private static final Uri FILE_INFO_URI = Uri.parse("content://" + OTHER_PKG + "/files");

    private Context mCe;
    private Context mDe;
    private PackageManager mPm;

    private UiDevice mDevice;
    private AwareActivity mActivity;

    @Override
    public void setUp() throws Exception {
        Log.d(TAG, "setUp() on user " + UserHandle.myUserId());
        super.setUp();

        mCe = getInstrumentation().getContext();
        mDe = mCe.createDeviceProtectedStorageContext();
        mPm = mCe.getPackageManager();

        mDevice = UiDevice.getInstance(getInstrumentation());
        assertNotNull(mDevice);
    }

    @Override
    public void tearDown() throws Exception {
        Log.d(TAG, "tearDown() on user " + UserHandle.myUserId());
        super.tearDown();

        if (mActivity != null) {
            mActivity.finish();
        }
    }

    public void testSetUp() throws Exception {
        Log.d(TAG, "testSetup()");

        // Write both CE/DE data for ourselves
        assertTrue("CE file", getTestFile(mCe).createNewFile());
        assertTrue("DE file", getTestFile(mDe).createNewFile());

        doBootCountBefore();

        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                AwareActivity.class, null);
        mDevice.waitForIdle();

        // Set a PIN for this user
        mDevice.executeShellCommand("locksettings set-disabled false");
        String output = mDevice.executeShellCommand("locksettings set-pin 1234");
        assertTrue("set-pin failed. Output: " + output, output.contains("1234"));

        // Clear all other requests for lskf from the system.
        String clearOutput = mDevice.executeShellCommand("cmd recovery clear-lskf android");
        assertTrue("clear-lskf failed for package android. Output: " + clearOutput,
                clearOutput.contains("success"));
    }

    public void testTearDown() throws Exception {
        Log.d(TAG, "testTearDown()");

        // Since there's not a good way to check whether the keyguard is already dismissed, summon
        // the keyguard and dismiss it.
        summonKeyguard();
        dismissKeyguard();

        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                AwareActivity.class, null);
        mDevice.waitForIdle();

        // Clear PIN for this user
        mDevice.executeShellCommand("locksettings clear --old 1234");
        mDevice.executeShellCommand("locksettings set-disabled true");
    }

    public void testLockScreen() throws Exception {
        Log.d(TAG, "testLockScreen()");

        summonKeyguard();
    }

    public void testUnlockScreen() throws Exception {
        Log.d(TAG, "testUnlockScreen()");

        dismissKeyguard();
    }

    public void doBootCountBefore() throws Exception {
        int thisCount = getBootCount();
        Log.d(TAG, "doBootCountBefore(): thisCount=" + thisCount);
        mDe.getSharedPreferences(KEY_BOOT, 0).edit().putInt(KEY_BOOT, thisCount).commit();
    }

    public void doBootCountAfter() throws Exception {
        int lastCount = mDe.getSharedPreferences(KEY_BOOT, 0).getInt(KEY_BOOT, -1);
        int thisCount = getBootCount();
        Log.d(TAG, "doBootCountAfter(): lastCount=" + lastCount + ", thisCount=" + thisCount);
        assertTrue("Current boot count " + thisCount + " not greater than last " + lastCount,
                thisCount > lastCount);
    }

    public void testCheckServiceInteraction() {
        Log.d(TAG, "testCheckServiceInteraction()");

        boolean wrapCalled =
                mDe.getSharedPreferences(RebootEscrowFakeService.SERVICE_PREFS, 0)
                        .getBoolean("WRAP_CALLED", false);
        assertWithMessage("WRAP_CALLED").that(wrapCalled).isTrue();

        boolean unwrapCalled =
                mDe.getSharedPreferences(RebootEscrowFakeService.SERVICE_PREFS, 0)
                        .getBoolean("UNWRAP_CALLED", false);
        assertWithMessage("UNWRAP_CALLED").that(unwrapCalled).isTrue();
    }

    public void testVerifyUnlockedAndDismiss() throws Exception {
        Log.d(TAG, "testVerifyUnlockedAndDismiss()");

        doBootCountAfter();
        assertUnlocked();
        dismissKeyguard();
        assertUnlocked();
    }

    public void testVerifyLockedAndDismiss() throws Exception {
        Log.d(TAG, "testVerifyLockedAndDismiss()");

        doBootCountAfter();
        assertLocked();

        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.v(TAG, "Received intent " + intent.getAction());

                        latch.countDown();
                    }
                };
        mDe.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        dismissKeyguard();

        // Dismiss keyguard should have kicked off immediate broadcast
        assertTrue("USER_UNLOCKED", latch.await(1, TimeUnit.MINUTES));

        // And we should now be fully unlocked; we run immediately like this to
        // avoid missing BOOT_COMPLETED due to instrumentation being torn down.
        assertUnlocked();
    }

    private void enterTestPin() throws Exception {
        Log.d(TAG, "enterTestPin()");

        // TODO: change the combination on my luggage

        // Give enough time for the lock screen to show up in the UI.
        SystemClock.sleep(UNLOCK_SCREEN_START_TIME_SECONDS * 1000);
        mDevice.waitForIdle();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mDevice.waitForIdle();
        mDevice.pressEnter();
        mDevice.waitForIdle();

        // TODO(189853309) make sure RebootEscrowManager get the unlock event
    }

    private void dismissKeyguard() throws Exception {
        Log.d(TAG, "dismissKeyguard()");

        mDevice.waitForIdle();
        mDevice.wakeUp();
        mDevice.waitForIdle();

        // Launch activity because KeyguardManager#requestDismissKeyguard()
        // must be invoked with an activity.
        mActivity =
            launchActivity(
                getInstrumentation().getTargetContext().getPackageName(),
                AwareActivity.class,
                null);
        KeyguardManager keyguardManager =
            mDe.getSystemService(KeyguardManager.class);
        keyguardManager.requestDismissKeyguard(mActivity, null);
        // Close activity to avoid interference with other parts of the test
        // that also use this activity.
        mActivity.finish();
        mActivity = null;

        enterTestPin();
        mDevice.waitForIdle();
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    private void waitFor(String msg, BooleanSupplier waitFor) {
        int retry = 1;
        do {
            if (waitFor.getAsBoolean()) {
                return;
            }
            Log.d(TAG, msg + " retry=" + retry);
            SystemClock.sleep(200);
        } while (retry++ < 5);
        if (!waitFor.getAsBoolean()) {
            fail(msg + " FAILED");
        }
    }

    private void summonKeyguard() throws Exception {
        Log.d(TAG, "summonKeyguard()");

        final KeyguardManager keyguardManager =
            mDe.getSystemService(KeyguardManager.class);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP);
        getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        waitFor(
            "display to turn off",
            () -> keyguardManager != null && keyguardManager.isKeyguardLocked());
    }

    public void assertLocked() throws Exception {
        Log.d(TAG, "assertLocked()");

        awaitBroadcast(Intent.ACTION_LOCKED_BOOT_COMPLETED);

        assertFalse("CE exists", getTestFile(mCe).exists());
        assertTrue("DE exists", getTestFile(mDe).exists());

        assertFalse("isUserUnlocked", mCe.getSystemService(UserManager.class).isUserUnlocked());
        assertFalse("isUserUnlocked", mDe.getSystemService(UserManager.class).isUserUnlocked());

        assertTrue("AwareProvider", AwareProvider.sCreated);
        assertFalse("UnawareProvider", UnawareProvider.sCreated);

        assertNotNull("AwareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.aware", 0));
        assertNull("UnawareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.unaware", 0));

        assertGetAware(true, 0);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE);
        assertGetAware(false, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertGetUnaware(false, 0);
        assertGetUnaware(false, MATCH_DIRECT_BOOT_AWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertQuery(1, 0);
        assertQuery(1, MATCH_DIRECT_BOOT_AWARE);
        assertQuery(1, MATCH_DIRECT_BOOT_UNAWARE);
        assertQuery(2, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        if (Environment.isExternalStorageEmulated()) {
            assertThat(Environment.getExternalStorageState())
                    .isIn(Arrays.asList(Environment.MEDIA_UNMOUNTED, Environment.MEDIA_REMOVED));

            final File expected = null;
            assertEquals(expected, mCe.getExternalCacheDir());
            assertEquals(expected, mDe.getExternalCacheDir());
        }

        assertViolation(
                new StrictMode.VmPolicy.Builder().detectImplicitDirectBoot()
                        .penaltyLog().build(),
                ImplicitDirectBootViolation.class,
                () -> {
                    final Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
                    mCe.getPackageManager().queryBroadcastReceivers(intent, 0);
                });

        final File ceFile = getTestFile(mCe);
        assertViolation(
                new StrictMode.VmPolicy.Builder().detectCredentialProtectedWhileLocked()
                        .penaltyLog().build(),
                CredentialProtectedWhileLockedViolation.class,
                ceFile::exists);
    }

    public void assertUnlocked() throws Exception {
        Log.d(TAG, "assertUnlocked()");
        awaitBroadcast(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        awaitBroadcast(Intent.ACTION_BOOT_COMPLETED);

        assertTrue("CE exists", getTestFile(mCe).exists());
        assertTrue("DE exists", getTestFile(mDe).exists());

        assertTrue("isUserUnlocked", mCe.getSystemService(UserManager.class).isUserUnlocked());
        assertTrue("isUserUnlocked", mDe.getSystemService(UserManager.class).isUserUnlocked());

        assertTrue("AwareProvider", AwareProvider.sCreated);
        assertTrue("UnawareProvider", UnawareProvider.sCreated);

        assertNotNull("AwareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.aware", 0));
        assertNotNull("UnawareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.unaware", 0));

        assertGetAware(true, 0);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE);
        assertGetAware(false, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertGetUnaware(true, 0);
        assertGetUnaware(false, MATCH_DIRECT_BOOT_AWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertQuery(2, 0);
        assertQuery(1, MATCH_DIRECT_BOOT_AWARE);
        assertQuery(1, MATCH_DIRECT_BOOT_UNAWARE);
        assertQuery(2, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        if (Environment.isExternalStorageEmulated()) {
            pollForExternalStorageMountedState();
            assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());

            final File expected = new File(
                    "/sdcard/Android/data/com.android.cts.encryptionapp/cache");
            assertCanonicalEquals(expected, mCe.getExternalCacheDir());
            assertCanonicalEquals(expected, mDe.getExternalCacheDir());
        }

        assertNoViolation(
                new StrictMode.VmPolicy.Builder().detectImplicitDirectBoot()
                        .penaltyLog().build(),
                () -> {
                    final Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
                    mCe.getPackageManager().queryBroadcastReceivers(intent, 0);
                });

        final File ceFile = getTestFile(mCe);
        assertNoViolation(
                new StrictMode.VmPolicy.Builder().detectCredentialProtectedWhileLocked()
                        .penaltyLog().build(),
                ceFile::exists);
        Log.v(TAG, "assertUnlocked(): Saul Goodman!");
    }

    private void pollForExternalStorageMountedState() {
        for (int i = 0; i < 10; i++) {
            if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
                break;
            }
            SystemClock.sleep(500);
        }
    }

    private void assertQuery(int count, int flags) throws Exception {
        final Intent intent = new Intent(TEST_ACTION);
        assertEquals("activity", count, mPm.queryIntentActivities(intent, flags).size());
        assertEquals("service", count, mPm.queryIntentServices(intent, flags).size());
        assertEquals("provider", count, mPm.queryIntentContentProviders(intent, flags).size());
        assertEquals("receiver", count, mPm.queryBroadcastReceivers(intent, flags).size());
    }

    private void assertGetUnaware(boolean visible, int flags) throws Exception {
        assertGet(visible, false, flags);
    }

    private void assertGetAware(boolean visible, int flags) throws Exception {
        assertGet(visible, true, flags);
    }

    private void assertCanonicalEquals(File expected, File actual) throws Exception {
        assertEquals(expected.getCanonicalFile(), actual.getCanonicalFile());
    }

    private ComponentName buildName(String prefix, String type) {
        return new ComponentName(TEST_PKG, TEST_PKG + "." + prefix + type);
    }

    private void assertGet(boolean visible, boolean aware, int flags) throws Exception {
        final String prefix = aware ? "Aware" : "Unaware";

        ComponentName name;
        ComponentInfo info;

        name = buildName(prefix, "Activity");
        try {
            info = mPm.getActivityInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Service");
        try {
            info = mPm.getServiceInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Provider");
        try {
            info = mPm.getProviderInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Receiver");
        try {
            info = mPm.getReceiverInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }
    }

    private File getTestFile(Context context) {
        return new File(context.getFilesDir(), "test");
    }

    private int getBootCount() throws Exception {
        return Settings.Global.getInt(mDe.getContentResolver(), Settings.Global.BOOT_COUNT);
    }

    private boolean queryFileExists(Uri fileUri) {
        Cursor c = mDe.getContentResolver().query(fileUri, null, null, null, null);
        if (c == null) {
            Log.w(TAG, "Couldn't query for file " + fileUri + "; returning false");
            return false;
        }

        c.moveToFirst();

        int colIndex = c.getColumnIndex("exists");
        if (colIndex < 0) {
            Log.e(TAG, "Column 'exists' does not exist; returning false");
            return false;
        }

        return c.getInt(colIndex) == 1;
    }

    private void awaitBroadcast(String action) throws Exception {
        Log.v(TAG, "awaitBroadcast(" + action + ")");
        String fileName = getBootCount() + "." + action;
        Uri fileUri = FILE_INFO_URI.buildUpon().appendPath(fileName).build();

        TestUtils.waitUntil("Didn't receive broadcast " + action + " for boot " + getBootCount(),
                BOOT_TIMEOUT_SECONDS, () -> queryFileExists(fileUri));
        Log.v(TAG, "awaitBroadcast(" + action + "): Saul Goodman!");
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertViolation(StrictMode.VmPolicy policy,
            Class<? extends Violation> expected, ThrowingRunnable r) throws Exception {
        inspectViolation(policy, r,
                info -> assertThat(info.getViolationClass()).isAssignableTo(expected));
    }

    private static void assertNoViolation(StrictMode.VmPolicy policy, ThrowingRunnable r)
            throws Exception {
        inspectViolation(policy, r,
                info -> assertWithMessage("Unexpected violation").that(info).isNull());
    }

    private static void inspectViolation(StrictMode.VmPolicy policy, ThrowingRunnable violating,
            Consumer<ViolationInfo> consume) throws Exception {
        final LinkedBlockingQueue<ViolationInfo> violations = new LinkedBlockingQueue<>();
        StrictMode.setViolationLogger(violations::add);

        final StrictMode.VmPolicy original = StrictMode.getVmPolicy();
        try {
            StrictMode.setVmPolicy(policy);
            violating.run();
            consume.accept(violations.poll(5, TimeUnit.SECONDS));
        } finally {
            StrictMode.setVmPolicy(original);
            StrictMode.setViolationLogger(null);
        }
    }
}
