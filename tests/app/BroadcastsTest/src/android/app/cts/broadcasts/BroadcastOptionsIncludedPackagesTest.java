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

import static com.android.app.cts.broadcasts.Common.ACTION_QUERY_PACKAGE_NAME;
import static com.android.app.cts.broadcasts.Common.EXTRA_PACKAGE_NAMES;

import static com.google.common.truth.Truth.assertThat;

import android.app.BroadcastOptions;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.app.cts.broadcasts.ICommandReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(BroadcastsTestRunner.class)
public class BroadcastOptionsIncludedPackagesTest extends BaseBroadcastTest {
    @Test
    public void testSetGetIncludedPackages() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        assertThat(options.getIncludedPackages()).isNull();

        final String[] helperPackages = {HELPER_PKG1, HELPER_PKG2};
        options.setIncludedPackages(helperPackages);
        assertThat(options.getIncludedPackages()).isEqualTo(helperPackages);
    }

    @Test
    public void testManifestReceivers() throws Exception {
        final Intent queryPackageIntent =
                new Intent(ACTION_QUERY_PACKAGE_NAME)
                        .addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        {
            final ResultReceiver resultReceiver = new ResultReceiver();
            mContext.sendOrderedBroadcast(
                    queryPackageIntent,
                    null /* receiverPermission */,
                    resultReceiver,
                    null /* scheduler */,
                    0 /* initialCode */,
                    null /* initialData */,
                    null /* initialExtras */);
            waitForBroadcastBarrier();
            assertThat(resultReceiver.getStringArrayListExtra(EXTRA_PACKAGE_NAMES))
                    .containsExactly(HELPER_PKG1, HELPER_PKG2);
        }
        {
            final ResultReceiver resultReceiver = new ResultReceiver();
            final BroadcastOptions options =
                    BroadcastOptions.makeBasic().setIncludedPackages(new String[] {HELPER_PKG1});
            mContext.sendOrderedBroadcast(
                    queryPackageIntent,
                    null /* receiverPermission */,
                    options.toBundle(),
                    resultReceiver,
                    null /* scheduler */,
                    0 /* initialCode */,
                    null /* initialData */,
                    null /* initialExtras */);
            waitForBroadcastBarrier();
            assertThat(resultReceiver.getStringArrayListExtra(EXTRA_PACKAGE_NAMES))
                    .containsExactly(HELPER_PKG1);
        }
    }

    @Test
    public void testRegisteredReceivers() throws Exception {
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(ACTION_QUERY_PACKAGE_NAME);
            for (ICommandReceiver cmdReceiver :
                    new ICommandReceiver[] {cmdReceiver1, cmdReceiver2}) {
                cmdReceiver.clearCookie(ACTION_QUERY_PACKAGE_NAME);
                cmdReceiver.monitorBroadcasts(filter, ACTION_QUERY_PACKAGE_NAME);
            }
            final Intent queryPackageIntent =
                    new Intent(ACTION_QUERY_PACKAGE_NAME)
                            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            {
                getContext().sendBroadcast(queryPackageIntent);
                verifyReceivedBroadcasts(
                        () -> cmdReceiver1.getReceivedBroadcasts(ACTION_QUERY_PACKAGE_NAME),
                        List.of(queryPackageIntent),
                        true);
                verifyReceivedBroadcasts(
                        () -> cmdReceiver2.getReceivedBroadcasts(ACTION_QUERY_PACKAGE_NAME),
                        List.of(queryPackageIntent),
                        true);
            }
            {
                cmdReceiver1.clearCookie(ACTION_QUERY_PACKAGE_NAME);
                cmdReceiver2.clearCookie(ACTION_QUERY_PACKAGE_NAME);
                final BroadcastOptions options =
                        BroadcastOptions.makeBasic()
                                .setIncludedPackages(new String[] {HELPER_PKG1});
                getContext()
                        .sendBroadcast(
                                queryPackageIntent,
                                null /* receiverPermission */,
                                options.toBundle());
                verifyReceivedBroadcasts(
                        () -> cmdReceiver1.getReceivedBroadcasts(ACTION_QUERY_PACKAGE_NAME),
                        List.of(queryPackageIntent),
                        true);
                verifyReceivedBroadcasts(
                        () -> cmdReceiver2.getReceivedBroadcasts(ACTION_QUERY_PACKAGE_NAME),
                        Collections.emptyList(),
                        true);
            }
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }
}
