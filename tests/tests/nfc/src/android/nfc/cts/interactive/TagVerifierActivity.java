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

package android.nfc.cts.interactive;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.cts.R;
import android.nfc.tech.cts.interactive.NdefTagTester;
import android.nfc.tech.cts.interactive.TagTester;
import android.nfc.tech.cts.interactive.TagVerifier;
import android.nfc.tech.cts.interactive.TagVerifier.Result;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

/**
 * Activity used by CTS-Interactive test to verify Tag
 */
public class TagVerifierActivity extends AppCompatActivity {

    private static final String TAG = "TagVerifierActivity";
    public static boolean sWriteComplete = false;
    public static boolean sVerifyComplete = false;
    public static Result sResult = null;
    private static Tag sTag;

    private TagTester mTagTester;
    private TagVerifier mTagVerifier;
    private LinearLayout mRootView;
    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;

    public static Result getResult() {
        return sResult;
    }

    public static boolean getWriteComplete() {
        return sWriteComplete;
    }

    public static boolean getVerifyComplete() {
        return sVerifyComplete;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTagTester = new NdefTagTester(this);
        NfcManager nfcManager = getSystemService(NfcManager.class);
        mNfcAdapter = nfcManager.getDefaultAdapter();
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE_UNAUDITED);
        setContentView(R.layout.activity_main);
        mRootView = findViewById(R.id.activity_main);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mNfcAdapter.isEnabled()) {
            Log.e(TAG, "NFC is not enabled");

        }
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            sTag = tag;
            if (!sWriteComplete) {
                handleWriteStep(tag);
            } else if (!sVerifyComplete) {
                handleVerifyStep();
            }
        }
    }

    private void handleWriteStep(Tag tag) {
        if (mTagTester.isTestableTag(tag)) {
            new WriteTagTask().execute(sTag);
        } else {
            Snackbar.make(mRootView, getResources().getString(R.string.nfc_wrong_tag_title),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private void handleVerifyStep() {
        new VerifyTagTask().execute(sTag);
    }

    class WriteTagTask extends AsyncTask<Tag, Void, TagVerifier> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected TagVerifier doInBackground(Tag... tags) {
            try {
                return mTagTester.writeTag(tags[0]);
            } catch (Exception e) {
                Log.e(TAG, "Error writing NFC tag...", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(TagVerifier tagVerifier) {
            mTagVerifier = tagVerifier;
            int stringResId;
            if (tagVerifier != null) {
                sWriteComplete = true;
                stringResId = R.string.nfc_successful_write;
            } else {
                stringResId = R.string.nfc_writing_tag_error;
            }
            Snackbar.make(mRootView, getResources().getString(stringResId),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    class VerifyTagTask extends AsyncTask<Tag, Void, Result> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Result doInBackground(Tag... tags) {
            try {
                return mTagVerifier.verifyTag(tags[0]);
            } catch (Exception e) {
                Log.e(TAG, "Error verifying NFC tag...", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            TagVerifierActivity.sResult = result;
            sVerifyComplete = true;
            if (sResult != null) {
                mTagVerifier = null;
            } else {
                Snackbar.make(mRootView, getResources().getString(R.string.nfc_reading_tag_error),
                        Snackbar.LENGTH_SHORT).show();
            }
        }
    }

}
