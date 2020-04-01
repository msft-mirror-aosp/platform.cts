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

import static android.autofillservice.cts.Timeouts.UI_TIMEOUT;

import android.autofillservice.cts.UiBot;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.Timeout;
import com.android.cts.mockime.MockIme;

/**
 * UiBot for the inline suggestion.
 */
public final class InlineUiBot extends UiBot {

    private static final String TAG = "AutoFillInlineCtsUiBot";

    public InlineUiBot() {
        this(UI_TIMEOUT);
    }

    public InlineUiBot(Timeout defaultTimeout) {
        super(defaultTimeout);
    }

    @Override
    public void assertNoDatasets() throws Exception {
        assertNoSuggestionStripEver();
    }

    @Override
    public void assertNoDatasetsEver() throws Exception {
        assertNoSuggestionStripEver();
    }

    /**
     * Selects the suggestion in the {@link MockIme}'s suggestion strip by the given text.
     */
    public void selectSuggestion(String name) throws Exception {
        final UiObject2 strip = findSuggestionStrip(UI_TIMEOUT);
        final UiObject2 dataset = strip.findObject(By.text(name));
        if (dataset == null) {
            throw new AssertionError("no dataset " + name + " in " + getChildrenAsText(strip));
        }
        dataset.click();
    }

    @Override
    public void selectDataset(String name) throws Exception {
        selectSuggestion(name);
    }

    @Override
    public UiObject2 assertDatasets(String...names) throws Exception {
        final UiObject2 picker = findSuggestionStrip(UI_TIMEOUT);
        return assertDatasets(picker, names);
    }
}
