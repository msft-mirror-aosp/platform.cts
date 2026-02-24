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

import static com.android.contactspicker.cts.common.ContactsPickerTestHelper.verifyUriReturned;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsPickerSessionContract;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.contactspicker.cts.common.ContactsPickerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for verifying that contact(s) selection via the system Contacts Picker returns the
 * correct contact(s) data for both ACTION_PICK and ACTION_PICK_CONTACTS.
 */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
public class ContactsPickerSelectionTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private UiDevice mUiDevice;
    // Map of Contact Name -> ContactDataIds
    private static final Map<String, ContactsPickerTestHelper.ContactDataIds> sContactDataIdMap =
            new HashMap<>();
    private static final List<Long> sCreatedContactDataIds = new ArrayList<>();
    private static final List<Long> sCreatedRawContactIds = new ArrayList<>();
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private static final int TIMEOUT_MS = 2000;
    private static final int CP2_IDLE_MS = 2000;
    private static final String DONE_BUTTON_CONTENT_DESC = "Done";

    // Test Contact Names
    private static final String RANDOM_SUFFIX =
            java.util.UUID.randomUUID().toString().substring(0, 8);
    private static final String CONTACT_PHONE = "Contact Phone " + RANDOM_SUFFIX;
    private static final String CONTACT_MULTI = "Contact Multi " + RANDOM_SUFFIX;

    @Before
    public void setUp() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();

        createTestContacts();
        // Wait for CP2 to process the newly created contacts.
        ContactsPickerTestHelper.waitForContactsToBeCreated(
                sContext.getContentResolver(), sCreatedContactDataIds);
    }

    @After
    public void tearDown() {
        mUiDevice.pressHome();

        ContactsPickerTestHelper.removeTestContacts(
                sContext, sCreatedRawContactIds, sContext.getUser());
        sCreatedRawContactIds.clear();
        sContactDataIdMap.clear();
        SystemClock.sleep(CP2_IDLE_MS);
    }

    /**
     * Verifies that selecting a contact via ACTION_PICK (Legacy) with phone mime-type returns the
     * correct Data URI.
     */
    @Test
    public void actionPickSelection_PhoneOnly() throws Exception {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(Phone.CONTENT_TYPE);

        long expectedDataId = sContactDataIdMap.get(CONTACT_PHONE).get(Phone.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_PHONE),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsContract.AUTHORITY,
                                List.of(expectedDataId)));
    }

    /**
     * Verifies that selecting a contact via ACTION_PICK (Legacy) with email mime-type returns the
     * correct Data URI.
     */
    @Test
    public void actionPickSelection_EmailOnly() throws Exception {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(Email.CONTENT_TYPE);

        long expectedDataId = sContactDataIdMap.get(CONTACT_MULTI).get(Email.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_MULTI),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsContract.AUTHORITY,
                                List.of(expectedDataId)));
    }

    /**
     * Verifies that selecting a contact via ACTION_PICK_CONTACTS for phone mime-type returns a *
     * Session URI containing the correct Data ID.
     */
    @Test
    public void actionPickContactsSelection_PhoneOnly() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                new ArrayList<>(Collections.singletonList(Phone.CONTENT_ITEM_TYPE)));

        long expectedDataId = sContactDataIdMap.get(CONTACT_PHONE).get(Phone.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_PHONE),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsPickerSessionContract.AUTHORITY,
                                List.of(expectedDataId)));
    }

    /**
     * Verifies that selecting a contact via ACTION_PICK_CONTACTS for email mime-type returns a
     * Session URI containing the correct Data ID.
     */
    @Test
    public void actionPickContactsSelection_EmailOnly() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                new ArrayList<>(Collections.singletonList(Email.CONTENT_ITEM_TYPE)));

        long expectedDataId = sContactDataIdMap.get(CONTACT_MULTI).get(Email.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_MULTI),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsPickerSessionContract.AUTHORITY,
                                List.of(expectedDataId)));
    }

    /**
     * Verifies that selecting multiple contacts with EXTRA_ALLOW_MULTIPLE returns a Session URI
     * containing data IDs for all selected contacts.
     */
    @Test
    public void actionPickContactsMultiSelection() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                new ArrayList<>(Collections.singletonList(Phone.CONTENT_ITEM_TYPE)));

        // Expected dataRowIds
        long phoneDataId = sContactDataIdMap.get(CONTACT_PHONE).get(Phone.CONTENT_ITEM_TYPE);
        long multiPhoneDataId = sContactDataIdMap.get(CONTACT_MULTI).get(Phone.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_PHONE, CONTACT_MULTI),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsPickerSessionContract.AUTHORITY,
                                List.of(phoneDataId, multiPhoneDataId)));
    }

    /**
     * Verifies that selecting a contact with multiple requested data fields returns a Session URI
     * containing data IDs for all matching fields of that contact.
     */
    @Test
    public void actionPickContactsMultiFieldSelection() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        ArrayList<String> requestedFields = new ArrayList<>();
        requestedFields.add(Phone.CONTENT_ITEM_TYPE);
        requestedFields.add(Email.CONTENT_ITEM_TYPE);
        intent.putStringArrayListExtra(EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, requestedFields);

        // Expected dataRowIds
        long phoneDataId = sContactDataIdMap.get(CONTACT_MULTI).get(Phone.CONTENT_ITEM_TYPE);
        long multiPhoneDataId = sContactDataIdMap.get(CONTACT_MULTI).get(Email.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_MULTI),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                InstrumentationRegistry.getInstrumentation().getContext(),
                                resultCode,
                                resultData,
                                ContactsPickerSessionContract.AUTHORITY,
                                List.of(phoneDataId, multiPhoneDataId)));
    }

    /** Verifies that exceeding the selection limit displays a warning Snack bar. */
    @Test
    public void actionPickContactsMultiSelection_SelectionLimit() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(
                android.provider.ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_SELECTION_LIMIT,
                1);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                new ArrayList<>(Collections.singletonList(Phone.CONTENT_ITEM_TYPE)));

        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(activity -> activity.startActivityForResult(intent, 1));
            ContactsPickerTestHelper.waitForPickerUi(mUiDevice);

            // Select first contact
            ContactsPickerTestHelper.clickContact(mUiDevice, CONTACT_PHONE);
            // Attempt to select second contact (should fail)
            ContactsPickerTestHelper.clickContact(mUiDevice, CONTACT_MULTI);

            // Assert that snack bar is displayed
            UiObject2 snackBarText =
                    mUiDevice.wait(
                            Until.findObject(By.descContains("Can share only 1 contact at a time")),
                            TIMEOUT_MS);
            assertNotNull("Selection limit warning snack bar not displayed", snackBarText);
        }
    }

    private void launchPickerAndSelect(
            Intent intent, List<String> contactNamesToSelect, ResultVerifier verifier)
            throws Exception {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(activity -> activity.startActivityForResult(intent, 1));

            ContactsPickerTestHelper.waitForPickerUi(mUiDevice);

            // Select all contacts
            for (String name : contactNamesToSelect) {
                ContactsPickerTestHelper.clickContact(mUiDevice, name);
            }

            // Click Done/Add button
            UiObject2 doneButton =
                    mUiDevice.wait(
                            Until.findObject(
                                    By.text(
                                            java.util.regex.Pattern.compile(
                                                    DONE_BUTTON_CONTENT_DESC))),
                            TIMEOUT_MS);
            if (doneButton == null) {
                throw new AssertionError("Done button not found");
            }
            doneButton.click();

            // Wait for result in TestActivity
            final TestActivity[] activityRef = new TestActivity[1];
            scenario.onActivity(activity -> activityRef[0] = activity);

            boolean finished = activityRef[0].resultLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertTrue("Activity did not receive result", finished);

            verifier.verify(activityRef[0].resultCode, activityRef[0].resultData);
        }
    }

    private interface ResultVerifier {
        void verify(int resultCode, Intent resultData);
    }

    /** Subsection: Setup for contacts removal and creation for this test. */
    private static void createTestContacts() {
        // Phone
        Map<String, Object> data = new HashMap<>();
        data.put(Phone.CONTENT_ITEM_TYPE, "1111111111");
        createContact(CONTACT_PHONE, data);

        // Multi
        Map<String, Object> multiData = new HashMap<>();
        multiData.put(Phone.CONTENT_ITEM_TYPE, "2222222222");
        multiData.put(Email.CONTENT_ITEM_TYPE, "multi@test.com");
        createContact(CONTACT_MULTI, multiData);
    }

    private static void createContact(String name, Map<String, Object> mimeTypeToValue) {
        Map<String, Object> allData = new HashMap<>(mimeTypeToValue);
        allData.put(StructuredName.CONTENT_ITEM_TYPE, name);

        ContactsPickerTestHelper.ContactCreationResult result =
                ContactsPickerTestHelper.createContact(sContext, allData, sContext.getUser());

        if (result.rawContactId != -1) {
            sCreatedRawContactIds.add(result.rawContactId);
            sContactDataIdMap.put(
                    name, new ContactsPickerTestHelper.ContactDataIds(result.mimeTypeToDataId));
        }
        sCreatedContactDataIds.addAll(result.mimeTypeToDataId.values());
    }
}
