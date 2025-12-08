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

package com.android.cts.verifier.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Test for secure playback that requires a Playback Analysis Tool attached & aligned to the
 * device's screen.
 *
 * This test activity requires a USB connection to a computer, and a corresponding host-side run
 * of the Python scripts found in the SecurePlaybackTestApp directory. The host-side script sends
 * intents to this activity to start playback of a DRM-protected video stream. The script then uses
 * a Playback Analysis Tool to analyze the video output on the device's screen to verify that
 * secure playback is working correctly. The results are then sent back to this activity.
 */
public class SecurePlaybackTestActivity extends PassFailButtons.Activity {

    private static final String TAG = "SecurePlaybackTest";
    private static final String ACTION_SECURE_PLAYBACK_START =
            "com.android.cts.verifier.media.ACTION_SECURE_PLAYBACK_START";
    private static final String ACTION_SECURE_PLAYBACK_RESULT =
            "com.android.cts.verifier.media.ACTION_SECURE_PLAYBACK_RESULT";
    private static final String SECURE_PLAYBACK_RESULTS = "media.secureplayback.extra.RESULTS";
    private static final String SECURE_PLAYBACK_CODEC_NAME =
            "media.secureplayback.extra.CODEC_NAME";
    private static final String SECURE_PLAYBACK_NUM_DROPPED_FRAMES =
            "media.secureplayback.extra.NUM_DROPPED_FRAMES";
    private static final String SECURE_PLAYBACK_STREAMING_URI =
            "media.secureplayback.extra.STREAMING_URI";
    private static final String SECURE_PLAYBACK_VIDEO_SCALING =
            "media.secureplayback.extra.VIDEO_SCALING";

    private static final String RESULT_PASS = "PASS";

    private final HostBroadcastReceiver mHostBroadcastReceiver = new HostBroadcastReceiver();
    private boolean mReceiverRegistered = false;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private static final int NETWORK_TIMEOUT_MS = 5000;

    private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    private static final String LICENSE_URL = "https://proxy.uat.widevine.com/proxy?provider=widevine_test";
    private static final String WIDEVINE_WEBSITE = "widevine.com";

    private ExoPlayer player;
    private PlayerView playerView;
    private View passFailButtons;
    private DrmSessionManager drmSessionManager;
    private TextView instructions;

    private CtsVerifierReportLog reportLog;
    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";

