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

package android.security.cts.CVE_2025_48546;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48546 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 388029380)
    public void testPocCVE_2025_48546() {
        try {
            // Register broadcast-receiver to receive broadcast messages.
            final Context context = getApplicationContext();
            final CompletableFuture<Boolean> vulnerableStatus = new CompletableFuture<Boolean>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                if (intent.hasExtra("vulnerableStatus")) {
                                    vulnerableStatus.complete(
                                            intent.getBooleanExtra("vulnerableStatus", false));
                                }
                            } catch (Exception e) {
                                Log.e(
                                        "CVE_2025_48546",
                                        "Exception occurred in setting broadcast receiver: "
                                                + e.getMessage());
                            }
                        }
                    } /* receiver */,
                    new IntentFilter("CVE_2025_48546_action") /* filter */,
                    Context.RECEIVER_EXPORTED /* flags */);

            // Start 'HijackActivity'. Without fix, the 'HijackActivity' creates a pinned,
            // non-draggable PiP window allowing the app in the background to get foreground
            // privileges. With the fix, the pinned PiP cannot be launched, and a
            // 'SecurityException' is thrown when an attempt is made to launch it."
            context.startActivity(
                    new Intent(context, HijackActivity.class)
                            .setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));

            // Check if the broadcast message was received or not.
            assume().withMessage("Failed to receive the vulnerable status via broadcast!!")
                    .that(
                            poll(
                                    () -> vulnerableStatus.isDone(),
                                    5_000L /* pollingTime */,
                                    10_000L /* maxPollingTime */))
                    .isTrue();

            // Fail the test if the DUT is vulnerable.
            assertWithMessage(
                            "Device is vulnerable to b/388029380!! A non-draggable PiP window can"
                                    + " be created giving the app foreground privileges!!")
                    .that(vulnerableStatus.getNow(false /* valueIfAbsent */))
                    .isFalse();
        } catch (Exception exception) {
            assume().that(exception).isNull();
        }
    }
}
