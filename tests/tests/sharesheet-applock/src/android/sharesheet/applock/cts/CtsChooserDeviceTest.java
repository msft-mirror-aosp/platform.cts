/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.sharesheet.applock.cts;

import static android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.util.AppLockSupportRule;
import android.content.pm.cts.util.RequiresAppLockSupported;
import android.graphics.Point;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class CtsChooserDeviceTest {
    public static final String TAG = CtsChooserDeviceTest.class.getSimpleName();

    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    START_ACTIVITIES_FROM_BACKGROUND);

    @Rule(order = 2)
    public final AppLockSupportRule mAppLockSupportRule = new AppLockSupportRule();

    private static final int WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5000;
    private static final int WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS = 2500;
    private static final int WAIT_FOR_IDLE_TIMEOUT_MS = 5000;
    private static final String CTS_DATA_TYPE = "test/cts"; // Special CTS mime type
    private static final String CATEGORY_CTS_TEST = "CATEGORY_CTS_TEST";

    private Context mContext;
    private UiDevice mDevice;
    private UiObject2 mChooser;

    private String mPkg;
    private String mChooserPkg;

    private ActivityManager mActivityManager;
    private ShortcutManager mShortcutManager;

    private String mSharingShortcutLabel;
    private Set<ComponentName> mTargetsToExclude;

    private int mMyDisplayId;

    @Before
    public void init() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
        mContext = instrumentation.getTargetContext();

        assumeTrue(
                "Skip test: Device doesn't meet minimum resolution",
                meetsResolutionRequirements(mDevice));

        mPkg = mContext.getPackageName();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mShortcutManager = mContext.getSystemService(ShortcutManager.class);
        PackageManager packageManager = mContext.getPackageManager();

        mSharingShortcutLabel = mContext.getString(R.string.test_sharing_shortcut_label);
        // We want to only show targets in the sheet put forth by the CTS test. In order to do that
        // a special type is used but this doesn't prevent apps registered against */* from showing.
        // To hide */* targets, search for all matching targets and exclude them. Requires
        // permission android.permission.QUERY_ALL_PACKAGES.
        List<ResolveInfo> matchingTargets =
                mContext.getPackageManager()
                        .queryIntentActivities(
                                createTargetIntent(),
                                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA);

        mTargetsToExclude =
                matchingTargets.stream()
                        .map(
                                ri ->
                                        new ComponentName(
                                                ri.activityInfo.packageName, ri.activityInfo.name))
                        .filter(
                                cn -> {
                                    // Exclude our own test targets
                                    return !cn.getPackageName().equals(mPkg);
                                })
                        .collect(Collectors.toSet());

        // We need to know the package used by the system Chooser so we can properly
        // wait for the UI to load. Do this by resolving which activity consumes the share intent.
        // There must be a system Chooser or fail, otherwise fetch its package.
        Intent chooserIntent = createChooserIntent();
        ResolveInfo chooserRi =
                packageManager.resolveActivity(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY);

        assertThat(chooserRi).isNotNull();
        assertThat(chooserRi.activityInfo).isNotNull();

        mChooserPkg = chooserRi.activityInfo.packageName;
        assertThat(mChooserPkg).isNotNull();

        UserHelper userHelper = new UserHelper(mContext);
        mMyDisplayId = userHelper.getMainDisplayId();

        // Finally ensure the device is awake
        mDevice.wakeUp();
    }

    @Test
    @RequiresAppLockSupported
    @RequiresFlagsEnabled({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    public void testShortcutsNotShownWhenTheAppIsLocked() {
        assumeFalse(
                "Direct share not required on low RAM devices", mActivityManager.isLowRamDevice());

        final String testShortcutId = "TEST_SHORTCUT";
        addShortcuts(createShortcut(testShortcutId));

        runAndExecuteCleanupBeforeAnyThrow(
                () -> {
                    Intent shareIntent = createChooserIntent();
                    launchChooser(shareIntent);
                    waitAndAssertTextContains(mSharingShortcutLabel);

                    setAppLockState(true);
                    waitAndAssertNoTextContains(mSharingShortcutLabel);

                    setAppLockState(false);
                    waitAndAssertTextContains(mSharingShortcutLabel);
                },
                () -> {
                    closeChooser();
                    clearShortcuts();
                    setAppLockState(false);
                });
    }

    /**
     * Included CTS tests can fail for resolutions that are too small. This is because the tests
     * check for visibility of UI elements that are hidden below certain resolutions. Ensure that
     * the device under test has the min necessary screen height in dp. Tests do not fail at any
     * width at or above the CDD minimum of 320dp.
     *
     * @return if min resolution requirements are met
     */
    private static boolean meetsResolutionRequirements(UiDevice device) {
        final Point displaySizeDp = device.getDisplaySizeDp();
        return displaySizeDp.y >= 700; // dp
    }

    private void addShortcuts(ShortcutInfo... shortcuts) {
        mShortcutManager.addDynamicShortcuts(Arrays.asList(shortcuts));
    }

    private void clearShortcuts() {
        mShortcutManager.removeAllDynamicShortcuts();
    }

    private ShortcutInfo createShortcut(String id) {
        HashSet<String> categories = new HashSet<>();
        categories.add(CATEGORY_CTS_TEST);

        Intent shortcutIntent = createTargetIntent();
        shortcutIntent.setComponent(new ComponentName(mContext, TestActivity.class));
        return new ShortcutInfo.Builder(mContext, id)
                .setShortLabel(mSharingShortcutLabel)
                .setIcon(Icon.createWithResource(mContext, R.drawable.black_64x64))
                .setCategories(categories)
                .setIntent(shortcutIntent) // must include an Intent w/ action
                .build();
    }

    private void launchChooser(Intent shareIntent) {
        TestApis.activities().startActivity(shareIntent);
        waitAndAssertPkgVisible(mChooserPkg, "Failed to find Chooser on screen");
        mChooser = mDevice.findObject(By.pkg(mChooserPkg).depth(0).displayId(mMyDisplayId));
        waitForIdle();
    }

    private void closeChooser() {
        mDevice.pressHome();
        waitAndAssertPkgNotVisible(mChooserPkg);
        waitForIdle();
    }

    private Intent createTargetIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(CTS_DATA_TYPE);
        return intent;
    }

    private Intent createChooserIntent() {
        Intent intent = createTargetIntent();
        Intent chooserIntent = Intent.createChooser(intent, null);
        // Ensure the sheet will launch directly from the test
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!mTargetsToExclude.isEmpty()) {
            // Intent.EXTRA_EXCLUDE_COMPONENTS is used to ensure only test targets appear
            chooserIntent.putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    mTargetsToExclude.toArray(new ComponentName[0]));
        }
        chooserIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        return chooserIntent;
    }

    private void waitForIdle() {
        mDevice.waitForIdle(WAIT_FOR_IDLE_TIMEOUT_MS);
    }

    private void waitAndAssertPkgVisible(String pkg, String failureMessage) {
        waitAndAssertFoundOnDevice(By.pkg(pkg).depth(0).displayId(mMyDisplayId), failureMessage);
    }

    private void waitAndAssertPkgNotVisible(String pkg) {
        waitAndAssertNotFoundOnDevice(By.pkg(pkg).displayId(mMyDisplayId));
    }

    private void waitAndAssertTextContains(String containsText) {
        String failureMessage = "Failed to find " + containsText + " on screen";
        waitAndAssertTextContains(containsText, failureMessage);
    }

    private void waitAndAssertTextContains(String containsText, String failureMessage) {
        BySelector selector =
                By.text(textContainsPattern(containsText, false)).displayId(mMyDisplayId);
        assertWithMessage(failureMessage)
                .that(mChooser.wait(Until.findObject(selector), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isNotNull();
    }

    private static Pattern textContainsPattern(String text, boolean caseSensitive) {
        int flags = Pattern.DOTALL;
        if (!caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        return Pattern.compile(String.format("^.*%s.*$", Pattern.quote(text)), flags);
    }

    /**
     * waitAndAssertNoTextContains waits for UI containing the given text within Chooser to be
     * hidden, validates that it's indeed gone without waiting more and returns. This means if the
     * UI wasn't visible to start with the method will return without no timeout. Take care to call
     * this method only once there's reason to think the UI is in the right state for testing.
     */
    private void waitAndAssertNoTextContains(String containsText) {
        BySelector selector = By.textContains(containsText).displayId(mMyDisplayId);
        String failureMessage = "Found text '" + containsText + "' but did not expect to";
        mChooser.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertWithMessage(failureMessage).that(mChooser.findObject(selector)).isNull();
    }

    /** Same as waitAndAssertFound but searching the entire device UI. */
    private void waitAndAssertFoundOnDevice(BySelector selector, String failureMessage) {
        assertWithMessage(failureMessage)
                .that(mDevice.wait(Until.findObject(selector), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isNotNull();
    }

    /** Same as waitAndAssertNotFound() but searching the entire device UI. */
    private void waitAndAssertNotFoundOnDevice(BySelector selector) {
        mDevice.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertThat(mDevice.findObject(selector)).isNull();
    }

    /**
     * A {@link Runnable}-like interface that's declared to throw checked exceptions. This is
     * provided for convenience in writing inline ("lambda") blocks, so that test code doesn't need
     * extra boilerplate to handle every possible site of a checked exception (since we're going to
     * end up propagating these exceptions as test failures anyways).
     */
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Perform the requested {@code execution} (which may throw), but then perform the requested
     * {@code cleanup} (whether or not the main execution succeeded) before potentially throwing any
     * exception from the main execution. This is similar to the normal `try/finally` construct,
     * except that the `finally` (or `cleanup`) step is executed <em>before</em> any stack-unwinding
     * to try to catch the exception. Note that any re-thrown exception is wrapped as a {@link
     * RuntimeException} so that clients can skip the checked-exception boilerplate. TODO: it may be
     * possible to move all our cleanup steps to an `@After` method and avoid this unusual
     * construct, but we'd have to refactor to unify the cleanup logic across all tests.
     */
    private static void runAndExecuteCleanupBeforeAnyThrow(
            ThrowingRunnable execution, Runnable cleanup) {
        Throwable exceptionToRethrow = null;
        try {
            execution.run();
        } catch (Throwable mainExecutionException) {
            exceptionToRethrow = mainExecutionException;
        } finally {
            try {
                cleanup.run();
            } catch (Throwable cleanupException) {
                if (exceptionToRethrow == null) {
                    exceptionToRethrow = cleanupException;
                } else {
                    exceptionToRethrow.addSuppressed(cleanupException);
                }
            }
        }
        if (exceptionToRethrow != null) {
            throw new RuntimeException(exceptionToRethrow);
        }
    }

    /**
     * Enables App Lock for the current package and returns an {@link AutoCloseable} that reverts
     * the state when closed.
     */
    private void setAppLockState(boolean isLocked) {
        final String packageName = mContext.getPackageName();
        final PackageManager packageManager = mContext.getPackageManager();

        // Enable App Lock.
        setAppLockState(packageManager, packageName, /* state= */ isLocked);
    }

    /** Helper method to set the App Lock state. */
    private void setAppLockState(PackageManager packageManager, String packageName, boolean state) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    final boolean isAppLockStateChanged =
                            packageManager.setPackageAppLockEnabled(packageName, state);
                    assertThat(isAppLockStateChanged).isTrue();
                },
                Manifest.permission.TEST_LOCK_APPS);
    }
}
