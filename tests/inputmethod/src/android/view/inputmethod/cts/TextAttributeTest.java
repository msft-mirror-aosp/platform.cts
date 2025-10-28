/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.Flags;
import android.view.inputmethod.TextAttribute;

import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

@SmallTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class TextAttributeTest {
    private static final String SUGGESTION = "suggestion";
    private static final String EXTRAS_KEY = "extras_key";
    private static final String EXTRAS_VALUE = "extras_value";
    private static final Boolean SUGGESTION_SELECTED = true;
    private static final Boolean SUGGESTION_NOT_SELECTED = false;

    private static final List<String> SUGGESTIONS = Collections.singletonList(SUGGESTION);
    private static final PersistableBundle EXTRA_BUNDLE;
    static {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putString(EXTRAS_KEY, EXTRAS_VALUE);
        EXTRA_BUNDLE = bundle;
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testTextAttributeDefaultValues() {
        final TextAttribute textAttribute = new TextAttribute.Builder().build();
        assertThat(textAttribute.getTextConversionSuggestions()).isEmpty();
        assertThat(textAttribute.getExtras().getString(EXTRAS_KEY)).isNull();
        if (Flags.a11yTextChangeTypesApi()) {
            assertTextSuggestionSelected(textAttribute, SUGGESTION_NOT_SELECTED);
        }
    }

    @Test
    public void testTextAttribute() {
        final TextAttribute textAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(SUGGESTIONS)
                .setExtras(EXTRA_BUNDLE)
                .build();
        assertTextAttribute(textAttribute);
        if (Flags.a11yTextChangeTypesApi()) {
            assertTextSuggestionSelected(textAttribute, SUGGESTION_NOT_SELECTED);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_TEXT_CHANGE_TYPES_API)
    public void testTextAttributeSuggestionSelected() {
        TextAttribute textAttribute =
                new TextAttribute.Builder().setTextSuggestionSelected(SUGGESTION_SELECTED).build();
        assertTextSuggestionSelected(textAttribute, SUGGESTION_SELECTED);

        textAttribute =
                new TextAttribute.Builder()
                        .setTextSuggestionSelected(SUGGESTION_NOT_SELECTED)
                        .build();
        assertTextSuggestionSelected(textAttribute, SUGGESTION_NOT_SELECTED);
    }

    @Test
    public void testWriteToParcel() {
        final TextAttribute textAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(SUGGESTIONS)
                .setExtras(EXTRA_BUNDLE)
                .build();

        assertTextAttribute(cloneViaParcel(textAttribute));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_TEXT_CHANGE_TYPES_API)
    public void testWriteToParcelTextSuggestionSelected() {
        final TextAttribute textAttribute =
                new TextAttribute.Builder()
                        .setTextConversionSuggestions(SUGGESTIONS)
                        .setExtras(EXTRA_BUNDLE)
                        .setTextSuggestionSelected(SUGGESTION_SELECTED)
                        .build();

        assertTextSuggestionSelected(cloneViaParcel(textAttribute), SUGGESTION_SELECTED);
    }

    private static void assertTextAttribute(TextAttribute info) {
        assertThat(info.getTextConversionSuggestions()).containsExactlyElementsIn(SUGGESTIONS);
        assertThat(info.getExtras().getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
    }

    @RequiresFlagsEnabled(Flags.FLAG_A11Y_TEXT_CHANGE_TYPES_API)
    private static void assertTextSuggestionSelected(TextAttribute info, boolean expectedValue) {
        assertThat(info.isTextSuggestionSelected()).isEqualTo(expectedValue);
    }

    private static TextAttribute cloneViaParcel(TextAttribute src) {
        final Parcel parcel = Parcel.obtain();
        try {
            src.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return TextAttribute.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
