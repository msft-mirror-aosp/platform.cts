/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.contactspicker.cts.sdk36;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.contactspicker.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** CTS tests for launching the Contacts Picker activity from targetSdk < 37 apps. */
@RunWith(AndroidJUnit4.class)
public final class ContactsPickerInvocationSdk36Test {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private UiDevice mUiDevice;
    private static final String CONTACTS_PICKER_PACKAGE = "com.android.contactspicker";
    private static final int TIMEOUT_MS = 5000;

    @Before
    public void setUp() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();
    }

    @After
    public void tearDown() {
        mUiDevice.pressHome();
    }

    /** Verifies that the Contacts Picker is not launched with ACTION_PICK and targetSdk < 37 */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_ACTION_PICK_TAKEOVER_IN_DROIDFOOD)
    public void actionPick_doesNotLaunchSystemPicker() throws Exception {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_TYPE);

        // Launch the TestActivity
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        activity.startActivityForResult(intent, 1);
                    });

            // Wait for the package to change to something other than the test app or the picker
            boolean forwarded = false;
            long endTime = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < endTime) {
                String currentPackage = mUiDevice.getCurrentPackageName();
                if (currentPackage != null && !currentPackage.equals(CONTACTS_PICKER_PACKAGE)) {
                    forwarded = true;
                    break;
                }
                SystemClock.sleep(500);
            }
            assertThat(forwarded).isTrue();
        }
    }

    /**
     * Verifies that the Contacts Picker is launched with ACTION_PICK, targetSdk < 37 when {@link
     * Intent.EXTRA_USE_SYSTEM_CONTACTS_PICKER} is set.
     */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPick_launchesSystemPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_TYPE);
        intent.putExtra(Intent.EXTRA_USE_SYSTEM_CONTACTS_PICKER, true);

        // Launch the TestActivity
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        activity.startActivityForResult(intent, 1);
                    });

            // Verify that the ContactsPicker activity is shown
            boolean isShown =
                    mUiDevice.wait(Until.hasObject(By.pkg(CONTACTS_PICKER_PACKAGE)), TIMEOUT_MS);
            assertThat(isShown).isTrue();
        }
    }
}
