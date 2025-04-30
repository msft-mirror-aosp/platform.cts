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

package com.android.cts.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * App side counterpart to UserManagerHostTest.
 */
@RunWith(JUnit4.class)
public final class UserManagerAppTest {

    private static final String TAG = UserManagerAppTest.class.getSimpleName();

    private final UserManager mUserManager =
            InstrumentationRegistry.getContext().getSystemService(UserManager.class);

    @Test
    public void getPreviousForegroundUserReturnsExpected() {
        final int expectedResult = Integer.parseInt(InstrumentationRegistry.getArguments()
                .getString("expectedResult"));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.CREATE_USERS,
                        // Need INTERACT_ACROSS_USERS to get current user
                        android.Manifest.permission.INTERACT_ACROSS_USERS);
        try {
            final UserHandle previousForegroundUser = mUserManager.getPreviousForegroundUser();
            Log.d(
                    TAG,
                    "previousForegroundUser: "
                            + previousForegroundUser
                            + ", currentUser: "
                            + ActivityManager.getCurrentUser()
                            + ", expected="
                            + expectedResult);
            // NOTE: UserHandle.of(USER_NULL) returns UserHandle.NULL, but API returns null instead
            final UserHandle expectedUser =
                    expectedResult == UserHandle.USER_NULL ? null : UserHandle.of(expectedResult);
            assertWithMessage("Result of UserManager.getPreviousForegroundUser()")
                    .that(previousForegroundUser)
                    .isEqualTo(expectedUser);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

}
