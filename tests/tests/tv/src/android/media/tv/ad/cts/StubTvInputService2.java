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

package android.media.tv.ad.cts;

import android.content.Context;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.view.Surface;

import androidx.annotation.Nullable;

public class StubTvInputService2 extends TvInputService {
    static String sTvInputSessionId;
    public static StubSessionImpl2 sStubSessionImpl2;

    public static String getSessionId() {
        return sTvInputSessionId;
    }

    @Override
    public Session onCreateSession(String inputId, String tvInputSessionId) {
        sTvInputSessionId = tvInputSessionId;
        sStubSessionImpl2 = new StubSessionImpl2(this);
        return sStubSessionImpl2;
    }

    @Override
    public Session onCreateSession(String inputId) {
        return new StubSessionImpl2(this);
    }

    public static class StubSessionImpl2 extends Session {

        StubSessionImpl2(Context context) {
            super(context);
        }

        /**
         * Resets values.
         */
        public void resetValues() {
        }

        @Override
        public void onRelease() {
            sTvInputSessionId = null;
        }

        @Override
        public boolean onSetSurface(@Nullable Surface surface) {
            return false;
        }

        @Override
        public void onSetStreamVolume(float volume) {
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return false;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
        }
    }
}