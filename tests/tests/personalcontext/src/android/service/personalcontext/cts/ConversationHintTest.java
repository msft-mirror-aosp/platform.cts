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
import android.service.personalcontext.hint.ChatMessageData;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ConversationData;
import android.service.personalcontext.hint.ConversationEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationEnterEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationExitEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationProcessingEvent;
import android.service.personalcontext.hint.ConversationEvent.ConversationUpdateEvent;
import android.service.personalcontext.hint.ConversationHint;
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

/** Build/Install/Run: atest CtsPersonalContextTestCases:ConversationHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ConversationHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String CONVERSATION_SESSION_ID = "session_id";
    private static final AutofillId AUTOFILL_ID = new AutofillId(1);
    private static final String CONTENT_DESCRIPTION = "content description";
    private Instant mReferenceTime;
    private ChatMessageData mChatMessageData;

    @Before
    public void setUp() {
        mReferenceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        mChatMessageData =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(mReferenceTime)
                        .setAutofillId(AUTOFILL_ID)
                        .setTimeText("12:00 PM")
                        .setDateText("Today")
                        .setContentDescription(CONTENT_DESCRIPTION)
                        .build();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#ConversationEnterEvent",
                "android.service.personalcontext.hint.ConversationHint.Builder#Builder",
                "android.service.personalcontext.hint.ConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ConversationHint#toBundle",
                "android.service.personalcontext.hint.ConversationHint#getConversationEvent",
                "android.service.personalcontext.hint.ConversationEvent#getEventTimestamp",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#getConversationSessionId",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#getConversationEnterTimestamp",
            })
    @Test
    public void testConversationHint_enterEvent_bundleUnbundle() {
        final Instant enterTimestamp = mReferenceTime;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(CONVERSATION_SESSION_ID, enterTimestamp, enterTimestamp);
        final ConversationHint hint = new ConversationHint.Builder(enterEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationEnterEvent.class);

        final ConversationEnterEvent outputEnterEvent = (ConversationEnterEvent) outputEvent;
        assertThat(outputEnterEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputEnterEvent.getConversationEnterTimestamp()).isEqualTo(enterTimestamp);
        assertThat(outputEnterEvent.getEventTimestamp()).isEqualTo(enterTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationHint#equals",
                "android.service.personalcontext.hint.ConversationHint#hashCode",
                "android.service.personalcontext.hint.ConversationHint#toString",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#equals",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#hashCode",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationEnterEvent#toString",
            })
    @Test
    public void testConversationHint_enterEvent_equalsHashCodeToString() {
        final Instant enterTimestamp = mReferenceTime;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(CONVERSATION_SESSION_ID, enterTimestamp, enterTimestamp);
        final ConversationEnterEvent enterEvent2 =
                new ConversationEnterEvent(CONVERSATION_SESSION_ID, enterTimestamp, enterTimestamp);
        assertThat(enterEvent).isEqualTo(enterEvent2);
        assertThat(enterEvent.hashCode()).isEqualTo(enterEvent2.hashCode());
        assertThat(enterEvent.toString()).isNotNull();

        final ConversationHint hint = new ConversationHint.Builder(enterEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationExitEvent#ConversationExitEvent",
                "android.service.personalcontext.hint.ConversationHint.Builder#Builder",
                "android.service.personalcontext.hint.ConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ConversationHint#toBundle",
                "android.service.personalcontext.hint.ConversationHint#getConversationEvent",
                "android.service.personalcontext.hint.ConversationEvent#getEventTimestamp",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationExitEvent#getConversationSessionId"
            })
    @Test
    public void testConversationHint_exitEvent_bundleUnbundle() {
        final Instant eventTimestamp = mReferenceTime;
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(CONVERSATION_SESSION_ID, eventTimestamp);
        final ConversationHint hint = new ConversationHint.Builder(exitEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationExitEvent.class);

        final ConversationExitEvent outputExitEvent = (ConversationExitEvent) outputEvent;
        assertThat(outputExitEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputExitEvent.getEventTimestamp()).isEqualTo(eventTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationHint#equals",
                "android.service.personalcontext.hint.ConversationHint#hashCode",
                "android.service.personalcontext.hint.ConversationHint#toString",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationExitEvent#equals",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationExitEvent#hashCode",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationExitEvent#toString",
            })
    @Test
    public void testConversationHint_exitEvent_equalsHashCodeToString() {
        final Instant eventTimestamp = mReferenceTime;
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(CONVERSATION_SESSION_ID, eventTimestamp);
        final ConversationExitEvent exitEvent2 =
                new ConversationExitEvent(CONVERSATION_SESSION_ID, eventTimestamp);
        assertThat(exitEvent).isEqualTo(exitEvent2);
        assertThat(exitEvent.hashCode()).isEqualTo(exitEvent2.hashCode());
        assertThat(exitEvent.toString()).isNotNull();

        final ConversationHint hint = new ConversationHint.Builder(exitEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#ConversationProcessingEvent",
                "android.service.personalcontext.hint.ConversationHint.Builder#Builder",
                "android.service.personalcontext.hint.ConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ConversationHint#toBundle",
                "android.service.personalcontext.hint.ConversationHint#getConversationEvent",
                "android.service.personalcontext.hint.ConversationEvent#getEventTimestamp",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#getStartProcessingTimestamp",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#getMessageAutofillId",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#getConversationSessionId",
            })
    @Test
    public void testConversationHint_processingEvent_bundleUnbundle() {
        final Instant processingTimestamp = mReferenceTime;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        processingTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ConversationHint hint = new ConversationHint.Builder(processingEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationProcessingEvent.class);

        final ConversationProcessingEvent outputProcessingEvent =
                (ConversationProcessingEvent) outputEvent;
        assertThat(outputProcessingEvent.getStartProcessingTimestamp())
                .isEqualTo(processingTimestamp);
        assertThat(outputProcessingEvent.getMessageAutofillId()).isEqualTo(messageAutofillId);
        assertThat(outputProcessingEvent.getConversationSessionId())
                .isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputProcessingEvent.getEventTimestamp()).isEqualTo(processingTimestamp);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationHint#equals",
                "android.service.personalcontext.hint.ConversationHint#hashCode",
                "android.service.personalcontext.hint.ConversationHint#toString",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#equals",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#hashCode",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationProcessingEvent#toString",
            })
    @Test
    public void testConversationHint_processingEvent_equalsHashCodeToString() {
        final Instant processingTimestamp = mReferenceTime;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        processingTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ConversationProcessingEvent processingEvent2 =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        processingTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        assertThat(processingEvent).isEqualTo(processingEvent2);
        assertThat(processingEvent.hashCode()).isEqualTo(processingEvent2.hashCode());
        assertThat(processingEvent.toString()).isNotNull();

        final ConversationHint hint = new ConversationHint.Builder(processingEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationUpdateEvent#ConversationUpdateEvent",
                "android.service.personalcontext.hint.ConversationHint.Builder#Builder",
                "android.service.personalcontext.hint.ConversationHint.Builder#build",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ConversationHint#toBundle",
                "android.service.personalcontext.hint.ConversationHint#getConversationEvent",
                "android.service.personalcontext.hint.ConversationEvent#getEventTimestamp",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationUpdateEvent#getConversationData",
                "android.service.personalcontext.hint.ConversationEvent"
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
        final Instant eventTimestamp = mReferenceTime;
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID, eventTimestamp, conversationData);
        final ConversationHint hint = new ConversationHint.Builder(updateEvent).build();
        assertThat(hint.getHintId()).isNotNull();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(ConversationHint.class);
        assertThat(outputHint.getHintId()).isEqualTo(hint.getHintId());
        final ConversationEvent outputEvent =
                ((ConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationUpdateEvent.class);

        final ConversationUpdateEvent outputUpdateEvent = (ConversationUpdateEvent) outputEvent;
        assertThat(outputUpdateEvent.getConversationData()).isEqualTo(conversationData);
        assertThat(outputUpdateEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputUpdateEvent.getEventTimestamp()).isEqualTo(eventTimestamp);
        assertThat(outputUpdateEvent.getConversationData().hasNewMessage()).isTrue();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ConversationHint#equals",
                "android.service.personalcontext.hint.ConversationHint#hashCode",
                "android.service.personalcontext.hint.ConversationHint#toString",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationUpdateEvent#equals",
                "android.service.personalcontext.hint.ConversationEvent"
                        + ".ConversationUpdateEvent#hashCode",
                "android.service.personalcontext.hint.ConversationEvent"
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
        final Instant eventTimestamp = mReferenceTime;
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID, eventTimestamp, conversationData);
        final ConversationUpdateEvent updateEvent2 =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID, eventTimestamp, conversationData);
        assertThat(updateEvent).isEqualTo(updateEvent2);
        assertThat(updateEvent.hashCode()).isEqualTo(updateEvent2.hashCode());
        assertThat(updateEvent.toString()).isNotNull();

        final ConversationHint hint = new ConversationHint.Builder(updateEvent).build();
        final ContextHint unbundledHint = bundleUnbundle(hint);
        assertThat(hint).isEqualTo(unbundledHint);
        assertThat(hint.hashCode()).isEqualTo(unbundledHint.hashCode());
        assertThat(hint.toString()).isNotNull();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ChatMessageData#getAuthor",
                "android.service.personalcontext.hint.ChatMessageData#getAutofillId",
                "android.service.personalcontext.hint.ChatMessageData#getContentDescription",
                "android.service.personalcontext.hint.ChatMessageData#getDateText",
                "android.service.personalcontext.hint.ChatMessageData#getReferenceTime",
                "android.service.personalcontext.hint.ChatMessageData#getText",
                "android.service.personalcontext.hint.ChatMessageData#getTimeText",
                "android.service.personalcontext.hint.ChatMessageData#isOutgoingMessage",
                "android.service.personalcontext.hint.ChatMessageData#equals",
                "android.service.personalcontext.hint.ChatMessageData#hashCode",
                "android.service.personalcontext.hint.ChatMessageData#toString",
                "android.service.personalcontext.hint.ChatMessageData.Builder#Builder",
                "android.service.personalcontext.hint.ChatMessageData.Builder#build",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setAuthor",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setAutofillId",
                "android.service.personalcontext.hint.ChatMessageData"
                        + ".Builder#setContentDescription",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setDateText",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setReferenceTime",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setText",
                "android.service.personalcontext.hint.ChatMessageData.Builder#setTimeText",
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
        final String chatMessageContentDescription = "chat message content description";
        final ChatMessageData chatMessageData =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(chatMessageReferenceTime)
                        .setAutofillId(chatMessageAutofillId)
                        .setTimeText("12:00 PM")
                        .setDateText("Today")
                        .setContentDescription(chatMessageContentDescription)
                        .build();
        assertThat(chatMessageData.getText()).isEqualTo("text");
        assertThat(chatMessageData.getAuthor()).isEqualTo("author");
        assertThat(chatMessageData.getReferenceTime()).isEqualTo(chatMessageReferenceTime);
        assertThat(chatMessageData.isOutgoingMessage()).isTrue();
        assertThat(chatMessageData.getTimeText()).isEqualTo("12:00 PM");
        assertThat(chatMessageData.getDateText()).isEqualTo("Today");
        assertThat(chatMessageData.getAutofillId()).isEqualTo(chatMessageAutofillId);
        assertThat(chatMessageData.getContentDescription())
                .isEqualTo(chatMessageContentDescription);
        assertThat(chatMessageData.toString()).isNotNull();

        final ChatMessageData chatMessageData2 =
                new ChatMessageData.Builder()
                        .setOutgoingMessage(true)
                        .setText("text")
                        .setAuthor("author")
                        .setReferenceTime(chatMessageReferenceTime)
                        .setAutofillId(chatMessageAutofillId)
                        .setTimeText("12:00 PM")
                        .setDateText("Today")
                        .setContentDescription(chatMessageContentDescription)
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
