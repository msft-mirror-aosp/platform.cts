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

package android.provider.cts.contacts.strictsqlcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.providers.contacts.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENFORCE_STRICT_SQL_CHECKS)
public class ContactsContractStrictSqlCheckTest {

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String CLIENT_APP_PACKAGE =
            "android.provider.cts.contacts.strictsqlcheck.client";
    private static final String ACTION_QUERY =
            "android.provider.cts.contacts.strictsqlcheck.client.ACTION_QUERY";

    private static final int RESULT_ILLEGAL_ARGUMENT_EXCEPTION = Activity.RESULT_FIRST_USER + 1;

    private final ArrayList<Long> mCreatedRawContactIds = new ArrayList<>();
    private ContentResolver mResolver;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() throws Exception {
        if (!mCreatedRawContactIds.isEmpty()) {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            for (Long rawContactId : mCreatedRawContactIds) {
                ops.add(
                        ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                                .withSelection(
                                        ContactsContract.RawContacts._ID + "=?",
                                        new String[] {String.valueOf(rawContactId)})
                                .build());
            }
            mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
            mCreatedRawContactIds.clear();
        }
    }

    @Test
    public void testDataUri_strictSelection_throwsException() throws Exception {
        createContact("testDataUri");

        Uri dataUri = ContactsContract.Data.CONTENT_URI;
        // Grant the URI permission to the client app for this specific row
        mContext.grantUriPermission(
                CLIENT_APP_PACKAGE, dataUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // A subquery in selection should trigger an IllegalArgumentException
        String selectionWithSubquery = "(SELECT 1) > 0";
        int result = queryViaClientApp(dataUri, selectionWithSubquery);
        assertEquals(
                "Expected IllegalArgumentException due to strict SQL check on selection",
                RESULT_ILLEGAL_ARGUMENT_EXCEPTION,
                result);
    }

    @Test
    public void testDataUriWithId_strictProjection_throwsException() throws Exception {
        long dataId = createContact("testDataUriWithId");

        Uri dataUri =
                ContactsContract.Data.CONTENT_URI
                        .buildUpon()
                        .appendPath(String.valueOf(dataId))
                        .build();
        // Grant the URI permission to the client app for this specific row
        mContext.grantUriPermission(
                CLIENT_APP_PACKAGE, dataUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // A subquery in projection should trigger an IllegalArgumentException
        String selectionWithSubquery = "(SELECT 1) > 0";
        int result = queryViaClientApp(dataUri, selectionWithSubquery);

        assertEquals(
                "Expected IllegalArgumentException due to strict SQL check on projection",
                RESULT_ILLEGAL_ARGUMENT_EXCEPTION,
                result);
    }

    private int queryViaClientApp(Uri uri, String selection) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] resultHolder = new int[1];

        Intent intent = new Intent(ACTION_QUERY);
        intent.setPackage(CLIENT_APP_PACKAGE);
        intent.putExtra("target_uri", uri);
        if (selection != null) {
            intent.putExtra("selection", selection);
        }
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        mContext.sendOrderedBroadcast(
                intent,
                null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        resultHolder[0] = getResultCode();
                        latch.countDown();
                    }
                },
                null,
                Activity.RESULT_CANCELED,
                null,
                null);

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue("Timed out waiting for Client App", received);
        return resultHolder[0];
    }

    private long createContact(String displayName) throws Exception {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        int rawContactOpIndex = ops.size();
        ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build());

        ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                                ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                        .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(
                                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                displayName)
                        .build());

        android.content.ContentProviderResult[] results =
                mResolver.applyBatch(ContactsContract.AUTHORITY, ops);

        assertNotNull(results[0].uri);
        long rawContactId = Long.parseLong(results[0].uri.getLastPathSegment());
        mCreatedRawContactIds.add(rawContactId);

        assertNotNull(results[1].uri);
        return Long.parseLong(results[1].uri.getLastPathSegment());
    }
}
