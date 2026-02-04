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

package android.app.cts.broadcasts;

import static com.google.common.truth.Truth.assertThat;

import android.app.ApplicationExitInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.DeadObjectException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.app.cts.broadcasts.Common;
import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BroadcastsTestRunner.class)
public class ExcessiveBroadcastsTest extends BaseBroadcastTest {
    /**
     * The amount of time in milliseconds that the broadcast receiver in the helper app should wait
     * before finishing. This is used to ensure that broadcasts remain in the pending queue for the
     * duration of the test.
     */
    private static final int BROADCAST_RECEIVER_WAIT_MS = 10_000;

    /** The number of initial broadcasts to send to ensure the receiver stays busy. */
    private static final int BLOCKING_BROADCASTS_COUNT = 10;

    /** Constant for querying process exit reasons for any PID. */
    private static final int ANY_PID = 0;

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @RequiresFlagsEnabled(Flags.FLAG_LIMIT_PENDING_BROADCASTS_PER_SENDER_UID)
    @Test
    public void testEnqueueExcessiveBroadcasts_senderTerminated() throws Exception {
        clearAppExitInfos(HELPER_PKG1);
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();
            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            // Send a broadcast that will cause the receiver to wait, blocking the queue.
            final Intent intent =
                    new Intent(Common.ACTION_WAIT_BROADCAST)
                            .setPackage(HELPER_PKG2)
                            .putExtra(Common.EXTRA_WAIT_PERIOD_MS, BROADCAST_RECEIVER_WAIT_MS);
            for (int i = 0; i < BLOCKING_BROADCASTS_COUNT; ++i) {
                cmdReceiver1.sendBroadcast(intent, null /* options */);
            }

            final int maxPendingBroadcastsPerSenderUid =
                    Integer.parseInt(
                            getBroadcastConstant(KEY_MAX_PENDING_BROADCASTS_PER_SENDER_UID));
            // Fill the broadcast queue up to the limit.
            for (int i = 0; i <= maxPendingBroadcastsPerSenderUid; ++i) {
                // Once the pending broadcasts hit the limit, it will cause the sender app
                // (HELPER_PKG1) to be killed, resulting in a DeadObjectException.
                try {
                    cmdReceiver1.sendBroadcast(new Intent(TEST_ACTION1), null /* options */);
                } catch (DeadObjectException e) {
                    // Expected; ignore
                }
            }

            final List<ApplicationExitInfo> appExitInfos =
                    SystemUtil.runWithShellPermissionIdentity(
                            () ->
                                    mAm.getHistoricalProcessExitReasons(
                                            HELPER_PKG1, ANY_PID, 1 /* maxNum */));
            assertThat(appExitInfos).hasSize(1);
            assertThat(appExitInfos.get(0).getReason()).isEqualTo(ApplicationExitInfo.REASON_CRASH);
            assertThat(appExitInfos.get(0).getSubReason())
                    .isEqualTo(ApplicationExitInfo.SUBREASON_EXCESSIVE_ENQUEUED_BROADCASTS_COUNT);

            // Verify that none of the enqueued broadcasts after the initial "wait" broadcast
            // were delivered, as they should be discarded as part of the treatment of the app
            // exceeding the broadcast enqueue limit
            assertThat(cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1)).isEmpty();
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }
}
