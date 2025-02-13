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

package android.virtualdevice.streamedtestapp;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.Intent.EXTRA_RESULT_RECEIVER;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_RECORD_AUDIO_SUCCESS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Activity used for testing audio recording permissions on different devices. It needs to be in a
 * separate apk because CTS is automatically granted runtime permissions.
 */
public class RecordAudioTestActivity extends Activity {

    private static final String TAG = RecordAudioTestActivity.class.getSimpleName();
    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 65536;
    private static final int AUDIO_PERMISSIONS_PROPAGATION_TIME_MS = 80;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recordAudio();
    }

    @SuppressLint("MissingPermission")
    // RECORD_AUDIO permission is set externally
    void recordAudio() {
        Bundle result = new Bundle();
        AudioRecord audioRecord = null;

        try {
            // TODO: b/383048413 - use PermissionUpdateBarrierRule
            // Account for the intentional delay until the audio permissions are propagated
            SystemClock.sleep(AUDIO_PERMISSIONS_PROPAGATION_TIME_MS);

            Log.d(TAG, "Before recording on context device id " + getDeviceId()
                    + " with RECORD_AUDIO permission state " + checkSelfPermission(RECORD_AUDIO));

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT, BUFFER_SIZE);

            audioRecord.startRecording();

            Log.d(TAG, "Recording audio in RecordAudioTestActivity... source: "
                    + audioRecord.getAudioSource() + " address: "
                    + audioRecord.getRoutedDevice().getAddress());

            result.putBoolean(EXTRA_RECORD_AUDIO_SUCCESS, /* record succeeded */ true);
        } catch (Exception e) {
            Log.d(TAG, "Could not start audio recording in RecordAudioTestActivity.");
            result.putBoolean(EXTRA_RECORD_AUDIO_SUCCESS, /* record failed */ false);
        } finally {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                } catch (Exception ex) {
                    Log.w(TAG, "Exception stopping and releasing the AudioRecord: " + ex);
                }
            }
        }

        RemoteCallback resultReceiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER,
                RemoteCallback.class);
        if (resultReceiver != null) {
            resultReceiver.sendResult(result);
        }

        finish();
    }
}
