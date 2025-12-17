/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.app.cts;

import static android.app.Flags.FLAG_APP_START_INFO;
import static android.app.Flags.FLAG_USE_APP_INFO_NOT_LAUNCHED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.BootReceiver;
import android.app.stubs.ISecondary;
import android.app.stubs.SimpleActivity;
import android.app.stubs.shared.CommandReceiver;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ForceStopTest {

    // A simple test activity from another package.
    private static final String APP_PACKAGE = "com.android.app1";
    private static final String APP_APK = "/data/local/tmp/cts/apps/CtsAppTestStubsApp1.apk";
    private static final String APP_ACTIVITY = "android.app.stubs.SimpleActivity";
    private static final String APP_PROVIDER_PACKAGE = "com.android.app.cts.provider";
    private static final String SECONDARY_MAIN_ACTION = "android.app.stubs.ISecondaryMain";

    private static final int INVALID_REASON = -2;
    private static final long DELAY_MILLIS = 10_000;
    private static final long SHORT_DELAY_MILLIS = 1_000;

    private Context mTargetContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        AmUtils.waitForBroadcastBarrier();
    }

    private Intent createSimpleActivityIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(APP_PACKAGE);
        intent.setClassName(APP_PACKAGE, APP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private ActivityReceiverFilter forceStopAndStartSimpleActivity(Intent intent) throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(intent.getPackage()));
        ActivityReceiverFilter appStartedReceiver =
                new ActivityReceiverFilter(mTargetContext, SimpleActivity.ACTION_ACTIVITY_STARTED);
        // Start an activity of another APK.
        mTargetContext.startActivity(intent);
        assertThat(appStartedReceiver.waitForActivity()).isTrue();
        return appStartedReceiver;
    }

    @Test
    public void testPackageStoppedState() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();
        forceStopAndStartSimpleActivity(intent);

        assertWithMessage("Package " + packageName + " shouldn't be in the stopped state")
                .that(mPackageManager.isPackageStopped(packageName))
                .isFalse();

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
        assertWithMessage("Package " + packageName + " should be in the stopped state")
                .that(mPackageManager.isPackageStopped(packageName))
                .isTrue();
    }

    @Test
    public void testPackageRestartedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final BlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
        final long preStopTimestampMs = SystemClock.elapsedRealtime();
        registerPackageEventReceiver(Intent.ACTION_PACKAGE_RESTARTED, packageName, timestampQueue);

        forceStopAndStartSimpleActivity(intent);

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));

        final Long timestampMs = timestampQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
        assertWithMessage("Didn't get ACTION_PACKAGE_RESTARTED").that(timestampMs).isNotNull();

        assertWithMessage("EXTRA_TIME " + timestampMs + " not after " + preStopTimestampMs)
                .that(timestampMs >= preStopTimestampMs)
                .isTrue();
    }

    @Test
    public void testPackageUnstoppedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final BlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
        final long preUnstopTimestampMs = SystemClock.elapsedRealtime();
        registerPackageEventReceiver(Intent.ACTION_PACKAGE_UNSTOPPED, packageName, timestampQueue);

        forceStopAndStartSimpleActivity(intent);

        final Long timestampMs = timestampQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
        assertWithMessage("Didn't get ACTION_PACKAGE_UNSTOPPED").that(timestampMs).isNotNull();

        assertWithMessage("EXTRA_TIME " + timestampMs + " not after " + preUnstopTimestampMs)
                .that(timestampMs >= preUnstopTimestampMs)
                .isTrue();

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    @Test
    @AppModeFull(reason = "Instant apps don't get BOOT_COMPLETED broadcasts")
    public void testBootCompletedBroadcasts_activity() throws Exception {
        final Intent intent = createSimpleActivityIntent();

        final BootCompletedReceiver bootCompletedReceiver = new BootCompletedReceiver();
        bootCompletedReceiver.register(mTargetContext);
        final ActivityStartedReceiver activityStartedReceiver = new ActivityStartedReceiver();
        activityStartedReceiver.register(mTargetContext);

        mTargetContext.startActivity(intent);

        assertWithMessage("Activity didn't start")
                .that(activityStartedReceiver.mGotActivityStarted.block(DELAY_MILLIS))
                .isTrue();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        mTargetContext.startActivity(intent);

        assertWithMessage("Didn't get LOCKED_BOOT_COMPLETED")
                .that(bootCompletedReceiver.mGotLockedBoot.block(DELAY_MILLIS))
                .isTrue();
        assertWithMessage("Didn't get BOOT_COMPLETED")
                .that(bootCompletedReceiver.mGotBoot.block(DELAY_MILLIS))
                .isTrue();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    // Verifies that no BOOT_COMPLETED broadcasts are received on first launch for given action
    private void verifyNoBootCompletedBroadcastsOnFirstLaunch(Runnable actionToTriggerAppStart)
            throws Exception {
        // Re-install the app to reset the notLaunched package state
        executeShellCommand("pm uninstall " + APP_PACKAGE);
        executeShellCommand("pm install -r -g --force-queryable " + APP_APK);

        final ConditionVariable gotAppStarted = new ConditionVariable();
        final BootCompletedReceiver receiver = new BootCompletedReceiver();
        receiver.register(mTargetContext);

        actionToTriggerAppStart.run();

        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        gotAppStarted.open();
                    }
                });

        assertWithMessage("App didn't start").that(gotAppStarted.block(DELAY_MILLIS)).isTrue();

        assertWithMessage("Got unexpected LOCKED_BOOT_COMPLETED")
                .that(receiver.mGotLockedBoot.block(DELAY_MILLIS))
                .isFalse();
        assertWithMessage("Got unexpected BOOT_COMPLETED")
                .that(receiver.mGotBoot.block(SHORT_DELAY_MILLIS))
                .isFalse();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for an activity start.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    @AppModeFull(reason = "Instant apps don't get BOOT_COMPLETED broadcasts")
    public void testNoBootCompletedBroadcastsOnFirstLaunch_activity() throws Exception {
        verifyNoBootCompletedBroadcastsOnFirstLaunch(
                () -> {
                    final Intent intent = createSimpleActivityIntent();
                    mTargetContext.startActivity(intent);
                });
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for a broadcast.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    @AppModeFull(reason = "Instant apps don't get BOOT_COMPLETED broadcasts")
    public void testNoBootCompletedBroadcastsOnFirstLaunch_broadcast() throws Exception {
        verifyNoBootCompletedBroadcastsOnFirstLaunch(
                () ->
                        CommandReceiver.sendCommandWithResultReceiver(
                                mTargetContext,
                                CommandReceiver.COMMAND_EMPTY,
                                APP_PACKAGE,
                                APP_PACKAGE,
                                0,
                                null,
                                null));
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for a service binding.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    @AppModeFull(reason = "Instant apps don't get BOOT_COMPLETED broadcasts")
    public void testNoBootCompletedBroadcastsOnFirstLaunch_bindService() throws Exception {
        verifyNoBootCompletedBroadcastsOnFirstLaunch(
                () -> {
                    int startReason = getStartReasonFromAppPackageService();
                    assertWithMessage("ForceStop reason should not be returned, should be -ve")
                            .that(startReason)
                            .isNotEqualTo(ApplicationStartInfo.START_REASON_SERVICE);
                });
    }

    @Test
    @AppModeFull(reason = "Instant apps don't get BOOT_COMPLETED broadcasts")
    public void testBootCompletedBroadcasts_broadcast() throws Exception {
        final ConditionVariable appStarted = new ConditionVariable();
        final BootCompletedReceiver receiver = new BootCompletedReceiver();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        appStarted.open();
                    }
                });

        assertWithMessage("App didn't start").that(appStarted.block(DELAY_MILLIS)).isTrue();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        AmUtils.waitForBroadcastBarrier();

        receiver.register(mTargetContext);

        CommandReceiver.sendCommand(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null);

        assertWithMessage("Didn't get LOCKED_BOOT_COMPLETED")
                .that(receiver.mGotLockedBoot.block(DELAY_MILLIS))
                .isTrue();
        assertWithMessage("Didn't get BOOT_COMPLETED")
                .that(receiver.mGotBoot.block(DELAY_MILLIS))
                .isTrue();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    private void registerPackageEventReceiver(
            String action, String packageName, BlockingQueue<Long> queue) {
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String intentAction = intent.getAction();
                        final Uri uri = intent.getData();
                        final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                        if (action.equals(intentAction) && packageName.equals(pkg)) {
                            queue.offer(intent.getLongExtra(Intent.EXTRA_TIME, 0L));
                        }
                    }
                };
        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(action);
        mTargetContext.registerReceiver(receiver, filter);
    }

    private void clearHistoricalStartInfo() throws Exception {
        executeShellCommand("am clear-start-info --user all " + APP_PACKAGE);
    }

    @Test
    @Ignore("b/415721228 - Fix and re-enable")
    @RequiresFlagsEnabled(FLAG_APP_START_INFO)
    public void testApplicationStartInfoWasForceStopped_bindService() throws Exception {
        clearHistoricalStartInfo();
        // Check bindService after a force-stop
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        int startReason = getStartReasonFromAppPackageService();
        assertWithMessage("ForceStop reason is not SERVICE")
                .that(startReason)
                .isEqualTo(ApplicationStartInfo.START_REASON_SERVICE);

        clearHistoricalStartInfo();
        // Check bindService after stop-app
        executeShellCommand("am stop-app --user " + mTargetContext.getUserId() + " " + APP_PACKAGE);
        startReason = getStartReasonFromAppPackageService();
        assertWithMessage("ForceStop reason should not be returned, should be -ve")
                .that(startReason)
                .isNotEqualTo(ApplicationStartInfo.START_REASON_SERVICE);

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_START_INFO)
    public void testApplicationStartInfoWasForceStopped_activity() throws Exception {
        clearHistoricalStartInfo();

        // Trigger an app start via service binding
        final int firstStartReason = getStartReasonFromAppPackageService();

        // Force-stop the app
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        final Intent intent = createSimpleActivityIntent();

        final ActivityReceiverFilter activityStartedReceiver =
                new ActivityReceiverFilter(mTargetContext, SimpleActivity.ACTION_ACTIVITY_STARTED);

        // Check startActivity after a force-stop
        mTargetContext.startActivity(intent);
        assertWithMessage("Activity didn't start")
                .that(activityStartedReceiver.waitForActivity())
                .isTrue();

        final int startReason = getStartReasonFromAppPackageService();
        assertWithMessage("ForceStop reason is not ACTIVITY")
                .that(startReason)
                .isEqualTo(ApplicationStartInfo.START_REASON_START_ACTIVITY);

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    /**
     * Returns the start reason only if the app was force-stopped earlier, else returns -ve.
     */
    private int getStartReasonFromAppPackageService() {
        final BlockingQueue<Integer> reasonQueue = new LinkedBlockingQueue<>();
        Intent serviceIntent = new Intent(SECONDARY_MAIN_ACTION);
        serviceIntent.setPackage(APP_PACKAGE);
        final ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        try {
                            reasonQueue.offer(
                                    ISecondary.Stub.asInterface(service)
                                            .getWasForceStoppedReason());
                        } catch (RemoteException re) {
                            // Expected in some cases, so we just unblock the poll.
                            // The caller will get a value that's not a valid reason.
                            reasonQueue.offer(INVALID_REASON);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        try {
            mTargetContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
            Integer reason = reasonQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
            assertWithMessage("Couldn't connect to android.app.stubs.ISecondaryMain")
                    .that(reason)
                    .isNotNull();
            return reason;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mTargetContext.unbindService(connection);
        }
    }

    @Test
    public void testPendingIntentCancellation() throws Exception {
        final PendingIntent pendingIntent = triggerPendingIntentCreation(APP_PACKAGE);
        assertThat(pendingIntent).isNotNull();

        final ConditionVariable pendingIntentCancelled = new ConditionVariable();
        pendingIntent.addCancelListener(mTargetContext.getMainExecutor(), pi -> {
            if (pendingIntent.equals(pi)) {
                pendingIntentCancelled.open();
            }
        });

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        assertWithMessage("Package " + APP_PACKAGE + " should be in the stopped state")
                .that(mPackageManager.isPackageStopped(APP_PACKAGE))
                .isTrue();

        // Verify that pending intent gets cancelled when the app that created it is force-stopped.
        assertWithMessage("Did not receive PendingIntent cancellation callback")
                .that(pendingIntentCancelled.block(DELAY_MILLIS))
                .isTrue();
        assertThrows(CanceledException.class, pendingIntent::send);

        // Trigger the PendingIntent creation to verify the app can create new PendingIntents
        // as usual.
        final PendingIntent pendingIntent2 = triggerPendingIntentCreation(APP_PACKAGE);
        assertThat(pendingIntent2).isNotNull();

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    public void testStickyBroadcastDispatch() throws Exception {
        final String pkg = APP_PROVIDER_PACKAGE;
        final IntentFilter intentFilter = triggerStickyBroadcastDispatch(pkg);
        assertThat(intentFilter).isNotNull();

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(pkg));
        assertWithMessage("Package " + pkg + " should be in the stopped state")
                .that(mPackageManager.isPackageStopped(pkg))
                .isTrue();

        // Register a receiver which involves intent-filter resolution and then verify
        // that this intent-filter resolution does not bring the broadcast sender out of
        // force-stop state.
        final Intent stickyIntent = mTargetContext.registerReceiver(null, intentFilter);
        assertThat(stickyIntent).isNotNull();

        assertWithMessage("Package " + pkg + " should still be in the stopped state")
                .that(mPackageManager.isPackageStopped(pkg))
                .isTrue();
    }

    private PendingIntent triggerPendingIntentCreation(final String packageName) throws Exception {
        final BlockingQueue<PendingIntent> blockingQueue = new LinkedBlockingQueue<>();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                packageName, packageName, Intent.FLAG_RECEIVER_FOREGROUND, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final PendingIntent pi = getResultExtras(true).getParcelable(
                                CommandReceiver.KEY_PENDING_INTENT, PendingIntent.class);
                        if (pi != null) {
                            blockingQueue.offer(pi);
                        }
                    }
                });
        return blockingQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private IntentFilter triggerStickyBroadcastDispatch(String packageName) throws Exception {
        final BlockingQueue<IntentFilter> blockingQueue = new LinkedBlockingQueue<>();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_SEND_STICKY_BROADCAST,
                packageName, packageName, Intent.FLAG_RECEIVER_FOREGROUND, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final IntentFilter intentFilter = getResultExtras(true).getParcelable(
                                CommandReceiver.KEY_STICKY_BROADCAST_FILTER, IntentFilter.class);
                        if (intentFilter != null) {
                            blockingQueue.offer(intentFilter);
                        }
                    }
                });
        return blockingQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Receiver that listens for BOOT_COMPLETED broadcasts. */
    private static final class BootCompletedReceiver extends BroadcastReceiver {
        final ConditionVariable mGotLockedBoot = new ConditionVariable();
        final ConditionVariable mGotBoot = new ConditionVariable();

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                final String extraAction =
                        intent.getStringExtra(BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                    mGotLockedBoot.open();
                } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                    mGotBoot.open();
                }
            }
        }

        void register(Context context) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
        }
    }

    /** Receiver that listens for ACTIVITY_STARTED broadcasts. */
    private static final class ActivityStartedReceiver extends BroadcastReceiver {
        final ConditionVariable mGotActivityStarted = new ConditionVariable();

        @Override
        public void onReceive(Context context, Intent intent) {
            if (SimpleActivity.ACTION_ACTIVITY_STARTED.equals(intent.getAction())) {
                mGotActivityStarted.open();
            }
        }

        void register(Context context) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(SimpleActivity.ACTION_ACTIVITY_STARTED);
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
        }
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private static final class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private final String mActivityToFilter;
        private final ConditionVariable mBroadcastCondition = new ConditionVariable();
        private final Context mTargetContext;

        // Create the filter with the intent to look for.
        ActivityReceiverFilter(Context targetContext, String activityToFilter) {
            mActivityToFilter = activityToFilter;
            mTargetContext = targetContext;
            final IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mTargetContext.registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                mBroadcastCondition.open();
                mTargetContext.unregisterReceiver(this);
            }
        }

        public boolean waitForActivity() {
            AmUtils.waitForBroadcastBarrier();
            // Wait for the broadcast
            return mBroadcastCondition.block(DELAY_MILLIS);
        }
    }

    private String executeShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        return uiDevice.executeShellCommand(cmd).trim();
    }
}
