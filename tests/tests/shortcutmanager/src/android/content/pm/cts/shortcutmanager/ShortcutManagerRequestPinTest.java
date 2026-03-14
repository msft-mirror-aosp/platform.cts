/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.content.pm.cts.shortcutmanager;

import static android.Manifest.permission.TEST_LOCK_APPS;
import static android.server.wm.UiDeviceUtils.pressHomeButton;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.content.pm.cts.util.AppLockSupportRule;
import android.content.pm.cts.util.RequiresAppLockSupported;
import android.os.PersistableBundle;
import android.os.Process;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.LockSettingsUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;

@CddTest(requirement="3.8.1/C-4-1")
@RunWith(AndroidJUnit4.class)
public class ShortcutManagerRequestPinTest extends ShortcutManagerCtsTestsBase {
    @Rule
    public final AppLockSupportRule mAppLockSupportRule = new AppLockSupportRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "ShortcutMRPT";

    private static final String SHORTCUT_ID = "s12345";
    private static final String HIDDEN_SHORTCUT_ID = "s24680";

    protected WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @CddTest(requirement="[3.8.1/C-2-1],[3.8.1/C-3-1]")
    @Test
    public void testIsRequestPinShortcutSupported() {

        // Launcher 1 supports it.
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().isRequestPinShortcutSupported());
        });

        // Launcher 4 does *not* supports it.
        setDefaultLauncher(getInstrumentation(), mLauncherContext4);

        runWithCaller(mPackageContext1, () -> {
            assertFalse(getManager().isRequestPinShortcutSupported());
        });
    }

    /**
     * A test for {@link ShortcutManager#requestPinShortcut}, a very simple case.
     */
    @Test
    public void testRequestPinShortcut() {
        Log.i(TAG, "Testing with launcher1.");

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().isRequestPinShortcutSupported());

            ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
                final PersistableBundle extras = new PersistableBundle();
                extras.putString(Constants.EXTRA_REPLY_ACTION, replyAction);
                extras.putString(Constants.LABEL, "label1");

                final ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID)
                        .setShortLabel("label1")
                        .setIntent(new Intent(Intent.ACTION_MAIN))
                        .setExtras(extras)
                        .build();

                // Note: Because requestPinShortcut() won't update the shortcut, but we need to
                // update the extras that contains the broadcast ID, we need to update the shortcut
                // manually here before requestPinShortcut().

                // This is only needed when a shortcut is already published with the same ID.
                assertTrue(getManager().updateShortcuts(list(shortcut)));

                Log.i(TAG, "Calling requestPinShortcut...");
                assertTrue(getManager().requestPinShortcut(shortcut, /* intent sender */ null));
                Log.i(TAG, "Done.");
            });
        });
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final ShortcutQuery query = new ShortcutQuery()
                    .setPackage(mPackageContext1.getPackageName())
                    .setShortcutIds(list(SHORTCUT_ID))
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | ShortcutQuery.FLAG_MATCH_PINNED | ShortcutQuery.FLAG_MATCH_MANIFEST);
            Log.i(TAG, "Waiting for shortcut to be visible to launcher...");
            retryUntil(() -> {
                final List<ShortcutInfo> shortcuts = getLauncherApps().getShortcuts(query,
                        Process.myUserHandle());
                if (shortcuts == null) {
                    // Launcher not responded yet.
                    return false;
                }
                assertWith(shortcuts)
                        .haveIds(SHORTCUT_ID)
                        .areAllPinned()
                        .areAllNotDynamic()
                        .areAllNotManifest();
                return true;
            }, "Shortcut still not pinned");
        });
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getPinnedShortcuts())
                    .forShortcutWithId(SHORTCUT_ID, si -> {
                        assertEquals("label1", si.getShortLabel());
                    })
                    .areAllPinned()
                    .areAllNotDynamic()
                    .areAllNotManifest()
                    .areAllMutable()
                    ;
        });

        Log.i(TAG, "Done testing with launcher1.");
    }

    @Test
    public void testRequestPinShortcut_multiLaunchers() {
        testRequestPinShortcut();

        Log.i(TAG, "Testing with launcher2.");

        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
                final PersistableBundle extras = new PersistableBundle();
                extras.putString(Constants.EXTRA_REPLY_ACTION, replyAction);
                extras.putString(Constants.LABEL, "label1");

                final ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID)
                        .setExtras(extras)
                        .build();

                // Note: Because requestPinShortcut() won't update the shortcut, but we need to
                // update the extras that contains the broadcast ID, we need to update the shortcut
                // manually here before requestPinShortcut().
                assertTrue(getManager().updateShortcuts(list(shortcut)));

                Log.i(TAG, "Calling requestPinShortcut...");
                assertTrue(getManager().requestPinShortcut(shortcut, /* intent sender */ null));
                Log.i(TAG, "Done.");
            });
        });
        runWithCallerWithStrictMode(mLauncherContext2, () -> {
            final ShortcutQuery query = new ShortcutQuery()
                    .setPackage(mPackageContext1.getPackageName())
                    .setShortcutIds(list(SHORTCUT_ID))
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | ShortcutQuery.FLAG_MATCH_PINNED | ShortcutQuery.FLAG_MATCH_MANIFEST);
            Log.i(TAG, "Waiting for shortcut to be visible to launcher...");
            retryUntil(() -> {
                final List<ShortcutInfo> shortcuts = getLauncherApps().getShortcuts(query,
                        Process.myUserHandle());
                if (shortcuts == null) {
                    // Launcher not responded yet.
                    return false;
                }
                assertWith(shortcuts)
                        .haveIds(SHORTCUT_ID)
                        .areAllPinned()
                        .areAllNotDynamic()
                        .areAllNotManifest();
                return true;
            }, "Shortcut still not pinned");
        });
        Log.i(TAG, "Done testing with launcher2.");
    }

    @Test
    public void testRequestPinShortcut_multiLaunchers_withDynamic() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        // Publish as a dynamic shortcut first, then call requestPin.
        ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID)
                .setShortLabel("label1")
                .setIntent(new Intent(Intent.ACTION_MAIN))
                .build();
        assertTrue(getManager().setDynamicShortcuts(list(shortcut)));

        // ==============================================================
        Log.i(TAG, "Testing with launcher1.");

        assertTrue(getManager().isRequestPinShortcutSupported());

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
                final PersistableBundle extras = new PersistableBundle();
                extras.putString(Constants.EXTRA_REPLY_ACTION, replyAction);
                extras.putString(Constants.LABEL, "label1");

                final ShortcutInfo shortcut2 = makeShortcutBuilder(SHORTCUT_ID)
                        .setExtras(extras)
                        .build();

                // Note: Because requestPinShortcut() won't update the shortcut, but we need to
                // update the extras that contains the broadcast ID, we need to update the shortcut
                // manually here before requestPinShortcut().

                // This is only needed when a shortcut is already published with the same ID.
                assertTrue(getManager().updateShortcuts(list(shortcut2)));

                Log.i(TAG, "Calling requestPinShortcut...");
                assertTrue(getManager().requestPinShortcut(shortcut2, /* intent sender */ null));
                Log.i(TAG, "Done.");
            });
        });
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final ShortcutQuery query = new ShortcutQuery()
                    .setPackage(mPackageContext1.getPackageName())
                    .setShortcutIds(list(SHORTCUT_ID))
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | ShortcutQuery.FLAG_MATCH_PINNED | ShortcutQuery.FLAG_MATCH_MANIFEST);
            Log.i(TAG, "Waiting for shortcut to be visible to launcher...");
            retryUntil(() -> {
                final List<ShortcutInfo> shortcuts = getLauncherApps().getShortcuts(query,
                        Process.myUserHandle());
                if (shortcuts == null) {
                    // Launcher not responded yet.
                    return false;
                }
                assertWith(shortcuts)
                        .haveIds(SHORTCUT_ID)
                        .areAllPinned()
                        .areAllDynamic()
                        .areAllNotManifest();
                return true;
            }, "Shortcut still not pinned");
        });
        // ==============================================================
        Log.i(TAG, "Testing with launcher2.");
        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        assertTrue(getManager().isRequestPinShortcutSupported());

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
                final PersistableBundle extras = new PersistableBundle();
                extras.putString(Constants.EXTRA_REPLY_ACTION, replyAction);
                extras.putString(Constants.LABEL, "label1");

                final ShortcutInfo shortcut2 = makeShortcutBuilder(SHORTCUT_ID)
                        .setExtras(extras)
                        .build();

                // Note: Because requestPinShortcut() won't update the shortcut, but we need to
                // update the extras that contains the broadcast ID, we need to update the shortcut
                // manually here before requestPinShortcut().

                // This is only needed when a shortcut is already published with the same ID.
                assertTrue(getManager().updateShortcuts(list(shortcut2)));

                Log.i(TAG, "Calling requestPinShortcut...");
                assertTrue(getManager().requestPinShortcut(shortcut2, /* intent sender */ null));
                Log.i(TAG, "Done.");
            });
        });
        runWithCallerWithStrictMode(mLauncherContext2, () -> {
            final ShortcutQuery query = new ShortcutQuery()
                    .setPackage(mPackageContext1.getPackageName())
                    .setShortcutIds(list(SHORTCUT_ID))
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | ShortcutQuery.FLAG_MATCH_PINNED | ShortcutQuery.FLAG_MATCH_MANIFEST);
            Log.i(TAG, "Waiting for shortcut to be visible to launcher...");
            retryUntil(() -> {
                final List<ShortcutInfo> shortcuts = getLauncherApps().getShortcuts(query,
                        Process.myUserHandle());
                if (shortcuts == null) {
                    // Launcher not responded yet.
                    return false;
                }
                assertWith(shortcuts)
                        .haveIds(SHORTCUT_ID)
                        .areAllPinned()
                        .areAllDynamic()
                        .areAllNotManifest();
                return true;
            }, "Shortcut still not pinned");
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getPinnedShortcuts())
                    .forShortcutWithId(SHORTCUT_ID, si -> {
                        assertEquals("label1", si.getShortLabel());
                    })
                    .areAllPinned()
                    .areAllDynamic()
                    .areAllNotManifest()
                    .areAllMutable()
            ;
        });
    }

    /**
     * Same as {@link ShortcutManager#requestPinShortcut} except the app has no main activities.
     */
    @Test
    public void testRequestPinShortcut_noMainActivity() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final PackageManager pm = getTestContext().getPackageManager();
        final HashMap<ComponentName, Integer> originalState = new HashMap<>();
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent().setPackage(mPackageContext1.getPackageName()), 0)) {
                final ActivityInfo activityInfo = ri.activityInfo;
                final ComponentName componentName =
                        new ComponentName(activityInfo.packageName, activityInfo.name);

                originalState.put(componentName, pm.getComponentEnabledSetting(componentName));
                Log.i(TAG, "Disabling " + componentName);
                pm.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }

            testRequestPinShortcut();

            runWithCaller(mPackageContext1, () -> {
                assertWith(getManager().getPinnedShortcuts())
                        .areAllPinned()
                        .areAllNotDynamic()
                        .areAllNotManifest()
                        .areAllMutable()
                        .areAllWithActivity(null)
                ;
                assertWith(getManager().getManifestShortcuts()).isEmpty();
                assertWith(getManager().getDynamicShortcuts()).isEmpty();
            });
        } finally {
            // Restore the original state.
            for (HashMap.Entry<ComponentName, Integer> e : originalState.entrySet()) {
                pm.setComponentEnabledSetting(e.getKey(), e.getValue()
                        , PackageManager.DONT_KILL_APP);
            }
        }
    }

    /**
     * Same as {@link ShortcutManager#requestPinShortcut} except the app has no main activities.
     */
    @Test
    public void testRequestPinShortcutExcludedFromLauncher_ThrowsException() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutInfo shortcut = makeShortcutBuilder(HIDDEN_SHORTCUT_ID)
                    .setExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER)
                    .build();

            Log.i(TAG, "Calling requestPinShortcut...");
            boolean isIllegalArgumentExceptionThrown = false;
            try {
                assertTrue(getManager().requestPinShortcut(shortcut, /* intent sender */ null));
            } catch (IllegalArgumentException e) {
                isIllegalArgumentExceptionThrown = true;
            }
            assertTrue(isIllegalArgumentExceptionThrown);
            Log.i(TAG, "Done.");
        });
    }

    /**
     * Tests that {@link ShortcutManager#requestPinShortcut} allows launching an activity directly
     * as the pin result callback.
     */
    @Test
    public void testRequestPinShortcut_directActivityLaunch() {
        Log.i(TAG, "testRequestPinShortcut: package4 requesting pin on launcher1, "
            + "callback launches cts activity");
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final ComponentName targetActivity = new ComponentName(
            "android.content.pm.cts.shortcutmanager",
            "android.content.pm.cts.shortcutmanager.MyActivity"
        );
        final PendingIntent callback = PendingIntent.getActivity(
            getTestContext(), 0, new Intent().setComponent(targetActivity),
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        final ComponentName requestPinActivity = new ComponentName(
            "android.content.pm.cts.shortcutmanager.packages.package4",
            "android.content.pm.cts.shortcutmanager.packages.RequestPinShortcutActivity"
        );
        ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
            final Intent requestIntent = new Intent().setComponent(requestPinActivity)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Constants.EXTRA_REPLY_ACTION, replyAction)
                .putExtra(Constants.EXTRA_TARGET_INTENT, callback.getIntentSender());
            getTestContext().startActivity(requestIntent);
        });

        boolean result = mWmState.waitForFocusedActivity(targetActivity);
        assertTrue("Did not find focused activity: " + targetActivity, result);
    }

    /**
     * Tests that {@link ShortcutManager#requestPinShortcut} does not allow indirectly launching an
     * activity through a service trampoline.
     */
    @Test
    public void testRequestPinShortcut_indirectActivityLaunch() {
        Log.i(TAG, "testRequestPinShortcut: package4 requesting pin on launcher1, "
            + "callback launches package4 service trampoline to cts activity");
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final ComponentName targetActivity = new ComponentName(
            "android.content.pm.cts.shortcutmanager.packages.package4",
            "android.content.pm.cts.shortcutmanager.packages.Launcher"
        );
        final ComponentName trampolineService = new ComponentName(
            "android.content.pm.cts.shortcutmanager.packages.package4",
            "android.content.pm.cts.shortcutmanager.packages.BalService"
        );
        final ComponentName requestPinActivity = new ComponentName(
            "android.content.pm.cts.shortcutmanager.packages.package4",
            "android.content.pm.cts.shortcutmanager.packages.RequestPinShortcutActivity"
        );
        ReplyUtil.invokeAndWaitForReply(getTestContext(), (trampolineReplyAction) -> {
            final PendingIntent target = PendingIntent.getActivity(getTestContext(), 0,
                new Intent().setComponent(targetActivity),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE,
                ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                    .toBundle());
            final PendingIntent trampoline = PendingIntent.getService(
                getTestContext(), 0,
                new Intent().setComponent(trampolineService)
                    .putExtra(Constants.EXTRA_REPLY_ACTION, trampolineReplyAction)
                    .putExtra(Constants.EXTRA_TARGET_INTENT, target.getIntentSender()),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            ReplyUtil.invokeAndWaitForReply(getTestContext(), (confirmPinReplyAction) -> {
                final Intent requestIntent = new Intent().setComponent(requestPinActivity)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Constants.EXTRA_REPLY_ACTION, confirmPinReplyAction)
                    .putExtra(Constants.EXTRA_TARGET_INTENT, trampoline.getIntentSender());
                getTestContext().startActivity(requestIntent);
                // waits for confirm pin activity to respond
            });
            // Press home key to ensure stopAppSwitches is called because the last-stop-app-switch-time
            // is a criteria of allowing background start.
            pressHomeButton();
            SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
            mWmState.waitForHomeActivityVisible();
            SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
            // waits for trampoline service to respond after it has launched the target activity.
        });
        boolean result = mWmState.waitForFocusedActivity(targetActivity);
        assertFalse("Should not able to launch background activity", result);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    @RequiresAppLockSupported
    @RequiresFlagsEnabled({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL
    })
    @ApiTest(apis = { "android.content.pm.ShortcutManager#isRequestPinShortcutSupported" })
    public void testIsRequestPinShortcutSupported_whenAppLockIsEnabled_returnsFalse()
            throws Exception {
        // Launcher1 supports request pin shortcut.
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCaller(mPackageContext1, () -> {
            assertThat(getManager().isRequestPinShortcutSupported()).isTrue();
        });

        try (AutoCloseable withLockScreen = new LockSettingsUtil(getTestContext()).withLockScreen();
                AutoCloseable withAppLockEnabled = setPackageAppLockEnabledScoped(
                        mPackageContext1.getPackageName(), getTestContext().getPackageManager())) {
            runWithCaller(mPackageContext1, () -> {
                assertThat(getManager().isRequestPinShortcutSupported()).isFalse();
            });
        }
    }

    @Test
    @DisabledOnRavenwood(blockedBy = PackageManager.class)
    @RequiresAppLockSupported
    @RequiresFlagsEnabled({
        android.security.Flags.FLAG_APP_LOCK_APIS,
        android.content.pm.Flags.FLAG_APP_LOCK_SHORTCUT_REMOVAL
    })
    @ApiTest(apis = { "android.content.pm.ShortcutManager#requestPinShortcut" })
    public void testRequestPinShortcut_whenAppLockIsEnabled_returnsFalse() throws Exception {
        // Launcher1 supports request pin shortcut
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID).setShortLabel("label1")
                .setIntent(new Intent(Intent.ACTION_MAIN)).build();

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertThat(getManager().isRequestPinShortcutSupported()).isTrue();
            assertThat(getManager().updateShortcuts(list(shortcut))).isTrue();
            assertThat(getManager().requestPinShortcut(shortcut, /* resultIntent= */ null))
                    .isTrue();
        });

        try (AutoCloseable withLockScreen = new LockSettingsUtil(getTestContext()).withLockScreen();
                AutoCloseable withAppLockEnabled = setPackageAppLockEnabledScoped(
                        mPackageContext1.getPackageName(), getTestContext().getPackageManager())) {
            runWithCallerWithStrictMode(mPackageContext1, () -> {
                assertThat(getManager().requestPinShortcut(shortcut, /* resultIntent= */ null))
                        .isFalse();
            });
        }
    }

    /**
     * Enables App Lock for the specified package and returns an {@link AutoCloseable} that
     * automatically disables it upon closing. Using {@link PackageManager#setPackageAppLockEnabled}
     * requires either {@link TEST_LOCK_APPS} or {@link android.Manifest.permission#LOCK_APPS}
     * permission. This method uses {@link TEST_LOCK_APPS} by adopting shell permission identity.
     *
     * <p>This method asserts that the operation to enable App Lock is successful. The returned
     * {@link AutoCloseable} also asserts that the operation to disable App Lock is successful
     * when closed.
     *
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>A screen lock must be set up on the device. See {@link setLskfScoped}</li>
     *   <li>The package must support the App Lock feature.</li>
     * </ul>
     *
     * @param packageName the name of the package for which App Lock should be enabled.
     * @param pm the {@link PackageManager} instance to use for the operation.
     * @return an {@link AutoCloseable} that disables App Lock for the package when closed.
     */
    private AutoCloseable setPackageAppLockEnabledScoped(String packageName, PackageManager pm) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThat(pm.setPackageAppLockEnabled(packageName, /* enabled= */ true)).isTrue();
        }, TEST_LOCK_APPS);

        return () -> {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                assertThat(pm.setPackageAppLockEnabled(packageName, /* enabled= */ false)).isTrue();
            }, TEST_LOCK_APPS);
        };
    }
}
