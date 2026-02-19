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

package com.android.contactspicker.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserHandle;
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
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.widget.EditText;

import com.android.bedstead.nene.TestApis;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for Contacts Picker CTS tests to manage test contacts creation, removal, search,
 * etc.
 */
public class ContactsPickerTestHelper {
    private static final String TAG = "ContactsPickerTestHelper";
    private static final int POLL_INTERVAL_MS = 200;
    private static final int POLL_TIMEOUT_MS = 5000;

    public static final int ELEMENT_DISPLAY_TIMEOUT_MS = 2000;
    public static final int ELEMENT_NOT_DISPLAY_TIMEOUT_MS = 1000;

    private static final String SEARCH_BUTTON_CONTENT_DESC = "Search";

    /** Result of a contact creation operation. */
    public static class ContactCreationResult {
        public final long rawContactId;
        public final Map<String, Long> mimeTypeToDataId;

        public ContactCreationResult(long rawContactId, Map<String, Long> mimeTypeToDataId) {
            this.rawContactId = rawContactId;
            this.mimeTypeToDataId = mimeTypeToDataId;
        }
    }

    /** Creates a byte[] array representing a dummy contact thumbnail. */
    public static byte[] getDummyColorBytes() {
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        return stream.toByteArray();
    }

    /** Removes the test contacts identified by their raw contact IDs. */
    public static void removeTestContacts(
            Context context, List<Long> rawContactIds, UserHandle removeAsUser) {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.WRITE_CONTACTS);
        if (context.getUser().getIdentifier() != removeAsUser.getIdentifier()) {
            perms.add(Manifest.permission.INTERACT_ACROSS_USERS);
        }

