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

import android.service.autofill.LuhnChecksumValidator;
import android.service.autofill.SimpleRegexValidator;
import android.service.autofill.Validator;
import android.service.autofill.Validators;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.autofill.AutofillId;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.function.BiFunction;

public class ValidatorTest extends AutoFillServiceTestCase {
    @Rule
    public final AutofillActivityTestRule<LoginActivity> mActivityRule =
        new AutofillActivityTestRule<>(LoginActivity.class);

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
     * @param validatorBuilder method to build a validator
     * @param willSaveBeShown  Whether the save pop-up will be shown
     */
    private void testValidator(
            @NonNull BiFunction<AutofillId, AutofillId, Validator> validatorBuilder,
            boolean willSaveBeShown) throws Exception {
        enableService();

        AutofillId usernameId = mActivity.getUsername().getAutofillId();
        AutofillId passwordId = mActivity.getPassword().getAutofillId();

        // Set response
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME, ID_PASSWORD)
                .setValidator(validatorBuilder.apply(usernameId, passwordId))
                .build());

        // Trigger auto-fill
        mActivity.onPassword(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("7992739871-3"));
        mActivity.onPassword((v) -> v.setText("passwd"));
        mActivity.tapLogin();

        if (willSaveBeShown) {
            sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
            sReplier.getNextSaveRequest();
        } else {
            sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);
        }

        assertNoDanglingSessions();
    }

    @Test
    public void checkForInvalidField() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.or(
                new LuhnChecksumValidator(new AutofillId(-1)),
                new SimpleRegexValidator(passwordId, "pass.*")), true);
    }

    @Test
    public void checkBoth() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.and(
                new LuhnChecksumValidator(usernameId),
                new SimpleRegexValidator(passwordId, "pass.*")), true);
    }

    @Test
    public void checkEither1() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.or(
                new SimpleRegexValidator(usernameId, "7.*"),
                new SimpleRegexValidator(passwordId, "pass.*")), true);
    }

    @Test
    public void checkEither2() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.or(
                new SimpleRegexValidator(usernameId, "invalid"),
                new SimpleRegexValidator(passwordId, "pass.*")), true);
    }

    @Test
    public void checkBothButFail() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.and(
                new SimpleRegexValidator(usernameId, "7.*"),
                new SimpleRegexValidator(passwordId, "invalid")), false);
    }

    @Test
    public void checkEitherButFail() throws Exception {
        testValidator((usernameId, passwordId) -> Validators.or(
                new SimpleRegexValidator(usernameId, "invalid"),
                new SimpleRegexValidator(passwordId, "invalid")), false);
    }
}
