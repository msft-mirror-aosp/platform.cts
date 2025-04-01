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

import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.sharesheet.TestContract.UriParams;
import com.android.cts.verifier.sharesheet.TestContract.Uris;

public class SharesheetTextPlusImageActivity extends PassFailButtons.Activity {
    private static final String MIME_TYPE = "image/png";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sharesheet_payload_text_plus_image);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.sharesheet_payload_text_plus_image_test,
                R.string.sharesheet_payload_text_plus_image_test_info,
                -1);

        Button mShareBtn = findViewById(R.id.share);

        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.GONE);

        mShareBtn.setOnClickListener(
                v -> {
                    share();
                });
    }

    private void share() {
        Intent sendIntent = getTargetIntent();
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        // Since we're specifying a target component, don't auto-launch it.
        shareIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        startActivity(shareIntent);
        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.VISIBLE);
    }

    @NonNull
    private static Intent getTargetIntent() {
        Uri imageUri =
                Uris.ImageBaseUri.buildUpon()
                        .appendQueryParameter(UriParams.Name, "A")
                        .appendQueryParameter(UriParams.BgColor, Integer.toString(Color.WHITE))
                        .appendQueryParameter(UriParams.TextColor, Integer.toString(Color.BLACK))
                        .appendQueryParameter(UriParams.Type, MIME_TYPE)
                        .build();
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "A shared text.");
        sendIntent.putExtra(Intent.EXTRA_TITLE, "Share Title");
        sendIntent.setClipData(new ClipData("Image", new String[] {MIME_TYPE}, new Item(imageUri)));
        sendIntent.setDataAndType(imageUri, "text/plain");
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // TODO: investigate why Chooser does not show this activity when launched
        // sendIntent.setClass(this, getClass());
        String category = "android.cts.intent.category.MANUAL_TEST.SharesheetTextPlusImageActivity";
        sendIntent.addCategory(category);
        return sendIntent;
    }
}
