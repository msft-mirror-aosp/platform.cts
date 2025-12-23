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
import android.graphics.Rect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.UserInputHint;
import android.service.personalcontext.hint.UserInputText;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:UserInputHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class UserInputHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.UserInputHint.Builder#build",
                "android.service.personalcontext.hint.UserInputHint.Builder#setSourceAppActivityComponentName",
                "android.service.personalcontext.hint.UserInputHint#getUserInputText",
                "android.service.personalcontext.hint.UserInputHint#getSourceAppActivityComponentName",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.UserInputHint#toBundle"
            })
    @Test
    public void testUserInputHint_bundleUnbundle() {
        final UserInputText userInputText =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final UserInputHint hint =
                new UserInputHint.Builder(userInputText)
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(UserInputHint.class);
        final UserInputHint outputUserInputHint = (UserInputHint) outputHint;
        final UserInputText outputUserInputText = outputUserInputHint.getUserInputText();
        assertThat(outputUserInputText).isEqualTo(userInputText);
        assertThat(outputUserInputHint.getSourceAppActivityComponentName()).isEqualTo(componentName);
        assertThat(outputHint).isEqualTo(hint);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.UserInputText.Builder#setText",
                "android.service.personalcontext.hint.UserInputText.Builder#setViewNodeBoundingBox",
                "android.service.personalcontext.hint.UserInputText.Builder#setFieldType",
                "android.service.personalcontext.hint.UserInputText.Builder#setUserInputTextSource",
                "android.service.personalcontext.hint.UserInputText.Builder#build",
                "android.service.personalcontext.hint.UserInputText#getText",
                "android.service.personalcontext.hint.UserInputText#getViewNodeBoundingBox",
                "android.service.personalcontext.hint.UserInputText#getFieldType",
                "android.service.personalcontext.hint.UserInputText#getUserInputTextSource"
            })
    @Test
    public void testUserInputText_getters() {
        final Rect boundingBox = new Rect(1, 2, 3, 4);
        final UserInputText userInputText =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(boundingBox)
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();

        assertThat(userInputText.getText()).isEqualTo("hello");
        assertThat(userInputText.getViewNodeBoundingBox()).isEqualTo(boundingBox);
        assertThat(userInputText.getFieldType()).isEqualTo(UserInputText.FIELD_TYPE_SEARCH_BOX);
        assertThat(userInputText.getUserInputTextSource())
                .isEqualTo(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED);
    }

    private ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.UserInputHint#equals",
                "android.service.personalcontext.hint.UserInputHint#hashCode",
                "android.service.personalcontext.hint.UserInputHint#toString"
            })
    @Test
    public void testUserInputHint_equalsHashCodeToString() {
        final UserInputText userInputText =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final UserInputHint hint =
                new UserInputHint.Builder(userInputText)
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final UserInputHint unbundledHint = (UserInputHint) bundleUnbundle(hint);
        assertThat(unbundledHint).isEqualTo(hint);
        assertThat(unbundledHint.hashCode()).isEqualTo(hint.hashCode());
        assertThat(hint.toString()).isNotNull();

        final UserInputText differentUserInputText =
                new UserInputText.Builder()
                        .setText("different")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();
        final UserInputHint differentHint =
                new UserInputHint.Builder(differentUserInputText)
                        .setSourceAppActivityComponentName(componentName)
                        .build();
        assertThat(hint).isNotEqualTo(differentHint);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.UserInputText#equals",
                "android.service.personalcontext.hint.UserInputText#hashCode",
                "android.service.personalcontext.hint.UserInputText#toString"
            })
    @Test
    public void testUserInputText_equalsHashCodeToString() {
        final Rect boundingBox = new Rect(1, 2, 3, 4);
        final UserInputText userInputText1 =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(boundingBox)
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();

        final UserInputText userInputText2 =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(boundingBox)
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();

        final UserInputText differentText =
                new UserInputText.Builder()
                        .setText("different")
                        .setViewNodeBoundingBox(boundingBox)
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();

        assertThat(userInputText1).isEqualTo(userInputText2);
        assertThat(userInputText1.hashCode()).isEqualTo(userInputText2.hashCode());
        assertThat(userInputText1.toString()).isNotNull();
        assertThat(userInputText1).isNotEqualTo(differentText);
    }
}
