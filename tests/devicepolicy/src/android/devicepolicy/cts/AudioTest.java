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

package android.devicepolicy.cts;

import static android.os.UserManager.DISALLOW_ADJUST_VOLUME;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.userController;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_UNMUTE_MICROPHONE;
import static com.android.bedstead.permissions.CommonPermissions.MODIFY_AUDIO_SETTINGS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.media.AudioManager;

import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasUserController;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.enterprise.policies.DisallowUnmuteMicrophone;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AudioTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final AudioManager sAudioManager = TestApis.context().instrumentedContext()
            .getSystemService(AudioManager.class);

    @CannotSetPolicyTest(policy = DisallowUnmuteMicrophone.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_UNMUTE_MICROPHONE));
    }

    @PolicyAppliesTest(policy = DisallowUnmuteMicrophone.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_UNMUTE_MICROPHONE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_UNMUTE_MICROPHONE))
                    .isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_UNMUTE_MICROPHONE);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowUnmuteMicrophone.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_UNMUTE_MICROPHONE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_UNMUTE_MICROPHONE))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_UNMUTE_MICROPHONE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_UNMUTE_MICROPHONE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    @EnsureHasPermission(MODIFY_AUDIO_SETTINGS)
    public void disallowUnmuteMicrophoneIsNotSet_canUnmuteMicrophone() throws Exception {
        sAudioManager.setMicrophoneMute(true);

        sAudioManager.setMicrophoneMute(false);

        Poll.forValue("isMicrophoneMute", sAudioManager::isMicrophoneMute)
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @EnsureHasUserRestriction(DISALLOW_UNMUTE_MICROPHONE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    @EnsureHasPermission(MODIFY_AUDIO_SETTINGS)
    public void disallowUnmuteMicrophoneIsSet_canNotUnmuteMicrophone() throws Exception {
        sAudioManager.setMicrophoneMute(true);

        sAudioManager.setMicrophoneMute(false);

        Poll.forValue("isMicrophoneMute", sAudioManager::isMicrophoneMute)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Test
    @Ignore("b/461439459 Re-enable once either the feature flag is added or tests are moved to"
            + "a different suite.")
    @RequireRunOnInitialUser(switchedToUser = OptionalBoolean.FALSE)
    @EnsureHasAdditionalUser(switchedToUser = OptionalBoolean.TRUE)
    @EnsureHasUserController(onUser = UserType.INITIAL_USER)
    @EnsureHasUserController(onUser = UserType.ADDITIONAL_USER)
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADJUST_VOLUME")
    public void disallowAdjustVolume_userSwitch_policyCorrectlyResets() {
        RemoteDpc userControllerInitialUser = userController(sDeviceState, UserType.INITIAL_USER);
        RemoteDpc userControllerAdditionalUser = userController(sDeviceState,
                UserType.ADDITIONAL_USER);
        try (var clearDisallowAdjustVolumeMultiUser =
                     new ClearDisallowAdjustVolumeMultiUser(
                             userControllerInitialUser)) {
            userControllerInitialUser.devicePolicyManager().addUserRestriction(
                    userControllerInitialUser.componentName(), DISALLOW_ADJUST_VOLUME);
            userControllerAdditionalUser.devicePolicyManager().clearUserRestriction(
                    userControllerAdditionalUser.componentName(), DISALLOW_ADJUST_VOLUME);
            Poll.forValue("Global mute state", () -> sAudioManager.isMasterMute())
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();

            UserReference initialUser = TestApis.users().initial();
            initialUser.switchTo();

            Poll.forValue("Global mute state", () -> sAudioManager.isMasterMute())
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        }
    }

    // TODO: Figure out where policy transparency for this control appears and add a test

    private record ClearDisallowAdjustVolumeMultiUser(RemoteDpc userController) implements
            AutoCloseable {
        @Override
        public void close() {
            userController.devicePolicyManager().clearUserRestriction(
                    userController.componentName(), DISALLOW_ADJUST_VOLUME);
        }
    }
}
