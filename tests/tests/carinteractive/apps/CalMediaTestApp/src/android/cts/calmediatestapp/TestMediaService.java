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

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

public class TestMediaService extends MediaBrowserServiceCompat {
    private static final String CHANNEL_ID = "test_media_channel";
    private static final String EMPTY_MEDIA_ROOT_ID = "test_media_root";
    private static final int NOTIFICATION_ID = 2;

    private MediaSessionCompat mMediaSession;
    private MediaPlayer mMediaPlayer;

    private final MediaSessionCompat.Callback mMediaSessionCallback =
            new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    startPlayback();
                }

                @Override
                public void onPause() {
                    pausePlayback();
                }

                @Override
                public void onSeekTo(long pos) {
                    seekTo(pos);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mMediaSession = new MediaSessionCompat(this, "TestMediaService");
        mMediaSession.setCallback(mMediaSessionCallback);

        createNotificationChannel();
        mMediaPlayer = MediaPlayer.create(this, R.raw.open_source_song);
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnCompletionListener(mp -> mMediaPlayer.seekTo(0));
        }

        showMediaNotification(PlaybackStateCompat.STATE_PAUSED);
        mMediaSession.setActive(true);
        setSessionToken(mMediaSession.getSessionToken());
    }

    private void startPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
        }
        showMediaNotification(PlaybackStateCompat.STATE_PLAYING);
    }

    private void pausePlayback() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            showMediaNotification(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    private void seekTo(long position) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo((int) position);
        }
    }

    private void showMediaNotification(int playbackState) {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent activityPendingIntent =
                PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Test Media Playing")
                        .setContentText("Background Audio Test")
                        .setSmallIcon(R.drawable.app_icon)
                        .setContentIntent(activityPendingIntent)
                        .setStyle(
                                new androidx.media.app.NotificationCompat.MediaStyle()
                                        .setMediaSession(mMediaSession.getSessionToken()));

        long duration = mMediaPlayer != null ? mMediaPlayer.getDuration() : 1000;
        MediaMetadataCompat metadata =
                new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Test Audio")
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Test Artist")
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                        .build();
        mMediaSession.setMetadata(metadata);

        long currentPosition = mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0;
        PlaybackStateCompat playbackStateCompat =
                new PlaybackStateCompat.Builder()
                        .setState(playbackState, currentPosition, 1)
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY
                                        | PlaybackStateCompat.ACTION_PAUSE
                                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                        | PlaybackStateCompat.ACTION_SEEK_TO)
                        .build();
        mMediaSession.setPlaybackState(playbackStateCompat);

        startForeground(NOTIFICATION_ID, builder.build(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
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

    @Nullable
    @Override
    public BrowserRoot onGetRoot(
            @NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(EMPTY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(
            @NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(new ArrayList<>());
    }

    private void createNotificationChannel() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID, "Media Playback", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
