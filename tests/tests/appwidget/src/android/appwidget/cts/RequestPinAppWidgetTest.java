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

package android.appwidget.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.UiDeviceUtils.pressHomeButton;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.cts.activity.EmptyActivity;
import android.appwidget.cts.common.Constants;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.cts.util.AppLockSupportRule;
import android.content.pm.cts.util.RequiresAppLockSupported;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.LockSettingsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Instant apps cannot provide or host app widgets")
public class RequestPinAppWidgetTest extends AppWidgetTestCase {
    @Rule
    public final AppLockSupportRule mAppLockSupportRule = new AppLockSupportRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String FIRST_WIDGET_CLASS =
            "android.appwidget.cts.provider.FirstAppWidgetProvider";
    private static final String LAUNCHER_CLASS = "android.appwidget.cts.packages.Launcher";
    private static final String ACTION_PIN_RESULT = "android.appwidget.cts.ACTION_PIN_RESULT";
    private static final String APPBAL_PACKAGE = "android.appwidget.cts.appbal";

    private String mDefaultLauncher;
    private ExecutorService mWaitExecutor = Executors.newSingleThreadExecutor();

    protected WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Before
    public void setUpLauncher() throws Exception {
        mDefaultLauncher = getDefaultLauncher();
    }

    @After
    public void tearDown() throws Exception {
        // Set the launcher back
        setLauncher(mDefaultLauncher, /* waitForLauncher= */ false);
        mWaitExecutor.shutdownNow();

        // Close the activities opened in the test.
        forceStopPackage(APPBAL_PACKAGE);
    }

    @CddTest(requirement = "3.8.2/C-2-2")
    private void runPinWidgetTest(final String launcherPkg) throws Exception {
        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);

        Context context = getInstrumentation().getContext();

        // Request to pin widget
        BlockingBroadcastReceiver setupReceiver = new BlockingBroadcastReceiver()
                .register(Constants.ACTION_SETUP_REPLY);

        Bundle extras = new Bundle();
        extras.putString("dummy", launcherPkg + "-dummy");

