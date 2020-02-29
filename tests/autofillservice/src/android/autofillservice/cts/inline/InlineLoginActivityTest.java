/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.autofillservice.cts.inline;

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findAutofillIdByResourceId;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.Timeouts.MOCK_IME_TIMEOUT_MS;
import static android.autofillservice.cts.inline.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.AbstractLoginActivityTestCase;
import android.autofillservice.cts.CannedFillResponse;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.InstrumentedAutoFillService;
import android.os.Process;
import android.service.autofill.FillContext;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

public class InlineLoginActivityTest extends AbstractLoginActivityTestCase {

    private static final String TAG = "InlineLoginActivityTest";

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
    }

    @Test
    public void testAutofill_noDataset() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

        final ImeEventStream stream = mockImeSession.openEventStream();
        mockImeSession.callRequestShowSelf(0);

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Trigger auto-fill.
        requestFocusOnUsername();
        expectEvent(stream, editorMatcher("onStartInput", mActivity.getUsername().getId()),
                MOCK_IME_TIMEOUT_MS);

        sReplier.getNextFillRequest();

        mUiBot.assertNoSuggestionStripEver();
        mUiBot.assertNoDatasetsEver();

        waitUntilDisconnected();
    }

    @Test
    public void testAutofill_oneDataset() throws Exception {
        testBasicLoginAutofill(/* numDatasets= */ 1, /* selectedDatasetIndex= */ 0);
    }

    @Test
    public void testAutofill_twoDatasets_selectFirstDataset() throws Exception {
        testBasicLoginAutofill(/* numDatasets= */ 2, /* selectedDatasetIndex= */ 0);

    }

    @Test
    public void testAutofill_twoDatasets_selectSecondDataset() throws Exception {
        testBasicLoginAutofill(/* numDatasets= */ 2, /* selectedDatasetIndex= */ 1);
    }

    private void testBasicLoginAutofill(int numDatasets, int selectedDatasetIndex)
            throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder();
        for (int i = 0; i < numDatasets; i++) {
            builder.addDataset(new CannedFillResponse.CannedDataset.Builder()
                    .setField(ID_USERNAME, "dude" + i)
                    .setField(ID_PASSWORD, "sweet" + i)
                    .setPresentation(createPresentation("The Dude" + i))
                    .setInlinePresentation(createInlinePresentation("The Dude" + i))
                    .build());
        }

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude" + selectedDatasetIndex, "sweet" + selectedDatasetIndex);

        final ImeEventStream stream = mockImeSession.openEventStream();

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Wait until IME is displaying.
        mockImeSession.callRequestShowSelf(0);
        expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);
        expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        // Trigger auto-fill.
        requestFocusOnUsername();
        expectEvent(stream, editorMatcher("onStartInput", mActivity.getUsername().getId()),
                MOCK_IME_TIMEOUT_MS);

        // Wait until suggestion strip is updated
        expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        mUiBot.assertSuggestionStrip(numDatasets);
        mUiBot.assertNoDatasetsEver();

        mUiBot.selectSuggestion(selectedDatasetIndex);

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure input was sanitized.
        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);
        final FillContext fillContext = request.contexts.get(request.contexts.size() - 1);
        assertThat(fillContext.getFocusedId())
                .isEqualTo(findAutofillIdByResourceId(fillContext, ID_USERNAME));

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }

    @Test
    public void testAutofill_disjointDatasets() throws Exception {
        // Set service.
        enableService();

        final MockImeSession mockImeSession = sMockImeSessionRule.getMockImeSession();
        assumeTrue("MockIME not available", mockImeSession != null);

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "lollipop")
                        .setPresentation(createPresentation("The Password2"))
                        .setInlinePresentation(createInlinePresentation("The Password2"))
                        .build());

        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude");

        final ImeEventStream stream = mockImeSession.openEventStream();

        // Wait until the MockIme gets bound to the TestActivity.
        expectBindInput(stream, Process.myPid(), MOCK_IME_TIMEOUT_MS);

        // Wait until IME is displaying.
        mockImeSession.callRequestShowSelf(0);
        expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);
        expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        // Trigger auto-fill.
        requestFocusOnUsername();
        expectEvent(stream, editorMatcher("onStartInput", mActivity.getUsername().getId()),
                MOCK_IME_TIMEOUT_MS);

        // Wait until suggestion strip is updated
        expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        mUiBot.assertNoDatasetsEver();

        mUiBot.assertSuggestionStrip(1);

        // Switch focus to password
        requestFocusOnPassword();
        expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        mUiBot.assertSuggestionStrip(2);

        // Switch focus back to username
        requestFocusOnUsername();
        expectEvent(stream, event -> "onSuggestionViewUpdated".equals(event.getEventName()),
                MOCK_IME_TIMEOUT_MS);

        mUiBot.assertSuggestionStrip(1);
        mUiBot.selectSuggestion(0);

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure input was sanitized.
        final InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);
        final FillContext fillContext = request.contexts.get(request.contexts.size() - 1);
        assertThat(fillContext.getFocusedId())
                .isEqualTo(findAutofillIdByResourceId(fillContext, ID_USERNAME));

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }
}
