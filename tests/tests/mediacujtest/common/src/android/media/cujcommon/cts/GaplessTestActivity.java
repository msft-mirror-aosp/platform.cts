/**
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

package android.media.cujcommon.cts;

import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GaplessTestActivity extends AppCompatActivity {

  protected PlayerView mExoplayerView;
  protected ExoPlayer mPlayer;
  protected static List<String> sVideoUrls = new ArrayList<>();
  protected PlayerListener mPlayerListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    buildPlayer();
  }

  /**
   * Build the player
   */
  protected void buildPlayer() {
    LoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3030, // buffer exactly one clip
                9090, // buffer three clips
                505, // start after 1/6 of the clip
                1010 // bufferForPlaybackAfterRebufferMs - 1/3rd of the clip
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();

    AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();
    mPlayer = new ExoPlayer.Builder(this)
                          .setLoadControl(loadControl)
                          .setAudioAttributes(audioAttributes, true)
                          .setHandleAudioBecomingNoisy(false)
                          .setPauseAtEndOfMediaItems(false)
                          .build();
    mPlayer.setSkipSilenceEnabled(false);
    mExoplayerView = findViewById(R.id.exoplayer);
    mExoplayerView.setPlayer(mPlayer);
  }

  /**
   * Prepare input list and add it to player's playlist.
   */
  public void prepareMediaItems(List<String> urls) {
    sVideoUrls = urls != null ? Collections.unmodifiableList(urls) : null;
    if (sVideoUrls == null) {
      return;
    }
    for (String videoUrl : sVideoUrls) {
      MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
      mPlayer.addMediaItem(mediaItem);
    }
  }

  /**
   * Prepare and play the player.
   */
  @Override
  protected void onStart() {
    super.onStart();
    mPlayer.prepare();
    mPlayer.play();
  }

  /**
   * Stop the player.
   */
  @Override
  protected void onStop() {
    mPlayer.stop();
    super.onStop();
  }

  /**
   * Release the player and destroy the activity
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    mPlayer.release();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  /**
   * Register a listener to receive events from the player.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  public void addPlayerListener(PlayerListener listener) {
    mPlayer.addListener(listener);
    this.mPlayerListener = listener;
  }

  /**
   * Unregister a listener registered through addPlayerListener(Listener). The listener will no
   * longer receive events.
   */
  public void removePlayerListener() {
    mPlayer.removeListener(this.mPlayerListener);
  }
}
