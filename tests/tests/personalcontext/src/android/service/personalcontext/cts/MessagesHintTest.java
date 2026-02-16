/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ChatMessageData;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.MessagesHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Build/Install/Run: atest CtsPersonalContextTestCases:MessagesHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class MessagesHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PACKAGE_NAME = "test_package";
    private static final Instant REFERENCE_TIME = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final ChatMessageData CHAT_MESSAGE_DATA =
            new ChatMessageData.Builder()
                    .setOutgoingMessage(true)
                    .setText("text")
                    .setAuthor("author")
                    .setReferenceTime(REFERENCE_TIME)
                    .build();
    private static final ChatMessageData CHAT_MESSAGE_DATA_2 =
            new ChatMessageData.Builder()
                    .setOutgoingMessage(false)
                    .setText("text2")
                    .setAuthor("author2")
                    .setReferenceTime(REFERENCE_TIME)
                    .build();

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.MessagesHint.Builder#Builder",
                "android.service.personalcontext.hint.MessagesHint.Builder#build",
                "android.service.personalcontext.hint.MessagesHint.Builder#setChatMessages",
                "android.service.personalcontext.hint.MessagesHint#getPackageName",
                "android.service.personalcontext.hint.MessagesHint#getChatMessages",
                "android.service.personalcontext.hint.MessagesHint#toBundle",
                "android.service.personalcontext.hint.MessagesHint#equals",
                "android.service.personalcontext.hint.MessagesHint#toString",
            })
    @Test
    public void testMessagesHint_bundleUnbundle() {
        final MessagesHint hint =
                new MessagesHint.Builder(PACKAGE_NAME)
                        .setChatMessages(List.of(CHAT_MESSAGE_DATA, CHAT_MESSAGE_DATA_2))
                        .build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(MessagesHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());

        final MessagesHint outputMessagesHint = (MessagesHint) outputHint;
        assertThat(outputMessagesHint).isEqualTo(hint);
        assertThat(outputMessagesHint.hashCode()).isEqualTo(hint.hashCode());
        assertThat(outputMessagesHint.toString()).isEqualTo(hint.toString());

        assertThat(outputMessagesHint.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(outputMessagesHint.getChatMessages())
                .containsExactly(CHAT_MESSAGE_DATA, CHAT_MESSAGE_DATA_2);
    }

    @Test
    public void testMessagesHint_builder() {
        // Missing package name.
        assertThrows(NullPointerException.class, () -> new MessagesHint.Builder(null));

        // No chat messages provided.
        assertThrows(
                IllegalArgumentException.class,
                () -> new MessagesHint.Builder(PACKAGE_NAME).build());

        // Empty list provided.
        assertThrows(
                IllegalArgumentException.class,
                () -> new MessagesHint.Builder(PACKAGE_NAME).setChatMessages(List.of()).build());
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