        try (var p = TestApis.permissions().withPermission(perms.toArray(new String[0]))) {
            ContentResolver resolver = context.getContentResolver();
            if (context.getUser().getIdentifier() != removeAsUser.getIdentifier()) {
                resolver = context.createContextAsUser(removeAsUser, 0).getContentResolver();
            }
            for (Long rawContactId : rawContactIds) {
                resolver.delete(
                        RawContacts.CONTENT_URI,
                        RawContacts._ID + " = ?",
                        new String[] {String.valueOf(rawContactId)});
            }
        }
    }

    /** Creates a contact with multiple data rows based on the provided mimeType to value map. */
    public static ContactCreationResult createContact(
            Context context, Map<String, Object> mimeTypeToValue, UserHandle createAsUser) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        int rawContactInsertIndex = ops.size();

        ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_TYPE, null)
                        .withValue(RawContacts.ACCOUNT_NAME, null)
                        .build());

        List<String> mimeTypesInOrder = new ArrayList<>();
        for (Map.Entry<String, Object> entry : mimeTypeToValue.entrySet()) {
            String mimeType = entry.getKey();
            Object value = entry.getValue();
            mimeTypesInOrder.add(mimeType);

            ContentProviderOperation.Builder builder =
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                            .withValue(Data.MIMETYPE, mimeType);

            switch (mimeType) {
                case StructuredName.CONTENT_ITEM_TYPE -> {
                    builder.withValue(StructuredName.DISPLAY_NAME, value);
                    builder.withValue(StructuredName.GIVEN_NAME, value);
                }
                case Phone.CONTENT_ITEM_TYPE -> {
                    builder.withValue(Phone.NUMBER, value);
                    builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
                }
                case Email.CONTENT_ITEM_TYPE -> {
                    builder.withValue(Email.ADDRESS, value);
                    builder.withValue(Email.TYPE, Email.TYPE_HOME);
                }
                case StructuredPostal.CONTENT_ITEM_TYPE -> {
                    builder.withValue(StructuredPostal.FORMATTED_ADDRESS, value);
                    builder.withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME);
                }
                case Organization.CONTENT_ITEM_TYPE ->
                        builder.withValue(Organization.COMPANY, value);
                case Relation.CONTENT_ITEM_TYPE -> {
                    builder.withValue(Relation.NAME, value);
                    builder.withValue(Relation.TYPE, Relation.TYPE_ASSISTANT);
                }
                case Event.CONTENT_ITEM_TYPE -> builder.withValue(Event.START_DATE, value);
                case Website.CONTENT_ITEM_TYPE -> builder.withValue(Website.URL, value);
                case Nickname.CONTENT_ITEM_TYPE -> builder.withValue(Nickname.NAME, value);
                case GroupMembership.CONTENT_ITEM_TYPE ->
                        builder.withValue(GroupMembership.GROUP_ROW_ID, value);
                case Photo.CONTENT_ITEM_TYPE -> builder.withValue(Photo.PHOTO, value);
                default -> builder.withValue(Data.DATA1, value);
            }
            ops.add(builder.build());
        }

        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.WRITE_CONTACTS);
        if (context.getUser().getIdentifier() != createAsUser.getIdentifier()) {
            perms.add(Manifest.permission.INTERACT_ACROSS_USERS);
        }

        try (var p = TestApis.permissions().withPermission(perms.toArray(new String[0]))) {
            ContentResolver resolver = context.getContentResolver();
            if (context.getUser().getIdentifier() != createAsUser.getIdentifier()) {
                resolver = context.createContextAsUser(createAsUser, 0).getContentResolver();
            }
            ContentProviderResult[] results = resolver.applyBatch(ContactsContract.AUTHORITY, ops);
            long rawContactId = -1;
            Map<String, Long> mimeToId = new HashMap<>();

            if (results.length > 0 && results[0].uri != null) {
                rawContactId = ContentUris.parseId(results[0].uri);
            }

            for (int i = 1; i < results.length; i++) {
                if (results[i].uri != null) {
                    long dataId = ContentUris.parseId(results[i].uri);
                    mimeToId.put(mimeTypesInOrder.get(i - 1), dataId);
                }
            }
            return new ContactCreationResult(rawContactId, mimeToId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create contact", e);
        }
    }

    /** Waits for the contacts to be fully created in the Contacts Provider. */
    public static void waitForContactsToBeCreated(ContentResolver resolver, List<Long> dataIds) {
        long startTime = SystemClock.elapsedRealtime();

        while (SystemClock.elapsedRealtime() - startTime < POLL_TIMEOUT_MS) {
            try (var p = TestApis.permissions().withPermission(Manifest.permission.READ_CONTACTS)) {
                String selection =
                        Data._ID
                                + " IN ("
                                + dataIds.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","))
                                + ")";
                try (Cursor cursor =
                        resolver.query(
                                Data.CONTENT_URI, new String[] {Data._ID}, selection, null, null)) {
                    if (cursor != null && cursor.getCount() == dataIds.size()) {
                        Log.i(TAG, "All data rows created: " + cursor.getCount());
                        return;
                    }
                }
            }
            SystemClock.sleep(POLL_INTERVAL_MS);
        }
        Log.w(TAG, "Timed out waiting for data rows to be created. Proceeding anyway.");
    }

    /** Verifies if a contact is displayed. If displayed, clicks the contact. */
    public static void clickContact(UiDevice uiDevice, String displayName) {
        UiObject2 contactItem;
        boolean searchUsed = false;
        Log.i(TAG, "Clicking contact: " + displayName);

        // Try to find in contact list first using desc
        contactItem =
                uiDevice.wait(Until.findObject(By.desc(displayName)), ELEMENT_DISPLAY_TIMEOUT_MS);

        if (contactItem == null) {
            // Try to find via search
            contactItem = getContactWithSearch(uiDevice, displayName, true);
            searchUsed = true;
        }

        if (contactItem == null) {
            throw new AssertionError(
                    "Contact '" + displayName + "' should be visible but was NOT found on screen.");
        }

        contactItem.click();
        // Reset the picker so we move out of search.
        if (searchUsed) {
            resetPicker(uiDevice);
        }
    }

    /** Verifies if contacts are displayed or not based on the shouldBeFound flag. */
    public static void verifyContactsDisplayed(
            UiDevice uiDevice, List<String> displayNames, boolean shouldBeFound) {
        boolean found;
        for (String name : displayNames) {
            int timeout =
                    shouldBeFound ? ELEMENT_DISPLAY_TIMEOUT_MS : ELEMENT_NOT_DISPLAY_TIMEOUT_MS;
            Log.i(
                    TAG,
                    (shouldBeFound
                                    ? "Verifying contact to be shown: "
                                    : "Verifying contact NOT to be shown: ")
                            + name);

            // Try to find in contact list first
            found = uiDevice.wait(Until.hasObject(By.desc(name)), timeout);

            if (!found) {
                // Try to find via search
                getContactWithSearch(uiDevice, name, shouldBeFound);
                // Reset the picker so we move out of search.
                resetPicker(uiDevice);
            } else {
                Log.i(TAG, "Contact found on screen: " + name);
                if (!shouldBeFound) {
                    throw new AssertionError(
                            "Contact '"
                                    + name
                                    + "' should NOT be visible but was found on screen.");
                }
            }
        }
    }

    /** Verifies if a contact is found using the search bar, then returns the same. */
    private static UiObject2 getContactWithSearch(
            UiDevice uiDevice, String name, boolean shouldBeFound) {
        UiObject2 contactItem;
        UiObject2 searchInput =
                uiDevice.wait(
                        Until.findObject(By.clazz(EditText.class)), ELEMENT_DISPLAY_TIMEOUT_MS);

        if (searchInput == null) {
            UiObject2 searchBar =
                    uiDevice.wait(
                            Until.findObject(By.desc(SEARCH_BUTTON_CONTENT_DESC)),
                            ELEMENT_DISPLAY_TIMEOUT_MS);
            if (searchBar != null) {
                searchBar.click();
                searchInput =
                        uiDevice.wait(
                                Until.findObject(By.clazz(EditText.class)),
                                ELEMENT_DISPLAY_TIMEOUT_MS);
            }
        }

        if (searchInput == null) {
            throw new AssertionError("Search input field not found.");
        }

        searchInput.setText(name);
        Log.i(TAG, "Search set to: " + name);

        long timeout = shouldBeFound ? ELEMENT_DISPLAY_TIMEOUT_MS : ELEMENT_NOT_DISPLAY_TIMEOUT_MS;
        contactItem = uiDevice.wait(Until.findObject(By.desc(name)), timeout);
        boolean found = contactItem != null;
        Log.i(TAG, "Search found: " + found);

        if (shouldBeFound && !found) {
            throw new AssertionError("Contact '" + name + "' not found in search results.");
        } else if (!shouldBeFound && found) {
            throw new AssertionError(
                    "Contact '" + name + "' should NOT be found in search results but was found.");
        }

        return contactItem;
    }

    /** Moves the picker back to initial state. Preserves the selected contacts. */
    private static void resetPicker(UiDevice uiDevice) {
        uiDevice.pressBack();
        uiDevice.waitForIdle();
    }

    /** Wait for picker ui to come up. */
    public static void waitForPickerUi(UiDevice uiDevice) {
        // Wait for the Search icon text to confirm UI is ready
        waitForPickerUi(uiDevice, ELEMENT_DISPLAY_TIMEOUT_MS);
    }

    /** Wait for picker ui to come up, with a provided timeout. */
    public static void waitForPickerUi(UiDevice uiDevice, int timeout) {
        // Wait for the Search icon text to confirm UI is ready
        boolean isShown =
                uiDevice.wait(Until.hasObject(By.text(SEARCH_BUTTON_CONTENT_DESC)), timeout);
        if (!isShown) {
            throw new AssertionError("Contacts Picker UI ('Search' text) did not appear.");
        }
        // Wait for any contact loading animations to clear.
        uiDevice.waitForIdle();
    }

    /**
     * Verify that the uri returned matches expected authority and provides the expected dataIds,
     * when queried.
     */
    public static void verifyUriReturned(
            Context context,
            int resultCode,
            Intent resultData,
            String expectedAuthority,
            List<Long> expectedDataIds) {
        assertThat(resultCode).isEqualTo(Activity.RESULT_OK);
        assertThat(resultData).isNotNull();
        Uri retUri = resultData.getData();
        assertThat(retUri).isNotNull();
        assertThat(retUri.getAuthority()).contains(expectedAuthority);

        // Query the URI to verify it contains the correct Data Id(s)
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                retUri,
                                new String[] {ContactsContract.Data._ID},
                                null,
                                null,
                                null)) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.getCount()).isEqualTo(expectedDataIds.size());
            List<Long> actualDataIds = new ArrayList<>();
            while (cursor.moveToNext()) {
                actualDataIds.add(cursor.getLong(0));
            }
            assertThat(actualDataIds).containsExactlyElementsIn(expectedDataIds);
        }
    }

    /** A data class to hold the mapping of MimeType to Data ID for a single contact. */
    public static class ContactDataIds {
        private final Map<String, Long> mMimeTypeToDataId;

        public ContactDataIds(Map<String, Long> mimeTypeToDataId) {
            this.mMimeTypeToDataId = mimeTypeToDataId;
        }

        /** Returns the dataId corresponding to the mimeType provided. */
        public Long get(String mimeType) {
            return mMimeTypeToDataId.get(mimeType);
        }
    }
}
