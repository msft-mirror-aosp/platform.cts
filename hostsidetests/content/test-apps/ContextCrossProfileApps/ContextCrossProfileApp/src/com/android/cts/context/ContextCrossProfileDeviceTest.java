/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.context;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This suite of test ensures that certain APIs in {@link Context} behaves correctly across users.
 */
@RunWith(JUnit4.class)
public class ContextCrossProfileDeviceTest {
    private static final String INTERACT_ACROSS_USERS_FULL_PERMISSION =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private static final String INTERACT_ACROSS_USERS_PERMISSION =
            "android.permission.INTERACT_ACROSS_USERS";
    private static final String INTERACT_ACROSS_PROFILES_PERMISSION =
            "android.permission.INTERACT_ACROSS_PROFILES";
    private static final String MANAGE_APP_OPS_MODE = "android.permission.MANAGE_APP_OPS_MODES";

    private static final String TEST_SERVICE_PKG =
            "com.android.cts.testService";
    private static final String TEST_SERVICE_IN_DIFFERENT_PKG_CLASS =
            TEST_SERVICE_PKG + ".ContextCrossProfileTestService";
    public static final ComponentName TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME =
            new ComponentName(TEST_SERVICE_PKG, TEST_SERVICE_IN_DIFFERENT_PKG_CLASS);

    private Context mContext;
    private UiAutomation mUiAutomation;
    private ComponentName mTestServiceInSamePkgComponentName;

    @Before
    public void setUp() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mUiAutomation = instrumentation.getUiAutomation();
        if (mUiAutomation == null) {
            // Retry once after a short wait if the device wasn't ready
            SystemClock.sleep(1000);
            mUiAutomation = instrumentation.getUiAutomation();
        }
        assertNotNull("Unable to connect to the UiAutomation instance.", mUiAutomation);

