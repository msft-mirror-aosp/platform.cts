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

package com.android.contactspicker.cts;

import static android.provider.ContactsPickerSessionContract.ACTION_PICK_CONTACTS;
import static android.provider.ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** CTS tests for launching the Contacts Picker activity via {@link ACTION_PICK_CONTACTS intent} */
@RunWith(AndroidJUnit4.class)
public final class ContactsPickerInvocationTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private UiDevice mUiDevice;

    private static final String CONTACTS_PICKER_PACKAGE = "com.android.contactspicker";
    private static final int TIMEOUT_MS = 5000;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();
    }

    @After
    public void tearDown() {
        mUiDevice.pressHome();
    }

    /**
     * Verifies that the Contacts Picker can be launched successfully with valid requested fields.
     */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPickContacts_launches() {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        ArrayList<String> requestedFields = new ArrayList<>();
        requestedFields.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        intent.putStringArrayListExtra(EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, requestedFields);

        // Verify that there is a handler for this intent
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
        assertThat(handlers).isNotEmpty();

        // Launch the TestActivity, which will in turn launch the Contacts Picker via Intent
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

    /** Verifies that the Contacts Picker finishes gracefully when no data fields are requested. */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPickContacts_noRequestedFields_cancels() {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);

        // Verify that there is a handler for this intent
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
        assertThat(handlers).isNotEmpty();

        // Launch the TestActivity, which will in turn launch the Contacts Picker via Intent.
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            final CountDownLatch[] latch = new CountDownLatch[1];
            scenario.onActivity(
                    activity -> {
                        latch[0] = activity.resultLatch;
                        activity.startActivityForResult(intent, 1);
                    });

            // Verify that the ContactsPicker activity finishes with RESULT_CANCELED
            try {
                assertThat(latch[0].await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            scenario.onActivity(
                    activity -> {
                        assertThat(activity.resultCode)
                                .isEqualTo(android.app.Activity.RESULT_CANCELED);
                    });
        }
    }

    /**
     * Verifies that the Contacts Picker finishes gracefully when an empty list of data fields is
     * requested.
     */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPickContacts_emptyRequestedFields_cancels() {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, new ArrayList<>());

        // Verify that there is a handler for this intent
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
        assertThat(handlers).isNotEmpty();

        // Launch the TestActivity, which will in turn launch the Contacts Picker via Intent.
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            final CountDownLatch[] latch = new CountDownLatch[1];
            scenario.onActivity(
                    activity -> {
                        latch[0] = activity.resultLatch;
                        activity.startActivityForResult(intent, 1);
                    });

            // Verify that the ContactsPicker activity finishes with RESULT_CANCELED
            try {
                assertThat(latch[0].await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            scenario.onActivity(
                    activity -> {
                        assertThat(activity.resultCode)
                                .isEqualTo(android.app.Activity.RESULT_CANCELED);
                    });
        }
    }

    /**
     * Verifies that the Contacts Picker finishes gracefully when an incorrect data field is
     * requested.
     */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPickContacts_incorrectRequestedFields_cancels() {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        ArrayList<String> requestedFields = new ArrayList<>();
        // Add a non-supported mime-type in request fields.
        requestedFields.add("invalid/data.type");
        intent.putStringArrayListExtra(EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, requestedFields);

        // Verify that there is a handler for this intent
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
        assertThat(handlers).isNotEmpty();

        // Launch the TestActivity, which will in turn launch the Contacts Picker via Intent.
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            final CountDownLatch[] latch = new CountDownLatch[1];
            scenario.onActivity(
                    activity -> {
                        latch[0] = activity.resultLatch;
                        activity.startActivityForResult(intent, 1);
                    });

            // Verify that the ContactsPicker activity finishes with RESULT_CANCELED
            try {
                assertThat(latch[0].await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            scenario.onActivity(
                    activity -> {
                        assertThat(activity.resultCode)
                                .isEqualTo(android.app.Activity.RESULT_CANCELED);
                    });
        }
    }

    /**
     * Verifies that the Contacts Picker can be launched successfully with legacy method, i.e. using
     * {@link Intent.ACTION_PICK} intent.
     */
    @Test
    @RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
    public void actionPick_launches() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_TYPE);

        // Verify that there is a handler for this intent
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, 0);
        assertThat(handlers).isNotEmpty();

        // Launch the TestActivity, which will in turn launch the Contacts Picker via Intent
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
