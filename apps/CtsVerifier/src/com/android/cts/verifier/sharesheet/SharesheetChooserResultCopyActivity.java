/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.verifier.sharesheet;

import static android.service.chooser.ChooserResult.CHOOSER_RESULT_COPY;

import android.content.Intent;
import android.os.Bundle;
import android.service.chooser.ChooserResult;

import com.android.cts.verifier.R;

public final class SharesheetChooserResultCopyActivity extends SharesheetChooserResultActivity {
    @Override
    protected Intent getTestActivityIntent() {
        return new Intent(this, SharesheetChooserResultCopyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected Intent createChooserIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.sharesheet_result_test_copy_instructions_short));
        sendIntent.setType("text/plain");
        return wrapWithChooserIntent(sendIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setInfoResources(
                R.string.sharesheet_result_test_copy,
                R.string.sharesheet_result_test_copy_info,
                -1);

        setInstructions(R.string.sharesheet_result_test_copy_instructions);

        setAfterShareButtonLabels(
                R.string.sharesheet_result_test_copy_pressed,
                R.string.sharesheet_result_test_copy_not_found);

        setExpectedResult(new ChooserResult(CHOOSER_RESULT_COPY, null, false));
    }
}