        final String testServiceInSamePkgClass =
                mContext.getPackageName() + ".ContextCrossProfileSamePackageTestService";
        mTestServiceInSamePkgComponentName =
                new ComponentName(mContext.getPackageName(), testServiceInSamePkgClass);
    }

    @After
    public void tearDown() throws Exception {
        if (mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private int getTestUser() {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        if (testArguments.containsKey("testUser")) {
            try {
                return Integer.parseInt(testArguments.getString("testUser"));
            } catch (NumberFormatException ignore) {
            }
        }
        fail("testUser not found.");
        return -1;
    }

    @Test
    public void testBindServiceAsUser_differentUser_bindsServiceToCorrectUser() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherProfileHandle))
                .isTrue();
        assertThat(
                        mContext.bindService(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE))
                .isFalse();
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_samePackage_withAcrossUsersPermission_bindsService() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherProfileHandle))
                .isTrue();
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_differentPackage_withAcrossUsersPermission_bindsService() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherProfileHandle))
                .isTrue();
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_samePackage_withAcrossProfilesPermission_bindsService() {
        final AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_APP_OPS_MODE);
        appOpsManager.setMode(
                AppOpsManager.permissionToOp(INTERACT_ACROSS_PROFILES_PERMISSION),
                Binder.getCallingUid(),
                mContext.getPackageName(),
                AppOpsManager.MODE_DEFAULT);
        mUiAutomation.dropShellPermissionIdentity();
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_PROFILES_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherProfileHandle))
                .isTrue();
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_differentPackage_withAcrossProfilesPermission_throwsException() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_PROFILES_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME);

        try {
            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherProfileHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_samePackage_withAcrossProfilesAppOp_bindsService(){
        final AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_APP_OPS_MODE);
        appOpsManager.setMode(
                AppOpsManager.permissionToOp(INTERACT_ACROSS_PROFILES_PERMISSION),
                Binder.getCallingUid(),
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);
        mUiAutomation.dropShellPermissionIdentity();
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherProfileHandle))
                .isTrue();
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_differentPackage_withAcrossProfilesAppOp_throwsException(){
        final AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_APP_OPS_MODE);
        appOpsManager.setMode(
                AppOpsManager.permissionToOp(INTERACT_ACROSS_PROFILES_PERMISSION),
                Binder.getCallingUid(),
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);
        mUiAutomation.dropShellPermissionIdentity();
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME);

        try {
            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherProfileHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_samePackage_withAcrossUsersPermission_bindsService() {
        int otherUserId = getTestUser();
        UserHandle otherUserHandle = UserHandle.of(otherUserId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        assertThat(
                        mContext.bindServiceAsUser(
                                bindIntent,
                                new ContextCrossProfileTestConnection(),
                                Context.BIND_AUTO_CREATE,
                                otherUserHandle))
                .isTrue();
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_differentPackage_withAcrossUsersPermission_throwsException() {
        int otherUserId = getTestUser();
        UserHandle otherUserHandle = UserHandle.of(otherUserId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);
        try {
            Intent bindIntent = new Intent();
            bindIntent.setComponent(TEST_SERVICE_IN_DIFFERENT_PKG_COMPONENT_NAME);

            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherUserHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesPermission_throwsException() {
        int otherUserId = getTestUser();
        UserHandle otherUserHandle = UserHandle.of(otherUserId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_PROFILES_PERMISSION);
        try {
            Intent bindIntent = new Intent();
            bindIntent.setComponent(mTestServiceInSamePkgComponentName);

            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherUserHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesAppOp_throwsException(){
        final AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_APP_OPS_MODE);
        appOpsManager.setMode(
                AppOpsManager.permissionToOp(INTERACT_ACROSS_PROFILES_PERMISSION),
                Binder.getCallingUid(),
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);
        mUiAutomation.dropShellPermissionIdentity();
        int otherUserId = getTestUser();
        UserHandle otherUserHandle = UserHandle.of(otherUserId);
        try {
            Intent bindIntent = new Intent();
            bindIntent.setComponent(mTestServiceInSamePkgComponentName);

            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherUserHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_withNoPermissions_throwsException() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        try {
            Intent bindIntent = new Intent();
            bindIntent.setComponent(mTestServiceInSamePkgComponentName);

            mContext.bindServiceAsUser(
                    bindIntent,
                    new ContextCrossProfileTestConnection(),
                    Context.BIND_AUTO_CREATE,
                    otherProfileHandle);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testBindServiceAsUser_withInteractAcrossProfilePermission_noAsserts() {
        final AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_APP_OPS_MODE);
        appOpsManager.setMode(
                AppOpsManager.permissionToOp(INTERACT_ACROSS_PROFILES_PERMISSION),
                Binder.getCallingUid(),
                mContext.getPackageName(),
                AppOpsManager.MODE_DEFAULT);
        mUiAutomation.dropShellPermissionIdentity();
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_PROFILES_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        mContext.bindServiceAsUser(
                bindIntent,
                new ContextCrossProfileTestConnection(),
                Context.BIND_AUTO_CREATE,
                otherProfileHandle);
    }

    @Test
    public void testBindServiceAsUser_withInteractAcrossUsersFullPermission_noAsserts() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(
                INTERACT_ACROSS_USERS_FULL_PERMISSION, INTERACT_ACROSS_USERS_PERMISSION);
        Intent bindIntent = new Intent();
        bindIntent.setComponent(mTestServiceInSamePkgComponentName);

        mContext.bindServiceAsUser(
                bindIntent,
                new ContextCrossProfileTestConnection(),
                Context.BIND_AUTO_CREATE,
                otherProfileHandle);
    }

    @Test
    public void testCreateContextAsUser_sameProfileGroup_withInteractAcrossProfilesPermission_throwsException() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_PROFILES_PERMISSION);

        try {
            mContext.createContextAsUser(otherProfileHandle, /* flags= */ 0);

            fail("Should throw a Security Exception");
        } catch (SecurityException ignored) {
        }
    }

    @Test
    public void testCreateContextAsUser_sameProfileGroup_withInteractAcrossUsersPermission_createsContext() {
        int otherProfileId = getTestUser();
        UserHandle otherProfileHandle = UserHandle.of(otherProfileId);
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_PERMISSION);

        Context otherProfileContext =
                mContext.createContextAsUser(otherProfileHandle, /* flags= */ 0);

        assertThat(otherProfileContext.getUserId()).isEqualTo(otherProfileId);
    }
}
