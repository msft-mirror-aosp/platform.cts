/*
 * Copyright 2025 The Android Open Source Project
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

import static android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.chooser.Flags;
import android.util.TypedValue;
import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.UserHelper;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_CHOOSER)
public class CtsChooserInteractiveSessionTest {
    private static final int WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5_000;
    private static final int MIN_CHOOSER_HEIGHT_DP = 48;
    private static final int MIN_TOP_SPACE_DP = 48;

    @Rule(order = 0)
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    START_ACTIVITIES_FROM_BACKGROUND);

    public UiDevice mDevice;
    private Context mContext;
    private String mChooserPackage;
    private int mMyDisplayId;

    @Before
    public void init() throws RemoteException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        PackageManager pm = mContext.getPackageManager();
        assumeFalse(
                "Skip test: Device is a wearable, TV or Auto",
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                        || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || pm.hasSystemFeature(PackageManager.FEATURE_WATCH));

        mDevice = UiDevice.getInstance(instrumentation);
        mChooserPackage = readChooserPackage();
        mMyDisplayId = new UserHelper(mContext).getMainDisplayId();

        mDevice.wakeUp();
    }

    private String readChooserPackage() {
        Intent shareIntent = Intent.createChooser(new Intent(Intent.ACTION_SEND), null);
        ResolveInfo shareRi =
                mContext.getPackageManager()
                        .resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

        assertThat(shareRi).isNotNull();
        assertThat(shareRi.activityInfo).isNotNull();
        return shareRi.activityInfo.packageName;
    }

    /** Tests that an application can close an interactive Chooser session. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#endSession",
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onStateChanged"
            })
    @Test
    public void test_applicationClosesChooser() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();

        clickCloseChooser();
        waitForChooserToBeGone();

        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    /** Test that Chooser closes the session after a target is selected. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onStateChanged"
            })
    @Test
    public void test_chooserTargetSelected_chooserSessionGetsClosed() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();

        clickChooserTarget("App One");
        waitForChooserToBeGone();

        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    /** Test that the session is closed when the Chooser is dismissed. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onStateChanged"
            })
    @Test
    public void test_chooserDismissed_chooserSessionGetsClosed() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();

        // dismiss Chooser
        mDevice.pressBack();
        waitForChooserToBeGone();

        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    /** Test Chooser bounds reporting. */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onBoundsChanged",
                "android.service.chooser.ChooserSession#getBounds",
            })
    @Test
    public void test_chooserReportsBounds() {
        InteractiveTestActivityController activityController = launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();
        mDevice.waitForIdle();

        InteractiveTestActivityReport testReport = activityController.getReport();
        assertThat(testReport.getHasActiveSession()).isTrue();
        assertThat(testReport.getBoundsUpdateHistory()).isNotEmpty();
        assertThat(testReport.getBoundsUpdateHistory().getLast())
                .isEqualTo(testReport.getChooserBounds());

        // dismiss Chooser
        mDevice.pressBack();
        waitForChooserToBeGone();
    }

    /** Test that no session updates received after unsubscribing from the session */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession#removeStateListener"
            })
    @Test
    public void test_activityUnsubscribesFromSessionUpdates_uiIsNotUpdatedAfterSessionClosed() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();
        clickUnsubscribeButton();

        // dismiss Chooser
        mDevice.pressBack();
        waitForChooserToBeGone();

        // check that the close button is still visible
        onView(withId(R.id.close_chooser)).check(matches(isDisplayed()));
    }

    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#updateIntent",
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onStateChanged"
            })
    @Test
    public void test_updateChooserIntent() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();

        clickUpdateChooserButton();
        mDevice.waitForIdle();

        clickChooserTarget("App Two");
        waitForChooserToBeGone();

        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    @ApiTest(apis = {"android.service.chooser.ChooserSession#setTargetsEnabled"})
    @Test
    public void test_changeTargetEnableStatus() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();
        verifyChooserTargetEnabledStatus("App One", true);
        clickDisableChooserTargetsButton();
        mDevice.waitForIdle();
        verifyChooserTargetEnabledStatus("App One", false);

        clickEnableChooserTargetsButton();
        mDevice.waitForIdle();
        verifyChooserTargetEnabledStatus("App One", true);

        mDevice.pressBack();
    }

    @Test
    public void test_minimalHeightAndTopSpace() {
        InteractiveTestActivityController activityController =
                launchTestActivity(SCREEN_ORIENTATION_LANDSCAPE);

        clickLaunchChooser();
        waitForChooserToAppear();
        mDevice.waitForIdle();

        InteractiveTestActivityReport report = activityController.getReport();
        int minChooserHeight =
                (int)
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                MIN_CHOOSER_HEIGHT_DP,
                                mContext.getResources().getDisplayMetrics());
        int minRemainingTopSpace =
                (int)
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                MIN_TOP_SPACE_DP,
                                mContext.getResources().getDisplayMetrics());
        Rect chooserBounds = report.getChooserBounds();
        assertThat(chooserBounds).isNotNull();
        int bottomInsets = report.getWindowInsets() == null ? 0 : report.getWindowInsets().bottom;
        int topInsets = report.getWindowInsets() == null ? 0 : report.getWindowInsets().top;
        if (topInsets + bottomInsets + minChooserHeight > report.getWindowHeight()) {
            return;
        }
        assertWithMessage(
                        "The Chooser needs sufficient height to ensure users can interact with it.")
                .that(chooserBounds.height() - bottomInsets >= minChooserHeight)
                .isTrue();
        if (topInsets + bottomInsets + minChooserHeight + minRemainingTopSpace
                > report.getWindowHeight()) {
            return;
        }
        assertWithMessage(
                        "Chooser needs to left sufficient amount of space for the app when"
                                + " collapsed.")
                .that(chooserBounds.top - topInsets >= minRemainingTopSpace)
                .isTrue();
    }

    private void clickLaunchChooser() {
        onView(withId(R.id.launch_chooser))
                .perform(
                        new ViewAction() {
                            @Override
                            public Matcher<View> getConstraints() {
                                return new BaseMatcher<View>() {
                                    @Override
                                    public void describeTo(Description description) {
                                        description.appendText("Launch button matcher");
                                    }

                                    @Override
                                    public boolean matches(Object item) {
                                        return item instanceof View view
                                                && view.isEnabled()
                                                && view.isClickable();
                                    }
                                };
                            }

                            @Override
                            public String getDescription() {
                                return "Test Action";
                            }

                            @Override
                            public void perform(UiController uiController, View view) {
                                view.callOnClick();
                            }
                        });
    }

    private void clickCloseChooser() {
        clickTestAppButton("Close");
    }

    private void clickUpdateChooserButton() {
        clickTestAppButton("Update");
    }

    private void clickUnsubscribeButton() {
        clickTestAppButton("Unsubscribe");
    }

    private void clickDisableChooserTargetsButton() {
        clickTestAppButton("Disable");
    }

    private void clickEnableChooserTargetsButton() {
        clickTestAppButton("Enable");
    }

    private void clickTestAppButton(String label) {
        // a way to click the test activity's button while it is behind the Chooser
        mDevice.wait(
                        Until.findObject(
                                By.pkg(mContext.getPackageName())
                                        .displayId(mMyDisplayId)
                                        .clickable(true)
                                        .text(Pattern.compile(label, Pattern.CASE_INSENSITIVE))),
                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                .click();
    }

    private void clickChooserTarget(String targetLabel) {
        mDevice.wait(
                        Until.findObject(
                                By.pkg(mChooserPackage)
                                        .displayId(mMyDisplayId)
                                        .clickable(true)
                                        .hasDescendant(
                                                By.text(
                                                        Pattern.compile(
                                                                targetLabel,
                                                                Pattern.CASE_INSENSITIVE)))),
                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                .click();
    }

    private InteractiveTestActivityController launchTestActivity() {
        return launchTestActivity(SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private InteractiveTestActivityController launchTestActivity(int orientation) {
        Intent testActivityIntent = new Intent();
        testActivityIntent.setComponent(
                new ComponentName(
                        mContext.getPackageName(),
                        CtsInteractiveChooserTestActivity.class.getName()));
        testActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        testActivityIntent.putExtra(
                CtsInteractiveChooserTestActivity.PARAM_ORIENTATION, orientation);
        CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<InteractiveTestActivityController> controllerRef =
                new AtomicReference<>();
        InteractiveTestActivityControllerCallback controllerCallback =
                new InteractiveTestActivityControllerCallback() {
                    @Override
                    public void setTestActivityController(
                            @NotNull InteractiveTestActivityController controller) {
                        controllerRef.set(controller);
                        cdl.countDown();
                    }
                };
        Bundle controllerCallbackBundle = new Bundle();
        controllerCallbackBundle.putBinder(
                CtsInteractiveChooserTestActivity.PARAM_ACTIVITY_CONTROLLER_CALLBACK,
                controllerCallback);
        testActivityIntent.putExtras(controllerCallbackBundle);
        mContext.startActivity(testActivityIntent);

        waitAndAssertPkgVisible(mContext.getPackageName(), "Failed to launch test activity");
        try {
            if (!cdl.await(WAIT_AND_ASSERT_FOUND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Test error: activity did not set provide a controller");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Objects.requireNonNull(controllerRef.get());
    }

    private void waitForChooserToAppear() {
        waitAndAssertPkgVisible(mChooserPackage, "Failed to find Chooser on screen");
    }

    private void waitForChooserToBeGone() {
        waitPackageGone(mChooserPackage);
        // TODO: find a better way to wait for the window transition to be over
        SystemClock.sleep(1_000);
        mDevice.waitForIdle();
    }

    private void waitAndAssertPkgVisible(String pkg, String failureMessage) {
        waitAndAssertFoundOnDevice(By.pkg(pkg).depth(0).displayId(mMyDisplayId), failureMessage);
    }

    private void waitPackageGone(String pkg) {
        assertWithMessage("Package " + pkg + " remains visible")
                .that(
                        mDevice.wait(
                                Until.gone(By.pkg(pkg).depth(0).displayId(mMyDisplayId)),
                                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isTrue();
    }

    private void waitAndAssertFoundOnDevice(BySelector selector, String failureMessage) {
        assertWithMessage(failureMessage)
                .that(mDevice.wait(Until.findObject(selector), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isNotNull();
    }

    private void verifyChooserTargetEnabledStatus(String label, boolean isEnabled) {
        assertThat(
                        mDevice.wait(
                                        Until.findObject(
                                                By.pkg(mChooserPackage)
                                                        .displayId(mMyDisplayId)
                                                        .hasDescendant(By.text(label))),
                                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                                .isEnabled())
                .isEqualTo(isEnabled);
    }
}
