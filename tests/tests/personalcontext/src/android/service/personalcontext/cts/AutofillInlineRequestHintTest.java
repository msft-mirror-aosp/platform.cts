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

import android.content.ComponentName;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.autofill.FillEventHistory;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.AutofillInlineRequestHint;
import android.service.personalcontext.hint.ContextHint;
import android.util.Size;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

/** Build/Install/Run: atest CtsPersonalContextTestCases:AutofillInlineRequestHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class AutofillInlineRequestHintTest {

    private static final InlinePresentationSpec INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private AutoCloseable mMockCloseable;

    @Before
    public void setUp() throws RemoteException {
        mMockCloseable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder#Builder",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setSessionId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder#setTaskId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setRequestTimestamp",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setActivityComponent",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setFocusedId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setAutofillValue",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setInlineSuggestionsRequest",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setAugmentedAutofillProxy",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder"
                        + "#setFillEventHistory",
                "android.service.personalcontext.hint.AutofillInlineRequestHint"
                        + ".AugmentedAutofillProxy#AugmentedAutofillProxy",
                "android.service.personalcontext.hint.AutofillInlineRequestHint.Builder#build",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#getSessionId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#getTaskId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint"
                        + "#getActivityComponent",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#getFocusedId",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#getAutofillValue",
                "android.service.personalcontext.hint.AutofillInlineRequestHint"
                        + "#getInlineSuggestionsRequest",
                "android.service.personalcontext.hint.AutofillInlineRequestHint"
                        + "#getAugmentedAutofillProxy",
                "android.service.personalcontext.hint.AutofillInlineRequestHint"
                        + "#getFillEventHistory",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#equals",
                "android.service.personalcontext.hint.AutofillInlineRequestHint#hashCode",
            })
    @Test
    public void testAutofillInlineRequestHint_bundleUnbundle() throws RemoteException {
        final int sessionId = 6;
        final int taskId = 7;
        final Instant requestTimestamp = Instant.ofEpochSecond(500);
        final ComponentName activityComponent = new ComponentName("test_package", "class");
        final AutofillId focusedId = new AutofillId(3);
        final AutofillValue autofillValue = AutofillValue.forText("test");
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(INLINE_PRESENTATION_SPEC)).build();
        final Bundle bundle = new Bundle();
        bundle.putBoolean("test", true);
        final FillEventHistory fillEventHistory = new FillEventHistory(sessionId, bundle);

        final AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder()
                        .setSessionId(sessionId)
                        .setTaskId(taskId)
                        .setRequestTimestamp(requestTimestamp)
                        .setActivityComponent(activityComponent)
                        .setFocusedId(focusedId)
                        .setAutofillValue(autofillValue)
                        .setInlineSuggestionsRequest(inlineSuggestionsRequest)
                        .setAugmentedAutofillManagerClient(new Binder())
                        .setFillEventHistory(fillEventHistory)
                        .build();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(AutofillInlineRequestHint.class);

        final AutofillInlineRequestHint outputAutofillHint = (AutofillInlineRequestHint) outputHint;
        assertThat(outputAutofillHint.getSessionId()).isEqualTo(sessionId);
        assertThat(outputAutofillHint.getTaskId()).isEqualTo(taskId);
        assertThat(outputAutofillHint.getActivityComponent()).isEqualTo(activityComponent);
        assertThat(outputAutofillHint.getFocusedId()).isEqualTo(focusedId);
        assertThat(outputAutofillHint.getAutofillValue()).isEqualTo(autofillValue);
        assertThat(outputAutofillHint.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest);
        assertThat(outputAutofillHint.getFillEventHistory().getClientState())
                .isEqualTo(fillEventHistory.getClientState());

        assertThat(outputAutofillHint).isEqualTo(hint);
        assertThat(outputAutofillHint.hashCode()).isEqualTo(hint.hashCode());
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
