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
import android.media.tv.ad.TvAdService;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stub implementation of {@link android.media.tv.ad.TvAdService}.
 */
public class StubTvAdService extends TvAdService {

    public static StubSessionImpl sSession;
    @Nullable
    @Override
    public Session onCreateSession(@NonNull String serviceId, @NonNull String type) {
        sSession = new StubSessionImpl(this);
        return sSession;
    }

    public static class StubSessionImpl extends TvAdService.Session {
        public int mStartAdServiceCount;
        public int mStopAdServiceCount;

        /**
         * Creates a new Session.
         *
         * @param context The context of the application
         */
        public StubSessionImpl(@NonNull Context context) {
            super(context);
        }

        /**
         * Resets values.
         */
        public void resetValues() {
            mStartAdServiceCount = 0;
            mStopAdServiceCount = 0;
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(@Nullable Surface surface) {
            return false;
        }

        @Override
        public void onStartAdService() {
            mStartAdServiceCount++;
        }

        @Override
        public void onStopAdService() {
            mStopAdServiceCount++;
        }
    }
}