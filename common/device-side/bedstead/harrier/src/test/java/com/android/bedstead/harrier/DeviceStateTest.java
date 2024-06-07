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

package com.android.bedstead.harrier;

import static android.app.AppOpsManager.OPSTR_START_FOREGROUND;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.bedstead.multiuser.UsersComponentKt.user;
import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.UserType.SECONDARY_USER;
import static com.android.bedstead.harrier.annotations.RequireAospBuild.GMS_CORE_PACKAGE;
import static com.android.bedstead.harrier.annotations.RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SYSTEM_USER_TYPE_NAME;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_BLUETOOTH;
import static com.android.bedstead.permissions.CommonPermissions.READ_CONTACTS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.contentsuggestions.ContentSuggestionsManager;
import android.app.role.RoleManager;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.view.contentcapture.ContentCaptureManager;

import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest;
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceDisabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceEnabled;
import com.android.bedstead.harrier.annotations.EnsureDemoMode;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasCloneProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoCloneProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoPrivateProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPrivateProfile;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode;
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePropertySet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet;
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme;
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation;
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.EnsureWillNotTakeQuickBugReports;
import com.android.bedstead.harrier.annotations.EnsureWillTakeQuickBugReports;
import com.android.bedstead.harrier.annotations.OtherUser;
import com.android.bedstead.harrier.annotations.RequireAospBuild;
import com.android.bedstead.harrier.annotations.RequireCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireGmsBuild;
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireInstantApp;
import com.android.bedstead.harrier.annotations.RequireLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireNotCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.harrier.annotations.RequirePackageInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled;
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.bedstead.harrier.annotations.RequireRunOnCloneProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrivateProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireRunOnTvProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnVisibleBackgroundNonProfileUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.RequireUserSupported;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.harrier.annotations.TestTag;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.parameterized.IncludeDarkMode;
import com.android.bedstead.harrier.annotations.parameterized.IncludeLandscapeOrientation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeLightMode;
import com.android.bedstead.harrier.annotations.parameterized.IncludePortraitOrientation;
import com.android.bedstead.harrier.policies.DisallowBluetooth;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.display.Display;
import com.android.bedstead.nene.display.DisplayProperties;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.testapp.NotFoundException;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceStateTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";

    // Expects that this package name matches an actual test app
    private static final String TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.LockTaskApp";
    private static final String TEST_APP_PACKAGE_NAME2 = "com.android.bedstead.testapp.SmsApp";
    private static final String TEST_APP_USED_IN_FIELD_NAME =
            "com.android.bedstead.testapp.NotEmptyTestApp";

    // This is not used but is depended on by testApps_staticTestAppsAreNotReleased_1 and
    // testApps_staticTestAppsAreNotReleased_2 which test that this testapp isn't released
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get();

    private static final String CLONE_PROFILE_TYPE_NAME = "android.os.usertype.profile.CLONE";
    private static final String PRIVATE_PROFILE_TYPE_NAME = "android.os.usertype.profile.PRIVATE";

    private static final String USER_RESTRICTION = UserManager.DISALLOW_AUTOFILL;
    private static final String SECOND_USER_RESTRICTION = UserManager.DISALLOW_AIRPLANE_MODE;

    @Test
    @RequireRunOnWorkProfile
    public void workProfile_runningOnWorkProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.workProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasTvProfile
    public void tvProfile_tvProfileProvided_returnsTvProfile() {
        assertThat(sDeviceState.tvProfile()).isNotNull();
    }

    @Test
    @RequireRunOnTvProfile
    public void tvProfile_runningOnTvProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.tvProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasNoTvProfile
    public void tvProfile_noTvProfile_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
    }

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void tvProfile_createdTvProfile_throwsException() {
        try (UserReference tvProfile = TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(TV_PROFILE_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
        }
    }

    @Test
    @EnsureHasTvProfile
    public void ensureHasTvProfileAnnotation_tvProfileExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void ensureHasNoTvProfileAnnotation_tvProfileDoesNotExist() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @EnsureHasCloneProfile
    public void cloneProfile_cloneProfileProvided_returnsCloneProfile() {
        assertThat(sDeviceState.cloneProfile()).isNotNull();
    }

    @Test
    @EnsureHasCloneProfile
    public void ensureHasCloneProfileAnnotation_cloneProfileExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(CLONE_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNotNull();
    }

    @Test
    @EnsureHasNoCloneProfile
    public void ensureHasNoCloneProfileAnnotation_cloneProfileDoesNotExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(CLONE_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @RequireRunOnCloneProfile
    public void cloneProfile_runningOnCloneProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.cloneProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @RequireRunOnCloneProfile
    public void requireRunOnCloneProfileAnnotation_isRunningOnCloneProfile() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(CLONE_PROFILE_TYPE_NAME);
    }

    @Test
    @EnsureHasPrivateProfile
    public void privateProfile_privateProfileProvided_returnsPrivateProfile() {
        assertThat(sDeviceState.privateProfile()).isNotNull();
    }

    @Test
    @EnsureHasPrivateProfile
    public void ensureHasPrivateProfileAnnotation_privateProfileExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(PRIVATE_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNotNull();
    }

    @Test
    @EnsureHasNoPrivateProfile
    public void ensureHasNoPrivateProfileAnnotation_privateProfileDoesNotExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(PRIVATE_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @RequireRunOnPrivateProfile
    public void privateProfile_runningOnPrivateProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.privateProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @RequireRunOnPrivateProfile
    public void requireRunOnPrivateProfileAnnotation_isRunningOnPrivateProfile() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(PRIVATE_PROFILE_TYPE_NAME);
    }

    @Test
    @EnsureHasSecondaryUser
    public void secondaryUser_secondaryUserProvided_returnsSecondaryUser() {
        assertThat(sDeviceState.secondaryUser()).isNotNull();
    }

    @Test
    @EnsureHasSecondaryUser
    public void user_userProvided_returnUser() {
        assertThat(user(sDeviceState, SECONDARY_USER_TYPE_NAME)).isNotNull();
    }

    @Test
    @RequireRunOnSecondaryUser
    public void secondaryUser_runningOnSecondaryUser_returnsCurrentUser() {
        assertThat(sDeviceState.secondaryUser()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    public void secondaryUser_noSecondaryUser_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    public void secondaryUser_createdSecondaryUser_throwsException() {
        try (UserReference secondaryUser = TestApis.users().createUser()
                .type(TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    public void ensureHasSecondaryUserAnnotation_secondaryUserExists() {
        assertThat(
                TestApis.users()
                        .findUsersOfType(
                                TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME)))
                .isNotEmpty();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): Test the forUser argument

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    public void ensureHasNoSecondaryUserAnnotation_secondaryUserDoesNotExist() {
        assertThat(TestApis.users().findUserOfType(
                TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
        ).isNull();
    }

    @RequireRunOnWorkProfile
    public void requireRunOnWorkProfileAnnotation_isRunningOnWorkProfile() {
        assertThat(
                TestApis.users().instrumented().type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME);
    }

    @Test
    @RequireRunOnWorkProfile
    public void requireRunOnWorkProfileAnnotation_workProfileHasProfileOwner() {
        assertThat(
                TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented())
        ).isNotNull();
    }

    @Test
    @RequireRunOnSecondaryUser
    public void requireRunOnSecondaryUserAnnotation_isRunningOnSecondaryUser() {
        assertThat(
                TestApis.users().instrumented().type().name()).isEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    // NOTE: this test must be manually run, as Test Bedstead doesn't support the
    // secondary_user_on_secondary_display metadata (for example, running
    //   atest --user-type secondary_user_on_secondary_display HarrierTest:com.android.bedstead
    //   .harrier
    //   .DeviceStateTest
    //   #requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser
    // would assumption-fail, even though the module is not annotated to support it). So, you need
    // to manually execute steps like:
    //   adb shell pm create-user TestUser // id 42
    //   adb shell am start-user -w --display 2 42
    //   adb shell pm install-existing --user 42  com.android.bedstead.harrier.test
    //   adb shell am instrument --user 42 -e class com.android.bedstead.harrier
    //   .DeviceStateTest
    //   #requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser -w com.android.bedstead.harrier.test/androidx.test.runner.AndroidJUnitRunner
    @Test
    @RequireRunOnVisibleBackgroundNonProfileUser
    public void requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser() {
        UserReference user = TestApis.users().instrumented();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isTrue();
    }

    @Test
    @RequireRunNotOnVisibleBackgroundNonProfileUser
    public void requireRunNotOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsNotVisibleBackgroundNonProfileUser() {
        UserReference user = TestApis.users().instrumented();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    @RequirePackageInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageInstalledAnnotation_anyUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isNotEmpty();
    }

    @Test
    @RequirePackageInstalled(GMS_CORE_PACKAGE)
    public void requirePackageInstalledAnnotation_currentUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUser())
                .isTrue();
    }

    @Test
    @RequirePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @RequirePackageNotInstalled(GMS_CORE_PACKAGE)
    public void requirePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @EnsurePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @EnsurePackageNotInstalled(GMS_CORE_PACKAGE)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @RequireAospBuild
    public void requireAospBuildAnnotation_isRunningOnAospBuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isFalse();
    }

    @Test
    @RequireGmsBuild
    public void requireGmsBuildAnnotation_isRunningOnGmsbuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isTrue();
    }

    @Test
    @RequireCnGmsBuild
    public void requireCnGmsBuildAnnotation_isRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireNotCnGmsBuild
    public void requireNotCnGmsBuildAnnotation_isNotRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);

    }

    @Test
    @RequireFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireHasFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireDoesNotHaveFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireDoesNotHaveFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireSdkVersion(min = 27)
    public void requireSdkVersionAnnotation_min_minIsMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
    }

    @Test
    @RequireSdkVersion(max = 30)
    public void requireSdkVersionAnnotation_max_maxIsMet() {
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireSdkVersion(min = 27, max = 30)
    public void requireSdkVersionAnnotation_minAndMax_bothAreMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireRunOnPrimaryUser
    public void requireRunOnPrimaryUserAnnotation_isRunningOnPrimaryUser() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(SYSTEM_USER_TYPE_NAME);
    }

    @Test
    @RequireRunOnTvProfile
    public void requireRunOnTvProfileAnnotation_isRunningOnTvProfile() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(TV_PROFILE_TYPE_NAME);
    }

    @Test
    @RequireRunOnInitialUser
    public void requireRunOnUser_isCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.initialUser());
    }

    @Test
    @RequireRunOnInitialUser(switchedToUser = FALSE)
    public void requireRunOnUser_specifyNotSwitchedToUser_isNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.initialUser());
    }

    @Test
    @RequireRunNotOnSecondaryUser
    public void requireRunNotOnSecondaryUser_currentUserIsNotSecondary() {
        assertThat(TestApis.users().current().type().name()).isNotEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @RequireRunNotOnSecondaryUser
    public void requireRunNotOnSecondaryUser_instrumentedUserIsNotSecondary() {
        assertThat(TestApis.users().instrumented().type().name())
                .isNotEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @EnsureHasAdditionalUser(switchedToUser = FALSE) // We don't test the default as it's ANY
    public void ensureHasUser_specifyIsNotSwitchedToUser_isNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.additionalUser());
    }

    @Test
    @EnsureHasAdditionalUser(switchedToUser = TRUE)
    public void ensureHasUser_specifySwitchedToUser_isCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.additionalUser());
    }

    @Test
    @EnsureHasAdditionalUser
    public void ensureHasAdditionalUser_hasAdditionalUser() {
        assertThat(sDeviceState.additionalUser()).isNotNull();
    }

    @Test
    @EnsureHasNoAdditionalUser
    public void ensureHasNoAdditionalUser_doesNotHaveAdditionalUser() {
        assertThrows(IllegalStateException.class, sDeviceState::additionalUser);
    }

    @Test
    @RequireRunOnWorkProfile
    public void requireRunOnProfile_parentIsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @RequireRunOnWorkProfile(switchedToParentUser = FALSE)
    public void requireRunOnProfile_specifyNotSwitchedToParentUser_parentIsNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @RequireNotHeadlessSystemUserMode(reason = "Test")
    public void requireNotHeadlessSystemUserModeAnnotation_notHeadlessSystemUserMode() {
        assertThat(TestApis.users().isHeadlessSystemUserMode()).isFalse();
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Test")
    public void requireHeadlessSystemUserModeAnnotation_isHeadlessSystemUserMode() {
        assertThat(TestApis.users().isHeadlessSystemUserMode()).isTrue();
    }

    @Test
    @RequireLowRamDevice(reason = "Test")
    public void requireLowRamDeviceAnnotation_isLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isTrue();
    }

    @Test
    @RequireNotLowRamDevice(reason = "Test")
    public void requireNotLowRamDeviceAnnotation_isNotLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isFalse();
    }

    @Test
    @RequireVisibleBackgroundUsers(reason = "Test")
    public void requireVisibleBackgroundUsersAnnotation_supported() {
        assertThat(TestApis.users().isVisibleBackgroundUsersSupported()).isTrue();
    }

    @Test
    @RequireNotVisibleBackgroundUsers(reason = "Test")
    public void requireNotVisibleBackgroundUsersAnnotation_notSupported() {
        assertThat(TestApis.users().isVisibleBackgroundUsersSupported()).isFalse();
    }

    @Test
    @RequireVisibleBackgroundUsersOnDefaultDisplay(reason = "Test")
    public void requireVisibleBackgroundUsersOnDefaultDisplayAnnotation_supported() {
        assertThat(TestApis.users().isVisibleBackgroundUsersOnDefaultDisplaySupported()).isTrue();
    }

    @Test
    @RequireNotVisibleBackgroundUsersOnDefaultDisplay(reason = "Test")
    public void requireNotVisibleBackgroundUsersOnDefaultDisplayAnnotation_notSupported() {
        assertThat(TestApis.users().isVisibleBackgroundUsersOnDefaultDisplaySupported()).isFalse();
    }

    @Test
    @TestTag("TestTag")
    public void testTagAnnotation_testTagIsSet() {
        assertThat(Tags.hasTag("TestTag")).isTrue();
    }


    @Test
    @EnsureScreenIsOn
    public void ensureScreenIsOnAnnotation_screenIsOn() {
        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @Test
    @EnsurePasswordNotSet
    public void requirePasswordNotSetAnnotation_passwordNotSet() {
        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser
    @OtherUser(SECONDARY_USER)
    public void otherUserAnnotation_otherUserReturnsCorrectType() {
        assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    public void otherUser_noOtherUserSpecified_throwsException() {
        assertThrows(IllegalStateException.class, () -> sDeviceState.otherUser());
    }

    @Test
    @EnsureBluetoothEnabled
    public void ensureBluetoothEnabledAnnotation_bluetoothIsEnabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isTrue();
    }

    @Test
    @EnsureBluetoothDisabled
    public void ensureBluetoothDisabledAnnotation_bluetoothIsDisabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isFalse();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_1() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_2() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_staticTestAppsAreNotReleased_1() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @Test
    public void testApps_staticTestAppsAreNotReleased_2() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalled() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME).installedOnUser()).isTrue();
    }

    @EnsureHasSecondaryUser
    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)), onUser = SECONDARY_USER)
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalledOnCorrectUser() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME)
                .installedOnUser(sDeviceState.secondaryUser())).isTrue();
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @Test
    public void testApp_returnsTestApp() {
        assertThat(sDeviceState.testApp().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void testApp_noHarrierManagedTestApp_throwsException() {
        try (TestAppInstance testApp = sDeviceState.testApps().any().install()) {
            assertThrows(NeneException.class, sDeviceState::testApp);
        }
    }

    @EnsureTestAppInstalled(key = "testApp1", query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppInstalled(key = "testApp2", query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME2)))
    @Test
    public void testApp_withKey_returnsCorrectTestApp() {
        assertThat(sDeviceState.testApp("testApp1").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(sDeviceState.testApp("testApp2").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME2);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)), isPrimary = true)
    @Test
    public void dpc_primaryTestApp_returnsTestApp() {
        assertThat(sDeviceState.dpc().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppHasPermission(READ_CONTACTS)
    @Test
    public void ensureTestAppHasPermissionAnnotation_testAppHasPermission() {
        assertThat(sDeviceState.testApp().context().checkSelfPermission(READ_CONTACTS))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppDoesNotHavePermission(READ_CONTACTS)
    @Test
    public void ensureTestAppDoesNotHavePermissionAnnotation_testAppDoesNotHavePermission() {
        assertThat(sDeviceState.testApp().context().checkSelfPermission(READ_CONTACTS))
                .isNotEqualTo(PERMISSION_GRANTED);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppHasAppOp(OPSTR_START_FOREGROUND)
    @Test
    public void ensureTestAppHasAppOpAnnotation_testAppHasAppOp() {
        assertThat(sDeviceState.testApp()
                .testApp().pkg().appOps().get(OPSTR_START_FOREGROUND)).isEqualTo(ALLOWED);
    }

    @Test
    @RequireRunOnWorkProfile(isOrganizationOwned = true)
    public void requireRunOnWorkProfile_isOrganizationOwned_organizationOwnedisTrue() {
        assertThat(((ProfileOwner) sDeviceState.profileOwner(
                sDeviceState.workProfile()).devicePolicyController()).isOrganizationOwned())
                .isTrue();
    }

    @Test
    @RequireRunOnWorkProfile(isOrganizationOwned = false)
    public void requireRunOnWorkProfile_isNotOrganizationOwned_organizationOwnedIsFalse() {
        assertThat(((ProfileOwner) sDeviceState.profileOwner(
                sDeviceState.workProfile()).devicePolicyController()).isOrganizationOwned())
                .isFalse();
    }

    //TODO(b/300218365): Test that settings are returned to their original values in teardown.

    @EnsureSecureSettingSet(key = "testSecureSetting", value = "testValue")
    @Test
    public void ensureSecureSettingSetAnnotation_secureSettingIsSet() {
        assertThat(TestApis.settings().secure().getString("testSecureSetting"))
                .isEqualTo("testValue");
    }

    @EnsureGlobalSettingSet(key = "testGlobalSetting", value = "testValue")
    @Test
    public void ensureGlobalSettingSetAnnotation_globalSettingIsSet() {
        assertThat(TestApis.settings().global().getString("testGlobalSetting"))
                .isEqualTo("testValue");
    }

    @EnsureDemoMode
    @Test
    public void ensureDemoModeAnnotation_deviceIsInDemoMode() {
        assertThat(TestApis.settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE))
                .isEqualTo(1);
    }

    @EnsureNotDemoMode
    @Test
    public void ensureNotDemoModeAnnotation_deviceIsNotInDemoMode() {
        assertThat(TestApis.settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE))
                .isEqualTo(0);
    }

    @RequireInstantApp(reason = "Testing RequireInstantApp")
    @Test
    public void requireInstantAppAnnotation_isInstantApp() {
        assertThat(TestApis.packages().instrumented().isInstantApp()).isTrue();
    }

    @RequireNotInstantApp(reason = "Testing RequireNotInstantApp")
    @Test
    public void requireNotInstantAppAnnotation_isNotInstantApp() {
        assertThat(TestApis.packages().instrumented().isInstantApp()).isFalse();
    }

    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
    @Test
    public void requireRunOnAnnotation_switchedToAny_switches() {
        assertThat(TestApis.users().instrumented()).isEqualTo(TestApis.users().current());
    }

    @EnsureHasAdditionalUser(switchedToUser = TRUE)
    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
    @Test
    public void requireRunOnAnnotation_switchedToAny_AnotherAnnotationSwitches_doesNotSwitch() {
        assertThat(TestApis.users().instrumented()).isNotEqualTo(TestApis.users().current());
    }

    @EnsureHasAccountAuthenticator
    @Test
    public void ensureHasAccountAuthenticatorAnnotation_accountAuthenticatorIsInstalled() {
        assertThat(sDeviceState.accounts().testApp().pkg().installedOnUser()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasAccountAuthenticator(onUser = ADDITIONAL_USER)
    public void ensureHasAccountAuthenticatorAnnotation_differentUser_accountAuthenticatorIsInstalledOnDifferentUser() {
        assertThat(sDeviceState.accounts(sDeviceState.additionalUser())
                .testApp().pkg().installedOnUser(sDeviceState.additionalUser())).isTrue();
    }

    @EnsureHasAccount
    @Test
    public void ensureHasAccountAnnotation_accountExists() {
        assertThat(sDeviceState.accounts().allAccounts()).isNotEmpty();
    }

    @EnsureHasAccounts({
            @EnsureHasAccount, @EnsureHasAccount
    })
    @Test
    public void ensureHasAccountsAnnotation_hasMultipleAccounts() {
        assertThat(sDeviceState.accounts().allAccounts().size()).isGreaterThan(1);
    }

    @EnsureHasAccount
    @Test
    public void account_returnsAccount() {
        assertThat(sDeviceState.account()).isNotNull();
    }

    @EnsureHasAccount(key = "testKey")
    @Test
    public void account_withKey_returnsAccount() {
        assertThat(sDeviceState.account("testKey")).isNotNull();
    }

    @EnsureHasNoAccounts
    @Test
    public void ensureHasNoAccountsAnnotation_hasNoAccounts() {
        assertThat(TestApis.accounts().all()).isEmpty();
    }

    @EnsureHasUserRestriction(USER_RESTRICTION)
    @Test
    public void ensureHasUserRestrictionAnnotation_userRestrictionIsSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(USER_RESTRICTION)
    @Test
    public void ensureDoesNotHaveUserRestrictionAnnotation_userRestrictionIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isFalse();
    }

    @EnsureHasUserRestriction(USER_RESTRICTION)
    @EnsureHasUserRestriction(SECOND_USER_RESTRICTION)
    @Test
    public void ensureHasUserRestrictionAnnotation_multipleRestrictions_userRestrictionsAreSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue();
        assertThat(TestApis.devicePolicy().userRestrictions()
                .isSet(SECOND_USER_RESTRICTION)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(USER_RESTRICTION)
    @EnsureDoesNotHaveUserRestriction(SECOND_USER_RESTRICTION)
    @Test
    public void ensureDoesNotHaveUserRestrictionAnnotation_multipleRestrictions_userRestrictionsAreNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isFalse();
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(SECOND_USER_RESTRICTION))
                .isFalse();
    }

    @EnsureHasAdditionalUser
    @EnsureHasUserRestriction(value = USER_RESTRICTION, onUser = ADDITIONAL_USER)
    @Test
    public void ensureHasUserRestrictionAnnotation_differentUser_userRestrictionIsSet() {
        assertThat(TestApis.devicePolicy().userRestrictions(sDeviceState.additionalUser())
                .isSet(USER_RESTRICTION)).isTrue();
    }

    @EnsureHasAdditionalUser
    @EnsureDoesNotHaveUserRestriction(value = USER_RESTRICTION, onUser = ADDITIONAL_USER)
    @Test
    public void ensureDoesNotHaveUserRestrictionAnnotation_differentUser_userRestrictionIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions(sDeviceState.additionalUser())
                .isSet(USER_RESTRICTION)).isFalse();
    }

    @EnsureWifiEnabled
    @Test
    public void ensureWifiEnabledAnnotation_wifiIsEnabled() {
        assertThat(TestApis.wifi().isEnabled()).isTrue();
    }

    @EnsureWifiDisabled
    @Test
    public void ensureWifiDisabledAnnotation_wifiIsNotEnabled() {
        assertThat(TestApis.wifi().isEnabled()).isFalse();
    }

    @RequireSystemServiceAvailable(ContentCaptureManager.class)
    @Test
    public void requireSystemServiceAvailable_systemServiceIsAvailable() {
        assertThat(TestApis.context().instrumentedContext()
                .getSystemService(ContentCaptureManager.class)).isNotNull();
    }

    @RequireRunOnWorkProfile(dpcKey = RequireRunOnWorkProfile.DEFAULT_KEY, dpcIsPrimary = true)
    @AdditionalQueryParameters(
            forTestApp = RequireRunOnWorkProfile.DEFAULT_KEY,
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    @Test
    public void additionalQueryParameters_requireRunOnWorkProfile_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @EnsureTestAppInstalled(key = EnsureTestAppInstalled.DEFAULT_KEY, isPrimary = true)
    @AdditionalQueryParameters(
            forTestApp = EnsureTestAppInstalled.DEFAULT_KEY,
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    @Test
    public void additionalQueryParameters_ensureTestAppInstalled_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @MostImportantCoexistenceTest(policy = DisallowBluetooth.class)
    public void mostImportantCoexistenceTestAnnotation_hasDpcsWithPermission() {
        assertThat(sDeviceState.testApp(MostImportantCoexistenceTest.MORE_IMPORTANT)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
        assertThat(sDeviceState.testApp(MostImportantCoexistenceTest.LESS_IMPORTANT)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();

    }

    @MostRestrictiveCoexistenceTest(policy = DisallowBluetooth.class)
    public void mostRestrictiveCoexistenceTestAnnotation_hasDpcsWithPermission() {
        assertThat(sDeviceState.testApp(MostRestrictiveCoexistenceTest.DPC_1)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
        assertThat(sDeviceState.testApp(MostRestrictiveCoexistenceTest.DPC_2)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureDefaultContentSuggestionsServiceDisabled
    @Test
    public void ensureDefaultContentSuggestionsServiceDisabledAnnotation_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled()).isFalse();
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureDefaultContentSuggestionsServiceEnabled
    @Test
    public void ensureDefaultContentSuggestionsServiceEnabledAnnotation_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled()).isTrue();
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceDisabled(onUser = ADDITIONAL_USER)
    @Test
    public void ensureDefaultContentSuggestionsServiceDisabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled(
                sDeviceState.additionalUser()
        )).isFalse();
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceEnabled(onUser = ADDITIONAL_USER)
    @Test
    public void ensureDefaultContentSuggestionsServiceEnabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled(
                sDeviceState.additionalUser()
        )).isTrue();
    }

    @Test
    @RequireHasDefaultBrowser
    public void requireHasDefaultBrowser_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.roles().getRoleHolders(RoleManager.ROLE_BROWSER)).isNotEmpty();
    }

    @Test
    @EnsurePropertySet(key = "dumpstate.key", value = "value")
    public void ensurePropertySetAnnotation_propertyIsSet() {
        assertThat(TestApis.properties().get("dumpstate.key")).isEqualTo("value");
    }

    @Test
    @EnsureWillTakeQuickBugReports
    public void ensureWillTakeQuickBugReportsAnnotation_willTakeQuickBugReports() {
        assertThat(TestApis.bugReports().willTakeQuickBugReports()).isTrue();
    }

    @Test
    @EnsureWillNotTakeQuickBugReports
    public void ensureWillNotTakeQuickBugReportsAnnotation_willNotTakeQuickBugReports() {
        assertThat(TestApis.bugReports().willTakeQuickBugReports()).isFalse();
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = true)
    @Test
    public void requireResourcesBooleanValueIsTrue_resourceValueIsTrue() {
        assertThat(TestApis
                .resources()
                .system()
                .getBoolean("config_enableMultiUserUI")
        ).isTrue();
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = false)
    @Test
    public void requireResourcesBooleanValueIsFalse_resourceValueIsFalse() {
        assertThat(TestApis
                .resources()
                .system()
                .getBoolean("config_enableMultiUserUI")
        ).isFalse();
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.LANDSCAPE)
    public void ensureUsingScreenOrientation_landscape_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE);
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.PORTRAIT)
    public void ensureUsingScreenOrientation_portrait_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT);
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.DARK)
    public void ensureUsingDisplayTheme_setDark_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK);
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.LIGHT)
    public void ensureUsingDisplayTheme_setLight_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT);
    }

    @Test
    @IncludeLandscapeOrientation
    public void includeRunOnLandscapeOrientationDevice_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE);
    }

    @Test
    @IncludePortraitOrientation
    public void includeRunOnPortraitOrientationDevice_orientationIsSet() {
    assertThat(Display.INSTANCE.getScreenOrientation())
        .isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT);
    }

    @Test
    @IncludeDarkMode
    public void includeRunOnDarkModeDevice_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK);
    }

    @Test
    @IncludeLightMode
    public void includeRunOnLightModeDevice_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT);
    }
}
