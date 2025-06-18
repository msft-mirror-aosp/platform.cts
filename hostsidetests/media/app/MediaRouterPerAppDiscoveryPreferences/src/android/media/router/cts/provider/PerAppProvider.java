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

package android.media.router.cts.provider;

import android.media.MediaRoute2ProviderService;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

/** A media route provider service for testing per-app discovery preference changed events */
public class PerAppProvider extends MediaRoute2ProviderService {
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static PerAppProvider sInstance;

    private CallbackProxy mProxy;

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sLock) {
            sInstance = this;
        }
    }

    @Override
    public void onDestroy() {
        mProxy = null;
        super.onDestroy();
        synchronized (sLock) {
            if (sInstance == this) {
                sInstance = null;
            }
        }
    }

    static PerAppProvider getInstance() {
        synchronized (sLock) {
            return sInstance;
        }
    }

    /** Used by test code to find out when route provider service callbacks fire */
    interface CallbackProxy {
        default void onDiscoveryPreferenceChanged(
                @NonNull RouteDiscoveryPreference compositePreference,
                @NonNull Map<String, RouteDiscoveryPreference> perAppPreferences) {}
    }

    public void setProxy(CallbackProxy proxy) {
        this.mProxy = proxy;
    }

    @Override
    public void onDiscoveryPreferenceChanged(
            @NonNull RouteDiscoveryPreference compositePreference,
            @NonNull Map<String, RouteDiscoveryPreference> perAppPreferences) {
        if (mProxy != null) {
            mProxy.onDiscoveryPreferenceChanged(compositePreference, perAppPreferences);
        }
    }

    @Override
    public void onSetRouteVolume(long requestId, String routeId, int volume) {}

    @Override
    public void onSetSessionVolume(long requestId, String sessionId, int volume) {}

    @Override
    public void onCreateSession(
            long requestId, String packageName, String routeId, Bundle sessionHints) {}

    @Override
    public void onReleaseSession(long requestId, String sessionId) {}

    @Override
    public void onSelectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onDeselectRoute(long requestId, String sessionId, String routeId) {}

    @Override
    public void onTransferToRoute(long requestId, String sessionId, String routeId) {}
}
