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

import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.nene.exceptions.AdbException;
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

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetUncategorizedDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        String response = executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetStorageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        String response = executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetBatteryDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        String response = executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetMobileDataUsageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getMobileDataUsageDeviceState"));

        String response = executeAppFunction("getMobileDataUsageDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetNotificationsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getNotificationsDeviceState"));

        String response = executeAppFunction("getNotificationsDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetAppsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getAppsDeviceState"));

        String response = executeAppFunction("getAppsDeviceState", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    public void testGetDeviceStateMetadata_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        String response = executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE);

        assertThat(response).contains(VALID_METADATA_RESPONSE_OUTPUT);
        assertThat(response).doesNotContain(testApp(sDeviceState).packageName());
    }

    private String executeAppFunction(String functionName, String packageName) throws AdbException {
        String command =
                "cmd app_function execute-app-function "
                        + "--package "
                        + packageName
                        + " --function "
                        + functionName
                        + " --parameters {}";
        return ShellCommand.builder(command).execute().trim();
    }

    private boolean deviceSupportsAppFunction(String functionName) throws AdbException {
        String command = "dumpsys app_function";
        return ShellCommand.builder(command).execute().trim().contains(functionName);
    }
}
