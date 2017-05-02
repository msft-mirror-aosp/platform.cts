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

import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.autofill.AutofillManager;

import static com.google.common.truth.Truth.assertWithMessage;

/**
 * This class simulates authentication at the dataset at reponse level
 */
public class AuthenticationActivity extends AbstractAutoFillActivity {
    private static CannedFillResponse sResponse;
    private static CannedFillResponse.CannedDataset sDataset;
    private static Bundle sData;

    public static void setResponse(CannedFillResponse response) {
        sResponse = response;
        sDataset = null;
    }

    public static void setDataset(CannedFillResponse.CannedDataset dataset) {
        sDataset = dataset;
        sResponse = null;
    }

    public static Bundle getData() {
        final Bundle data = sData;
        sData = null;
        return data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We should get the assist structure...
        final AssistStructure structure = getIntent().getParcelableExtra(
                AutofillManager.EXTRA_ASSIST_STRUCTURE);
        assertWithMessage("structure not called").that(structure).isNotNull();

        // and the bundle
        sData = getIntent().getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE);

        final Parcelable result;
        if (sResponse != null) {
            result = sResponse.asFillResponse((id) -> Helper.findNodeByResourceId(structure, id));
        } else if (sDataset != null) {
            result = sDataset.asDataset((id) -> Helper.findNodeByResourceId(structure, id));
        } else {
            throw new IllegalStateException("no dataset or response");
        }

        // Pass on the auth result
        final Intent intent = new Intent();
        intent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result);
        setResult(RESULT_OK, intent);

        // Done
        finish();
    }
}
