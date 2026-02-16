/*
 * Copyright 2025 The Android Open Source Project
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

import android.app.assist.ActivityId;
import android.content.ComponentName;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ChatMessageContentCaptureData;
import android.service.personalcontext.hint.ChatMessageData;
import android.service.personalcontext.hint.ContentCaptureConversationEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationEnterEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationExitEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationProcessingEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationUpdateEvent;
import android.service.personalcontext.hint.ContentCaptureConversationHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ConversationData;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Build/Install/Run: atest CtsPersonalContextTestCases:ContentCaptureConversationHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ContentCaptureConversationHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String CONVERSATION_SESSION_ID = "session_id";
    private static final String CONTENT_DESCRIPTION = "content description";
    private Instant mReferenceTime;
    private Instant mClientEventTimestamp;
    private ChatMessageData mChatMessageData;

    @Before
    public void setUp() {
        mReferenceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        mClientEventTimestamp = mReferenceTime.minusSeconds(1);
        mChatMessageData =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(mReferenceTime)
                        .build();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#ConversationEnterEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder"
                        + "#Builder",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toBundle",
                "android.service.personalcontext.hint.ContentCaptureConversationHint"
                        + "#getConversationEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + "#getClientEventTimestamp",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#getConversationSessionId",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#getConversationEnterTimestamp",
            })
    @Test
    public void testConversationHint_enterEvent_bundleUnbundle() {
        final Instant enterTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, enterTimestamp);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(enterEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationEnterEvent.class);

        final ConversationEnterEvent outputEnterEvent = (ConversationEnterEvent) outputEvent;
        assertThat(outputEnterEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputEnterEvent.getTimestamp()).isEqualTo(enterTimestamp);
        assertThat(outputEnterEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationHint#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toString",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationEnterEvent#toString",
            })
    @Test
    public void testConversationHint_enterEvent_equalsHashCodeToString() {
        final Instant enterTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, enterTimestamp);
        final ConversationEnterEvent enterEvent2 =
                new ConversationEnterEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, enterTimestamp);
        assertThat(enterEvent).isEqualTo(enterEvent2);
        assertThat(enterEvent.hashCode()).isEqualTo(enterEvent2.hashCode());
        assertThat(enterEvent.toString()).isNotNull();

        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(enterEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationExitEvent#ConversationExitEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder"
                        + "#Builder",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toBundle",
                "android.service.personalcontext.hint.ContentCaptureConversationHint"
                        + "#getConversationEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + "#getClientEventTimestamp",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationExitEvent#getConversationSessionId"
            })
    @Test
    public void testConversationHint_exitEvent_bundleUnbundle() {
        final Instant eventTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, eventTimestamp);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(exitEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationExitEvent.class);

        final ConversationExitEvent outputExitEvent = (ConversationExitEvent) outputEvent;
        assertThat(outputExitEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputExitEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationHint#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toString",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationExitEvent#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationExitEvent#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationExitEvent#toString",
            })
    @Test
    public void testConversationHint_exitEvent_equalsHashCodeToString() {
        final Instant eventTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, eventTimestamp);
        final ConversationExitEvent exitEvent2 =
                new ConversationExitEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, eventTimestamp);
        assertThat(exitEvent).isEqualTo(exitEvent2);
        assertThat(exitEvent.hashCode()).isEqualTo(exitEvent2.hashCode());
        assertThat(exitEvent.toString()).isNotNull();

        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(exitEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#ConversationProcessingEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder"
                        + "#Builder",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toBundle",
                "android.service.personalcontext.hint.ContentCaptureConversationHint"
                        + "#getConversationEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + "#getClientEventTimestamp",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#getStartProcessingTimestamp",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#getMessageAutofillId",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#getConversationSessionId",
            })
    @Test
    public void testConversationHint_processingEvent_bundleUnbundle() {
        final Instant processingTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(processingEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationProcessingEvent.class);

        final ConversationProcessingEvent outputProcessingEvent =
                (ConversationProcessingEvent) outputEvent;
        assertThat(outputProcessingEvent.getStartProcessingTimestamp())
                .isEqualTo(processingTimestamp);
        assertThat(outputProcessingEvent.getMessageAutofillId()).isEqualTo(messageAutofillId);
        assertThat(outputProcessingEvent.getConversationSessionId())
                .isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputProcessingEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationHint#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toString",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationProcessingEvent#toString",
            })
    @Test
    public void testConversationHint_processingEvent_equalsHashCodeToString() {
        final Instant processingTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ConversationProcessingEvent processingEvent2 =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        assertThat(processingEvent).isEqualTo(processingEvent2);
        assertThat(processingEvent.hashCode()).isEqualTo(processingEvent2.hashCode());
        assertThat(processingEvent.toString()).isNotNull();

        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(processingEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#ConversationUpdateEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder"
                        + "#Builder",
                "android.service.personalcontext.hint.ContentCaptureConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toBundle",
                "android.service.personalcontext.hint.ContentCaptureConversationHint"
                        + "#getConversationEvent",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + "#getClientEventTimestamp",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#getConversationData",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#getConversationSessionId",
                "android.service.personalcontext.hint.ConversationData#hasNewMessage",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setHasNewMessage",
            })
    @Test
    public void testConversationHint_updateEvent_bundleUnbundle() {
        final ActivityId activityId = new ActivityId(1, null);
        final Instant processingStartTimestamp = mReferenceTime;
        final Instant processingEndTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(List.of(mChatMessageData))
                        .setActivityId(activityId)
                        .setHasNewMessage(true)
                        .build();
        final Instant updateTimestamp = mReferenceTime;
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        updateTimestamp,
                        conversationData);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(updateEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationUpdateEvent.class);

        final ConversationUpdateEvent outputUpdateEvent = (ConversationUpdateEvent) outputEvent;
        assertThat(outputUpdateEvent.getConversationData()).isEqualTo(conversationData);
        assertThat(outputUpdateEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputUpdateEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
        assertThat(outputUpdateEvent.getConversationData().hasNewMessage()).isTrue();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContentCaptureConversationHint#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationHint#toString",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#equals",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#hashCode",
                "android.service.personalcontext.hint.ContentCaptureConversationEvent"
                        + ".ConversationUpdateEvent#toString",
                "android.service.personalcontext.hint.ConversationData#hasNewMessage",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setHasNewMessage",
            })
    @Test
    public void testConversationHint_updateEvent_equalsHashCodeToString() {
        final ActivityId activityId = new ActivityId(1, null);
        final Instant processingStartTimestamp = mReferenceTime;
        final Instant processingEndTimestamp = mReferenceTime;
        final Instant clientEventTimestamp = mClientEventTimestamp;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(List.of(mChatMessageData))
                        .setActivityId(activityId)
                        .setHasNewMessage(true)
                        .build();
        final Instant updateTimestamp = mReferenceTime;
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        updateTimestamp,
                        conversationData);
        final ConversationUpdateEvent updateEvent2 =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        updateTimestamp,
                        conversationData);
        assertThat(updateEvent).isEqualTo(updateEvent2);
        assertThat(updateEvent.hashCode()).isEqualTo(updateEvent2.hashCode());
        assertThat(updateEvent.toString()).isNotNull();

        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(updateEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ChatMessageData#getAuthor",
                "android.service.personalcontext.hint.ChatMessageData#getReferenceTime",
                "android.service.personalcontext.hint.ChatMessageData#getText",
                "android.service.personalcontext.hint.ChatMessageData#getContentCaptureData",
                "android.service.personalcontext.hint.ChatMessageData#isOutgoingMessage",
                "android.service.personalcontext.hint.ChatMessageData#equals",
                "android.service.personalcontext.hint.ChatMessageData#hashCode",
                "android.service.personalcontext.hint.ChatMessageData#toString",
                "android.service.personalcontext.hint.ChatMessageData.Builder#Builder",
                "android.service.personalcontext.hint.ChatMessageData.Builder#build",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setAuthor",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setReferenceTime",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setText",
                "android.service.personalcontext.hint.ChatMessageData.Builder"
                        + "#setContentCaptureData",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData#getAutofillId",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData"
                        + "#getRawParsedDateString",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData"
                        + "#getRawParsedTimeString",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData#equals",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData.Builder"
                        + "#Builder",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData.Builder"
                        + "#setAutofillId",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData.Builder"
                        + "#setRawParsedDateString",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData.Builder"
                        + "#setRawParsedTimeString",
                "android.service.personalcontext.hint.ChatMessageContentCaptureData.Builder"
                        + "#build",
                "android.service.personalcontext.hint.ConversationData#getChatMessages",
                "android.service.personalcontext.hint.ConversationData#getComponentName",
                "android.service.personalcontext.hint.ConversationData#getConversationTitle",
                "android.service.personalcontext.hint.ConversationData#getInputBoxAutofillId",
                "android.service.personalcontext.hint.ConversationData#getInputBoxText",
                "android.service.personalcontext.hint"
                        + ".ConversationData#getProcessingEndTimestamp",
                "android.service.personalcontext.hint"
                        + ".ConversationData#getProcessingStartTimestamp",
                "android.service.personalcontext.hint.ConversationData#isKeyboardShown",
                "android.service.personalcontext.hint"
                        + ".ConversationData#isLastMessageFromTheUser",
                "android.service.personalcontext.hint.ConversationData#hasNewMessage",
                "android.service.personalcontext.hint.ConversationData#equals",
                "android.service.personalcontext.hint.ConversationData#hashCode",
                "android.service.personalcontext.hint.ConversationData#toString",
                "android.service.personalcontext.hint.ConversationData.Builder#Builder",
                "android.service.personalcontext.hint.ConversationData.Builder#build",
                "android.service.personalcontext.hint.ConversationData.Builder#setActivityId",
                "android.service.personalcontext.hint.ConversationData.Builder#setChatMessages",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setComponentName",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setConversationTitle",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setInputBoxAutofillId",
                "android.service.personalcontext.hint.ConversationData.Builder#setInputBoxText",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setProcessingEndTimestamp",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setProcessingStartTimestamp",
                "android.service.personalcontext.hint.ConversationData"
                        + ".Builder#setHasNewMessage",
            })
    @Test
    public void testConversationData_getters() {
        final Instant chatMessageReferenceTime = mReferenceTime;
        final AutofillId chatMessageAutofillId = new AutofillId(2);

        ChatMessageContentCaptureData contentCaptureData =
                new ChatMessageContentCaptureData.Builder()
                        .setRawParsedTimeString("12:00 PM")
                        .setRawParsedDateString("Today")
                        .setAutofillId(chatMessageAutofillId)
                        .build();

        final ChatMessageData chatMessageData =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(chatMessageReferenceTime)
                        .setContentCaptureData(contentCaptureData)
                        .build();
        assertThat(chatMessageData.getText()).isEqualTo("text");
        assertThat(chatMessageData.getAuthor()).isEqualTo("author");
        assertThat(chatMessageData.getReferenceTime()).isEqualTo(chatMessageReferenceTime);
        assertThat(chatMessageData.isOutgoingMessage()).isTrue();

        assertThat(chatMessageData.getContentCaptureData()).isEqualTo(contentCaptureData);
        assertThat(chatMessageData.getContentCaptureData().getRawParsedTimeString())
                .isEqualTo(contentCaptureData.getRawParsedTimeString());
        assertThat(chatMessageData.getContentCaptureData().getRawParsedDateString())
                .isEqualTo(contentCaptureData.getRawParsedDateString());
        assertThat(chatMessageData.getContentCaptureData().getAutofillId())
                .isEqualTo(chatMessageAutofillId);

        assertThat(chatMessageData.toString()).isNotNull();

        final ChatMessageData chatMessageData2 =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(chatMessageReferenceTime)
                        .setContentCaptureData(contentCaptureData)
                        .build();
        assertThat(chatMessageData).isEqualTo(chatMessageData2);
        assertThat(chatMessageData.hashCode()).isEqualTo(chatMessageData2.hashCode());

        final Instant processingStartTimestamp = mReferenceTime;
        final Instant processingEndTimestamp = mReferenceTime;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(3);
        final List<ChatMessageData> chatMessages = List.of(chatMessageData);

        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(chatMessages)
                        .setHasNewMessage(true)
                        .build();

        assertThat(conversationData.getProcessingStartTimestamp())
                .isEqualTo(processingStartTimestamp);
        assertThat(conversationData.getProcessingEndTimestamp()).isEqualTo(processingEndTimestamp);
        assertThat(conversationData.getComponentName()).isEqualTo(componentName);
        assertThat(conversationData.getInputBoxAutofillId()).isEqualTo(inputBoxAutofillId);
        assertThat(conversationData.getInputBoxText()).isEqualTo("inputBoxText");
        assertThat(conversationData.getConversationTitle()).isEqualTo("title");
        assertThat(conversationData.isKeyboardShown()).isTrue();
        assertThat(conversationData.isLastMessageFromTheUser()).isFalse();
        assertThat(conversationData.hasNewMessage()).isTrue();
        assertThat(conversationData.getChatMessages()).isEqualTo(chatMessages);
        assertThat(conversationData.toString()).isNotNull();

        final ConversationData conversationData2 =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(chatMessages)
                        .setHasNewMessage(true)
                        .build();
        assertThat(conversationData).isEqualTo(conversationData2);
        assertThat(conversationData.hashCode()).isEqualTo(conversationData2.hashCode());
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
