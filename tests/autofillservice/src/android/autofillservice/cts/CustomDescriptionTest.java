/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertNoDanglingSessions;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.CustomDescription;
import android.service.autofill.ImageTransformation;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.function.BiFunction;

public class CustomDescriptionTest extends AutoFillServiceTestCase {
    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule = new ActivityTestRule<>(
            LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    /**
     * Base test
     *
     * @param descriptionBuilder method to build a custom description
     * @param uiVerifier         Ran when the custom description is shown
     */
    private void testCustomDescription(
            @NonNull BiFunction<AutofillId, AutofillId, CustomDescription> descriptionBuilder,
            @Nullable Runnable uiVerifier) throws Exception {
        enableService();

        AutofillId usernameId = mActivity.getUsername().getAutofillId();
        AutofillId passwordId = mActivity.getPassword().getAutofillId();

        // Set response with custom description
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME, ID_PASSWORD)
                .setCustomDescription(descriptionBuilder.apply(usernameId, passwordId))
                .build());

        // Trigger auto-fill with custom description
        mActivity.onPassword(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("usernm"));
        mActivity.onPassword((v) -> v.setText("passwd"));
        mActivity.tapLogin();

        if (uiVerifier != null) {
            uiVerifier.run();
        }

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
        sReplier.getNextSaveRequest();

        assertNoDanglingSessions();
    }

    @Test
    public void validTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans1 = new CharSequenceTransformation.Builder(usernameId,
                    "(.*)", "$1").addField(passwordId, ".*(..)", "..$1").build();
            ImageTransformation trans2 = new ImageTransformation.Builder(usernameId, ".*",
                    R.drawable.android).build();

            return new CustomDescription.Builder(presentation).addChild(R.id.first,
                    trans1).addChild(R.id.img, trans2).build();
        }, () -> sUiBot.assertShownByText("usernm..wd"));
    }

    @Test
    public void badImageTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation.Builder(usernameId, ".*",
                    1).build();

            return new CustomDescription.Builder(presentation).addChild(R.id.img, trans).build();
        }, null);
    }

    @Test
    public void unusedImageTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation.Builder(usernameId, "invalid",
                    R.drawable.android).build();

            return new CustomDescription.Builder(presentation).addChild(R.id.img, trans).build();
        }, null /* Just check that we don't crash */);
    }

    @Test
    public void applyImageTransformationToTextView() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation.Builder(usernameId, ".*",
                    R.drawable.android).build();

            return new CustomDescription.Builder(presentation).addChild(R.id.first, trans).build();
        }, null /* Just check that we don't crash */);
    }

    @Test
    public void badCharSequenceTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans = new CharSequenceTransformation.Builder(
                    usernameId, "(.*)", "$1").addField(passwordId, ".*", "..$1").build();

            return new CustomDescription.Builder(presentation).addChild(R.id.first, trans).build();
        }, () -> sUiBot.assertShownByText("usernm"));
    }

    @Test
    public void applyCharSequenceTransformationToImageView() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans = new CharSequenceTransformation.Builder(
                    usernameId, "(.*)", "$1").build();

            return new CustomDescription.Builder(presentation).addChild(R.id.img, trans).build();
        }, null /* Just check that we don't crash */);
    }
}
