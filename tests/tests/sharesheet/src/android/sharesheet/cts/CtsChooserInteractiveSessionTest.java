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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.chooser.Flags;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.UserHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_CHOOSER)
public class CtsChooserInteractiveSessionTest {
    private static final int WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5_000;

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
        mDevice = UiDevice.getInstance(instrumentation);
        mContext = instrumentation.getTargetContext();
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
                "android.service.chooser.ChooserSession#close",
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

    /**
     * Test that the session is closed when the Chooser is dismissed. Also check that Chooser
     * reports its bounds.
     */
    @ApiTest(
            apis = {
                "android.service.chooser.ChooserSession#addStateListener",
                "android.service.chooser.ChooserSession.StateListener#onBoundsChanged",
                "android.service.chooser.ChooserSession.StateListener#onStateChanged"
            })
    @Test
    public void test_chooserDismissed_chooserSessionGetsClosed() {
        launchTestActivity();

        clickLaunchChooser();
        waitForChooserToAppear();
        verifyChooserReportedItsBounds();
        // dismiss Chooser
        mDevice.pressBack();
        waitForChooserToBeGone();
        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    /**
     * Test that the session is closed when the Chooser is dismissed. Also check that Chooser
     * reports its bounds.
     */
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
        // check that the launch button is visible again
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
        clickChooserTarget("App Two");
        waitForChooserToBeGone();
        // check that the launch button is visible again
        onView(withId(R.id.launch_chooser)).check(matches(isDisplayed()));
    }

    private void clickLaunchChooser() {
        onView(withId(R.id.launch_chooser)).perform(click());
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

    private void clickTestAppButton(String label) {
        // a way to click the test activity's button while it is behind the Chooser
        mDevice.wait(
                        Until.findObject(
                                By.pkg(mContext.getPackageName())
                                        .displayId(mMyDisplayId)
                                        .clickable(true)
                                        .text(label)),
                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                .click();
    }

    private void clickChooserTarget(String targetLabel) {
        mDevice.wait(
                        Until.findObject(
                                By.pkg(mChooserPackage)
                                        .displayId(mMyDisplayId)
                                        .clickable(true)
                                        .hasDescendant(By.text(targetLabel))),
                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                .click();
    }

    private void verifyChooserReportedItsBounds() {
        mDevice.wait(
                        Until.findObject(
                                By.pkg(mContext.getPackageName())
                                        .displayId(mMyDisplayId)
                                        .text("Bounds Updated")),
                        WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)
                .click();
    }

    private void launchTestActivity() {
        Intent testActivityIntent = new Intent();
        testActivityIntent.setComponent(
                new ComponentName(
                        mContext.getPackageName(),
                        CtsInteractiveChooserTestActivity.class.getName()));
        testActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(testActivityIntent);

        waitAndAssertPkgVisible(mContext.getPackageName(), "Failed to launch test activity");
    }

    private void waitForChooserToAppear() {
        waitAndAssertPkgVisible(mChooserPackage, "Failed to find Chooser on screen");
    }

    private void waitForChooserToBeGone() {
        waitPackageGone(mChooserPackage);
    }

    private void waitAndAssertPkgVisible(String pkg, String failureMessage) {
        waitAndAssertFoundOnDevice(By.pkg(pkg).depth(0).displayId(mMyDisplayId), failureMessage);
    }

    private void waitPackageGone(String pkg) {
        mDevice.wait(
                Until.gone(By.pkg(pkg).depth(0).displayId(mMyDisplayId)),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
    }

    private void waitAndAssertFoundOnDevice(BySelector selector, String failureMessage) {
        assertWithMessage(failureMessage)
                .that(mDevice.wait(Until.findObject(selector), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isNotNull();
    }
}
