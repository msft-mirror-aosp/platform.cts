/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyIdentifiers.SCREEN_CAPTURE_DISABLED_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;
import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyIdentifier;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.flags.Flags;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import androidx.test.InstrumentationRegistry;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.enterprise.policies.ScreenCaptureDisabled;
import com.android.bedstead.enterprise.policies.ScreenCaptureDisabledDevice;
import com.android.bedstead.enterprise.policies.ScreenCaptureDisabledUser;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import java.time.Duration;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ScreenCaptureDisabledTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final TestApp sTestApp =
            testApps(sDeviceState).query().whereActivities().isNotEmpty().get();

    /** See {@code DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE} */
    private static final String POLICY_DISABLE_SCREEN_CAPTURE = "policy_disable_screen_capture";

    /**
     * see {@code DevicePolicyManager.EXTRA_RESTRICTION}
     */
    private static final String EXTRA_RESTRICTION = "android.app.extra.RESTRICTION";

    private final String mPolicyIdentifier = PolicyIdentifier.SCREEN_CAPTURE.getId();

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_works() {
        dpc(sDeviceState).devicePolicyManager()
                .setScreenCaptureDisabled(dpc(sDeviceState).componentName(), false);

        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_checkWithDPC_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(dpc(sDeviceState).devicePolicyManager().getScreenCaptureDisabled(
                    dpc(sDeviceState).componentName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setScreenCaptureDisabled(dpc(sDeviceState).componentName(), false));
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(
                    /* admin= */ null)).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_checkWithDPC_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(dpc(sDeviceState).devicePolicyManager().getScreenCaptureDisabled(
                    dpc(sDeviceState).componentName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_doesNotApply() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */
                    null)).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {

            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();

        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureRedactedOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(takeScreenshotExpectingRedactionOrNull()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setScreenCaptureDisabled_receivedPolicySetBroadcast() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    SCREEN_CAPTURE_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class, singleTestOnly = true)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @Ignore // need to restore with some root-only capability to force migration
    public void setScreenCaptureDisabled_policyMigration_works() {
        try {
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(
                    sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_disallowScreenCapture_createsIntent() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(
                    POLICY_DISABLE_SCREEN_CAPTURE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_allowScreenCapture_doesNotCreate() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent).isNull();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledDevice.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_DEVICE",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_deviceScope_disallowed_works() {
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_DEVICE, PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED);
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledDevice.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_DEVICE",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_deviceScope_allowed_works() {
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_DEVICE, PolicyIdentifier.SCREEN_CAPTURE_ALLOWED);
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledDevice.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_DEVICE",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_deviceScope_null_works() {
        testSetAndGetPolicy(DevicePolicyManager.POLICY_SCOPE_DEVICE, null);
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledUser.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_USER",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_userScope_disallowed_works() {
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_USER, PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED);
    }

    /** Check that setting the policy at the device scope to false works */
    @PolicyAppliesTest(policy = ScreenCaptureDisabledUser.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_USER",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_userScope_allowed_works() {
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_USER, PolicyIdentifier.SCREEN_CAPTURE_ALLOWED);
    }

    /** Check that setting the policy at the user scope to null works */
    @PolicyAppliesTest(policy = ScreenCaptureDisabledUser.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getPolicy",
                "android.app.admin.DevicePolicyManager#POLICY_SCOPE_USER",
                "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE"
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_userScope_null_works() {
        testSetAndGetPolicy(DevicePolicyManager.POLICY_SCOPE_USER, null);
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledUser.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_allowed_getScreenCaptureDisabled_returnsEnabled() {
        if (isParentInstance()) {
            // TODO(b/434655549): Support the parent scope.
            return;
        }

        // Set the policy
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_USER, PolicyIdentifier.SCREEN_CAPTURE_ALLOWED);

        // Get the policy
        boolean isDisabled = sLocalDevicePolicyManager.getScreenCaptureDisabled(null);
        assertThat(isDisabled).isFalse();
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabledUser.class)
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = {
                "android.app.admin.DevicePolicyManager#setPolicy",
                "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            })
    @RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
    public void setPolicy_disallowed_getScreenCaptureDisabled_returnsDisabled() {
        if (isParentInstance()) {
            // TODO(b/434655549): Support the parent scope.
            return;
        }

        // Set the policy
        testSetAndGetPolicy(
                DevicePolicyManager.POLICY_SCOPE_USER, PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED);

        // Get the policy
        boolean isDisabled = sLocalDevicePolicyManager.getScreenCaptureDisabled(null);
        assertThat(isDisabled).isTrue();
    }

    private boolean takeScreenshotExpectingRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(true).await();
        }
    }

    private boolean takeScreenshotExpectingNoRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(false).await();
        }
    }

    private boolean checkScreenshotIsRedactedOrNull(Bitmap screenshot) {
        if (screenshot == null) {
            return true;
        }
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();

        // Getting pixels of only the middle part(from y  = height/4 to 3/4(height)) of the
        // screenshot to check(screenshot is redacted) for only the middle part of the screen,
        // skipping the "margin" (1/4 of the height) from the bottom and the top. It is because
        // there could be notifications in the top part and white line(navigation bar) at bottom
        // which are included in the screenshot and are not redacted(black). It's not perfect, but
        // seems best option to avoid any flakiness at this point.
        // For split-screen multi-tasking, use a taller margin (2/5 of the height), taking into
        // account the other panels. Ideally, we would want to use the actual dimensions of the app
        // panel, but there is no good way to get access to the current test activity or its window,
        // because the activity is running in a different process, according to the bedstead team.
        // We would need an adb command that can return the app panel layouts on the screen.
        // TODO(b/481701311): Clean up this special case logic.
        int margin = isCarSplitscreenMultitasking() ? (height * 2 / 5) : (height / 4);
        int effectiveHeight = height - (2 * margin);
        int len = width * effectiveHeight;
        int[] pixels = new int[len];
        // Get the actual pixels to check from the screenshot, excluding the margins.
        screenshot.getPixels(pixels, /* offset= */ 0, /* stride= */ width,
                /* x= */ 0, /* y= */ margin, /* width= */ width, /* height = */ effectiveHeight);

        for (int i = 0; i < len; ++i) {
            // Skip some pixels from the right to accommodate for the edge panel(present on
            // some devices) which will not be redacted in the screenshot.
            if ((i % width) /* X-position */ > (width - 34)) {
                // skipping edge panel
                continue;
            }
            if (!(pixels[i] == Color.BLACK || (
                    (pixels[i] == Color.TRANSPARENT || pixels[i] == Color.WHITE)
                            && isAutomotive()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAutomotive() {
        return TestApis.context().instrumentedContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static boolean isCarSplitscreenMultitasking() {
        return isAutomotive()
                && TestApis.context().instrumentedContext().getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING);
    }

    // Set the policy, get the policy and assert that the correct value is returned.
    private void testSetAndGetPolicy(int scope, Integer inputValue) {
        if (isParentInstance()) {
            // TODO(b/434655549): Support the parent scope.

            return;
        }

        var policyIdentifier = PolicyIdentifier.SCREEN_CAPTURE.getId();
        var dpm = dpc(sDeviceState).devicePolicyManager();

        if (inputValue == null) {
            dpm.clearPolicy(policyIdentifier, scope);
            int returnedValue = dpm.getIntegerPolicy(policyIdentifier, scope);
            int resolvedValue = dpm.getIntegerResolvedPerUserPolicy(policyIdentifier);

            // Bedstead doesn't support Integer values, so it returns -1 instead of null.
            assertThat(returnedValue).isEqualTo(-1);
            assertThat(resolvedValue).isEqualTo(-1);
        } else {
            dpm.setIntegerPolicy(policyIdentifier, scope, inputValue);
            int returnedValue = dpm.getIntegerPolicy(policyIdentifier, scope);
            int resolvedValue = dpm.getIntegerResolvedPerUserPolicy(policyIdentifier);

            assertThat(returnedValue).isEqualTo(inputValue);
            assertThat(resolvedValue).isEqualTo(inputValue);
        }
    }

    private boolean isParentInstance() {
        return dpc(sDeviceState).isParentInstance();
    }
}
