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
import static android.provider.ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS;
import static android.provider.ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS;

import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.support.test.uiautomator.UiDevice;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CTS tests for verifying the Contacts Picker API behavior by ensuring the correct contacts are
 * displayed according to the intent configuration (e.g., ACTION_PICK, ACTION_PICK_CONTACTS).
 */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
public class ContactsPickerViewTest {

    private static final String TAG = "ContactsPickerViewTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private UiDevice mUiDevice;
    private static final List<Long> sCreatedRawContactIds = new ArrayList<>();
    private static final List<Long> sCreatedDataIds = new ArrayList<>();
    private static final int CP2_IDLE_MS = 2000;

    // Test Contact Names
    private static final String CONTACT_NAME = "Contact Name";
    private static final String CONTACT_EMAIL = "Contact Email";
    private static final String CONTACT_PHONE = "Contact Phone";
    private static final String CONTACT_ADDRESS = "Contact Address";
    private static final String CONTACT_ORG = "Contact Organization";
    private static final String CONTACT_RELATION = "Contact Relation";
    private static final String CONTACT_EVENT = "Contact Event";
    private static final String CONTACT_PHOTO = "Contact Photo";
    private static final String CONTACT_GROUP = "Contact Group";
    private static final String CONTACT_WEBSITE = "Contact Website";
    private static final String CONTACT_NICKNAME = "Contact Nickname";
    // A contact with multiple mime-types
    private static final String CONTACT_MULTI = "Contact Multi";

