/*
 * Copyright 2026 The Android Open Source Project
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
package android.cts.mediatestapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.TextView;

/** Activity that that starts MediaService and starts playing audio */
public class MediaTestActivity extends Activity {

    private static final String TAG = "MediaTestActivity";
    private MediaBrowserCompat mMediaBrowser;
    private TextView mStatusTextView;

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    // Get the token for the MediaSession
                    Log.d(TAG, "Connected to MediaService");
                    MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();

                    // Create a MediaControllerCompat
                    MediaControllerCompat mediaController =
                            new MediaControllerCompat(MediaTestActivity.this, token);

                    // Save the controller
                    MediaControllerCompat.setMediaController(
                            MediaTestActivity.this, mediaController);

                    mediaController.getTransportControls().seekTo(0);
                    mediaController.getTransportControls().play();
                    mStatusTextView.setText(getString(R.string.status_text));
                }

                @Override
                public void onConnectionSuspended() {
                    Log.e(TAG, "Disconnected from MediaService");
                    mStatusTextView.setText(getString(R.string.media_service_error));
                }

                @Override
                public void onConnectionFailed() {
                    Log.e(TAG, "Unable to connect to MediaService");
                    mStatusTextView.setText(getString(R.string.media_service_error));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.media_test_activity_layout);
        mStatusTextView = requireViewById(R.id.status_text_view);

        // Start and connect to service
        Intent serviceIntent = new Intent(this, MediaService.class);
        startService(serviceIntent);

        // Create MediaBrowserServiceCompat
        mMediaBrowser =
                new MediaBrowserCompat(
                        this,
                        new ComponentName(this, MediaService.class),
                        mConnectionCallbacks,
                        /* rootHints= */ null);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mMediaBrowser.isConnected()) {
            mMediaBrowser.connect();
        }
    }

    @Override
    public void onDestroy() {
        mMediaBrowser.disconnect();
    }
}
