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

import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_AUTOMOTIVE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_LEANBACK;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_WATCH;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appsearch.GenericDocument;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;

import androidx.test.filters.SdkSuppress;

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
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** For testing device state related app function capabilities. */
@RunWith(BedsteadJUnit4.class)
public class DeviceStateAppFunctionsTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String VALID_DEVICE_STATE_RESPONSE_OUTPUT = "perScreenDeviceStates";
    private static final String VALID_METADATA_RESPONSE_OUTPUT = "perScreenMetadata";
    private static final String SETTINGS_PACKAGE = "com.android.settings";

    private static final KeyguardManager sLocalKeyguardManager =
            TestApis.context().instrumentedContext().getSystemService(KeyguardManager.class);

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetUncategorizedDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetStorageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetBatteryDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetMobileDataUsageDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getMobileDataUsageDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getMobileDataUsageDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetNotificationsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getNotificationsDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getNotificationsDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetAppsDeviceState_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getAppsDeviceState"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getAppsDeviceState", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_DEVICE_STATE_RESPONSE_OUTPUT);
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasWorkProfile
    @EnsureTestAppInstalled(onUser = WORK_PROFILE)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetDeviceStateMetadata_shouldNotReturnWorkData() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        ExecuteAppFunctionResponse response =
                executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE, null);

        String responseString = response.getResultDocument().toString();
        assertThat(responseString).contains(VALID_METADATA_RESPONSE_OUTPUT);
        assertThat(responseString).contains("itemizationTypes");
        assertThat(responseString).doesNotContain(testApp(sDeviceState).packageName());
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetDeviceStateMetadata_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE, null));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetDeviceStateMetadata_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        executeAppFunction(
                "getDeviceStateMetadata",
                SETTINGS_PACKAGE,
                buildInitiatedWhileUnlockedParams("getDeviceStateMetadataParams"));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetDeviceStateMetadata_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getDeviceStateMetadata"));

        executeAppFunction("getDeviceStateMetadata", SETTINGS_PACKAGE, null);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetUncategorizedDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                executeAppFunction(
                                        "getUncategorizedDeviceState", SETTINGS_PACKAGE, null));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void
            testGetUncategorizedDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        executeAppFunction(
                "getUncategorizedDeviceState",
                SETTINGS_PACKAGE,
                buildInitiatedWhileUnlockedParams("getUncategorizedDeviceStateParams"));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetUncategorizedDeviceState_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getUncategorizedDeviceState"));

        executeAppFunction("getUncategorizedDeviceState", SETTINGS_PACKAGE, null);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetStorageDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE, null));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetStorageDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        executeAppFunction(
                "getStorageDeviceState",
                SETTINGS_PACKAGE,
                buildInitiatedWhileUnlockedParams("getStorageDeviceStateParams"));
    }

    @Postsubmit(reason = "New test")
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetStorageDeviceState_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getStorageDeviceState"));

        executeAppFunction("getStorageDeviceState", SETTINGS_PACKAGE, null);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetBatteryDeviceState_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE, null));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testGetBatteryDeviceState_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        executeAppFunction(
                "getBatteryDeviceState",
                SETTINGS_PACKAGE,
                buildInitiatedWhileUnlockedParams("getBatteryDeviceStateParams"));
    }

    @Postsubmit(reason = "New test")
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasNoDeviceOwner
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testGetBatteryDeviceState_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("getBatteryDeviceState"));

        executeAppFunction("getBatteryDeviceState", SETTINGS_PACKAGE, null);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testSetDeviceStateItem_deviceLocked_throwsException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "", "", "setDeviceStateItemParams")
                        .setPropertyString("key", "key")
                        .setPropertyString("value", "value")
                        .build();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executeAppFunction("setDeviceStateItem", SETTINGS_PACKAGE, params));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testSetDeviceStateItem_requestInitiatedWhileUnlockedSet_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "setDeviceStateItemParams")
                        .setPropertyString("key", "key")
                        .setPropertyString("value", "value")
                        .setPropertyBoolean("requestInitiatedWhileUnlocked", true)
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument("setDeviceStateItemParams", innerDoc)
                        .build();

        executeAppFunction("setDeviceStateItem", SETTINGS_PACKAGE, params);
    }

    @Postsubmit(reason = "New test")
    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testSetDeviceStateItem_deviceNotLocked_doesNotThrowException() throws Exception {
        assumeTrue(deviceSupportsAppFunction("setDeviceStateItem"));

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "setDeviceStateItemParams")
                        .setPropertyString("key", "key")
                        .setPropertyString("value", "value")
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument("setDeviceStateItemParams", innerDoc)
                        .build();

        executeAppFunction("setDeviceStateItem", SETTINGS_PACKAGE, params);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testOffsetNumericDeviceStateItemByValue_deviceLocked_throwsException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "", "", "offsetNumericDeviceStateItemByValueParams")
                        .setPropertyString("key", "key")
                        .setPropertyDouble("valueAdjustment", 0.0)
                        .build();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                executeAppFunction(
                                        "offsetNumericDeviceStateItemByValue",
                                        SETTINGS_PACKAGE,
                                        params));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void
            testOffsetNumericDeviceStateItemByValue_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "offsetNumericDeviceStateItemByValueParams")
                        .setPropertyString("key", "key")
                        .setPropertyDouble("valueAdjustment", 0.0)
                        .setPropertyBoolean("requestInitiatedWhileUnlocked", true)
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument("offsetNumericDeviceStateItemByValueParams", innerDoc)
                        .build();

        executeAppFunction("offsetNumericDeviceStateItemByValue", SETTINGS_PACKAGE, params);
    }

    @Postsubmit(reason = "New test")
    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testOffsetNumericDeviceStateItemByValue_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("offsetNumericDeviceStateItemByValue"));

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "offsetNumericDeviceStateItemByValueParams")
                        .setPropertyString("key", "key")
                        .setPropertyDouble("valueAdjustment", 0.0)
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument("offsetNumericDeviceStateItemByValueParams", innerDoc)
                        .build();

        executeAppFunction("offsetNumericDeviceStateItemByValue", SETTINGS_PACKAGE, params);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void testAdjustNumericDeviceStateItemByPercentage_deviceLocked_throwsException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "", "", "adjustNumericDeviceStateItemByPercentageParams")
                        .setPropertyString("key", "key")
                        .setPropertyLong("percentageAdjustment", 0)
                        .build();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                executeAppFunction(
                                        "adjustNumericDeviceStateItemByPercentage",
                                        SETTINGS_PACKAGE,
                                        params));
        assertThat(e.getCause()).isInstanceOf(AppFunctionException.class);
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "New test")
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    public void
            testAdjustNumericDeviceStateItemByPercentage_requestInitiatedWhileUnlockedSet_doesNotThrowException()
                    throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));
        assumeTrue(supportsSecureLock());

        dpc(sDeviceState).devicePolicyManager().lockNow();
        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "adjustNumericDeviceStateItemByPercentageParams")
                        .setPropertyString("key", "key")
                        .setPropertyLong("percentageAdjustment", 0)
                        .setPropertyBoolean("requestInitiatedWhileUnlocked", true)
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument(
                                "adjustNumericDeviceStateItemByPercentageParams", innerDoc)
                        .build();

        executeAppFunction("adjustNumericDeviceStateItemByPercentage", SETTINGS_PACKAGE, params);
    }

    @Postsubmit(reason = "New test")
    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireDoesNotHaveFeature(FEATURE_LEANBACK)
    @RequireDoesNotHaveFeature(FEATURE_WATCH)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public void testAdjustNumericDeviceStateItemByPercentage_deviceNotLocked_doesNotThrowException()
            throws Exception {
        assumeTrue(deviceSupportsAppFunction("adjustNumericDeviceStateItemByPercentage"));

        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "adjustNumericDeviceStateItemByPercentageParams")
                        .setPropertyString("key", "key")
                        .setPropertyLong("percentageAdjustment", 0)
                        .build();
        GenericDocument params =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", "requestSchema")
                        .setPropertyDocument(
                                "adjustNumericDeviceStateItemByPercentageParams", innerDoc)
                        .build();

        executeAppFunction("adjustNumericDeviceStateItemByPercentage", SETTINGS_PACKAGE, params);
    }

    private ExecuteAppFunctionResponse executeAppFunction(
            String functionName, String packageName, GenericDocument params)
            throws InterruptedException, ExecutionException, TimeoutException {
        AppFunctionManager appFunctionManager =
                TestApis.context().instrumentedContext().getSystemService(AppFunctionManager.class);
        ExecuteAppFunctionRequest.Builder requestBuilder =
                new ExecuteAppFunctionRequest.Builder(packageName, functionName);
        if (params != null) {
            requestBuilder.setParameters(params);
        }
        ExecuteAppFunctionRequest request = requestBuilder.build();

        CompletableFuture<ExecuteAppFunctionResponse> future = new CompletableFuture<>();
        appFunctionManager.executeAppFunction(
                request,
                TestApis.context().instrumentedContext().getMainExecutor(),
                new CancellationSignal(),
                new OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>() {
                    @Override
                    public void onResult(ExecuteAppFunctionResponse result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(AppFunctionException error) {
                        future.completeExceptionally(error);
                    }
                });
        return future.get();
    }

    private GenericDocument buildInitiatedWhileUnlockedParams(String schemaType) {
        GenericDocument innerDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>(
                                "namespace", "id", schemaType)
                        .setPropertyBoolean("requestInitiatedWhileUnlocked", true)
                        .build();
        return new GenericDocument.Builder<GenericDocument.Builder<?>>(
                        "namespace", "id", "requestSchema")
                .setPropertyDocument(schemaType, innerDoc)
                .build();
    }

    private boolean deviceSupportsAppFunction(String functionName) throws AdbException {
        String command = "dumpsys app_function";
        return ShellCommand.builder(command).execute().trim().contains(functionName);
    }

    private boolean supportsSecureLock() {
        return FeatureUtil.hasSystemFeature(FEATURE_SECURE_LOCK_SCREEN);
    }
}