        PendingIntent pinResult = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_PIN_RESULT).setPackage(context.getPackageName()),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        assertTrue(
                "requestPinAppWidget returned false",
                AppWidgetManager.getInstance(context)
                        .requestPinAppWidget(getFirstWidgetComponent(), extras, pinResult));

        setupReceiver.await();
        // Verify that the confirmation dialog was opened
        assertTrue(setupReceiver.result.getBooleanExtra(Constants.EXTRA_SUCCESS, false));
        assertEquals(launcherPkg, setupReceiver.result.getStringExtra(Constants.EXTRA_PACKAGE));

        LauncherApps.PinItemRequest req =
                setupReceiver.result.getParcelableExtra(Constants.EXTRA_REQUEST);
        assertNotNull(req);
        // Verify that multiple calls to getAppWidgetProviderInfo have proper dimension.
        boolean[] providerInfo = verifyInstalledProviders(Arrays.asList(
                req.getAppWidgetProviderInfo(context), req.getAppWidgetProviderInfo(context)));
        assertTrue(providerInfo[0]);
        assertNotNull(req.getExtras());
        assertEquals(launcherPkg + "-dummy", req.getExtras().getString("dummy"));

        // Accept the request
        BlockingBroadcastReceiver resultReceiver = new BlockingBroadcastReceiver()
                .register(ACTION_PIN_RESULT);
        context.sendBroadcast(new Intent(Constants.ACTION_CONFIRM_PIN)
                .setPackage(launcherPkg)
                .putExtra("dummy", "dummy-2"));
        resultReceiver.await();

        // Verify that the result contain the extras
        assertEquals("dummy-2", resultReceiver.result.getStringExtra("dummy"));
    }

    @Test
    public void testPinWidget_launcher1() throws Exception {
        runPinWidgetTest("android.appwidget.cts.packages.launcher1");
    }

    @Test
    public void testPinWidget_launcher2() throws Exception {
        runPinWidgetTest("android.appwidget.cts.packages.launcher2");
    }

    @CddTest(requirement = "3.8.2/C-2-1")
    public void verifyIsRequestPinAppWidgetSupported(String launcherPkg, boolean expectedSupport)
            throws Exception {
        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);

        Context context = getInstrumentation().getContext();
        assertEquals(expectedSupport,
                AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported());
    }

    @Test
    public void testIsRequestPinAppWidgetSupported_launcher1() throws Exception {
        verifyIsRequestPinAppWidgetSupported("android.appwidget.cts.packages.launcher1", true);
    }

    @Test
    public void testIsRequestPinAppWidgetSupported_launcher2() throws Exception {
        verifyIsRequestPinAppWidgetSupported("android.appwidget.cts.packages.launcher2", true);
    }

    @Test
    public void testIsRequestPinAppWidgetSupported_launcher3() throws Exception {
        verifyIsRequestPinAppWidgetSupported("android.appwidget.cts.packages.launcher3", false);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    @RequiresAppLockSupported
    @RequiresFlagsEnabled({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.appwidget.flags.Flags.FLAG_APP_LOCK_WIDGET_REMOVAL
    })
    @ApiTest(apis = { "android.appwidget.AppWidgetManager#isRequestPinAppWidgetSupported" })
    public void testIsRequestPinAppWidgetSupported_whenAppLockIsEnabled_returnsFalse() throws
            Exception {
        final Context context = getInstrumentation().getContext();
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final String launcherPkg = "android.appwidget.cts.packages.launcher1";

        // Setup a valid launcher and verify pinning is supported before enabling App Lock.
        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);
        assertThat(appWidgetManager.isRequestPinAppWidgetSupported()).isTrue();

        try (AutoCloseable withLockScreen = new LockSettingsUtil(context).withLockScreen();
                AutoCloseable withAppLockEnabled = setAppLock(context)) {
            assertThat(appWidgetManager.isRequestPinAppWidgetSupported()).isFalse();
        }
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    @RequiresAppLockSupported
    @RequiresFlagsEnabled({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.appwidget.flags.Flags.FLAG_APP_LOCK_WIDGET_REMOVAL
    })
    @ApiTest(apis = { "android.appwidget.AppWidgetManager#requestPinAppWidget" })
    public void testRequestPinAppWidget_whenAppLockIsEnabled_returnsFalse() throws Exception {
        final Context context = getInstrumentation().getContext();
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final String launcherPkg = "android.appwidget.cts.packages.launcher1";
        final ComponentName provider = new ComponentName(context.getPackageName(),
                FIRST_WIDGET_CLASS);

        // Setup a valid launcher and verify pinning is supported before enabling App Lock.
        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);
        assertThat(appWidgetManager.requestPinAppWidget(provider, /* extras= */ null,
                /* successCallback= */ null)).isTrue();

        try (AutoCloseable withLockScreen = new LockSettingsUtil(context).withLockScreen();
                AutoCloseable withAppLockEnabled = setAppLock(context)) {
            assertThat(appWidgetManager.requestPinAppWidget(provider, /* extras= */ null,
                    /* successCallback= */ null)).isFalse();
        }
    }

    /**
     * Enables App Lock for the current package and returns an {@link AutoCloseable} that reverts
     * the state when closed.
     */
    private AutoCloseable setAppLock(Context context) {
        final String packageName = context.getPackageName();
        final PackageManager packageManager = context.getPackageManager();

        // Enable App Lock.
        setAppLockState(packageManager, packageName, /* state= */ true);

        // Disable App Lock.
        return () -> setAppLockState(packageManager, packageName, /* state= */ false);
    }

    /** Helper method to set the App Lock state. */
    private void setAppLockState(PackageManager packageManager, String packageName, boolean state) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final boolean isAppLockStateChanged =
                    packageManager.setPackageAppLockEnabled(packageName, state);
            assertThat(isAppLockStateChanged).isTrue();
        }, Manifest.permission.TEST_LOCK_APPS);
    }

    private String getDefaultLauncher() throws Exception {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : runShellCommand("cmd shortcut get-default-launcher --user "
                + getInstrumentation().getContext().getUserId())) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new Exception("Default launcher not found");
    }

    private void setLauncher(String component, boolean waitForLauncher) throws Exception {
        Log.i("BalActivity", "cmd package set-home-activity --user "
                + getInstrumentation().getContext().getUserId() + " " + component);
        final String previousLauncher = getDefaultLauncher();
        runShellCommand("cmd package set-home-activity --user "
                + getInstrumentation().getContext().getUserId() + " " + component);
        if (waitForLauncher) {
            waitForLauncher(component);
        }
        // Force restarting home activities so the new launcher takes effect
        forceStopPackage(ComponentName.unflattenFromString(previousLauncher).getPackageName());
    }

    private void setLauncher(String component) throws Exception {
        setLauncher(component, /* waitForLauncher= */ true);
    }

    /**
     * Poll for the launcher to match the specified component, timeout after 60s.
     */
    private void waitForLauncher(String component) {
        Future<?> waitFuture = mWaitExecutor.submit(() -> {
            // Poll until the default launcher is updated in shortcut service.
            while (true) {
                try {
                    if (getDefaultLauncher().equals(component)) break;
                    Thread.sleep(25);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        try {
            waitFuture.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Error while waiting for launcher to be: " + component, e);
        }
    }

    @Test
    public void testRequestPinAppWidgetNotAllowBal_indirectLaunch() throws Exception {
        runBalTest(/* directActivityLaunch= */ false, /* isBalAllowed= */ false);
    }

    @Test
    public void testRequestPinAppWidgetAllowBal_directLaunch() throws Exception {
        runBalTest(/* directActivityLaunch= */ true, /* isBalAllowed= */ true);
    }

    /**
     * Runs the background activity launch test by calling requestPinAppWidget with a PendingIntent
     * that causes an activity to be launched.
     *
     * @param directActivityLaunch If true, the PendingIntent callback given to requestPinAppWidget
     *     will directly launch an activity. If false, the PendingIntent will launch a background
     *     service that which launches an activity.
     * @param isBalAllowed If true, verifies that the background app launch was allowed. If false,
     *     verifies that the launch was blocked.
     */
    private void runBalTest(boolean directActivityLaunch, boolean isBalAllowed) throws Exception {
        String launcherPkg = "android.appwidget.cts.packages.launcher1";
        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);
        Context context = getInstrumentation().getContext();
        // Request to pin widget
        BlockingBroadcastReceiver setupReceiver = new BlockingBroadcastReceiver()
                .register(Constants.ACTION_SETUP_REPLY);

        // starts the BalActivity in the test app AppBal.
        context.startActivity(
                new Intent(Intent.ACTION_MAIN)
                        .setPackage(APPBAL_PACKAGE)
                        .putExtra(Constants.EXTRA_DIRECT_LAUNCH, directActivityLaunch)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK));

        setupReceiver.await();
        // Verify that the confirmation dialog was opened
        assertTrue(setupReceiver.result.getBooleanExtra(Constants.EXTRA_SUCCESS, false));

        // Accept the request
        context.sendBroadcast(new Intent(Constants.ACTION_CONFIRM_PIN)
                .setPackage(launcherPkg));

        if (!directActivityLaunch) {
            // Press home key to ensure stopAppSwitches is called because the last-stop-app-switch-time
            // is a criteria of allowing background start.
            pressHomeButton();
            SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
            mWmState.waitForHomeActivityVisible();
            SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
        }

        boolean result = false;
        // The background activity will be launched 11s after the BalService starts. The
        // waitForFocusedActivity only waits for 5s. So put it in a for loop.
        for (int i = 0; i < 5; i++) {
            result = mWmState.waitForFocusedActivity(
                    "Empty Activity is launched", new ComponentName(context, EmptyActivity.class));
            if (result) break;
        }
        if (isBalAllowed) {
            assertTrue("Should be able to launch background activity", result);
        } else {
            assertFalse("Should not able to launch background activity", result);
        }
    }

    private void forceStopPackage(String packageName) {
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        getInstrumentation()
                                .getContext()
                                .getSystemService(ActivityManager.class)
                                .forceStopPackage(packageName));
    }
}
