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
package com.android.cts.crossprofileappstest;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Tests the {@link CrossProfileApps#startActivity(ComponentName, UserHandle)} and
 * {@link CrossProfileApps#startActivity(Intent, UserHandle)} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CrossProfileAppsStartActivityTest {
    private static final String PARAM_TARGET_USER = "TARGET_USER";
    private static final String ID_USER_TEXTVIEW =
            "com.android.cts.crossprofileappstest:id/user_textview";
    private static final String ID_USER_TEXTVIEW2 =
            "com.android.cts.crossprofileappstest:id/user_textview2";
    private static final long TIMEOUT_WAIT_UI = TimeUnit.SECONDS.toMillis(10);

    private CrossProfileApps mCrossProfileApps;
    private UserHandle mTargetUser;
    private Context mContext;
    private UiDevice mDevice;
    private long mUserSerialNumber;

    @Before
    public void setupCrossProfileApps() {
        mContext = InstrumentationRegistry.getContext();
        mCrossProfileApps = mContext.getSystemService(CrossProfileApps.class);
    }

    @Before
    public void wakeupDeviceAndPressHome() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.wakeUp();
        mDevice.pressMenu();
        mDevice.pressHome();
    }

    @Before
    public void readTargetUser() {
        Context context = InstrumentationRegistry.getContext();
        Bundle arguments = InstrumentationRegistry.getArguments();
        UserManager userManager = context.getSystemService(UserManager.class);
        mUserSerialNumber = Long.parseLong(arguments.getString(PARAM_TARGET_USER));
        mTargetUser = userManager.getUserForSerialNumber(mUserSerialNumber);
        assertNotNull(mTargetUser);
    }

    @After
    public void pressHome() throws RemoteException {
        mDevice.pressHome();
    }

    @Test(expected=SecurityException.class)
    public void testCannotStartActivityByIntentWithNoPermissions() {
        Intent intent = new Intent();
        intent.setComponent(MainActivity.getComponentName(mContext));
        ShellIdentityUtils.dropShellPermissionIdentity();

        mCrossProfileApps.startActivity(intent, mTargetUser);
    }

    @Test
    public void testCanStartActivityByIntentWithInteractAcrossProfilesPermission() {
        Intent intent = new Intent();
        intent.setComponent(MainActivity.getComponentName(mContext));
        ShellIdentityUtils.dropShellPermissionIdentity();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                crossProfileApps -> crossProfileApps.startActivity(intent, mTargetUser), INTERACT_ACROSS_PROFILES);

        // Look for the text view to verify that MainActivity is started.
        UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW)),
                TIMEOUT_WAIT_UI);
        assertNotNull("Failed to start main activity in target user", textView);
        assertEquals("Main Activity is started in wrong user",
                String.valueOf(mUserSerialNumber), textView.getText());
    }

    @Test
    public void testCanStartActivityByIntentWithInteractAcrossUsersPermission() {
        Intent intent = new Intent();
        intent.setComponent(MainActivity.getComponentName(mContext));
        ShellIdentityUtils.dropShellPermissionIdentity();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                crossProfileApps -> crossProfileApps.startActivity(intent, mTargetUser), INTERACT_ACROSS_USERS);

        // Look for the text view to verify that MainActivity is started.
        UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW)),
                TIMEOUT_WAIT_UI);
        assertNotNull("Failed to start main activity in target user", textView);
        assertEquals("Main Activity is started in wrong user",
                String.valueOf(mUserSerialNumber), textView.getText());
    }

    @Test
    public void testCanStartActivityByIntentWithInteractAcrossUsersFullPermission() {
        Intent intent = new Intent();
        intent.setComponent(MainActivity.getComponentName(mContext));
        ShellIdentityUtils.dropShellPermissionIdentity();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                crossProfileApps -> crossProfileApps.startActivity(intent, mTargetUser), INTERACT_ACROSS_USERS_FULL);

        // Look for the text view to verify that MainActivity is started.
        UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW)),
                TIMEOUT_WAIT_UI);
        assertNotNull("Failed to start main activity in target user", textView);
        assertEquals("Main Activity is started in wrong user",
                String.valueOf(mUserSerialNumber), textView.getText());
    }


    @Test(expected = NullPointerException.class)
    public void testCannotStartActivityWithImplicitIntent() {
        Intent nonMainActivityImplicitIntent = new Intent();
        nonMainActivityImplicitIntent.setAction(Intent.ACTION_VIEW);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                crossProfileApps -> crossProfileApps.startActivity(
                        nonMainActivityImplicitIntent, mTargetUser));
    }

    @Test
    public void testCanStartMainActivityByIntent() {
        Intent mainActivityIntent = new Intent();
        mainActivityIntent.setComponent(MainActivity.getComponentName(mContext));

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                    crossProfileApps -> mCrossProfileApps.startActivity(
                            mainActivityIntent, mTargetUser));

            // Look for the text view to verify that MainActivity is started.
            UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW)),
                    TIMEOUT_WAIT_UI);
            assertNotNull("Failed to start main activity in target user", textView);
            assertEquals("Main Activity is started in wrong user",
                    String.valueOf(mUserSerialNumber), textView.getText());
        } catch (Exception e) {
            fail("unable to start main activity via CrossProfileApps#startActivity: " + e);
        }
    }

    @Test
    public void testCanStartNonMainActivityByIntent() {
        Intent nonMainActivityIntent = new Intent();
        nonMainActivityIntent.setComponent(NonMainActivity.getComponentName(mContext));

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                    crossProfileApps -> mCrossProfileApps.startActivity(
                            nonMainActivityIntent, mTargetUser));

            // Look for the text view to verify that NonMainActivity is started.
            UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW2)),
                    TIMEOUT_WAIT_UI);
            assertNotNull("Failed to start non-main activity in target user", textView);
            assertEquals("Non-Main Activity is started in wrong user",
                    String.valueOf(mUserSerialNumber), textView.getText());
        } catch (Exception e) {
            fail("unable to start non-main activity via CrossProfileApps#startActivity: " + e);
        }
    }

    /**
     * Calls {@link CrossProfileApps#startActivity(Intent, UserHandle)}. This can then be used by
     * host-side tests.
     */
    @Test
    public void testStartActivityByIntent_noAsserts() throws Exception {
        Intent nonMainActivityIntent = new Intent();
        nonMainActivityIntent.setComponent(NonMainActivity.getComponentName(mContext));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mCrossProfileApps,
                crossProfileApps ->
                        mCrossProfileApps.startActivity(nonMainActivityIntent, mTargetUser));
    }

    @Test
    public void testCanStartMainActivityByComponent() {
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                    crossProfileApps -> mCrossProfileApps.startActivity(
                            MainActivity.getComponentName(mContext), mTargetUser));

            // Look for the text view to verify that MainActivity is started.
            UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW)),
                    TIMEOUT_WAIT_UI);
            assertNotNull("Failed to start main activity in target user", textView);
            assertEquals("Main Activity is started in wrong user",
                    String.valueOf(mUserSerialNumber), textView.getText());
        } catch (Exception e) {
            fail("unable to start main activity via CrossProfileApps#startActivity: " + e);
        }
    }

    @Test
    public void testCanStartNonMainActivityByComponent() {
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                    crossProfileApps -> mCrossProfileApps.startActivity(
                            NonMainActivity.getComponentName(mContext), mTargetUser));

            // Look for the text view to verify that NonMainActivity is started.
            UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW2)),
                    TIMEOUT_WAIT_UI);
            assertNotNull("Failed to start non-main activity in target user", textView);
            assertEquals("Non-Main Activity is started in wrong user",
                    String.valueOf(mUserSerialNumber), textView.getText());
        } catch (Exception e) {
            fail("unable to start non-main activity via CrossProfileApps#startActivity: " + e);
        }
    }

    @Test
    public void testCanStartNotExportedActivityByIntent() throws Exception {
        Intent nonExportedActivityIntent = new Intent();
        nonExportedActivityIntent.setComponent(NonExportedActivity.getComponentName(mContext));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCrossProfileApps,
                crossProfileApps -> mCrossProfileApps.startActivity(nonExportedActivityIntent, mTargetUser));

        // Look for the text view to verify that NonExportedActivity is started.
        UiObject2 textView = mDevice.wait(Until.findObject(By.res(ID_USER_TEXTVIEW2)),
                TIMEOUT_WAIT_UI);
        assertNotNull("Failed to start not exported activity in target user", textView);
        assertEquals("Not exported Activity is started in wrong user",
                String.valueOf(mUserSerialNumber), textView.getText());
    }

    @Test(expected = SecurityException.class)
    public void testCannotStartNotExportedActivityByComponent() throws Exception {
        mCrossProfileApps.startActivity(
                NonExportedActivity.getComponentName(mContext), mTargetUser);
    }

    @Test(expected = SecurityException.class)
    public void testCannotStartActivityInOtherPackageByIntent() throws Exception {
        Intent otherPackageIntent = new Intent();
        otherPackageIntent.setComponent(new ComponentName(
                "com.android.cts.launcherapps.simpleapp",
                "com.android.cts.launcherapps.simpleapp.SimpleActivity"));
        mCrossProfileApps.startActivity(otherPackageIntent,
                mTargetUser
        );
    }

    @Test(expected = SecurityException.class)
    public void testCannotStartActivityInOtherPackageByComponent() throws Exception {
        mCrossProfileApps.startMainActivity(new ComponentName(
                "com.android.cts.launcherapps.simpleapp",
                "com.android.cts.launcherapps.simpleapp.SimpleActivity"),
                mTargetUser
        );
    }
}
