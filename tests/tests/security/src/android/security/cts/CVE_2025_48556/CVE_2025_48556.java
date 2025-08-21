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

package android.security.cts.CVE_2025_48556;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import com.google.common.base.Strings;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48556 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 419014146)
    public void testPocCVE_2025_48556() {
        try {
            final NotificationChannel notificationChannel =
                    new NotificationChannel(
                            "cve_2025_48556_id",
                            "cve_2025_48556_name",
                            NotificationManager.IMPORTANCE_DEFAULT);

            // Try to set the 'ID' field with a text greater than 'MAX_TEXT_LENGTH'.
            final String channelId = Strings.repeat("A", NotificationChannel.MAX_TEXT_LENGTH * 2);
            notificationChannel.setId(channelId);

            // Try to set the 'Parent ID' and the 'Conversation ID' fields with a text greater than
            // 'MAX_TEXT_LENGTH'.
            final String channelParentId =
                    Strings.repeat("B", NotificationChannel.MAX_TEXT_LENGTH * 2);
            final String channelConversationId =
                    Strings.repeat("C", NotificationChannel.MAX_TEXT_LENGTH * 2);
            notificationChannel.setConversationId(channelParentId, channelConversationId);

            // Check if the 'mId', 'mParentId' and 'mConversationId' fields were set correctly.
            final String fetchedChannelId = notificationChannel.getId();
            final String fetchedChannelParentId = notificationChannel.getParentChannelId();
            final String fetchedChannelConversationId = notificationChannel.getConversationId();
            final Boolean validAssumeChecks =
                    fetchedChannelId.equals(
                                    channelId.substring(
                                            0 /* start index */, fetchedChannelId.length()))
                            && fetchedChannelParentId.equals(
                                    channelParentId.substring(
                                            0 /* start index */, fetchedChannelParentId.length()))
                            && fetchedChannelConversationId.equals(
                                    channelConversationId.substring(
                                            0 /* start index */,
                                            fetchedChannelConversationId.length()));
            assume().withMessage("Could not set the fields of NotificationChannel!!")
                    .that(validAssumeChecks)
                    .isTrue();

            // Fail the test if the IDs of the 'NotificationChannel' were not trimmed.
            final Boolean isDUTVulnerable =
                    (fetchedChannelId.equals(channelId)
                            && fetchedChannelParentId.equals(channelParentId)
                            && fetchedChannelConversationId.equals(channelConversationId));
            assertWithMessage(
                            "The DUT is vulnerable to b/419014146!! A malicious app can affect"
                                    + " subsequent writes to notification_policy.xml in the disk"
                                    + " leading to desynchronization from persistence!!!")
                    .that(isDUTVulnerable)
                    .isFalse();
        } catch (Exception exception) {
            assume().that(exception).isNull();
        }
    }
}
