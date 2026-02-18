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

package android.service.personalcontext.cts.hint.autofill;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.AutofillInlineRequestHint;
import android.service.personalcontext.hint.autofill.AugmentedAutofillProxy;
import android.service.personalcontext.hint.autofill.AutofillInlineRequestHintConsumer;
import android.util.Size;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class AutofillInlineRequestHintConsumerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock AugmentedAutofillProxy mProxy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.autofill.AutofillInlineRequestHintConsumer"
                        + "#fetchFocusedViewNode",
                "android.service.personalcontext.hint.autofill.AutofillInlineRequestHintConsumer"
                        + "#fetchViewCoordinates",
            })
    @Test
    public void test() {
        final int sessionId = 6;
        final int taskId = 7;
        final Instant requestTimestamp = Instant.ofEpochSecond(500);
        final ComponentName activityComponent = new ComponentName("test_package", "class");
        final AutofillId focusedId = new AutofillId(3);
        final AutofillValue autofillValue = AutofillValue.forText("test");
        final InlinePresentationSpec inlinePresentationSpec =
                new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();
        final InlineSuggestionsRequest request =
                new InlineSuggestionsRequest.Builder(List.of(inlinePresentationSpec)).build();

        final AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder()
                        .setSessionId(sessionId)
                        .setTaskId(taskId)
                        .setRequestTimestamp(requestTimestamp)
                        .setActivityComponent(activityComponent)
                        .setFocusedId(focusedId)
                        .setAutofillValue(autofillValue)
                        .setInlineSuggestionsRequest(request)
                        .setAugmentedAutofillProxy(mProxy)
                        .build();

        final AutofillInlineRequestHintConsumer consumer =
                new AutofillInlineRequestHintConsumer(hint);
        consumer.fetchFocusedViewNode();
        verify(mProxy).fetchFocusedViewNode(eq(focusedId));
        consumer.fetchViewCoordinates();
        verify(mProxy).fetchViewCoordinates(eq(focusedId));
    }
}
