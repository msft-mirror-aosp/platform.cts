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

package android.content.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings.Secure;
import android.text.ShowSecretsSetting;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;
import com.android.text.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(
        blockedBy = UserHelper.class,
        reason =
                "Underlying call to UserManager.isVisibleBackgroundUsersSupported() fails with"
                        + " userManager being null")
public class ShowSecretsSettingTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEXT_SHOW_PASSWORD_TOUCH = "show_passwords_touch";
    private static final String TEXT_SHOW_PASSWORD_PHYSICAL = "show_passwords_physical";
    private UserHelper mUserHelper;
    private ContentResolver mContentResolver;
    private String mOriginalTouchValue;
    private String mOriginalPhysicalValue;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mContentResolver = context.getContentResolver();
        mUserHelper = new UserHelper(context);
        mOriginalTouchValue = Secure.getString(mContentResolver, TEXT_SHOW_PASSWORD_TOUCH);
        mOriginalPhysicalValue = Secure.getString(mContentResolver, TEXT_SHOW_PASSWORD_PHYSICAL);
    }

    @After
    public void tearDown() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    Secure.putString(
                            mContentResolver, TEXT_SHOW_PASSWORD_TOUCH, mOriginalTouchValue);
                    Secure.putString(
                            mContentResolver, TEXT_SHOW_PASSWORD_PHYSICAL, mOriginalPhysicalValue);
                },
                Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testDefaults() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    Secure.putString(mContentResolver, TEXT_SHOW_PASSWORD_TOUCH, null);
                    Secure.putString(mContentResolver, TEXT_SHOW_PASSWORD_PHYSICAL, null);
                },
                Manifest.permission.WRITE_SECURE_SETTINGS);

        assertEquals(
                true,
                ShowSecretsSetting.shouldShowTouchInputForUser(
                        mContentResolver, mUserHelper.getUser()));
        assertEquals(
                false,
                ShowSecretsSetting.shouldShowPhysicalInputForUser(
                        mContentResolver, mUserHelper.getUser()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testNullParamHandling() {
        for (UserHandle user : new UserHandle[] {mUserHelper.getUser(), null}) {
            for (ContentResolver cr : new ContentResolver[] {mContentResolver, null}) {
                if (user != null && cr != null) {
                    continue;
                }
                assertThrows(
                        NullPointerException.class,
                        () -> ShowSecretsSetting.shouldShowTouchInputForUser(cr, user));
                assertThrows(
                        NullPointerException.class,
                        () -> ShowSecretsSetting.shouldShowPhysicalInputForUser(cr, user));
                assertThrows(
                        NullPointerException.class,
                        () -> ShowSecretsSetting.setShouldShowTouchInputForUser(cr, true, user));
                assertThrows(
                        NullPointerException.class,
                        () -> ShowSecretsSetting.setShouldShowPhysicalInputForUser(cr, true, user));
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testSettingWithPermission() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    boolean val =
                            ShowSecretsSetting.shouldShowPhysicalInputForUser(
                                    mContentResolver, mUserHelper.getUser());

                    ShowSecretsSetting.setShouldShowPhysicalInputForUser(
                            mContentResolver, !val, mUserHelper.getUser());
                    assertEquals(
                            !val,
                            ShowSecretsSetting.shouldShowPhysicalInputForUser(
                                    mContentResolver, mUserHelper.getUser()));

                    val =
                            ShowSecretsSetting.shouldShowTouchInputForUser(
                                    mContentResolver, mUserHelper.getUser());
                    ShowSecretsSetting.setShouldShowTouchInputForUser(
                            mContentResolver, !val, mUserHelper.getUser());
                    assertEquals(
                            !val,
                            ShowSecretsSetting.shouldShowTouchInputForUser(
                                    mContentResolver, mUserHelper.getUser()));
                },
                Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testSettingWithoutPermission() {
        assertThrows(
                SecurityException.class,
                () ->
                        ShowSecretsSetting.setShouldShowTouchInputForUser(
                                mContentResolver, true, mUserHelper.getUser()));
        assertThrows(
                SecurityException.class,
                () ->
                        ShowSecretsSetting.setShouldShowPhysicalInputForUser(
                                mContentResolver, true, mUserHelper.getUser()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testUrisNotNull() {
        assertNotNull(ShowSecretsSetting.getTouchUri());
        assertNotNull(ShowSecretsSetting.getPhysicalUri());
        assertNotEquals(ShowSecretsSetting.getPhysicalUri(), ShowSecretsSetting.getTouchUri());
    }
}
