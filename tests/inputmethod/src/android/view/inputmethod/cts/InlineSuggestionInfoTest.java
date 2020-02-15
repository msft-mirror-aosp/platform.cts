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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.Parcel;
import android.util.Size;
import android.view.inline.InlinePresentationSpec;
import android.view.inputmethod.InlineSuggestionInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InlineSuggestionInfoTest {

    private InlinePresentationSpec mInlinePresentationSpec = new InlinePresentationSpec.Builder(
            new Size(100, 100), new Size(400, 100)).build();

    @Test
    public void testNullInlinePresentationSpecThrowsException() {
        assertThrows(NullPointerException.class,
                () -> InlineSuggestionInfo.newInlineSuggestionInfo(/* presentationSpec */ null,
                        InlineSuggestionInfo.SOURCE_AUTOFILL, new String[]{""}));
    }

    @Test
    public void testNullSourceThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> InlineSuggestionInfo.newInlineSuggestionInfo(
                        mInlinePresentationSpec, /* source */null, new String[]{""}));
    }

    @Test
    public void testInlineSuggestionInfoValues() {
        InlineSuggestionInfo info =
                InlineSuggestionInfo.newInlineSuggestionInfo(mInlinePresentationSpec,
                        InlineSuggestionInfo.SOURCE_AUTOFILL, new String[]{"password"});

        assertThat(info.getSource()).isEqualTo(InlineSuggestionInfo.SOURCE_AUTOFILL);
        assertThat(info.getPresentationSpec()).isNotNull();
        assertThat(info.getAutofillHints()).isNotNull();
        assertThat(info.getAutofillHints().length).isEqualTo(1);
        assertThat(info.getAutofillHints()[0]).isEqualTo("password");
        assertThat(info.getType()).isEqualTo(InlineSuggestionInfo.TYPE_SUGGESTION);
    }

    @Test
    public void testInlineSuggestionInfoParcelizeDeparcelize() {
        InlineSuggestionInfo info = InlineSuggestionInfo.newInlineSuggestionInfo(
                mInlinePresentationSpec, InlineSuggestionInfo.SOURCE_AUTOFILL, new String[]{""});
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);

        InlineSuggestionInfo targetInfo = InlineSuggestionInfo.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(targetInfo).isEqualTo(info);
    }
}
