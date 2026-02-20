/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.media.router.cts.output.switcher.creator;

import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.os.IBinder;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;

/** Creates a media session on behalf of another app (using the owner overriding functionality). */
public class MediaSessionCreatorService extends Service {
    private static final String MEDIA_SESSION_TAG = "MediaSessionCreatorService";

    public static final CountDownLatch sServiceDestroyedLatch = new CountDownLatch(1);

    private MediaSession mMediaSession;

    private final IMediaSessionCreator.Stub mBinder =
            new IMediaSessionCreator.Stub() {
                @Override
                public MediaSession.Token createSession(String ownerPackageName) {
                    // Grants OVERRIDE_MEDIA_SESSION_OWNER (requested in the manifest).
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .adoptShellPermissionIdentity();
                    if (mMediaSession != null) {
                        mMediaSession.release();
                    }
                    mMediaSession =
                            new MediaSession(
                                    MediaSessionCreatorService.this,
                                    MEDIA_SESSION_TAG,
                                    /* sessionInfo= */ null,
                                    ownerPackageName);
                    mMediaSession.setActive(true);
                    return mMediaSession.getSessionToken();
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
        sServiceDestroyedLatch.countDown();
        super.onDestroy();
    }
}