    /**
     * Receives commands from the host-side script to start playback or record test results.
     */
    class HostBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SECURE_PLAYBACK_START.equals(intent.getAction())) {
                Log.d(TAG, "Received start action");
                if (!canConnectToWidevine()) {
                    Log.e(TAG, "Cannot connect to Widevine. Please check your network settings.");
                    instructions.setText(R.string.secure_playback_test_info_no_connection);
                    return;
                }
                String streamingUri = intent.getStringExtra(SECURE_PLAYBACK_STREAMING_URI);
                if (streamingUri == null) {
                    Log.e(TAG, "No valid URI was passed");
                    return;
                }
                float videoScaling = intent.getFloatExtra(SECURE_PLAYBACK_VIDEO_SCALING, 1.0f);
                setNonPlayerVisibility(false);
                Log.d(TAG, "Received video scaling " + videoScaling);
                scalePlayerView(videoScaling);
                startPlaying(getStreamingMediaSource(streamingUri));
            } else if (ACTION_SECURE_PLAYBACK_RESULT.equals(intent.getAction())) {
                String results = intent.getStringExtra(SECURE_PLAYBACK_RESULTS);
                Log.d(TAG, "Received result: " + results);
                boolean testPassed = results.equals(RESULT_PASS);
                if (testPassed) {
                    // All tests completed - report results in one log.
                    reportLog.submit();
                    SecurePlaybackTestActivity.this.getPassButton().setEnabled(testPassed);
                    setTestResultAndFinish(testPassed);
                } else {
                    // Single test completed - report results by codec.
                    String codecName = intent.getStringExtra(SECURE_PLAYBACK_CODEC_NAME);
                    int numDroppedFrames = intent.getIntExtra(SECURE_PLAYBACK_NUM_DROPPED_FRAMES, -1);
                    Log.d(TAG, "Dropped frames for " + codecName + ": " + numDroppedFrames);
                    reportLog.addValue(
                            codecName + "_dropped_frames",
                            numDroppedFrames,
                            ResultType.LOWER_BETTER,
                            ResultUnit.NONE);
                }
            } else {
                Log.d(TAG, "Received unknown action: " + intent.getAction());
            }
        }
    }

    private class PlayerEventListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                setNonPlayerVisibility(true);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "Player error: " + error.getMessage());
            setNonPlayerVisibility(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.secure_playback);
        setPassFailButtonClickListeners();
        passFailButtons = findViewById(R.id.pass_fail_buttons);

        if (!isL1()) {
            Log.d(TAG, "Device is not L1, skipping test");
            setTestResultAndFinish(true);
            return;
        }

        this.getPassButton().setEnabled(true);

        instructions = findViewById(R.id.instructions);
        instructions.setText(R.string.secure_playback_test_info);

        if (!canConnectToWidevine()) {
            Log.e(TAG, "Cannot connect to Widevine. Please check your network settings.");
            instructions.setText(R.string.secure_playback_test_info_no_connection);
        }

        playerView = findViewById(R.id.player_view);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);

        setNonPlayerVisibility(true);

        if (reportLog == null) {
            reportLog = new CtsVerifierReportLog(REPORT_LOG_NAME, "media_secureplayback_results");
        }
    }

    /**
     * Hides system UI components to make the Playback Analysis Tool easier to align.
     */
    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    /**
     * Sets the visibility of this activity's UI elements other than the player view.
     *
     * @param visible {@code true} to show non-player UI elements, {@code false} to hide them.
     */
    private void setNonPlayerVisibility(boolean visible) {
        instructions.setVisibility(visible ? View.VISIBLE : View.GONE);
        passFailButtons.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Scales the player view based on a scaling factor.
     * <p>
     * This can make alignment easier when the device's screen is too large.
     *
     * @param videoScaling The factor by which to scale the player view.
     */
    private void scalePlayerView(float videoScaling) {
        View parent = (View) playerView.getParent();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        int newWidth = (int) (parentWidth * videoScaling);
        int newHeight = (int) (parentHeight * videoScaling);

        ViewGroup.LayoutParams layoutParams = playerView.getLayoutParams();
        layoutParams.width = newWidth;
        layoutParams.height = newHeight;
        playerView.setLayoutParams(layoutParams);
    }

    private void initializePlayer() {
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(
                LICENSE_URL, new DefaultHttpDataSource.Factory());
        drmSessionManager = new DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(drmCallback);

        player = new ExoPlayer.Builder(this).build();
        player.addAnalyticsListener(new EventLogger());
        player.addListener(new PlayerEventListener());
        playerView.setPlayer(player);
    }

    /**
     * Creates a {@link MediaSource} for the given streaming URI.
     *
     * @param uri The URI of the media to be played.
     * @return A {@link MediaSource} configured for DASH streaming with Widevine DRM.
     */
    private MediaSource getStreamingMediaSource(String uri) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        MediaSource mediaSource = new DashMediaSource.Factory(new DefaultHttpDataSource.Factory())
                .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
                .createMediaSource(mediaItem);
        return mediaSource;
    }

    /**
     * Starts playback of the provided media source.
     *
     * @param mediaSource The {@link MediaSource} to play.
     */
    private void startPlaying(MediaSource mediaSource) {
        if (player == null) {
            Log.e(TAG, "Cannot start playing, player is null!");
            return;
        }
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        setNonPlayerVisibility(true);
    }

    private boolean isL1() throws IllegalStateException {
        try (MediaDrm mediaDrm = new MediaDrm(WIDEVINE_UUID)) {
            return mediaDrm.getPropertyString("securityLevel").equals("L1");
        } catch (UnsupportedSchemeException e) {
            throw new IllegalStateException("Widevine MediaDrm instantiation fails", e);
        }
    }

    private boolean canConnectToWidevine() {
        Future<Boolean> future = networkExecutor.submit(() -> {
            try {
                InetAddress.getAllByName(WIDEVINE_WEBSITE);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        });

        try {
            return future.get(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Could not connect to Widevine", e);
            return false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        initializePlayer();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SECURE_PLAYBACK_START);
        filter.addAction(ACTION_SECURE_PLAYBACK_RESULT);
        registerReceiver(mHostBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        mReceiverRegistered = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mReceiverRegistered) {
            unregisterReceiver(mHostBroadcastReceiver);
        }
        releasePlayer();
        networkExecutor.shutdown();
    }
}
