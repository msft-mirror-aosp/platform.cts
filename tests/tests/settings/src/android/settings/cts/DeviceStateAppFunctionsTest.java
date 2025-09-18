/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.settings.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_AUTOMOTIVE;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;

import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** For testing device state related app function capabilities. */
@RunWith(BedsteadJUnit4.class)
public class DeviceStateAppFunctionsTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String SETTINGS_PACKAGE = "com.android.settings";

    private static final String VALID_DEVICE_STATE_RESPONSE_OUTPUT = "\"perScreenDeviceStates\": [";
    private static final String VALID_METADATA_RESPONSE_OUTPUT = "\"perScreenMetadata\": [";

    private static final KeyguardManager sLocalKeyguardManager =
            TestApis.context().instrumentedContext().getSystemService(KeyguardManager.class);

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetUncategorizedDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        String response = executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetStorageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        String response = executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetBatteryDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        String response = executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetMobileDataUsageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getMobileDataUsageDeviceState"));

        String response = executeAppFunction("getMobileDataUsageDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetNotificationsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getNotificationsDeviceState"));

        String response = executeAppFunction("getNotificationsDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetAppsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getAppsDeviceState"));

        String response = executeAppFunction("getAppsDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetDeviceStateMetadata_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        String response = executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_METADATA_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetDeviceStateMetadata_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetDeviceStateMetadata_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "getDeviceStateMetadata",
                                SETTINGS_PACKAGE,
                                "\"getDeviceStateMetadataParams\":{\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetDeviceStateMetadata_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        assertThat(executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetUncategorizedDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void
            testGetUncategorizedDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "getUncategorizedDeviceState",
                                SETTINGS_PACKAGE,
                                "\"getUncategorizedDeviceStateParams\":{\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetUncategorizedDeviceState_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        assertThat(executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetStorageDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetStorageDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "getStorageDeviceState",
                                SETTINGS_PACKAGE,
                                "\"getStorageDeviceStateParams\":{\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetStorageDeviceState_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        assertThat(executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetBatteryDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testGetBatteryDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "getBatteryDeviceState",
                                SETTINGS_PACKAGE,
                                "\"getBatteryDeviceStateParams\":{\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testGetBatteryDeviceState_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        assertThat(executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testSetDeviceStateItem_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "setDeviceStateItem",
                                SETTINGS_PACKAGE,
                                "\"setDeviceStateItemParams\":{\"key\":\"key\"}"))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testSetDeviceStateItem_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "setDeviceStateItem",
                                SETTINGS_PACKAGE,
                                "\"setDeviceStateItemParams\":{\"key\":\"key\","
                                        + "\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testSetDeviceStateItem_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));

        assertThat(
                        executeAppFunctionWithParams(
                                "setDeviceStateItem",
                                SETTINGS_PACKAGE,
                                "\"setDeviceStateItemParams\":{\"key\":\"key\"}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testOffsetNumericDeviceStateItemByValue_deviceLocked_throwsException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "offsetNumericDeviceStateItemByValue",
                                SETTINGS_PACKAGE,
                                "\"offsetNumericDeviceStateItemByValueParams\":{\"key\":\"key\"}"))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void
            testOffsetNumericDeviceStateItemByValue_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "offsetNumericDeviceStateItemByValue",
                                SETTINGS_PACKAGE,
                                "\"offsetNumericDeviceStateItemByValueParams\":{\"key\":\"key\","
                                        + "\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testOffsetNumericDeviceStateItemByValue_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));

        assertThat(
                        executeAppFunctionWithParams(
                                "offsetNumericDeviceStateItemByValue",
                                SETTINGS_PACKAGE,
                                "\"offsetNumericDeviceStateItemByValueParams\":{\"key\":\"key\"}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void testAdjustNumericDeviceStateItemByPercentage_deviceLocked_throwsException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "adjustNumericDeviceStateItemByPercentage",
                                SETTINGS_PACKAGE,
                                "\"adjustNumericDeviceStateItemByPercentageParams\":{\"key\":\"key\"}"))
                .contains("android.app.appfunctions.AppFunctionException");
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    public void
            testAdjustNumericDeviceStateItemByPercentage_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        assertThat(
                        executeAppFunctionWithParams(
                                "adjustNumericDeviceStateItemByPercentage",
                                SETTINGS_PACKAGE,
                                "\"adjustNumericDeviceStateItemByPercentageParams\":{\"key\":\"key\","
                                    + "\"requestInitiatedWhileUnlocked\":true}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureUnlocked
    @EnsurePasswordNotSet
    public void testAdjustNumericDeviceStateItemByPercentage_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));

        assertThat(
                        executeAppFunctionWithParams(
                                "adjustNumericDeviceStateItemByPercentage",
                                SETTINGS_PACKAGE,
                                "\"adjustNumericDeviceStateItemByPercentageParams\":{\"key\":\"key\"}"))
                .doesNotContain("android.app.appfunctions.AppFunctionException");
    }

    private String executeAppFunction(String functionName, String packageName) throws AdbException {
        return executeAppFunctionWithParams(functionName, packageName, /* params= */ "");
    }

    private String executeAppFunctionWithParams(
            String functionName, String packageName, String params) throws AdbException {
        String command =
                "cmd app_function execute-app-function "
                        + "--package "
                        + packageName
                        + " --function "
                        + functionName
                        + " --parameters {"
                        + params
                        + "}";
        return ShellCommand.builder(command).execute().trim();
    }


    private boolean deviceSupportsAppFunction(String functionName) throws AdbException {
        String command = "dumpsys app_function";
        return ShellCommand.builder(command).execute().trim().contains(functionName);
    }
}
