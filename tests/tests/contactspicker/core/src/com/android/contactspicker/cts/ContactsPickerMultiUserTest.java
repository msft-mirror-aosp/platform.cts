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

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsPickerSessionContract;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunOnCloneProfile;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.contactspicker.cts.common.ContactsPickerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
public class ContactsPickerMultiUserTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final Map<String, ContactsPickerTestHelper.ContactDataIds> sContactDataIdMap =
            new HashMap<>();
    private static final List<Long> sCreatedContactDataIds = new ArrayList<>();
    private static final List<Long> sCreatedRawContactIds = new ArrayList<>();

    private static final int PICKER_LOAD_TIMEOUT_MS = 8000;
    private static final int TIMEOUT_MS = 2000;
    private static final int CP2_IDLE_MS = 2000;

    private static final String DONE_BUTTON_CONTENT_DESC = "Done";
    private static final String RANDOM_SUFFIX =
            java.util.UUID.randomUUID().toString().substring(0, 8);
    private static final String CONTACT_PHONE_MULTIUSER =
            "Contact Phone Multiuser " + RANDOM_SUFFIX;

    private UiDevice mUiDevice;

    private UserManager mUserManager;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.pressHome();

        // Create Contacts
        createContactWithPhoneNumber(getUserForContactWriteOps());
        ContactsPickerTestHelper.waitForContactsToBeCreated(
                sContext.getContentResolver(), sCreatedContactDataIds);
    }

    @After
    public void tearDown() {
        mUiDevice.pressHome();

        // Remove Contacts
        ContactsPickerTestHelper.removeTestContacts(
                sContext, sCreatedRawContactIds, getUserForContactWriteOps());
        sCreatedRawContactIds.clear();
        sContactDataIdMap.clear();
        SystemClock.sleep(CP2_IDLE_MS);
    }

    @Test
    @RequireRunOnWorkProfile
    public void actionPickContactsSelection_PhoneOnly_WorkProfile() throws Exception {
        verifyActionPickContactsSelectionForPhoneMime();
    }

    @Test
    @RequireRunOnCloneProfile(installInstrumentedAppInParent = OptionalBoolean.TRUE)
    public void actionPickContactsSelection_PhoneOnly_CloneProfile() throws Exception {
        verifyActionPickContactsSelectionForPhoneMime();
    }

    @Test
    @RequireRunOnPrivateProfile
    public void actionPickContactsSelection_PhoneOnly_PrivateProfile() throws Exception {
        verifyActionPickContactsSelectionForPhoneMime();
    }

    private void verifyActionPickContactsSelectionForPhoneMime() throws Exception {
        Intent intent = new Intent(ACTION_PICK_CONTACTS);
        intent.putStringArrayListExtra(
                EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                new ArrayList<>(
                        Collections.singletonList(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)));

        long expectedDataId =
                sContactDataIdMap
                        .get(CONTACT_PHONE_MULTIUSER)
                        .get(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);

        launchPickerAndSelect(
                intent,
                List.of(CONTACT_PHONE_MULTIUSER),
                (resultCode, resultData) ->
                        verifyUriReturned(
                                sContext,
                                resultCode,
                                resultData,
                                ContactsPickerSessionContract.AUTHORITY,
                                List.of(expectedDataId)));

        ContactsPickerTestHelper.removeTestContacts(
                sContext, sCreatedRawContactIds, sContext.getUser());
        sCreatedRawContactIds.clear();
        sContactDataIdMap.clear();
        SystemClock.sleep(CP2_IDLE_MS);
    }

    private UserHandle getUserForContactWriteOps() {
        UserHandle user = sContext.getUser();
        try (var p =
                TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS)) {
            // For Clone Profile, we need to perform write ops in parent user.
            if (mUserManager.isCloneProfile()) {
                return mUserManager.getProfileParent(sContext.getUser());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query UserManager", e);
        }
        return user;
    }

    private static void createContactWithPhoneNumber(UserHandle user) {
        Map<String, Object> data = new HashMap<>();
        data.put(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "1234567890");
        data.put(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                CONTACT_PHONE_MULTIUSER);
        ContactsPickerTestHelper.ContactCreationResult result =
                ContactsPickerTestHelper.createContact(sContext, data, user);

        if (result.rawContactId != -1) {
            sCreatedRawContactIds.add(result.rawContactId);
            sContactDataIdMap.put(
                    CONTACT_PHONE_MULTIUSER,
                    new ContactsPickerTestHelper.ContactDataIds(result.mimeTypeToDataId));
        }
        sCreatedContactDataIds.addAll(result.mimeTypeToDataId.values());
    }

    private interface ResultVerifier {
        void verify(int resultCode, Intent resultData);
    }

    private void launchPickerAndSelect(
            Intent intent, List<String> contactNamesToSelect, ResultVerifier verifier)
            throws Exception {
        try (ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(TestActivity.class)) {
            scenario.onActivity(activity -> activity.startActivityForResult(intent, 1));

            ContactsPickerTestHelper.waitForPickerUi(mUiDevice, PICKER_LOAD_TIMEOUT_MS);

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
}
