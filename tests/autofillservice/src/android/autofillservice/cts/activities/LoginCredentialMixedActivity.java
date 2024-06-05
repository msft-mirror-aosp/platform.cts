/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.widget.EditText;

import androidx.credentials.PasswordCredential;

/**
 * Same as {@link LoginActivity}, but with login fields integrated with CredentialManager, and an
 * additional payment field to make the activity mixed.
 */
public class LoginCredentialMixedActivity extends LoginActivity {

    private static final String USERNAME_HINT = "username";
    private static final String PASSWORD_HINT = "password";
    private static final String CREDIT_HINT = "creditCardNumber";

    private EditText mCreditEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUsernameEditText.setAutofillHints(USERNAME_HINT);
        this.mPasswordEditText.setAutofillHints(PASSWORD_HINT);
        this.mCreditEditText = findViewById(R.id.card_number);
        this.mCreditEditText.setAutofillHints(CREDIT_HINT);
        GetCredentialRequest.Builder builder = new GetCredentialRequest.Builder(new Bundle());
        builder.addCredentialOption(
                new CredentialOption.Builder(
                        "android.credentials.TYPE_PASSWORD_CREDENTIAL",
                        new Bundle(),
                        new Bundle()).build());
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> outcomeReceiver = result -> {
            androidx.credentials.Credential credential = androidx.credentials.Credential.createFrom(
                    result.getCredential().getType(), result.getCredential().getData());
            if (credential instanceof PasswordCredential) {
                PasswordCredential pwCredential = (PasswordCredential) credential;
                mUsernameEditText.setText(pwCredential.getId());
                mPasswordEditText.setText(pwCredential.getPassword());
            }
        };
        this.mUsernameEditText.setPendingCredentialRequest(builder.build(), outcomeReceiver);
        this.mPasswordEditText.setPendingCredentialRequest(builder.build(), outcomeReceiver);
    }

    @Override
    protected int getContentView() {
        return R.layout.mixed_fields_important_for_credential_manager;
    }

    /**
     * Sets the expectation for an autofill request (for credit card only), so it can be asserted
     * through {@link #assertAutoFilled()} later.
     *
     * <p><strong>NOTE: </strong>This method checks the result of text change, it should not call
     * this method too early, it may cause test fail. Call this method before checking autofill
     * behavior. {@see #expectAutoFill(String)} for how it should be used.
     */
    public void expectCreditCardAutoFill(String creditNumber) {
        mExpectation = new FillExpectation("credit", creditNumber, mCreditEditText);
        mCreditEditText.addTextChangedListener(mExpectation.mCustomFieldWatcher);
    }
}
