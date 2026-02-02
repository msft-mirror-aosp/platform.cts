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

import static android.Manifest.permission.RECEIVE_SENSITIVE_NOTIFICATIONS;
import static android.provider.Telephony.Sms.CONTAINS_OTP;
import static android.provider.Telephony.Sms.OTP_SUBTYPE_SMS_RETRIEVER_OTP;
import static android.provider.Telephony.Sms.OTP_SUBTYPE_WEB_OTP;
import static android.provider.Telephony.Sms.OTP_TYPE_CONTAINS_OTP;
import static android.provider.Telephony.Sms.OTP_TYPE_NONE;
import static android.telephony.cts.util.DefaultSmsAppHelper.assumeMessaging;
import static android.telephony.cts.util.DefaultSmsAppHelper.assumeTelephony;
import static android.view.flags.Flags.FLAG_REDACT_OTP_APP_COMPAT_API;
import static android.view.flags.Flags.FLAG_REDACT_WEB_OTP_SMS_API;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.internal.telephony.flags.Flags.FLAG_LIMIT_RAW_TABLE_VISIBILITY;
import static com.android.internal.telephony.flags.Flags.FLAG_MESSAGE_PROMOTION;
import static com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS;
import static com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS_API;
import static com.android.internal.telephony.flags.Flags.FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteCallback;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.provider.Telephony.ReadRestriction;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(BedsteadJUnit4.class)
public class SmsTest {
    private static final String TAG = "SmsTest";
    private static final String TEST_SMS_BODY = "TEST_SMS_BODY";
    private static final String TEST_WEB_OTP_SMS_SUFFIX = "\n\n@test.domain #12345";
    private static final String TEST_OTP_SMS_BODY = "Your one time code is 12345";
    private static final String TEST_NOT_OTP_SMS_BODY = "Your account number is 12345";
    private static final String TEST_ADDRESS = "+19998880001";
    private static final int TEST_THREAD_ID_1 = 101;
    private static final long OTP_HIDING_TIME_MS = TimeUnit.HOURS.toMillis(3);
    private static final int CONTAINS_SMS_RETRIEVER_OTP =
            OTP_TYPE_CONTAINS_OTP | OTP_SUBTYPE_SMS_RETRIEVER_OTP;
    private static final int CONTAINS_WEB_OTP =
            OTP_TYPE_CONTAINS_OTP | OTP_SUBTYPE_WEB_OTP;
    private static final int CONTAINS_GENERIC_OTP =
            OTP_TYPE_CONTAINS_OTP;
    private static final long FILTER_GENERIC_OTP_CHANGE_ID = 437043173L;
    private Context mContext;
    private ContentResolver mContentResolver;
    private RoleManager mRoleManager;
    private SmsTestHelper mSmsTestHelper;
    private BroadcastReceiver mSmsRetrieverReceiver;
    private static final String APP_THAT_RETURNS_RETRIEVER_HASH =
            "android.telephony.cts.smsretriever";
    private ActivityManager mActivityManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void ensureDefaultSmsApp() {
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver = getInstrumentation().getContext().getContentResolver();
        contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null);
        contentResolver.delete(Telephony.Threads.CONTENT_URI, null, null);
    }

    @AfterClass
    public static void stopBeingDefaultSmsApp() {
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Before
    public void setupTestEnvironment() {
        assumeTelephony();
        assumeMessaging();
        cleanup();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mRoleManager = mContext.getSystemService(RoleManager.class);
        mSmsTestHelper = new SmsTestHelper();

        mSmsRetrieverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Ignored, as it's never called in this test
            }
        };
        mActivityManager = mContext.getSystemService(ActivityManager.class);
    }

    /**
     * Asserts that a URI returned from an SMS insert operation represents a pass Insert.
     */
    @Test
    public void testSmsInsert() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(uri, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();

        String actualSmsBody = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
        assertThat(actualSmsBody).isEqualTo(TEST_SMS_BODY);
    }

    /**
     * The purpose of this test is to perform delete operation and assert that SMS is deleted.
     */
    @Test
    public void testSmsDelete() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        int deletedRows = mContentResolver.delete(uri, null, null);

        assertThat(deletedRows).isEqualTo(1);

        Cursor cursor = mContentResolver.query(uri, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(0);
    }

    /**
     * The purpose of this test is to update the message body and verify the message body is
     * updated.
     */
    @Test
    public void testSmsUpdate() {
        String testSmsBodyUpdate = "TEST_SMS_BODY_UPDATED";
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, uri, TEST_SMS_BODY);

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, TEST_ADDRESS);
        values.put(Telephony.Sms.BODY, testSmsBodyUpdate);

        mContentResolver.update(uri, values, null, null);

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, uri, testSmsBodyUpdate);
    }

    @Test
    public void testInsertSmsFromSubid_verifySmsFromNotOtherSubId() {
        int subId = -1;

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.BODY, TEST_SMS_BODY);
        values.put(Telephony.Sms.SUBSCRIPTION_ID, subId);
        Uri uri = mContentResolver.insert(Telephony.Sms.CONTENT_URI, values);

        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.SUBSCRIPTION_ID, uri,
                String.valueOf(subId));
    }

    @Test
    public void testInsertSms_canUpdateSeen() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(uri, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);

        final ContentValues updateValues = new ContentValues();
        updateValues.put(Telephony.Sms.SEEN, 1);

        int cursorUpdate = mContentResolver.update(uri, updateValues, null, null);

        assertThat(cursorUpdate).isEqualTo(1);

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.SEEN, uri, String.valueOf(1));
    }

    @Test
    public void testInsertSms_canUpdateSmsStatus() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(uri, null, null, null);

        assertThat(cursor.getCount()).isEqualTo(1);
        // STATUS_FAILED = 64;  0x40
        mSmsTestHelper.assertUpdateSmsStatus(Telephony.Sms.STATUS_FAILED, uri);
        // STATUS_PENDING = 32;  0x20
        mSmsTestHelper.assertUpdateSmsStatus(Telephony.Sms.STATUS_PENDING, uri);
        //  STATUS_COMPLETE = 0; 0x0
        mSmsTestHelper.assertUpdateSmsStatus(Telephony.Sms.STATUS_COMPLETE, uri);
    }

    @Test
    public void testInsertSms_canUpdateSmsType() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(uri, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);
        // MESSAGE_TYPE_INBOX = 1;  0x1
        mSmsTestHelper.assertUpdateSmsType(Telephony.Sms.MESSAGE_TYPE_INBOX, uri);
        // MESSAGE_TYPE_SENT = 2;  0x2
        mSmsTestHelper.assertUpdateSmsType(Telephony.Sms.MESSAGE_TYPE_SENT, uri);
    }

    // Queries for a thread ID returns the same and correct thread ID.
    @Test
    public void testQueryThreadId_returnSameThreadId() {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.THREAD_ID, TEST_THREAD_ID_1);
        values.put(Telephony.Sms.BODY, TEST_SMS_BODY);
        Uri uri = mContentResolver.insert(Telephony.Sms.CONTENT_URI, values);

        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.THREAD_ID, uri,
                String.valueOf(TEST_THREAD_ID_1));
    }

    /**
     * Asserts that content provider bulk insert and returns the same count while query.
     */
    @Test
    public void testSmsBulkInsert() {
        ContentValues[] smsContentValues = new ContentValues[] {
                mSmsTestHelper.createSmsValues(mSmsTestHelper.SMS_ADDRESS_BODY_1),
                mSmsTestHelper.createSmsValues(mSmsTestHelper.SMS_ADDRESS_BODY_2)};

        int count = mContentResolver.bulkInsert(Telephony.Sms.CONTENT_URI, smsContentValues);
        mSmsTestHelper.assertBulkSmsContentEqual(count, smsContentValues);
    }

    /**
     * Asserts that SMS inserted is auto populated with default values as mentioned in the table
     * schema.
     */
    @Test
    public void testDefaultValuesAreInsertedInSmsTable() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.READ))).isEqualTo(0);

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.STATUS))).isEqualTo(-1);

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.DATE_SENT))).isEqualTo(0);

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.LOCKED))).isEqualTo(0);

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID))).isEqualTo(
                SubscriptionManager.getDefaultSmsSubscriptionId());

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.ERROR_CODE))).isEqualTo(-1);

        assertThat(
            cursor.getInt(cursor.getColumnIndex(Telephony.Sms.SEEN))).isEqualTo(0);
    }

    @Test
    public void testDeleteSms_ifLastSmsDeletedThenThreadIsDeleted() {
        int testThreadId2 = 102;

        Uri uri1 = mSmsTestHelper
                .insertTestSmsWithThread(TEST_SMS_BODY, TEST_ADDRESS, TEST_THREAD_ID_1);
        assertThat(uri1).isNotNull();

        Uri uri2 = mSmsTestHelper
                .insertTestSmsWithThread(TEST_SMS_BODY, TEST_ADDRESS, testThreadId2);
        assertThat(uri2).isNotNull();

        int deletedRows = mContentResolver.delete(uri1, null, null);
        assertThat(deletedRows).isEqualTo(1);

        Cursor cursor = mContentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();

        int thread_id = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.THREAD_ID));
        assertThat(thread_id).isNotEqualTo(TEST_THREAD_ID_1);
    }

    /**
     * Asserts that a Emoji SMS body returned from an SMS insert operation are equal
     */
    @Test
    public void testInsertEmoji_andVerify() {
        String testSmsBodyEmoji = "\uD83D\uDE0D\uD83D\uDE02"
                + "\uD83D\uDE1B\uD83D\uDE00\uD83D\uDE1E☺️\uD83D\uDE1B"
                + "\uD83D\uDE1E☺️\uD83D\uDE0D";

        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, testSmsBodyEmoji);

        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, uri,
                String.valueOf(testSmsBodyEmoji));
    }

    /**
     * Verifies that subqueries are not allowed with a restricted view
     */
    @Test
    public void testSubqueryNotAllowed() {
        Uri uri = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        assertThat(uri).isNotNull();

        String currentDefaultSmsApp = getDefaultSmsApp();
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
        // In case there is no available default sms app, roleManager returns successful, without
        // changing default sms app from CTS SMS test app to user-selected default SMS app.
        String newDefaultSmsApp = getDefaultSmsApp();
        assumeFalse(newDefaultSmsApp.equals(currentDefaultSmsApp));

        {
            // selection
            Cursor cursor1 = mContentResolver.query(Telephony.Sms.CONTENT_URI,
                    null, "seen=(SELECT seen FROM sms LIMIT 1)", null, null);
            assertNull(cursor1);
            Cursor cursor2 = mContentResolver.query(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                    null, "seen=(SELECT seen FROM sms LIMIT 1)", null, null);
            assertNull(cursor2);
        }

        {
            // projection
            Cursor cursor1 = mContentResolver.query(Telephony.Sms.CONTENT_URI,
                    new String[] {"(SELECT seen from sms LIMIT 1) AS d"}, null, null, null);
            assertNull(cursor1);
            Cursor cursor2 = mContentResolver.query(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                    new String[] {"(SELECT seen from sms LIMIT 1) AS d"}, null, null, null);
            assertNull(cursor2);
        }

        {
            // sort order
            Cursor cursor1 = mContentResolver.query(Telephony.Sms.CONTENT_URI,
                    null, null, null,
                    "CASE (SELECT count(seen) FROM sms) WHEN 0 THEN 1 ELSE 0 END DESC");
            assertNull(cursor1);
            Cursor cursor2 = mContentResolver.query(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                    null, null, null,
                    "CASE (SELECT count(seen) FROM sms) WHEN 0 THEN 1 ELSE 0 END DESC");
            assertNull(cursor2);
        }

        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    /**
     * Verifies sql injection is not allowed within a URI.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.MmsSmsProvider#query")
    public void query_msgParameter_sqlInjection() {
        Uri uriWithSqlInjection = Uri.parse("content://mms-sms/pending?protocol=sms&message=1 "
                + "union select type,name,tbl_name,rootpage,sql,1,1,1,1,1 FROM SQLITE_MASTER; --");
        Cursor uriWithSqlInjectionCur = mContentResolver.query(uriWithSqlInjection, null,
                null, null, null);
        assertNull(uriWithSqlInjectionCur);
    }

    /**
     * Verifies query() returns non-null cursor when valid URI is passed to it.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.MmsSmsProvider#query")
    public void query_msgParameter_withoutSqlInjection() {
        Uri uriWithoutSqlInjection = Uri.parse("content://mms-sms/pending?protocol=sms&message=1");
        Cursor uriWithoutSqlInjectionCur = mContentResolver.query(uriWithoutSqlInjection,
                null, null, null, null);
        assertNotNull(uriWithoutSqlInjectionCur);
    }

    /** Verifies sql injection is not allowed within a URI. */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.MmsSmsProvider#query")
    public void query_threadIdParameter_sqlInjection() {
        Uri uriWithSqlInjection =
                Uri.parse(
                        "content://mms-sms/conversations?simple=true&thread_type=1 union select"
                                + " type,name,tbl_name,rootpage,sql FROM SQLITE_MASTER;; --");
        Cursor uriWithSqlInjectionCur = mContentResolver.query(uriWithSqlInjection,
                new String[]{"1","2","3","4","5"}, null, null, null);
        assertNull(uriWithSqlInjectionCur);
    }

    /**
     * Verifies query() returns non-null cursor when valid URI is passed to it.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.MmsSmsProvider#query")
    public void query_threadIdParameter_withoutSqlInjection() {
        Uri uriWithoutSqlInjection = Uri.parse(
                "content://mms-sms/conversations?simple=true&thread_type=1");
        Cursor uriWithoutSqlInjectionCur = mContentResolver.query(uriWithoutSqlInjection,
                new String[]{"1","2","3","4","5"}, null, null, null);
        assertNotNull(uriWithoutSqlInjectionCur);
    }

    /**
     * Verifies query() with conversations path and non-int threadId fails.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#query")
    public void query_threadIdParameter_invalidWithNonIntValue() {
        Uri uri1 = mSmsTestHelper
                .insertTestSmsWithThread(TEST_SMS_BODY, TEST_ADDRESS, TEST_THREAD_ID_1);
        assertThat(uri1).isNotNull();

        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations/4 garbage");
        Cursor cursor = mContentResolver.query(threadUri, null, null, null);
        assertNull(cursor);
    }

    /**
     * Verifies query() with conversations path and int threadId succeeds.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#query")
    public void query_threadIdParameter_validWithIntValue() {
        Uri uri1 = mSmsTestHelper
                .insertTestSmsWithThread(TEST_SMS_BODY, TEST_ADDRESS, TEST_THREAD_ID_1);
        assertThat(uri1).isNotNull();

        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations/" + TEST_THREAD_ID_1);
        Cursor cursor = mContentResolver.query(threadUri, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();

        int thread_id = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.THREAD_ID));
        assertThat(thread_id).isEqualTo(TEST_THREAD_ID_1);
    }

    /**
     * Verifies delete() with conversations path and non-int threadId fails.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#delete")
    public void delete_threadIdParameter_invalidWithNonIntValue() {
        Uri uri = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1);
        assertThat(uri).isNotNull();

        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations/3 garbage");
        assertThrows(IllegalArgumentException.class, () -> mContentResolver.delete(threadUri,
                null, null));
    }

    /**
     * Verifies delete() with conversations path and int threadId succeeds.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#delete")
    public void delete_threadIdParameter_validWithIntValue() {
        Uri uri = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1);
        assertThat(uri).isNotNull();

        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations/" + TEST_THREAD_ID_1);
        int deletedRows = mContentResolver.delete(threadUri, null, null);

        assertThat(deletedRows).isEqualTo(1);
    }

    /**
     * Verifies update() with conversations path and non-int threadId fails.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#update")
    public void update_threadIdParameter_invalidWithNonIntValue() {
        Uri uri = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1);
        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, uri, TEST_SMS_BODY);

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, TEST_ADDRESS);
        values.put(Telephony.Sms.BODY, "173 monster");

        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations/garbage");
        assertThrows(UnsupportedOperationException.class, () -> mContentResolver.update(threadUri,
                values, null));
    }

    /**
     * Verifies update() with conversations path and int threadId succeeds.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#update")
    public void update_threadIdParameter_validWithIntValue() {
        String testSmsBodyUpdate = "TEST_SMS_BODY_UPDATED";
        Uri uri = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1);
        assertThat(uri).isNotNull();

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, uri, TEST_SMS_BODY);

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.ADDRESS, TEST_ADDRESS);
        values.put(Telephony.Sms.BODY, testSmsBodyUpdate);

        Uri threadUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI,
                "conversations/" + TEST_THREAD_ID_1);
        mContentResolver.update(threadUri, values, null, null);

        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.BODY, threadUri, testSmsBodyUpdate);
    }


    /**
     * Verifies query() with threadID path and non-int threadId fails.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#query")
    public void query_threadIdUri_ignoresNonIntValue() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Telephony.CanonicalAddressesColumns.ADDRESS, "867-5309");
        Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/threadID");
        Uri uri1 = mContentResolver.insert(threadUri, contentValues);
        assertThat(uri1).isNotNull();

        Uri canonicalAddressUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI,
                "threadID/garbage");
        Cursor cursor = mContentResolver.query(canonicalAddressUri, null, null, null);
        assertThat(cursor).isNull();
    }

    /**
     * Verifies query() with threadID path and int threadId succeeds.
     */
    @Test
    @ApiTest(apis = "com.android.providers.telephony.SmsProvider#query")
    public void query_threadIdUri_validWithIntValue() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Telephony.CanonicalAddressesColumns.ADDRESS, "867-5309");
        Uri threadUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "threadID");
        Uri uri1 = mContentResolver.insert(threadUri, contentValues);
        assertThat(uri1).isNotNull();

        Uri canonicalAddressUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI,
                "threadID/" + TEST_THREAD_ID_1);
        Cursor cursor = mContentResolver.query(canonicalAddressUri, null, null, null);
        assertThat(cursor).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIMIT_RAW_TABLE_VISIBILITY)
    public void query_rawTable_cantQuery() {
        Uri rawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");
        try {
            stopBeingDefaultSmsApp();
            Cursor cursor = mContentResolver.query(rawUri, null, null, null);
            assertThat(cursor).isNull();
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_defaultSmsAppCanRead() {
        final String message = getSmsRetrieverOtpMessage();
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, System.currentTimeMillis(), CONTAINS_SMS_RETRIEVER_OTP, -1);
        assertSmsPresence(inserted, message, true);
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_roleHoldingAppCanRead() throws Exception {
        final String message = getSmsRetrieverOtpMessage();
        List<String> smsOtpReadingRoles =
                List.of(RoleManager.ROLE_ASSISTANT, RoleManager.ROLE_DIALER);
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS, message, System.currentTimeMillis(),
                        CONTAINS_SMS_RETRIEVER_OTP, -1);
        for (String roleName : smsOtpReadingRoles) {
            List<String> oldRoleHolders =
                    callWithShellPermissionIdentity(
                            () ->
                                    mRoleManager.getRoleHoldersAsUser(
                                            roleName, Process.myUserHandle()));
            try {
                addRoleHolder(roleName, mContext.getPackageName());
                assertSmsPresence(inserted, message, true);
            } finally {
                removeRoleHolder(roleName, mContext.getPackageName());
                for (String oldHolder : oldRoleHolders) {
                    addRoleHolder(roleName, oldHolder);
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_defaultSmsAppCantUpdate() {
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                getSmsRetrieverOtpMessage(), System.currentTimeMillis(),
                CONTAINS_SMS_RETRIEVER_OTP, -1);
        ContentValues values = new ContentValues();
        values.put(CONTAINS_OTP, OTP_TYPE_NONE);
        try {
            // Attempt to update the CONTAINS_OTP column, which will be removed on the back end
            mContentResolver.update(inserted, values, null, null);
            fail("Expected update call to throw IllegalArgumentException for empty ContentValues");
        } catch (IllegalArgumentException expected) {
            // Pass the test
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_standardAppCantRead() {
        final String message = getSmsRetrieverOtpMessage();
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS,
                        message,
                        System.currentTimeMillis(),
                        CONTAINS_SMS_RETRIEVER_OTP,
                        TEST_THREAD_ID_1);
        try {
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying directly, or by conversation ID
            assertSmsPresence(inserted, message, /* canRead */ false);
            Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations");
            assertSmsPresence(threadUri, message, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_retrieverHashOwningAppCanRead() {
        final String message = TEST_OTP_SMS_BODY + " " + getSelfSmsRetrieverHash();
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, System.currentTimeMillis(), CONTAINS_SMS_RETRIEVER_OTP, -1);
        try {
            stopBeingDefaultSmsApp();
            assertSmsPresence(inserted, message, true);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_standardAppCanReadAfterOtpHidingTimeExpires() {
        final String message = getSmsRetrieverOtpMessage();
        long expiredOtpHidingTime =
                System.currentTimeMillis() - OTP_HIDING_TIME_MS - TimeUnit.MINUTES.toMillis(1);
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, expiredOtpHidingTime, CONTAINS_SMS_RETRIEVER_OTP, -1);
        try {
            stopBeingDefaultSmsApp();
            assertSmsPresence(inserted, message, true);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_appWithReadSensitiveNotificationsCanRead() {
        final String message = getSmsRetrieverOtpMessage();
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, System.currentTimeMillis(), CONTAINS_SMS_RETRIEVER_OTP, -1);
        try {
            stopBeingDefaultSmsApp();
            SystemUtil.runWithShellPermissionIdentity(
                    () -> assertSmsPresence(inserted, message, true),
                    RECEIVE_SENSITIVE_NOTIFICATIONS);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_appWithCdmAssociationCanRead() {
        final String message = getSmsRetrieverOtpMessage();
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS, message, System.currentTimeMillis(),
                        CONTAINS_SMS_RETRIEVER_OTP, -1);
        associateCdm();
        try {
            stopBeingDefaultSmsApp();
            assertSmsPresence(inserted, message, true);
        } finally {
            disassociateCdm();
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_appWithCarrierPrivilegeCanRead() throws Exception {
        assumeTrue(
                "Skipping test: No valid default subscription ID found.",
                SubscriptionManager.isValidSubscriptionId(
                        SubscriptionManager.getDefaultSubscriptionId()));

        final String message = getSmsRetrieverOtpMessage();
        assumeTrue(
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS, message, System.currentTimeMillis(),
                        CONTAINS_SMS_RETRIEVER_OTP, -1);
        try {
            stopBeingDefaultSmsApp();
            CarrierPrivilegeUtils.withCarrierPrivileges(
                    getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> assertSmsPresence(inserted, message, true));
        } finally {
            disassociateCdm();
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_updatesFromOtpPending() {
        Uri inserted = mSmsTestHelper.insertTestSms(TEST_ADDRESS, getSmsRetrieverOtpMessage());
        SystemUtil.eventually(() -> assertSmsOtpColumn(inserted, CONTAINS_SMS_RETRIEVER_OTP));
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasDeviceOwner
    public void testOtpSms_standardAppCanRead_ifOwnedDevice() {
        final String message = getSmsRetrieverOtpMessage();
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS, message, System.currentTimeMillis(), OTP_TYPE_NONE, -1);
        assertSmsPresence(inserted, message, true);
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testOtpSms_otpFalsePositive() {
        Uri inserted = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_NOT_OTP_SMS_BODY);
        SystemUtil.eventually(() -> assertSmsOtpColumn(inserted, OTP_TYPE_NONE));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void insertUnrestrictedSms_verifyRestrictedBitIsUnset() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ false);

        mSmsTestHelper.assertSmsColumnEquals(ReadRestriction.RESTRICTED, inserted,
                String.valueOf(0));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void insertRestrictedSms_verifyRestrictedBitIsSet() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        mSmsTestHelper.assertSmsColumnEquals(ReadRestriction.RESTRICTED, inserted,
                String.valueOf(1));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void updateRestrictedSms_toUnrestricted_verifyRestrictionBitIsUnset() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        ContentValues values = new ContentValues();
        values.put(ReadRestriction.RESTRICTED, false);
        mContentResolver.update(inserted, values, null, null);

        mSmsTestHelper.assertSmsColumnEquals(ReadRestriction.RESTRICTED, inserted,
                String.valueOf(0));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void updateUnRestrictedSms_toRestricted_failsWithUnsupportedOperation() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ false);

        ContentValues values = new ContentValues();
        values.put(ReadRestriction.RESTRICTED, true);

        assertThrows(UnsupportedOperationException.class, () -> mContentResolver.update(
                inserted, values, null, null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void insertSms_messageRestricted_verifyRestrictionBitIsSet() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        mSmsTestHelper.assertSmsColumnEquals(ReadRestriction.RESTRICTED, inserted,
                String.valueOf(1));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void queryRestrictedSms_byNonDefaultSmsApp_returnsEmptyCursor() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        try {
            stopBeingDefaultSmsApp();
            assertSmsPresence(inserted, TEST_SMS_BODY, /* canRead= */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void queryRestrictedSms_byDefaultSmsApp_returnsTheMessage() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        assertSmsPresence(inserted, TEST_SMS_BODY, /* canRead= */ true);
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_WEB_OTP_SMS_API,
            FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testWebOtpSms_defaultSmsApp_handlesWebOtpSuccessfully() {
        // This test inserts a Web OTP message.
        // The TextClassifier should correctly classifies it as a Web OTP message.
        // Default SMS app is considered a trusted package.
        // Hence a Web OTP message should be visible to a default SMS app.
        final String message = getWebOtpMessage();
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, System.currentTimeMillis(), CONTAINS_WEB_OTP, -1);
        assertSmsPresence(inserted, message, true);
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_WEB_OTP_SMS_API,
            FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testWebOtpSms_standardApp_protectsWebOtpSuccessfully() {
        // This test inserts a Web OTP message.
        // The TextClassifier should correctly classifies it as a Web OTP message.
        // A standard app is considered not a trusted package by default (unless by exception).
        // Hence a Web OTP message should not be visible to a standard app.
        final String message = getWebOtpMessage();
        Uri inserted =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS,
                        message,
                        System.currentTimeMillis(),
                        CONTAINS_WEB_OTP,
                        TEST_THREAD_ID_1);
        try {
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying directly, or by conversation ID
            assertSmsPresence(inserted, message, /* canRead */ false);
            Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations");
            assertSmsPresence(threadUri, message, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testGenericOtpSms_defaultSmsApp_handlesGenericOtpSuccessfully() {
        // In this test, a Generic OTP message is inserted.
        // The TextClassifier should correctly classifies the message as a Generic OTP.
        // The default SMS app is considered a trusted package.
        // Hence, the Generic OTP should be visible to the trusted package.
        String message = getGenericOtpMessage();
        Uri inserted = mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(TEST_ADDRESS,
                message, System.currentTimeMillis(), CONTAINS_GENERIC_OTP, -1);
        assertSmsPresence(inserted, message, true);
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testGenericOtpSms_filterGenericOtpEnabled_protectsGenericOtpSuccessfully() {
        // If a standard app targets SDK 37 or above, generic OTP protection is strictly enabled.
        // This means, a standard app should not be able to view the generic OTP.
        try {
            runShellCommand("am compat enable --no-kill "
                    + FILTER_GENERIC_OTP_CHANGE_ID + " " + getContext().getPackageName());
            final String message = getGenericOtpMessage();
            Uri inserted =
                    mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                            TEST_ADDRESS,
                            message,
                            System.currentTimeMillis(),
                            CONTAINS_GENERIC_OTP,
                            TEST_THREAD_ID_1);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying directly, or by conversation ID
            assertSmsPresence(inserted, message, /* canRead */ false);
            Uri threadUri = Uri.parse(Telephony.Sms.CONTENT_URI + "/conversations");
            assertSmsPresence(threadUri, message, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
            runShellCommand("am compat reset --no-kill "
                    + FILTER_GENERIC_OTP_CHANGE_ID + " " + getContext().getPackageName());
        }
    }

    @Test
    @RequiresFlagsEnabled({
            FLAG_REDACT_OTP_SMS, FLAG_REDACT_OTP_SMS_API, FLAG_REDACT_OTP_APP_COMPAT_API})
    @EnsureHasNoDeviceOwner
    public void testGenericOtpSms_filterGenericOtpDisabled_handlesAppCompatSuccessfully() {
        // If a standard app does not target SDK 37 or above, the generic OTP protection is not
        // enabled for that app to ensure app compatibility. Hence, the app should still be able
        // to see the generic OTP message.
        try {
            runShellCommand("am compat disable --no-kill "
                    + FILTER_GENERIC_OTP_CHANGE_ID + " " + getContext().getPackageName());
            final String message = getGenericOtpMessage();
            Uri inserted =
                    mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                            TEST_ADDRESS,
                            message,
                            System.currentTimeMillis(),
                            CONTAINS_GENERIC_OTP,
                            TEST_THREAD_ID_1);
            stopBeingDefaultSmsApp();
            assertSmsPresence(inserted, message, /* canRead */ true);
        } finally {
            ensureDefaultSmsApp();
            runShellCommand("am compat reset --no-kill "
                    + FILTER_GENERIC_OTP_CHANGE_ID + " " + getContext().getPackageName());
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SECURE_ACCESS_TO_RESTRICTED_RCS_MESSAGES)
    public void querySms_byDefaultSmsApp_readRestrictionColumnIsHidden() {
        Uri inserted = mSmsTestHelper.insertTestSmsWithThread(TEST_ADDRESS, TEST_SMS_BODY,
                TEST_THREAD_ID_1, /* isRestricted= */ true);

        Cursor cursor = mContentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null);
        cursor.moveToNext();
        assertThat(cursor.getColumnIndex(ReadRestriction.READ_RESTRICTION_COLUMN_NAME))
                .isEqualTo(-1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_MESSAGE_PROMOTION)
    public void testInsertSms_withoutTransactionId() {
        Uri inserted = mSmsTestHelper.insertTestSms(TEST_ADDRESS, TEST_SMS_BODY);
        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.TRANSACTION_ID, inserted, null);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_MESSAGE_PROMOTION)
    public void testInsertSms_withTransactionId() {
        String transactionId = "TEST_SMS_TR_ID";
        Uri inserted =
                mSmsTestHelper.insertTestSmsWithTransactionId(
                        TEST_ADDRESS, TEST_SMS_BODY, transactionId);
        mSmsTestHelper.assertSmsColumnEquals(Telephony.Sms.TRANSACTION_ID, inserted, transactionId);
    }

    // Gets the retriever hash belong to itself
    private String getSelfSmsRetrieverHash() {
        Context context = getInstrumentation().getContext();
        Intent intent = new Intent("android.telephony.cts.action.SMS_RETRIEVED")
                .setPackage(context.getPackageName());
        PendingIntent pIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        return SmsManager.getDefault().createAppSpecificSmsTokenWithPackageInfo(
                "testprefix1,testprefix2", pIntent);
    }

    // Gets the retriever hash belong to a test app: APP_THAT_RETURNS_RETRIEVER_HASH
    private String getSmsRetrieverHash() {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mActivityManager.forceStopPackage(APP_THAT_RETURNS_RETRIEVER_HASH)
        );
        CompletableFuture<Bundle> callbackResult = new CompletableFuture<>();
        mContext.startActivity(new Intent()
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(new ComponentName(
                        APP_THAT_RETURNS_RETRIEVER_HASH,
                        APP_THAT_RETURNS_RETRIEVER_HASH + ".MainActivity"))
                .putExtra("callback", new RemoteCallback(callbackResult::complete)));
        Bundle bundle;
        try {
            bundle = callbackResult.get(200, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = bundle.getString("token");
        Assert.assertNotNull(bundle.getString("class"));
        Assert.assertTrue(bundle.getString("class").startsWith(APP_THAT_RETURNS_RETRIEVER_HASH));
        assertNotNull(token);
        return token;
    }

    private String getSmsRetrieverOtpMessage() {
        return TEST_OTP_SMS_BODY + " " + getSmsRetrieverHash();
    }

    private String getGenericOtpMessage() {
        return TEST_OTP_SMS_BODY;
    }

    private String getWebOtpMessage() {
        return TEST_OTP_SMS_BODY + TEST_WEB_OTP_SMS_SUFFIX;
    }

    private void assertSmsPresence(Uri uri, String smsBody, boolean canRead) {
        Cursor cursor = mContentResolver.query(uri, null, null, null);
        if (!canRead) {
            assertThat(cursor.getCount()).isEqualTo(0);
            return;
        }
        assertWithMessage("Expected to get a query result")
                .that(cursor.getCount())
                .isGreaterThan(0);

        while (cursor.moveToNext()) {
            String actualSmsBody = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
            if (canRead) {
                assertThat(actualSmsBody).isEqualTo(smsBody);
            } else {
                assertThat(actualSmsBody).isNotEqualTo(smsBody);
            }
        }
    }

    private void assertSmsOtpColumn(Uri uri, int expectedOtpColumn) {
        Cursor cursor = mContentResolver.query(uri, null, null, null);
        cursor.moveToNext();

        int actualSmsOtpColumn = cursor.getInt(cursor.getColumnIndex(CONTAINS_OTP));
        assertThat(actualSmsOtpColumn).isEqualTo(expectedOtpColumn);
    }

    private String getDefaultSmsApp() {
        String defaultSmsApp = "";
        try {
            defaultSmsApp = DefaultSmsAppHelper.getDefaultSmsApp(
                    getInstrumentation().getContext());
            logd("getDefaultSmsApp: defaultSmsApp=" + defaultSmsApp);
        } catch (Exception ex) {
            loge("Exception for DefaultSmsAppHelper.getDefaultSmsApp, ex=" + ex);
        }
        return defaultSmsApp;
    }

    private void associateCdm() {
        runShellCommand(
                "cmd companiondevice associate %s %s 00:00:00:00:00:AA",
                android.os.Process.myUserHandle().getIdentifier(), getContext().getPackageName());
    }

    private void disassociateCdm() {
        runShellCommand(
                "cmd companiondevice disassociate %s %s 00:00:00:00:00:AA",
                android.os.Process.myUserHandle().getIdentifier(), mContext.getPackageName());
    }

    @SuppressLint("MissingPermission")
    private void addRoleHolder(String roleName, String packageName) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        runWithShellPermissionIdentity(
                () ->
                        mRoleManager.addRoleHolderAsUser(
                                roleName,
                                packageName,
                                0,
                                android.os.Process.myUserHandle(),
                                getContext().getMainExecutor(),
                                success -> {
                                    assertTrue(
                                            "Failed to set role " + roleName + " to " + packageName,
                                            success);
                                    latch.countDown();
                                }));
        latch.await();
    }

    @SuppressLint("MissingPermission")
    private void removeRoleHolder(String roleName, String packageName) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        runWithShellPermissionIdentity(
                () ->
                        mRoleManager.removeRoleHolderAsUser(
                                roleName,
                                packageName,
                                0,
                                Process.myUserHandle(),
                                getContext().getMainExecutor(),
                                success -> {
                                    assertTrue(
                                            "Failed to remove role holder "
                                                    + packageName
                                                    + " from "
                                                    + roleName,
                                            success);
                                    latch.countDown();
                                }));
        latch.await();
    }

    private static void logd(String log) {
        Log.d(TAG, log);
    }

    private static void loge(String log) {
        Log.e(TAG, log);
    }
}
