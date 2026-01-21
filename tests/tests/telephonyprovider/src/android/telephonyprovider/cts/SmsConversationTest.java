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

import static com.android.internal.telephony.flags.Flags.FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.provider.Telephony.Threads;
import android.provider.Telephony.ReadRestriction;
import android.provider.Telephony.ReadRestriction.ReadRestrictionValues;
import android.provider.Telephony.Sms.Conversations;
import android.telephony.cts.util.DefaultSmsAppHelper;

import androidx.test.filters.SmallTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class SmsConversationTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEST_ADDRESS = "+19998880001";
    private static final String TEST_SMS_BODY = "TEST_SMS_BODY";
    private Context mContext;
    private ContentResolver mContentResolver;

    @BeforeClass
    public static void ensureDefaultSmsApp() {
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver = getInstrumentation().getContext().getContentResolver();
        contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null);
    }

    @Before
    public void setupTestEnvironment() {
        assumeTelephony();
        assumeMessaging();
        cleanup();
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    /**
     * The purpose of this test is to check most recent insert sms body equals to the Conversation
     * Snippet
     */
    @Test
    public void testQueryConversation_snippetEqualsMostRecentMessageBody() {
        String testSmsMostRecent = "TEST_SMS_MOST_RECENT";

        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);
        saveToTelephony(testSmsMostRecent, TEST_ADDRESS, /* isRestricted= */ false);

        Cursor cursor = mContentResolver
                .query(Telephony.Sms.CONTENT_URI, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(2);

        Cursor conversationCursor = mContentResolver
                .query(Conversations.CONTENT_URI, null, null, null);
        assertThat(conversationCursor.getCount()).isEqualTo(1);
        conversationCursor.moveToNext();

        assertThat(
            conversationCursor.getString(conversationCursor.getColumnIndex(Conversations.SNIPPET)))
            .isEqualTo(testSmsMostRecent);
    }

    @Test
    public void testQueryConversation_usingRestrictedInWhereClause_returnsCorrectMessages() {
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ true);
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ true);
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ true);

        Cursor restrictedMessagesCursor = mContentResolver
                .query(Telephony.Sms.CONTENT_URI,
                        /* projection= */ null,
                        /* selection= */ "restricted = ?",
                        /* selectionArgs= */ new String[] { "1" },
                        /* sortOrder= */ null,
                        /* cancellationSignal= */ null);

        assertThat(restrictedMessagesCursor.getCount()).isEqualTo(3);

        Cursor unrestrictedMessagesCursor = mContentResolver
            .query(Telephony.Sms.CONTENT_URI,
                    /* projection= */ null,
                    /* selection= */ "restricted = ?",
                    /* selectionArgs= */ new String[] { "0" },
                    /* sortOrder= */ null,
                    /* cancellationSignal= */ null);

        assertThat(unrestrictedMessagesCursor.getCount()).isEqualTo(2);
    }

    /**
     * The purpose of this test is to check that the conversation is visible to non default sms apps
     * after inserting an unrestricted sms.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void testQueryConversation_threadBecomesNonRestrictedAfterInsertingNonRestrictedSms() {
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ true);

        {
            try {
                stopBeingDefaultSmsApp();

                Cursor threadCursor = mContentResolver
                        .query(Threads.CONTENT_URI, null, null, null);

                assertThat(threadCursor.getCount()).isEqualTo(0);
            } finally {
                ensureDefaultSmsApp();
            }
        }


        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);

        {
            try {
                stopBeingDefaultSmsApp();

                Cursor threadCursor = mContentResolver
                        .query(Threads.CONTENT_URI, null, null, null);

                assertThat(threadCursor.getCount()).isEqualTo(1);
            } finally {
                ensureDefaultSmsApp();
            }
        }
    }


    /**
     * The purpose of this test is to check that the conversation is visible to non default sms apps
     * after inserting an unrestricted sms.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void queryConversation_threadIsEmptyForNonDefaultSmsAppAfterRemovingUnrestrictedSms() {
        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ true);
        Uri unrestricted = saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);

        // The thread is visible to the default sms app.
        {
            Cursor conversationCursor = mContentResolver
                    .query(Conversations.CONTENT_URI, null, null, null);
            assertThat(conversationCursor.getCount()).isEqualTo(1);
        }
        // The thread is visible to a non default sms app.
        {
            try {
                stopBeingDefaultSmsApp();

                Cursor conversationCursor = mContentResolver
                        .query(Conversations.CONTENT_URI, null, null, null);
                assertThat(conversationCursor.getCount()).isEqualTo(1);
            } finally {
                ensureDefaultSmsApp();
            }
        }

        // Remove the unrestricted message.
        mContentResolver.delete(unrestricted, null, null);

        // The thread is still visible to the default sms app.
        {
            Cursor conversationCursor = mContentResolver
                    .query(Conversations.CONTENT_URI, null, null, null);
            assertThat(conversationCursor.getCount()).isEqualTo(1);
        }
        // The thread is no longer visible to a non default sms app.
        {
            try {
                stopBeingDefaultSmsApp();

                Cursor conversationCursor = mContentResolver
                        .query(Conversations.CONTENT_URI, null, null, null);
                assertThat(conversationCursor.getCount()).isEqualTo(0);
            } finally {
                ensureDefaultSmsApp();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void queryNonExistingThreadId_nonDefaultSmsApp_returnsEmptyCursor() {
        {
            try {
                stopBeingDefaultSmsApp();

                Cursor conversationCursor = mContentResolver.query(
                        Uri.parse("content://mms-sms/threadID?recipient=" + TEST_ADDRESS),
                        null, null, null);
                assertThat(conversationCursor.getCount()).isEqualTo(0);
            } finally {
                ensureDefaultSmsApp();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void queryNonExistingThreadId_defaultSmsApp_createsNewThread() {
            Cursor conversationCursor = mContentResolver.query(
                    Uri.parse("content://mms-sms/threadID?recipient=" + TEST_ADDRESS),
                    null, null, null);
            assertThat(conversationCursor.getCount()).isEqualTo(1);
    }

    /**
     * The purpose of this test is to check Conversation message count is equal to the number of sms
     * inserted.
     */
    @Test
    public void testQueryConversation_returnsCorrectMessageCount() {
        String testSecondSmsBody = "TEST_SECOND_SMS_BODY";

        saveToTelephony(TEST_SMS_BODY, TEST_ADDRESS, /* isRestricted= */ false);
        saveToTelephony(testSecondSmsBody, TEST_ADDRESS, /* isRestricted= */ false);

        Cursor cursor = mContentResolver
                .query(Telephony.Sms.CONTENT_URI, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(2);

        Cursor conversationCursor = mContentResolver
                .query(Conversations.CONTENT_URI, null, null, null);
        assertThat(conversationCursor.getCount()).isEqualTo(1);
        conversationCursor.moveToNext();

        assertThat(conversationCursor
            .getInt(conversationCursor.getColumnIndex(Conversations.MESSAGE_COUNT)))
            .isEqualTo(2);
    }

    private Uri saveToTelephony(String messageBody, String address, boolean isRestricted) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.BODY, messageBody);
        values.put(Telephony.Sms.ADDRESS, address);
        if (isRestricted) {
            values.put(ReadRestriction.RESTRICTED, true);
        }
        return mContentResolver.insert(Telephony.Sms.CONTENT_URI, values);
    }

    private static void stopBeingDefaultSmsApp() {
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
        getInstrumentation().waitForIdleSync();
    }
}

