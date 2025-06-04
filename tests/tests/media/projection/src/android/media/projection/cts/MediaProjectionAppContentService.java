/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.projection.cts;

import android.graphics.Bitmap;
import android.media.projection.AppContentProjectionService;
import android.media.projection.AppContentProjectionSession;
import android.media.projection.AppContentRequest;
import android.media.projection.MediaProjectionAppContent;

import androidx.annotation.NonNull;

import java.util.List;

/** Test service implementation of {@link AppContentProjectionService} */
public class MediaProjectionAppContentService extends AppContentProjectionService {

    public static final List<MediaProjectionAppContent> TEST_APP_CONTENT_LIST =
            List.of(
                    createAppContent("Title1", 1),
                    createAppContent("Title2", 2),
                    createAppContent("Title3", 3));

    public enum State {
        NOT_CALLED,
        CONTENT_REQUESTED,
        SESSION_STARTED,
        REQUEST_CANCELLED,
        SESSION_STOPPED
    }

    public static AppContentProjectionSession sStartedSession = null;
    public static AppContentProjectionSession sStoppedSession = null;
    public static AppContentRequest sLastAppContentRequest = null;
    public static State sState = State.NOT_CALLED;

    /** Resets the static field of this class to their initial value. */
    public static void reset() {
        sState = State.NOT_CALLED;
        sStartedSession = null;
        sStoppedSession = null;
        sLastAppContentRequest = null;
    }

    @Override
    public void onContentRequest(@NonNull AppContentRequest request) {
        sState = State.CONTENT_REQUESTED;
        sLastAppContentRequest = request;
        request.provideContent(TEST_APP_CONTENT_LIST);
    }

    @NonNull
    private static MediaProjectionAppContent createAppContent(String title, int id) {
        return new MediaProjectionAppContent(
                Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888), title, id);
    }

    @Override
    public boolean onLoopbackProjectionStarted(
            @NonNull AppContentProjectionSession session, int contentId) {
        sState = State.SESSION_STARTED;
        sStartedSession = session;
        return true;
    }

    @Override
    public void onSessionStopped(@NonNull AppContentProjectionSession session) {
        sState = State.SESSION_STOPPED;
        sStoppedSession = session;
    }

    @Override
    public void onContentRequestCanceled() {
        sState = State.REQUEST_CANCELLED;
    }

    /**
     * Stops the current session or throws an {@link java.lang.IllegalStateException} if no session
     * is currently active
     */
    public static void stopSession() {
        AppContentProjectionSession startedSession = sStartedSession;
        if (startedSession == null) {
            throw new IllegalStateException(
                    "AppContentProjectionSession#notifySessionStop cannot be called. No started "
                            + "session");
        }
        startedSession.notifySessionStop();
    }
}
