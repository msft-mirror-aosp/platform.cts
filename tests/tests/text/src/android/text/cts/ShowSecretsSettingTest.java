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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Looper;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private static final String TEXT_SHOW_PASSWORD_TOUCH = "show_password_touch";
    private static final String TEXT_SHOW_PASSWORD_PHYSICAL = "show_password_physical";
    private ContentResolver mContentResolver;
    private Context mContext;
    private String mOriginalTouchValue;
    private String mOriginalPhysicalValue;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
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

        assertEquals(true, ShowSecretsSetting.shouldShowTouchInput(mContext));
        assertEquals(false, ShowSecretsSetting.shouldShowPhysicalInput(mContext));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testNullParamHandling() {
        assertThrows(
                NullPointerException.class, () -> ShowSecretsSetting.shouldShowTouchInput(null));
        assertThrows(
                NullPointerException.class, () -> ShowSecretsSetting.shouldShowPhysicalInput(null));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.setShouldShowTouchInput(null, true));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.setShouldShowPhysicalInput(null, true));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.registerCallback(null, () -> {}));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.registerCallback(mContext, null));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.registerCallback(mContext, null, () -> {}));
        assertThrows(
                NullPointerException.class,
                () -> ShowSecretsSetting.registerCallback(mContext, Runnable::run, null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testSettingWithPermission() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    boolean val = ShowSecretsSetting.shouldShowPhysicalInput(mContext);

                    ShowSecretsSetting.setShouldShowPhysicalInput(mContext, !val);
                    assertEquals(!val, ShowSecretsSetting.shouldShowPhysicalInput(mContext));

                    val = ShowSecretsSetting.shouldShowTouchInput(mContext);
                    ShowSecretsSetting.setShouldShowTouchInput(mContext, !val);
                    assertEquals(!val, ShowSecretsSetting.shouldShowTouchInput(mContext));
                },
                Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testSettingWithoutPermission() {
        assertThrows(
                SecurityException.class,
                () -> ShowSecretsSetting.setShouldShowTouchInput(mContext, true));
        assertThrows(
                SecurityException.class,
                () -> ShowSecretsSetting.setShouldShowPhysicalInput(mContext, true));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testCallback_onExecutor() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Runnable unregister =
                ShowSecretsSetting.registerCallback(mContext, Runnable::run, latch::countDown);

        try {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        boolean current = ShowSecretsSetting.shouldShowTouchInput(mContext);
                        ShowSecretsSetting.setShouldShowTouchInput(mContext, !current);
                    },
                    Manifest.permission.WRITE_SECURE_SETTINGS);

            assertTrue("Callback should be called", latch.await(1, TimeUnit.SECONDS));
        } finally {
            unregister.run();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL)
    public void testCallback_onMainLooper() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable callback =
                () -> {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        latch.countDown();
                    }
                };

        Runnable unregister = ShowSecretsSetting.registerCallback(mContext, callback);

        try {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        boolean current = ShowSecretsSetting.shouldShowTouchInput(mContext);
                        ShowSecretsSetting.setShouldShowTouchInput(mContext, !current);
                    },
                    Manifest.permission.WRITE_SECURE_SETTINGS);

            assertTrue("Callback should be called on MainLooper", latch.await(1, TimeUnit.SECONDS));
        } finally {
            unregister.run();
        }
    }
}
