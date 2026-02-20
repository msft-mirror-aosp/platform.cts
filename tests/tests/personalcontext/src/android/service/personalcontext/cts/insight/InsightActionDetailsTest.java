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

package insight;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.InsightActionDetails;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:InsightActionDetailsTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightActionDetailsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.InsightActionDetails.Builder#build",
                "android.service.personalcontext.insight.InsightActionDetails.Builder"
                        + "#setIntent",
                "android.service.personalcontext.insight.InsightActionDetails#hasActionType",
                "android.service.personalcontext.insight.InsightActionDetails#getActionTypes",
            })
    @Test
    public void testCreateActionDetailsWithIntent() {
        final Intent intent = new Intent();
        final InsightActionDetails details =
                new InsightActionDetails.Builder().setIntent(intent).build();
        assertThat(details.getIntent()).isEqualTo(intent);
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_INTENT)).isTrue();
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION)).isFalse();
        assertThat(details.getActionTypes()).isEqualTo(InsightActionDetails.ACTION_TYPE_INTENT);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.InsightActionDetails.Builder#build",
                "android.service.personalcontext.insight.InsightActionDetails.Builder"
                        + "#setIntent",
                "android.service.personalcontext.insight.InsightActionDetails#hasActionType",
                "android.service.personalcontext.insight.InsightActionDetails#getActionTypes",
            })
    @Test
    public void testCreateActionDetailsWithRemoteAction() {
        final RemoteAction action = createTestRemoteAction();
        final InsightActionDetails details =
                new InsightActionDetails.Builder().setRemoteAction(action).build();
        assertThat(details.getRemoteAction()).isEqualTo(action);
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_INTENT)).isFalse();
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION)).isTrue();
        assertThat(details.getActionTypes())
                .isEqualTo(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.InsightActionDetails.Builder#build",
                "android.service.personalcontext.insight.InsightActionDetails.Builder"
                        + "#setIntent",
                "android.service.personalcontext.insight.InsightActionDetails#hasActionType",
                "android.service.personalcontext.insight.InsightActionDetails#getActionTypes",
            })
    @Test
    public void testCreateActionDetailsWithIntentAndRemoteAction() {
        final Intent intent = new Intent();
        final RemoteAction action = createTestRemoteAction();
        final InsightActionDetails details =
                new InsightActionDetails.Builder()
                        .setIntent(intent)
                        .setRemoteAction(action)
                        .build();
        assertThat(details.getIntent()).isEqualTo(intent);
        assertThat(details.getRemoteAction()).isEqualTo(action);
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_INTENT)).isTrue();
        assertThat(details.hasActionType(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION)).isTrue();
        assertThat(details.getActionTypes())
                .isEqualTo(
                        InsightActionDetails.ACTION_TYPE_INTENT
                                + InsightActionDetails.ACTION_TYPE_REMOTE_ACTION);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.InsightActionDetails.Builder#build",
            })
    @Test
    public void testNoActionSpecifiedThrowsException() {
        assertThrows(IllegalStateException.class, () -> new InsightActionDetails.Builder().build());
    }

    private RemoteAction createTestRemoteAction() {
        final Icon icon = Icon.createWithContentUri("content://test");
        final String title = "title";
        final String description = "description";
        final PendingIntent action =
                PendingIntent.getBroadcast(
                        InstrumentationRegistry.getTargetContext(),
                        0,
                        new Intent("TESTACTION"),
                        PendingIntent.FLAG_IMMUTABLE);
        return new RemoteAction(icon, title, description, action);
    }
}
