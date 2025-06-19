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

package android.media.cts.app.common;

import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;

import java.util.List;

/**
 * A simple activity which starts scanning for media routes as soon as it is created. This is useful
 * to cause registered MediaRoute2ProviderService services to be created and sent
 * onDiscoveryPreferenceChanged callbacks.
 */
public abstract class BaseScanningActivity extends ScreenOnActivity {

    /** Return the preferred features to scan for. */
    public abstract List<String> getPreferredFeatures();

    /** Return whether to perform active scanning. */
    public abstract boolean shouldRequestActiveScanning();

    MediaRouter2 mRouter;
    MediaRouter2.RouteCallback mEmptyCallback;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mRouter = MediaRouter2.getInstance(this);
        mEmptyCallback = new MediaRouter2.RouteCallback() {};
        mRouter.registerRouteCallback(
                Runnable::run,
                mEmptyCallback,
                new RouteDiscoveryPreference.Builder(
                                getPreferredFeatures(), shouldRequestActiveScanning())
                        .build());
    }

    @Override
    protected void onDestroy() {
        if (mEmptyCallback != null && mRouter != null) {
            mRouter.unregisterRouteCallback(mEmptyCallback);
        }
        super.onDestroy();
    }
}
