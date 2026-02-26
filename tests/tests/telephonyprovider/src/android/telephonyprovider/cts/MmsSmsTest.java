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

package android.telephonyprovider.cts;

import static android.telephony.cts.util.DefaultSmsAppHelper.assumeMessaging;
import static android.telephony.cts.util.DefaultSmsAppHelper.assumeTelephony;
import static android.view.flags.Flags.FLAG_REDACT_OTP_APP_COMPAT_API;

import static com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS;
import static com.android.internal.telephony.flags.Flags.FLAG_REDACT_OTP_SMS_API;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.telephony.cts.util.DefaultSmsAppHelper;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(BedsteadJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN, codeName = "CinnamonBun")
@RequiresFlagsEnabled({
        FLAG_REDACT_OTP_SMS,
        FLAG_REDACT_OTP_SMS_API,
        FLAG_REDACT_OTP_APP_COMPAT_API
})
@EnsureHasNoDeviceOwner
public class MmsSmsTest {
    private static final String TEST_ADDRESS = "+19998880001";

    private SmsOtpTestHelper mSmsOtpTestHelper;
    private SmsTestHelper mSmsTestHelper;
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();
    private static final Context CONTEXT = INSTRUMENTATION.getContext();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeClass() {
        ensureDefaultSmsApp();
    }

    @AfterClass
    public static void afterClass() {
        cleanup();
        SmsTestHelper.changeSmsAppComponentsState(CONTEXT, true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    private static void cleanup() {
        ContentResolver contentResolver = CONTEXT.getContentResolver();
        contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null);
    }

    private static void ensureDefaultSmsApp() {
        SmsTestHelper.changeSmsAppComponentsState(CONTEXT, true);
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    private static void stopBeingDefaultSmsApp() {
        SmsTestHelper.changeSmsAppComponentsState(CONTEXT, false);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
        INSTRUMENTATION.waitForIdleSync();
    }

    @Before
    public void setupTestEnvironment() {
        assumeTelephony();
        assumeMessaging();
        cleanup();
        mSmsOtpTestHelper = new SmsOtpTestHelper();
        mSmsTestHelper = new SmsTestHelper();
    }

    private long insertOtpMessage(String message) {
        Uri uri =
                mSmsTestHelper.insertTestOtpSmsAndWaitForOtpDetection(
                        TEST_ADDRESS,
                        message,
                        System.currentTimeMillis(),
                        SmsOtpTestHelper.CONTAINS_SMS_RETRIEVER_OTP,
                        -1);
        return getThreadId(uri);
    }

    private long getThreadId(Uri uri) {
        try (Cursor cursor =
                CONTEXT.getContentResolver()
                        .query(uri, new String[] {Telephony.Sms.THREAD_ID}, null, null, null)) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.moveToFirst()).isTrue();
            return cursor.getLong(0);
        }
    }

    @Test
    public void testOtpSms_standardAppCantRead_mmsSmsConversation() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        insertOtpMessage(message);
        try {
            Uri mmsSmsConversationsUri = Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
            mSmsOtpTestHelper.assertSmsPresence(mmsSmsConversationsUri, /* canRead */ true);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying by mms-sms conversation
            mSmsOtpTestHelper.assertSmsPresence(mmsSmsConversationsUri, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_standardAppCantRead_mmsSmsConversationByThreadId() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        final long threadId = insertOtpMessage(message);
        try {
            Uri mmsSmsConversationUri =
                    ContentUris.withAppendedId(
                            Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, threadId);
            mSmsOtpTestHelper.assertSmsPresence(mmsSmsConversationUri, /* canRead */ true);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying by mms-sms conversation
            mSmsOtpTestHelper.assertSmsPresence(mmsSmsConversationUri, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_standardAppCantRead_mmsSmsCompleteConversation() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        insertOtpMessage(message);
        try {
            Uri completeConversation = Uri.parse("content://mms-sms/complete-conversations");
            mSmsOtpTestHelper.assertSmsPresence(completeConversation, /* canRead */ true);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying by mms-sms conversation
            mSmsOtpTestHelper.assertSmsPresence(completeConversation, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_standardAppCantRead_mmsSmsMessagesByPhone() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        insertOtpMessage(message);
        try {
            Uri uri =
                    Uri.withAppendedPath(Telephony.MmsSms.CONTENT_FILTER_BYPHONE_URI, TEST_ADDRESS);
            mSmsOtpTestHelper.assertSmsPresence(uri, /* canRead */ true);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying by mms-sms conversation
            mSmsOtpTestHelper.assertSmsPresence(uri, /* canRead */ false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_standardAppCantRead_mmsSmsSearch() {
        final String testWord = "xyz";
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage() + " " + testWord;
        insertOtpMessage(message);
        try {
            Uri uri = Telephony.MmsSms.SEARCH_URI.buildUpon().appendQueryParameter("pattern",
                    testWord).build();
            mSmsOtpTestHelper.assertSmsPresenceWithoutProjection(uri, true);
            stopBeingDefaultSmsApp();
            // Message should be inaccessible when querying by mms-sms search
            mSmsOtpTestHelper.assertSmsPresenceWithoutProjection(uri, false);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_snippetRedacted_mmsSmsSimpleConversation() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        insertOtpMessage(message);
        try {
            Uri uri =
                    Telephony.MmsSms.CONTENT_CONVERSATIONS_URI
                            .buildUpon()
                            .appendQueryParameter("simple", "true")
                            .build();
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ false);
            stopBeingDefaultSmsApp();
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ true);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_snippetRedacted_mmsSmsConversationRecipients() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        final long threadId = insertOtpMessage(message);
        try {
            Uri uri = Uri.parse("content://mms-sms/conversations/" + threadId + "/recipients");
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ false);
            stopBeingDefaultSmsApp();
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ true);
        } finally {
            ensureDefaultSmsApp();
        }
    }

    @Test
    public void testOtpSms_snippetRedacted_mmsSmsConversationSubject() {
        final String message = mSmsOtpTestHelper.getSmsRetrieverOtpMessage();
        final long threadId = insertOtpMessage(message);
        try {
            Uri uri = Uri.parse("content://mms-sms/conversations/" + threadId + "/subject");
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ false);
            stopBeingDefaultSmsApp();
            mSmsOtpTestHelper.assertSnippetRedacted(uri, message, /* isRedacted */ true);
        } finally {
            ensureDefaultSmsApp();
        }
    }
}
