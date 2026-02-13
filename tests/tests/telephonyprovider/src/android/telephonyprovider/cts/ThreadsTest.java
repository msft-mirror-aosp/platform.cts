/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephonyprovider.cts;

import static android.telephony.cts.util.DefaultSmsAppHelper.assumeTelephony;
import static android.telephony.cts.util.DefaultSmsAppHelper.assumeMessaging;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.provider.Telephony.Threads;
import android.telephony.cts.util.DefaultSmsAppHelper;
import com.android.internal.telephony.flags.Flags;

import androidx.test.filters.SmallTest;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class ThreadsTest {

    private static final String DESTINATION = "+19998880001";
    private static final String MESSAGE_BODY = "MESSAGE_BODY";
    private static final int THREAD_ID = 101;

    private Context mContext;
    private ContentResolver mContentResolver;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void ensureDefaultSmsApp() {
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver = getInstrumentation().getContext().getContentResolver();
        contentResolver.delete(Telephony.Threads.CONTENT_URI, null, null);
        contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null);
    }

    @Before
    public void setupTestEnvironment() {
        assumeTelephony();
        assumeMessaging();
        cleanup();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
    }

   @Test
   @RequiresFlagsEnabled(Flags.FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
   public void testThreadRestriction() {
       Set<Uri> restrictedMmsUris = new HashSet<>();
       Set<Uri> unrestrictedMmsUris = new HashSet<>();
       // Non-empty, unrestricted thread.
       String address1 = "+19998880001";
       long threadId1 = Threads.getOrCreateThreadId(mContext, address1);
       restrictedMmsUris.add(saveMmsToTelephony(threadId1, /* isRestricted= */ true));
       unrestrictedMmsUris.add(saveMmsToTelephony(threadId1, /* isRestricted= */ false));

       // Non-empty, restricted thread.
       String address2 = "+19998880002";
       long threadId2 = Threads.getOrCreateThreadId(mContext, address2);
       restrictedMmsUris.add(saveMmsToTelephony(threadId2, /* isRestricted= */ true));
       restrictedMmsUris.add(saveMmsToTelephony(threadId2, /* isRestricted= */ true));

       // Non-empty, unrestricted group thread.
       String address3_1 = "+19998880003";
       String address3_2 = "+19998880004";
       long threadId3 = Threads.getOrCreateThreadId(mContext, Set.of(address3_1, address3_2));
       unrestrictedMmsUris.add(saveMmsToTelephony(threadId3, /* isRestricted= */ false));
       restrictedMmsUris.add(saveMmsToTelephony(threadId3, /* isRestricted= */ true));

       // Non-empty, restricted group thread.
       String address4_1 = "+19998880005";
       String address4_2 = "+19998880006";
       long threadId4 = Threads.getOrCreateThreadId(mContext, Set.of(address3_2, address4_1,
            address4_2));
       restrictedMmsUris.add(saveMmsToTelephony(threadId4, /* isRestricted= */ true));
       restrictedMmsUris.add(saveMmsToTelephony(threadId4, /* isRestricted= */ true));

       Set<Long> unrestrictedThreadIds = Set.of(threadId1, threadId3);
       Set<Long> restrictedThreadIds = Set.of(threadId2, threadId4);
       Set<Long> allThreadIds = Stream.concat(unrestrictedThreadIds.stream(),
            restrictedThreadIds.stream()).collect(Collectors.toSet());
       Set<String> allAddresses = Set.of(
            address1, address2, address3_1, address3_2, address4_1, address4_2);
       Set<String> unrestrictedAddresses = Set.of(address1, address3_1, address3_2);

       // Query all threads, messages, and canonical addresses as DMA.
       {
            assertMms(unrestrictedMmsUris, /* isVisible= */ true);
            assertMms(restrictedMmsUris, /* isVisible= */ true);
            assertVisibleThreadIds(allThreadIds);
            assertVisibleAddresses(allAddresses);
       }
       // Query only unrestricted threads, messages, and canonical addresses as non-DMA.
       {
           try {
               DefaultSmsAppHelper.stopBeingDefaultSmsApp();
               assertMms(unrestrictedMmsUris, /* isVisible= */ true);
               assertMms(restrictedMmsUris, /* isVisible= */ false);
               assertVisibleAddresses(unrestrictedAddresses);
               assertVisibleThreadIds(unrestrictedThreadIds);
            } finally {
                DefaultSmsAppHelper.ensureDefaultSmsApp();
            }
       }
   }

   private void assertVisibleThreadIds(Set<Long> visibleThreadIds) {
        Cursor cursor = mContentResolver.query(
            Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(),
            null, null, null);
        assertThat(cursor.getCount()).isEqualTo(visibleThreadIds.size());
        while (cursor.moveToNext()) {
            assertThat(visibleThreadIds).contains(
                cursor.getLong(cursor.getColumnIndex(Telephony.Threads._ID)));
        }
    }

    private void assertVisibleAddresses(Set<String> visibleAddresses) {
        Cursor cursor = mContentResolver.query(
            Uri.parse("content://mms-sms/canonical-addresses"),
            null, null, null);
        assertThat(cursor.getCount()).isEqualTo(visibleAddresses.size());
        while (cursor.moveToNext()) {
            assertThat(visibleAddresses).contains(
                cursor.getString(cursor.getColumnIndex(Telephony.CanonicalAddressesColumns.ADDRESS)));
        }
    }

    private void assertMms(Set<Uri> visibleMmsUris, boolean isVisible) {
        for (Uri uri : visibleMmsUris) {
            Cursor cursor = mContentResolver.query(uri, null, null, null);
            assertThat(cursor.getCount()).isEqualTo(isVisible ? 1 : 0);
        }
    }

    @Test
    public void testThreadDeletion_doNotReuseThreadIdsFromEmptyThreads() {
        String destination2 = "+19998880002";

        long threadId1 = Telephony.Threads.getOrCreateThreadId(mContext, DESTINATION);

        int deletedCount =
                mContentResolver.delete(
                        saveToTelephony(threadId1, destination2, "testThreadDeletion body"),
                        null,
                        null);

        assertThat(deletedCount).isEqualTo(1);

        long threadId2 = Telephony.Threads.getOrCreateThreadId(mContext, destination2);

        assertThat(threadId2).isGreaterThan(threadId1);
    }

    // This purpose of this test case is to return latest date inserted as sms from thread
    @Test
    public void testMultipleSmsInsertDate_returnsLatestDateFromThread() {
        final long earlierTime = 1557382640;

        Uri smsUri = addMessageToTelephonyWithDate(earlierTime, MESSAGE_BODY, THREAD_ID);
        assertThat(smsUri).isNotNull();

        assertVerifyThreadDate(earlierTime);

        final long laterTime = 1557382650;

        Uri smsUri2 = addMessageToTelephonyWithDate(laterTime, MESSAGE_BODY,
                THREAD_ID);
        assertThat(smsUri2).isNotNull();

        assertVerifyThreadDate(laterTime);
    }

    private Uri addMessageToTelephonyWithDate(long date, String messageBody, long threadId) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(Telephony.Sms.THREAD_ID, threadId);
        contentValues.put(Telephony.Sms.DATE, date);
        contentValues.put(Telephony.Sms.BODY, messageBody);

        return mContext.getContentResolver().insert(Telephony.Sms.CONTENT_URI, contentValues);
    }

    private void assertVerifyThreadDate(long timeStamp) {
        Cursor cursor = mContentResolver.query(Telephony.Threads.CONTENT_URI,
                null, null, null);

        assertThat(cursor.getCount()).isEqualTo(1);
        assertThat(cursor.moveToNext()).isEqualTo(true);

        assertThat(cursor.getLong(cursor.getColumnIndex(Telephony.Threads.DATE)))
            .isEqualTo(timeStamp);
    }

    private Uri saveToTelephony(long threadId, String address, String body) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Telephony.Sms.THREAD_ID, threadId);
        contentValues.put(Telephony.Sms.ADDRESS, address);
        contentValues.put(Telephony.Sms.BODY, body);

        return mContext.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, contentValues);
    }

    private Uri saveMmsToTelephony(long threadId, boolean isRestricted) {
        final ContentValues mmsValues = new ContentValues();
        mmsValues.put(Telephony.Mms.TEXT_ONLY, 1);
        mmsValues.put(Telephony.Mms.MESSAGE_TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
        mmsValues.put(Telephony.Mms.SUBJECT, "subject");
        mmsValues.put(Telephony.ReadRestriction.RESTRICTED, isRestricted);
        mmsValues.put(Telephony.Mms.THREAD_ID, threadId);

        return mContext.getContentResolver().insert(Telephony.Mms.CONTENT_URI, mmsValues);
    }
}
