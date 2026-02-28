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

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

/** Service that loads and manages media session */
public class MediaService extends MediaBrowserServiceCompat {

    private static final String TAG = "MediaService";
    private static final String EMPTY_MEDIA_ROOT_ID = "my_root_id";
    private MediaSessionCompat mMediaSession;
    private MediaPlayer mMediaPlayer;

    private final MediaSessionCompat.Callback mMediaSessionCallback =
            new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    Log.d(TAG, "onPlay");
                    startPlayback();
                }

                @Override
                public void onPause() {
                    Log.d(TAG, "onPause");
                    pausePlayback();
                }

                @Override
                public void onSeekTo(long pos) {
                    Log.d(TAG, "onSeekTo " + pos);
                    seekTo(pos);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();

        // Create MediaSession
        mMediaSession = new MediaSessionCompat(this, TAG);
        mMediaSession.setCallback(mMediaSessionCallback);

        mMediaPlayer = MediaPlayer.create(this, R.raw.open_source_song);
        mMediaPlayer.setOnCompletionListener(mp -> mMediaPlayer.seekTo(0));
        mMediaSession.setActive(true);
        setSessionToken(mMediaSession.getSessionToken());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
        mMediaSession.setActive(false);
        mMediaSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(
            @NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(EMPTY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(
            @NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        //  No browsable items
        result.sendResult(new ArrayList<>());
    }

    private void startPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
        }
    }

    private void pausePlayback() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }
    }

    private void seekTo(long position) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo((int) position);
        }
    }
}
