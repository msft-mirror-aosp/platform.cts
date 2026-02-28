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
package android.cts.calmediatestapp;

import android.app.Activity;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.car.app.activity.CarAppActivity;

/** Activity that is not Distraction Optimized. Launches CarAppActivity if DO required */
public class MainActivity extends Activity {
    private static final String TAG = "CalMediaTestApp";

    private Car mCar;
    private CarUxRestrictionsManager mUxRestrictionsManager;
    private MediaBrowserCompat mMediaBrowser;
    private TextView mStatusTextView;
    private ImageButton mPlayPauseButton;

    private final MediaControllerCompat.Callback mControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    if (state == null) {
                        return;
                    }

                    if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                        mPlayPauseButton.setImageResource(R.drawable.ic_pause);
                    } else {
                        mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow);
                    }
                }
            };

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    // Get the token for the MediaSession
                    Log.d(TAG, "Connected to MediaService");
                    MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();

                    // Create a MediaControllerCompat
                    MediaControllerCompat mediaController =
                            new MediaControllerCompat(MainActivity.this, token);

                    // Save the controller
                    MediaControllerCompat.setMediaController(MainActivity.this, mediaController);
                    mediaController.registerCallback(mControllerCallback);

                    mediaController.getTransportControls().seekTo(0);
                    mediaController.getTransportControls().play();
                    mStatusTextView.setText("MainActivity (Not Distraction Optimized)");
                }

                @Override
                public void onConnectionSuspended() {
                    Log.e(TAG, "Disconnected from MediaService");
                    mStatusTextView.setText(
                            "Disconnected from MediaService, please kill and"
                                    + " relaunch the app");
                }

                @Override
                public void onConnectionFailed() {
                    Log.e(TAG, "Unable to connect to MediaService");
                    mStatusTextView.setText(
                            "Unable to connect to MediaService please kill and"
                                    + " relaunch the app");
                }
            };

    private final CarUxRestrictionsManager.OnUxRestrictionsChangedListener mUxListener =
            new CarUxRestrictionsManager.OnUxRestrictionsChangedListener() {
                @Override
                public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
                    if (restrictions.isRequiresDistractionOptimization()) {
                        Log.i(TAG, "Distraction Optimization Required! Launching CarAppActivity");
                        launchCarAppActivity();
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_test_activity_layout);

        mStatusTextView = findViewById(R.id.status_text_view);
        mStatusTextView.setText("MainActivity (Not Distraction Optimized)");

        // Start and connect to service to maintain audio
        Intent serviceIntent = new Intent(this, TestMediaService.class);
        startService(serviceIntent);

        // Create MediaBrowserServiceCompat
        mMediaBrowser =
                new MediaBrowserCompat(
                        this,
                        new ComponentName(this, TestMediaService.class),
                        mConnectionCallbacks,
                        /* rootHints= */ null);

        // Initialize view logic
        mPlayPauseButton = findViewById(R.id.play_pause_button);
        mPlayPauseButton.setOnClickListener(
                v -> {
                    MediaControllerCompat mediaController =
                            MediaControllerCompat.getMediaController(MainActivity.this);

                    if (mediaController != null) {
                        PlaybackStateCompat state = mediaController.getPlaybackState();
                        if (state != null) {
                            if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                                mediaController.getTransportControls().pause();
                            } else {
                                mediaController.getTransportControls().play();
                            }
                        }
                    }
                });

        // Connect to CarService to listen to UxR
        mCar =
                Car.createCar(
                        this,
                        null,
                        Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                        (car, ready) -> {
                            if (ready) {
                                mUxRestrictionsManager =
                                        (CarUxRestrictionsManager)
                                                car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                                if (mUxRestrictionsManager != null) {
                                    mUxRestrictionsManager.registerListener(mUxListener);
                                }
                            }
                        });
    }

    private void launchCarAppActivity() {
        Intent intent = new Intent(this, CarAppActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mMediaBrowser.isConnected()) {
            mMediaBrowser.connect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUxRestrictionsManager != null) {
            mUxRestrictionsManager.unregisterListener();
        }
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
        }
        mMediaBrowser.disconnect();
    }
}