    // All test contact names
    private static final List<String> ALL_TEST_CONTACTS =
            List.of(
                    CONTACT_EMAIL,
                    CONTACT_PHONE,
                    CONTACT_ADDRESS,
                    CONTACT_ORG,
                    CONTACT_RELATION,
                    CONTACT_EVENT,
                    CONTACT_PHOTO,
                    CONTACT_GROUP,
                    CONTACT_WEBSITE,
                    CONTACT_NICKNAME,
                    CONTACT_MULTI);

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Setup test contacts
        createTestContacts();
        // Wait for CP2 to process the newly created contacts.
        ContactsPickerTestHelper.waitForContactsToBeCreated(
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver(),
                sCreatedDataIds);
    }

    @AfterClass
    public static void tearDownClass() {
        // Remove test contacts
        ContactsPickerTestHelper.removeTestContacts(
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver(),
                sCreatedRawContactIds);
        sCreatedRawContactIds.clear();
        sCreatedDataIds.clear();
        // Give CP2 a bit of time to settle.
        SystemClock.sleep(CP2_IDLE_MS);
    }

    @Before
    public void setUp() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();
    }

    @After
    public void tearDown() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();
    }

    @Test
    public void actionPickContactsFilterByName() {
        List<String> expectedSubsetShown =
                List.of(CONTACT_NAME, CONTACT_PHONE, CONTACT_EMAIL, CONTACT_MULTI);

        launchPickerAndVerify(
                Collections.singletonList(StructuredName.CONTENT_ITEM_TYPE),
                false,
                () ->
                        ContactsPickerTestHelper.verifyContactsDisplayed(
                                mUiDevice, expectedSubsetShown, true));
    }

    @Test
    public void actionPickContactsFilterByPhone() {
        List<String> expectedSubsetShown = List.of(CONTACT_PHONE, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_EMAIL, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Phone.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByEmail() {
        List<String> expectedSubsetShown = List.of(CONTACT_EMAIL, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Email.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByAddress() {
        List<String> expectedSubsetShown = List.of(CONTACT_ADDRESS, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_EMAIL, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(StructuredPostal.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByOrganization() {
        List<String> expectedSubsetShown = List.of(CONTACT_ORG);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Organization.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByRelation() {
        List<String> expectedSubsetShown = List.of(CONTACT_RELATION);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Relation.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByEvent() {
        List<String> expectedSubsetShown = List.of(CONTACT_EVENT);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Event.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByPhoto() {
        List<String> expectedSubsetShown = List.of(CONTACT_PHOTO);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Photo.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByGroup() {
        List<String> expectedSubsetShown = List.of(CONTACT_GROUP);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(GroupMembership.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByWebsite() {
        List<String> expectedSubsetShown = List.of(CONTACT_WEBSITE);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_EMAIL);

        launchPickerAndVerify(
                Collections.singletonList(Website.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByNickname() {
        List<String> expectedSubsetShown = List.of(CONTACT_NICKNAME);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                Collections.singletonList(Nickname.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByPhoneOrEmail() {
        List<String> expectedSubsetShown = List.of(CONTACT_PHONE, CONTACT_EMAIL, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_NICKNAME, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerify(
                List.of(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
                false,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickContactsFilterByPhoneAndAddress_MatchAll() {
        List<String> expectedSubsetShown = List.of(CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_EMAIL, CONTACT_WEBSITE);

        launchPickerAndVerify(
                List.of(Phone.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE),
                true,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickFilterByEmail() {
        List<String> expectedSubsetShown = List.of(CONTACT_EMAIL, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_PHONE, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerifyForActionPick(
                ContactsContract.CommonDataKinds.Email.CONTENT_TYPE,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickFilterByPhone() {
        List<String> expectedSubsetShown = List.of(CONTACT_PHONE, CONTACT_MULTI);
        List<String> expectedNotShown = List.of(CONTACT_EMAIL, CONTACT_ADDRESS, CONTACT_WEBSITE);

        launchPickerAndVerifyForActionPick(
                ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE,
                () -> {
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedSubsetShown, true);
                    ContactsPickerTestHelper.verifyContactsDisplayed(
                            mUiDevice, expectedNotShown, false);
                });
    }

    @Test
    public void actionPickFilterByContact() {
        List<String> expectedSubsetShown =
                List.of(
                        CONTACT_EMAIL,
                        CONTACT_PHONE,
                        CONTACT_ADDRESS,
                        CONTACT_MULTI,
                        CONTACT_PHOTO);

        launchPickerAndVerifyForActionPick(
                ContactsContract.Contacts.CONTENT_TYPE,
                () ->
                        ContactsPickerTestHelper.verifyContactsDisplayed(
                                mUiDevice, expectedSubsetShown, true));
    }

    /** Subsection: Setup for contacts removal and creation for this test. */
    private static void createTestContacts() {
        // 0. Only Name
        Map<String, Object> contactName = new HashMap<>();
        contactName.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_NAME);
        createContactHelper(contactName);

        // 1. Email
        Map<String, Object> contactEmail = new HashMap<>();
        contactEmail.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_EMAIL);
        contactEmail.put(Email.CONTENT_ITEM_TYPE, "test@example.com");
        createContactHelper(contactEmail);

        // 2. Phone
        Map<String, Object> contactPhone = new HashMap<>();
        contactPhone.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_PHONE);
        contactPhone.put(Phone.CONTENT_ITEM_TYPE, "1234567890");
        createContactHelper(contactPhone);

        // 3. Address
        Map<String, Object> contactAddress = new HashMap<>();
        contactAddress.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_ADDRESS);
        contactAddress.put(StructuredPostal.CONTENT_ITEM_TYPE, "123 Main St");
        createContactHelper(contactAddress);

        // 4. Organization
        Map<String, Object> contactOrg = new HashMap<>();
        contactOrg.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_ORG);
        contactOrg.put(Organization.CONTENT_ITEM_TYPE, "Test Corp");
        createContactHelper(contactOrg);

        // 5. Relation
        Map<String, Object> contactRelation = new HashMap<>();
        contactRelation.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_RELATION);
        contactRelation.put(Relation.CONTENT_ITEM_TYPE, "Assistant");
        createContactHelper(contactRelation);

        // 6. Event
        Map<String, Object> contactEvent = new HashMap<>();
        contactEvent.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_EVENT);
        contactEvent.put(Event.CONTENT_ITEM_TYPE, "2000-01-01");
        createContactHelper(contactEvent);

        // 7. Photo
        Map<String, Object> contactPhoto = new HashMap<>();
        contactPhoto.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_PHOTO);
        contactPhoto.put(Photo.CONTENT_ITEM_TYPE, ContactsPickerTestHelper.getDummyColorBytes());
        createContactHelper(contactPhoto);

        // 8. Group
        Map<String, Object> contactGroup = new HashMap<>();
        contactGroup.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_GROUP);
        contactGroup.put(GroupMembership.CONTENT_ITEM_TYPE, "1");
        createContactHelper(contactGroup);

        // 9. Website
        Map<String, Object> contactWebsite = new HashMap<>();
        contactWebsite.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_WEBSITE);
        contactWebsite.put(Website.CONTENT_ITEM_TYPE, "http://www.example.com");
        createContactHelper(contactWebsite);

        // 10. Nickname
        Map<String, Object> contactNickname = new HashMap<>();
        contactNickname.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_NICKNAME);
        contactNickname.put(Nickname.CONTENT_ITEM_TYPE, "Nick");
        createContactHelper(contactNickname);

        // 11. Multi (Phone + Email + Address)
        Map<String, Object> contactMulti = new HashMap<>();
        contactMulti.put(StructuredName.CONTENT_ITEM_TYPE, CONTACT_MULTI);
        contactMulti.put(Phone.CONTENT_ITEM_TYPE, "9876543210");
        contactMulti.put(Email.CONTENT_ITEM_TYPE, "multi@example.com");
        contactMulti.put(StructuredPostal.CONTENT_ITEM_TYPE, "456 Side St");
        createContactHelper(contactMulti);
    }

    private static void createContactHelper(Map<String, Object> mimeTypeToValue) {
        ContactsPickerTestHelper.ContactCreationResult result =
                ContactsPickerTestHelper.createContact(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getContentResolver(),
                        mimeTypeToValue);
        if (result.rawContactId != -1) {
            sCreatedRawContactIds.add(result.rawContactId);
        }
        sCreatedDataIds.addAll(result.mimeTypeToDataId.values());
    }

    /**
     * Subsection: Picker launching strategy. Helper methods to invoke the picker in various
     * mime-type combinations for action_pick_contacts and action_pick. Also calls Verification
     * methods on the picker displayed.
     */
    private void launchPickerAndVerify(
            List<String> mimeTypes, boolean matchAll, Runnable verificationBlock) {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, new ArrayList<>(mimeTypes));
        if (matchAll) {
            intent.putExtra(EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS, true);
        }
        launchActivityScenario(intent, verificationBlock);
    }

    private void launchPickerAndVerifyForActionPick(String mimeType, Runnable verificationBlock) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(mimeType);
        launchActivityScenario(intent, verificationBlock);
    }

    private void launchActivityScenario(Intent intent, Runnable verificationBlock) {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(activity -> activity.startActivityForResult(intent, 1));
            // Execute verification while the activity is still live
            ContactsPickerTestHelper.waitForPickerUi(mUiDevice);
            verificationBlock.run();
        }
    }
}
