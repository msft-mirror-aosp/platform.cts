/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.Manifest.permission.MANAGE_MULTIUSER_DEVICE_PROVISIONING_STATE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED;
import static android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpmRoleHolder;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.UserType.SYSTEM_USER;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;

import android.annotation.NonNull;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.MultiuserManagedDeviceProvisioningParams;
import android.app.admin.ProvisioningException;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.devicepolicy.cts.utils.DevicePolicyManagementRoleUtils;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.bedstead.accounts.annotations.EnsureHasAccount;
import com.android.bedstead.accounts.annotations.EnsureHasNoAccounts;
import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc;
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue;
import com.android.bedstead.multiuser.annotations.EnsureCanAddSecondaryUser;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.roles.RoleContext;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.CommonPermissions;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.compatibility.common.util.CddTest;
import com.android.eventlib.truth.EventLogsSubject;
import com.android.xts.root.annotations.RequireRootInstrumentation;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

// TODO(b/228016400): replace usages of createAndProvisionManagedProfile with a nene API
@RunWith(BedsteadJUnit4.class)
public class DevicePolicyManagementRoleHolderTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule public final TestRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private final TestAppProvider mTestAppProvider = new TestAppProvider();

    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            DeviceAdminApp.deviceAdminComponentName(sContext);
    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final ManagedProfileProvisioningParams MANAGED_PROFILE_PROVISIONING_PARAMS =
            createManagedProfileProvisioningParamsBuilder().build();
    private static final String MANAGED_USER_NAME = "managed user name";

    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String FEATURE_ALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED";

    private enum DmrhQualificationApi {
        SHOULD_ALLOW_BYPASSING_DEVICE_POLICY_MANAGEMENT_ROLE_QUALIFICATION,
        IS_PACKAGE_QUALIFIED_FOR_DEVICE_POLICY_MANAGEMENT_ROLE
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndProvisionManagedProfile_roleHolderIsInWorkProfile()
            throws ProvisioningException {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            Poll.forValue(() -> TestApis.packages().installedForUser(profile))
                    .toMeet(packages -> packages.contains(
                            Package.of(dpmRoleHolder(sDeviceState).packageName())))
                    .errorOnFail("Role holder package not installed on the managed profile.")
                    .await();
            }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureCanAddSecondaryUser
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = SYSTEM_USER)
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndManageUser_roleHolderIsInManagedUser() {
        try (UserReference userReference = UserReference.of(
                dpc(sDeviceState).devicePolicyManager().createAndManageUser(
                        dpc(sDeviceState).componentName(),
                        MANAGED_USER_NAME,
                        dpc(sDeviceState).componentName(),
                        /* adminExtras= */ null,
                        /* flags= */ 0))) {
            Poll.forValue(() -> TestApis.packages().installedForUser(userReference))
                    .toMeet(packages -> packages.contains(Package.of(
                            dpmRoleHolder(sDeviceState).packageName())))
                    .errorOnFail("Role holder package not installed on the managed user.")
                    .await();
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileRemoved_roleHolderReceivesBroadcast() throws Exception {
        UserHandle profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                MANAGED_PROFILE_PROVISIONING_PARAMS);

        TestApis.users().find(profile).remove();

        EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                        .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_REMOVED))
                .eventOccurred();
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileEntersQuietMode_roleHolderReceivesBroadcast() throws Exception {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            profile.setQuietMode(true);

            EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                            .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_UNAVAILABLE))
                    .eventOccurred();
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileStarted_roleHolderReceivesBroadcast() throws Exception {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            profile.setQuietMode(true);

            profile.setQuietMode(false);

            EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                            .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_AVAILABLE))
                    .eventOccurred();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    public void
            shouldAllowBypassingDevicePolicyManagementRoleQualification_noUsersAndAccounts_returnsTrue()
                    throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("shouldAllowBypassingDevicePolicyManagementRoleQualification", () -> {
                    TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                    return sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
                }).toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddSecondaryUser
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withNonTestUsers_false()
            throws Exception {
        TestApis.devicePolicy()
                .resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
        try (UserReference user =
                TestApis.users()
                        .createUser()
                        .name(
                                "shouldAllowBypassingDevicePolicyManagementRoleQualification_"
                                        + "withNonTestUsers_returnsFalse")
                        .forTesting(false)
                        .create()) {
            assertThat(
                    sDevicePolicyManager
                            .shouldAllowBypassingDevicePolicyManagementRoleQualification())
                    .isFalse();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddSecondaryUser
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withTestUsers_returnsTrue()
            throws Exception {
        TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
        try (UserReference user = TestApis.users().createUser()
                .forTesting(true)
                .create()) {
            assertThat(
                    sDevicePolicyManager
                            .shouldAllowBypassingDevicePolicyManagementRoleQualification())
                    .isTrue();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasAccount(
            onUser = ADDITIONAL_USER,
            features = {})
    public void
            shouldAllowBypassingDevicePolicyManagementRoleQualification_withNonAllowedAccounts_returnsFalse()
                    throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached -
        // REMOVE THIS ONCE ADD ACCOUNT AND REMOVE ACCOUNT IS BLOCKING CORRECTLY
        Poll.forValue(
                        "shouldAllowBypassingDevicePolicyManagementRoleQualification",
                        () -> {
                            TestApis.devicePolicy()
                                    .resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                            return sDevicePolicyManager
                                    .shouldAllowBypassingDevicePolicyManagementRoleQualification();
                        })
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts // TODO: Specify no accounts that don't match the account we actually want
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = FEATURE_ALLOW)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withAllowedAccounts_returnsTrue()
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("shouldAllowBypassingDevicePolicyManagementRoleQualification", () -> {
                    TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                    return sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
                }).toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withoutRequiredPermission_throwsSecurityException() {
        TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();

        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification());
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void callDmrhQualificationApi_noUsersAndAccounts_returnsTrue(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api)
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("callDmrhQualificationApi", () -> callDmrhQualificationApi(api))
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddSecondaryUser
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void isPackageQualifiedForDevicePolicyManagementRole_withNonTestUsers_false()
            throws Exception {
        try (UserReference user =
                TestApis.users()
                        .createUser()
                        .name(
                                "isPackageQualifiedForDevicePolicyManagementRole_"
                                        + "withNonTestUsers_false")
                        .forTesting(false)
                        .create()) {
            // The packageName is only used for checking if the application is marked as testOnly,
            // which is not relevant for this test case.
            boolean qualified =
                    sDevicePolicyManager.isPackageQualifiedForDevicePolicyManagementRole("package");
            assertThat(qualified).isFalse();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddSecondaryUser
    @EnsureHasDevicePolicyManagerRoleHolder
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void
            isPackageQualifiedForDevicePolicyManagementRole_withNonTestUsers_previouslyBypassed_true()
                    throws Exception {
        try (UserReference user =
                TestApis.users()
                        .createUser()
                        .name(
                                "isPackageQualifiedForDevicePolicyManagementRole_"
                                        + "withNonTestUsers_previouslyBypassed_true")
                        .forTesting(false)
                        .create()) {
            String packageName = dpmRoleHolder(sDeviceState).packageName();
            boolean qualified =
                    sDevicePolicyManager.isPackageQualifiedForDevicePolicyManagementRole(
                            packageName);
            assertThat(qualified).isTrue();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddSecondaryUser
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void callDmrhQualificationApi_withTestUsers_returnsTrue(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api)
            throws Exception {
        try (UserReference user = TestApis.users().createUser().forTesting(true).create()) {
            assertThat(callDmrhQualificationApi(api)).isTrue();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasAccount(
            onUser = ADDITIONAL_USER,
            features = {})
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void callDmrhQualificationApi_withNonAllowedAccounts_returnsFalse(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api)
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached -
        // REMOVE THIS ONCE ADD ACCOUNT AND REMOVE ACCOUNT IS BLOCKING CORRECTLY
        Poll.forValue("callDmrhQualificationApi", () -> callDmrhQualificationApi(api))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts // TODO: Specify no accounts that don't match the account we actually want
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = FEATURE_ALLOW)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void callDmrhQualificationApi_withAllowedAccounts_returnsTrue(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api)
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("callDmrhQualificationApi", () -> callDmrhQualificationApi(api))
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void callDmrhQualificationApi_withoutRequiredPermission_throwsSecurityException(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        assertThrows(SecurityException.class, () -> callDmrhQualificationApi(api));
    }

    private static ManagedProfileProvisioningParams.Builder
            createManagedProfileProvisioningParamsBuilder() {
        return new ManagedProfileProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME, PROFILE_OWNER_NAME);
    }

    /**
     * Verify that a non-preinstalled DMRH can be uninstalled when there is no management.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void uninstallAllowedForNonPreinstalledDmrhWhenUnmanaged() {
        dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented());
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isFalse();
    }

    /**
     * Verify that DMRH can't be uninstalled from managed user.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void uninstallNotAllowedForNonPreinstalledDmrhWhenManaged() {
        assertThrows(NeneException.class, () ->
                dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented()));
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isTrue();
    }

    /**
     * Verifies that a non-preinstalled DMRH on the work profile can be uninstalled from the
     * personal user.
     * If the DMRH is preinstalled, the behaviour will be different that the ability for the
     * personal side to "uninstall updates" is blocked. Ideally we want to test this scenario as
     * well but getting a preinstalled DMRH in CTS is not possible right now.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = WORK_PROFILE)
    @Test
    public void workProfileDmrhCanBeUninstalledFromPersonal() {
        Package dmrhPackage = dpmRoleHolder(sDeviceState).pkg();

        if (!dmrhPackage.installedOnUser()) {
            dmrhPackage.installExisting(TestApis.users().instrumented());
        }

        dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented());
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isFalse();
    }

    /**
     * Verifies that a non-preinstalled DMRH on the work profile can't be uninstalled from the
     * profile.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = WORK_PROFILE)
    @Test
    public void workProfileDmrhCantBeUninstalledFromWork() {
        assertThrows(
                NeneException.class,
                () -> dpmRoleHolder(sDeviceState).pkg().uninstall(workProfile(sDeviceState)));
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser(workProfile(sDeviceState)))
                .isTrue();
    }

    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasPermission(android.Manifest.permission.MANAGE_ROLE_HOLDERS)
    public void verifyManuallySettingTestAppAsDmrh() {
        TestApp dpc =
                mTestAppProvider
                        .query()
                        .wherePackageName()
                        .isEqualTo("com.android.DevicePolicyManagerRoleHolder")
                        .whereTestOnly()
                        .isEqualTo(true)
                        .get();

        try (TestAppInstance ignored = dpc.install();
                RoleContext ignored1 =
                        TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(dpc.pkg())) {
            assertThat(TestApis.roles().getRoleHolders(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT))
                    .contains(dpc.packageName());
        }
        assertThat(TestApis.roles().getRoleHolders(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT))
                .doesNotContain(dpc.packageName());
    }

    /**
     * Verifies that the checks for adding a DMRH cannot be bypassed when a non-test-only DO exists
     * on the device.
     */
    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasPermission(CommonPermissions.MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    // TODO(b/476350458): enable test for HSUM
    @RequireNotHeadlessSystemUserMode(reason = "No non-testonly test app which supports headless")
    public void nonTestOnlyDeviceOwner_shouldAllowBypassing_false(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApp dpc = getDeviceAdminTestApp(/* isTestOnly */ false);
        ComponentName adminComponent = getDeviceAdminComponentName(dpc);
        try (TestAppInstance ignored = dpc.install(TestApis.users().instrumented());
                DeviceOwner ignored1 = TestApis.devicePolicy().setDeviceOwner(adminComponent)) {
            assertThat(callDmrhQualificationApi(api)).isFalse();
        }
    }

    /**
     * Verifies that the checks for adding a DMRH can be bypassed for the default DMRH package even
     * when a non-test-only DO exists on the device.
     */
    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasPermission(CommonPermissions.MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    // TODO(b/476350458): enable test for HSUM
    @RequireNotHeadlessSystemUserMode(reason = "No non-testonly test app which supports headless")
    public void isPackageQualified_defaultDmrh_nonTestOnlyDeviceOwner_true() {
        String defaultDmrh =
                DevicePolicyManagementRoleUtils.getDefaultRoleHolderPackageName(sContext);
        assumeNotNull(defaultDmrh);
        TestApp dpc = getDeviceAdminTestApp(/* isTestOnly */ false);
        ComponentName adminComponent = getDeviceAdminComponentName(dpc);

        try (TestAppInstance ignored = dpc.install(TestApis.users().instrumented());
                DeviceOwner ignored1 = TestApis.devicePolicy().setDeviceOwner(adminComponent)) {

            boolean isQualified =
                    sDevicePolicyManager.isPackageQualifiedForDevicePolicyManagementRole(
                            defaultDmrh);

            assertThat(isQualified).isTrue();
        }
    }

    /**
     * Verifies that the checks for adding a DMRH can be bypassed when a test-only DO exists on the
     * device.
     */
    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasPermission(android.Manifest.permission.MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void testOnlyDeviceOwner_shouldAllowBypassing_true(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApp dpc = getDeviceAdminTestApp(/* isTestOnly */ true);
        ComponentName adminComponent = getDeviceAdminComponentName(dpc);
        try (TestAppInstance ignored = dpc.install(TestApis.users().system());
                DeviceOwner ignored1 = TestApis.devicePolicy().setDeviceOwner(adminComponent)) {

            assertThat(callDmrhQualificationApi(api)).isTrue();
        }
    }

    /**
     * Verifies that the checks for adding a DMRH cannot be bypassed when a non-test-only PO exists
     * on a managed profile.
     */
    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasNoWorkProfile
    @EnsureHasPermission(CommonPermissions.MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void nonTestOnlyProfileOwner_shouldAllowBypassing_false(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApp dpc = getDeviceAdminTestApp(/* isTestOnly */ false);
        ComponentName adminComponent = getDeviceAdminComponentName(dpc);

        try (UserReference profileUser = createAndStartProfile()) {
            TestAppInstance ignored = dpc.install(profileUser);
            TestApis.devicePolicy().setProfileOwner(profileUser, adminComponent);

            assertThat(callDmrhQualificationApi(api)).isFalse();
        }
    }

    /**
     * Verifies that the checks for adding a DMRH can be bypassed when a test-only PO exists on a
     * managed profile.
     */
    @Test
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasNoWorkProfile
    @EnsureHasPermission(android.Manifest.permission.MANAGE_ROLE_HOLDERS)
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING)
    public void testOnlyProfileOwner_shouldAllowBypassing_true(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApp dpc = getDeviceAdminTestApp(/* isTestOnly */ true);
        ComponentName adminComponent = getDeviceAdminComponentName(dpc);

        try (UserReference profileUser = createAndStartProfile()) {
            TestAppInstance ignored = dpc.install(profileUser);
            TestApis.devicePolicy().setProfileOwner(profileUser, adminComponent);

            assertThat(callDmrhQualificationApi(api)).isTrue();
        }
    }

    /**
     * Verifies that the checks for adding a DMRH can be bypassed when a test-only DMRH (and no
     * non-test-only DMRH) exists on a multi-user managed device.
     */
    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasNoAdditionalUser
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = SYSTEM_USER)
    @EnsureHasPermission({
        CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS,
        CommonPermissions.MANAGE_ROLE_HOLDERS,
        MANAGE_MULTIUSER_DEVICE_PROVISIONING_STATE
    })
    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.TRUE)
    @RequireHeadlessSystemUserMode(
            reason =
                    "This test provisions a multi-user device, which is "
                            + " only supported on headless system user mode devices",
            interactive = OptionalBoolean.TRUE)
    @RequireResourcesBooleanValue(
            configName = "config_enableMultiuserManagement",
            requiredValue = true)
    @RequiresFlagsEnabled({
        android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING,
        android.app.admin.flags.Flags.FLAG_MULTI_USER_MANAGEMENT_DEVICE_PROVISIONING
    })
    @RequireRootInstrumentation(
            reason = "Requires permission MANAGE_MULTIUSER_DEVICE_PROVISIONING_STATE")
    public void testOnlyDevicePolicyManagementRoleHolder_shouldAllowBypassing_true(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApis.users().ensureNoOtherUsers();
        Package dmrhPackage = dpmRoleHolder(sDeviceState).pkg();

        try (var ignored =
                TestApis.devicePolicy()
                        .setDevicePolicyManagementRoleHolder(
                                dmrhPackage, TestApis.users().instrumented())) {
            try {
                provisionMultiuserManagedDevice(dmrhPackage.packageName());

                assertThat(callDmrhQualificationApi(api)).isTrue();
            } finally {
                TestApis.devicePolicy().clearMultiuserDeviceManagement(dmrhPackage.packageName());
            }
        }
    }

    /**
     * Verifies that the checks for adding a DMRH cannot be bypassed when a non test-only DMRH
     * exists on a multi-user managed device.
     *
     * <p> The test manually sets a non testOnly DMRH on the initial user (most often user 10),
     * then provisions the device using a testOnly DMRH set on system user (user 0).
     * It is necessary sing two different DMRHs as using a non testOnly test app for MUM
     * provisioning will throw an error during cleanup.
     */
    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasNoAccounts
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = SYSTEM_USER)
    @EnsureHasPermission({
        CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS,
        CommonPermissions.MANAGE_ROLE_HOLDERS
    })
    @RequireHeadlessSystemUserMode(
            reason =
                    "This test provisions a multi-user device, which is "
                            + " only supported on headless system user mode devices",
            interactive = OptionalBoolean.TRUE)
    @RequireResourcesBooleanValue(
            configName = "config_enableMultiuserManagement",
            requiredValue = true)
    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.TRUE)
    @RequiresFlagsEnabled({
        android.app.admin.flags.Flags.FLAG_SECURE_ADB_ROLE_BYPASSING,
        android.app.admin.flags.Flags.FLAG_MULTI_USER_MANAGEMENT_DEVICE_PROVISIONING
    })
    public void nonTestOnlyDevicePolicyManagementRoleHolder_shouldAllowBypassing_false(
            @EnumTestParameter(DmrhQualificationApi.class) DmrhQualificationApi api) {
        TestApp nonTestOnlyTestApp = getDeviceAdminTestApp(/* isTestOnly */ false);
        var dmrhPackageName = dpmRoleHolder(sDeviceState).packageName();

        try (var ignored1 = nonTestOnlyTestApp.install(TestApis.users().initial());
                var ignored2 = TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(
                        nonTestOnlyTestApp.pkg(), TestApis.users().initial())) {
            try {
                provisionMultiuserManagedDevice(dmrhPackageName);
                assertThat(callDmrhQualificationApi(api)).isFalse();
            } finally {
                TestApis.devicePolicy().clearMultiuserDeviceManagement(dmrhPackageName);
            }
        }
    }

    private static void provisionMultiuserManagedDevice(String dmrhPackageName) {
        withIncompleteSetupOnAllUsers(
                () -> {
                    var params = new MultiuserManagedDeviceProvisioningParams.Builder(dmrhPackageName).build();
                    try {
                        sDevicePolicyManager.provisionMultiuserManagedDevice(params);
                    } catch (ProvisioningException e) {
                        throw new RuntimeException(e);
                    }
                });
        assertThat(sDevicePolicyManager.isDeviceManaged()).isTrue();
    }

    /**
     * Makes sure the all the users on the devices have not finished setup. Resets to initial state
     * after the {@code block} finishes.
     */
    private static void withIncompleteSetupOnAllUsers(Runnable block) {
        var allUsers = TestApis.users().all();

        Map<UserReference, Boolean> originalSetupCompleteMap = new HashMap<>();
        for (UserReference user : allUsers) {
            originalSetupCompleteMap.put(user, user.getSetupComplete());
        }

        try {
            for (UserReference user : allUsers) {
                user.setSetupComplete(false);
            }
            block.run();
        } finally {
            originalSetupCompleteMap.forEach(UserReference::setSetupComplete);
        }
    }

    private static UserReference createAndStartProfile() {
        return TestApis.users()
                .createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
    }

    private TestApp getDeviceAdminTestApp(boolean isTestOnly) {
        return mTestAppProvider
                .query()
                .whereIsDeviceAdmin()
                .isTrue()
                .whereTestOnly()
                .isEqualTo(isTestOnly)
                .get();
    }

    // The packageName is only used for checking if the application is already a Device Policy
    // Management Role Holder, which may not be relevant for some test cases.
    private static boolean callDmrhQualificationApi(@NonNull DmrhQualificationApi api) {
        return callDmrhQualificationApi(api, "generic_package_name");
    }

    private static boolean callDmrhQualificationApi(
            @NonNull DmrhQualificationApi api, @NonNull String packageName) {
        return switch (api) {
            case SHOULD_ALLOW_BYPASSING_DEVICE_POLICY_MANAGEMENT_ROLE_QUALIFICATION -> {
                TestApis.devicePolicy()
                        .resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                yield sDevicePolicyManager
                        .shouldAllowBypassingDevicePolicyManagementRoleQualification();
            }
            case IS_PACKAGE_QUALIFIED_FOR_DEVICE_POLICY_MANAGEMENT_ROLE ->
                    sDevicePolicyManager.isPackageQualifiedForDevicePolicyManagementRole(
                            packageName);
        };
    }

    private static ComponentName getDeviceAdminComponentName(TestApp testApp) {
        return testApp.pkg()
                .component(testApp.packageName() + ".DeviceAdminReceiver")
                .componentName();
    }
}
