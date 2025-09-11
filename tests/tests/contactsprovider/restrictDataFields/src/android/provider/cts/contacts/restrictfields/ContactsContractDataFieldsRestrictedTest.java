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

package android.provider.cts.contacts.restrictfields;

import static android.provider.ContactsContract.CommonDataKinds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.providers.contacts.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ContactsContractDataFieldsRestrictedTest {

    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mResolver =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getContentResolver();
        ContentProviderClient provider =
                mResolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        mBuilder = new ContactsContract_TestDataBuilder(provider);
    }

    @After
    public void tearDown() throws Exception {
        mBuilder.cleanup();
    }

    @RequiresFlagsEnabled(Flags.FLAG_RESTRICT_PII_DATA_URI_COLUMNS)
    @Test
    public void testDataView_projection_restrictedColumnsNotPresent() throws Exception {
        TestRawContact rawContact =
                mBuilder.newRawContact()
                        .with(RawContacts.ACCOUNT_TYPE, "test_type")
                        .with(RawContacts.ACCOUNT_NAME, "test_name")
                        .with(RawContacts.SOURCE_ID, "source_id")
                        .insert();
        rawContact
                .newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();

        // We don't need to query for the data row just created, querying for all rows
        // and getting a non-null Cursor should suffice for us.
        try (Cursor cursor = mResolver.query(Data.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
            assertEquals(-1, cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));
            assertEquals(-1, cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
            assertEquals(-1, cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE_AND_DATA_SET));
        }
    }
}
