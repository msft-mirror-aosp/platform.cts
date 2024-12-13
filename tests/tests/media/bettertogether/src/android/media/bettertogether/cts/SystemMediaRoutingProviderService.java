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

package android.media.bettertogether.cts;

import static android.media.MediaRoute2Info.FLAG_ROUTING_TYPE_REMOTE;
import static android.media.MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_AUDIO;

import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.media.flags.Flags;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

/**
 * {@link MediaRoute2ProviderService} implementation that serves routes that support system media.
 *
 * @see MediaRoute2Info#getSupportedRoutingTypes()
 * @see MediaRoute2ProviderService#onCreateSystemRoutingSession
 */
public class SystemMediaRoutingProviderService extends MediaRoute2ProviderService {

    public static final String FEATURE_SAMPLE = "android.media.bettertogether.cts.FEATURE_SAMPLE";
    public static final String ROUTE_ID_ONLY_SYSTEM_AUDIO = "ROUTE_ID_ONLY_SYSTEM_AUDIO";
    public static final String ROUTE_ID_ONLY_REMOTE = "ROUTE_ID_ONLY_REMOTE";
    public static final String ROUTE_ID_BOTH_SYSTEM_AND_REMOTE = "ROUTE_ID_BOTH_SYSTEM_AND_REMOTE";

    private final Object mLock = new Object();

    /** Maps route ids to routes. */
    @GuardedBy("mLock")
    private final Map<String, MediaRoute2Info> mRoutes = new HashMap<>();

    @Override
    public void onSetRouteVolume(long requestId, @NonNull String routeId, int volume) {
        throw new IllegalStateException("Unexpected: Routes have fixed volume.");
    }

    @Override
    public void onSetSessionVolume(long requestId, @NonNull String sessionId, int volume) {
        throw new IllegalStateException("Unexpected: The session has fixed volume.");
    }

    @Override
    public void onCreateSession(
            long requestId,
            @NonNull String packageName,
            @NonNull String routeId,
            @Nullable Bundle sessionHints) {
        throw new IllegalStateException(
                "Unexpected: This provider only expects system-media routing.");
    }

    @Override
    public void onReleaseSession(long requestId, @NonNull String sessionId) {
        // TODO: b/362507305 - Implement this when testing system media routing sessions.
    }

    @Override
    public void onSelectRoute(long requestId, @NonNull String sessionId, @NonNull String routeId) {
        throw new IllegalStateException(
                "Unexpected: This provider doesn't support stream expansion.");
    }

    @Override
    public void onDeselectRoute(
            long requestId, @NonNull String sessionId, @NonNull String routeId) {
        throw new IllegalStateException(
                "Unexpected: This provider doesn't support stream expansion.");
    }

    @Override
    public void onTransferToRoute(
            long requestId, @NonNull String sessionId, @NonNull String routeId) {
        // TODO: b/362507305 - Implement this when testing system media routing sessions.
    }

    @Override
    public void onDiscoveryPreferenceChanged(@NonNull RouteDiscoveryPreference preference) {
        synchronized (mLock) {
            if (preference.shouldPerformActiveScan()) {
                initializeRoutes();
            } else {
                mRoutes.clear();
            }
            notifyRoutes(mRoutes.values());
        }
    }

    @GuardedBy("mLock")
    private void initializeRoutes() {
        if (!Flags.enableMirroringInMediaRouter2()) {
            return;
        }
        mRoutes.clear();
        // For convenience we use the id as the name of the route as well. We don't need a human
        // readable string for the test route names.
        MediaRoute2Info onlySystemAudioRoute =
                new MediaRoute2Info.Builder(
                                ROUTE_ID_ONLY_SYSTEM_AUDIO, /* name= */ ROUTE_ID_ONLY_SYSTEM_AUDIO)
                        .addFeature(FEATURE_SAMPLE)
                        .setSupportedRoutingTypes(FLAG_ROUTING_TYPE_SYSTEM_AUDIO)
                        .build();
        MediaRoute2Info onlyRemoteRoute =
                new MediaRoute2Info.Builder(ROUTE_ID_ONLY_REMOTE, /* name= */ ROUTE_ID_ONLY_REMOTE)
                        .addFeature(FEATURE_SAMPLE)
                        .build();
        MediaRoute2Info bothSystemAudioAndRemoteRoute =
                new MediaRoute2Info.Builder(
                                ROUTE_ID_BOTH_SYSTEM_AND_REMOTE,
                                /* name= */ ROUTE_ID_BOTH_SYSTEM_AND_REMOTE)
                        .setSupportedRoutingTypes(
                                FLAG_ROUTING_TYPE_SYSTEM_AUDIO | FLAG_ROUTING_TYPE_REMOTE)
                        .addFeature(FEATURE_SAMPLE)
                        .build();

        mRoutes.put(onlySystemAudioRoute.getId(), onlySystemAudioRoute);
        mRoutes.put(onlyRemoteRoute.getId(), onlyRemoteRoute);
        mRoutes.put(bothSystemAudioAndRemoteRoute.getId(), bothSystemAudioAndRemoteRoute);
    }
}
