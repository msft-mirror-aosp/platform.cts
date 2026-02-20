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

import static android.provider.Telephony.Sms.CONTAINS_OTP;
import static android.provider.Telephony.Sms.OTP_SUBTYPE_SMS_RETRIEVER_OTP;
import static android.provider.Telephony.Sms.OTP_SUBTYPE_WEB_OTP;
import static android.provider.Telephony.Sms.OTP_TYPE_CONTAINS_OTP;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.provider.Telephony;
import android.telephony.SmsManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SmsOtpTestHelper {
    // Note that this domain does not actually exist, hence cannot be auto-verified.
    public static final String TEST_DOMAIN = "telephony.cts.test.domain";
    private static final String TEST_WEB_OTP_SMS_SUFFIX = "\n\n@" + TEST_DOMAIN + " #12345";
    public static final String TEST_OTP_SMS_BODY = "Your one time code is 12345";
    public static final String TEST_NOT_OTP_SMS_BODY = "Your account number is 12345";

    public static final int CONTAINS_SMS_RETRIEVER_OTP =
            OTP_TYPE_CONTAINS_OTP | OTP_SUBTYPE_SMS_RETRIEVER_OTP;
    public static final int CONTAINS_WEB_OTP = OTP_TYPE_CONTAINS_OTP | OTP_SUBTYPE_WEB_OTP;
    public static final int CONTAINS_GENERIC_OTP = OTP_TYPE_CONTAINS_OTP;

    private static final String APP_THAT_RETURNS_RETRIEVER_HASH =
            "android.telephony.cts.smsretriever";

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final ActivityManager mActivityManager;

    public SmsOtpTestHelper() {
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
    }

    /** Gets the retriever hash belong to itself */
    public String getSelfSmsRetrieverHash() {
        Intent intent =
                new Intent("android.telephony.cts.action.SMS_RETRIEVED")
                        .setPackage(mContext.getPackageName());
        PendingIntent pIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        intent,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        return SmsManager.getDefault()
                .createAppSpecificSmsTokenWithPackageInfo("testprefix1,testprefix2", pIntent);
    }

    /** Gets the retriever hash belong to a test app: APP_THAT_RETURNS_RETRIEVER_HASH */
    public String getSmsRetrieverHash() {
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_THAT_RETURNS_RETRIEVER_HASH));
        CompletableFuture<Bundle> callbackResult = new CompletableFuture<>();
        mContext.startActivity(
                new Intent()
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setComponent(
                                new ComponentName(
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
        assertNotNull(bundle.getString("class"));
        assertThat(bundle.getString("class")).startsWith(APP_THAT_RETURNS_RETRIEVER_HASH);
        assertNotNull(token);
        return token;
    }

    public String getSmsRetrieverOtpMessage() {
        return TEST_OTP_SMS_BODY + " " + getSmsRetrieverHash();
    }

    public String getGenericOtpMessage() {
        return TEST_OTP_SMS_BODY;
    }

    public String getWebOtpMessage() {
        return TEST_OTP_SMS_BODY + TEST_WEB_OTP_SMS_SUFFIX;
    }

    /** Asserts that the SMS is present in the given URI with the expected body. */
    public void assertSmsPresence(Uri uri, String smsBody, boolean canRead) {
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
            assertThat(actualSmsBody).isEqualTo(smsBody);
        }
    }

    /** Asserts that the SMS is present in the given URI. */
    public void assertSmsPresence(Uri uri, boolean canRead) {
        Cursor cursor = mContentResolver.query(uri, new String[] {Telephony.Sms._ID}, null, null);
        int expectedCount = canRead ? 1 : 0;
        assertThat(cursor.getCount()).isEqualTo(expectedCount);
    }

    /** Asserts that the SMS is present in the given URI without projection. */
    public void assertSmsPresenceWithoutProjection(Uri uri, boolean canRead) {
        Cursor cursor = mContentResolver.query(uri, null, null, null);
        int expectedCount = canRead ? 1 : 0;
        assertThat(cursor.getCount()).isEqualTo(expectedCount);
    }

    /** Asserts that the SMS OTP column matches the expected value. */
    public void assertSmsOtpColumn(Uri uri, int expectedOtpColumn) {
        Cursor cursor = mContentResolver.query(uri, null, null, null);
        cursor.moveToNext();

        int actualSmsOtpColumn = cursor.getInt(cursor.getColumnIndex(CONTAINS_OTP));
        assertThat(actualSmsOtpColumn).isEqualTo(expectedOtpColumn);
    }
}
