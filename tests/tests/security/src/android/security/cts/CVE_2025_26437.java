/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.security.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialRequest;
import android.credentials.ICredentialManager;
import android.credentials.IGetCandidateCredentialsCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.ServiceManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26437 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 370477460)
    @Test
    public void testPocCVE_2025_26437() {
        try {
            // Create a bundle and configure it.
            final Bundle passkeyCandidateQueryData = new Bundle();
            passkeyCandidateQueryData.putString(
                    "androidx.credentials.BUNDLE_KEY_REQUEST_JSON",
                    "{'challenge': '', 'rpId': 'cve_2025_26437}");

            // Create and configure a credentialRequest.
            final GetCredentialRequest credentialRequest =
                    new GetCredentialRequest.Builder(Bundle.EMPTY)
                            .setCredentialOptions(
                                    List.of(
                                            new CredentialOption.Builder(
                                                            Credential.TYPE_PASSWORD_CREDENTIAL,
                                                            Bundle.EMPTY,
                                                            Bundle.EMPTY)
                                                    .build(),
                                            new CredentialOption.Builder(
                                                            "androidx.credentials."
                                                                    + "TYPE_PUBLIC_KEY_CREDENTIAL",
                                                            Bundle.EMPTY,
                                                            passkeyCandidateQueryData)
                                                    .build()))
                            .build();

            // With the fix: Validates the calling package and throws 'SecurityException' if
            // unauthorized
            // Without the fix: Function executes normally, returning a non-null value
            final Object result =
                ICredentialManager.Stub.asInterface(
                        ServiceManager.getService(Context.CREDENTIAL_SERVICE))
                    .getCandidateCredentials(
                        credentialRequest,
                        IGetCandidateCredentialsCallback.Stub.asInterface(new Binder()),
                        new Binder(),
                        "android.security.cts");
            assertWithMessage(
                            "Device is vulnerable to b/370477460. There is a possible way to"
                                    + " retrieve candidate credentials due to a missing permission"
                                    + " check")
                    .that(result)
                    .isNull();
        } catch (SecurityException e) {
            if (e.getMessage().contains("is not the device's credential autofill package")) {
                return;
            } else {
                assume().that(e).isNull();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
