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

import static android.media.cts.MediaRouterTestConstants.PER_APP_DISCOVERY_CONSUMER_APP1_ACTIVITY;
import static android.media.cts.MediaRouterTestConstants.PER_APP_DISCOVERY_CONSUMER_APP1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.PER_APP_DISCOVERY_CONSUMER_APP2_ACTIVITY;
import static android.media.cts.MediaRouterTestConstants.PER_APP_DISCOVERY_CONSUMER_APP2_PACKAGE;
import static android.media.router.cts.consumer.App1Scanner.APP1_PREFERRED_FEATURE;
import static android.media.router.cts.consumer.App2Scanner.APP2_PREFERRED_FEATURE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.media.cts.app.common.MediaRouter2TestUtils;
import android.platform.test.annotations.LargeTest;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tests of per-app RouteDiscoveryPreference information sent in onDiscoveryPreferenceChanged. */
@LargeTest
public class PerAppDiscoveryDeviceTest {
    private static final long TIMEOUT_MS = 5000;

    private ExecutorService mExecutor;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private Activity mScreenOnActivity;
    private MediaRouter2 mRouter;
    private MediaRouter2.RouteCallback mEmptyCallback;
    private PerAppProvider mService;
    @Mock PerAppProvider.CallbackProxy mProxy;

    @Rule public MockitoRule initMocksRule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mExecutor = Executors.newSingleThreadExecutor();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mScreenOnActivity = MediaRouter2TestUtils.launchScreenOnActivity(mContext);
        mRouter = MediaRouter2.getInstance(mContext);
        mEmptyCallback = new MediaRouter2.RouteCallback() {};
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        if (mService != null) {
            mService.setProxy(null);
        }
        mRouter.unregisterRouteCallback(mEmptyCallback);
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
        mExecutor.shutdown();
    }

    @Test
    public void perAppPreferences_providerWithoutPermissionDoesNotSeePerAppPrefs() {
        getProviderServiceInstance();
        mService.setProxy(mProxy);

        launchActivity(
                PER_APP_DISCOVERY_CONSUMER_APP1_PACKAGE, PER_APP_DISCOVERY_CONSUMER_APP1_ACTIVITY);

        verify(mProxy, timeout(TIMEOUT_MS).atLeastOnce())
                .onDiscoveryPreferenceChanged(
                        argThat(preferredFeaturesContains(APP1_PREFERRED_FEATURE)),
                        argThat(Map::isEmpty));

        launchActivity(
                PER_APP_DISCOVERY_CONSUMER_APP2_PACKAGE, PER_APP_DISCOVERY_CONSUMER_APP2_ACTIVITY);

        verify(mProxy, timeout(TIMEOUT_MS).atLeastOnce())
                .onDiscoveryPreferenceChanged(
                        argThat(preferredFeaturesContains(APP2_PREFERRED_FEATURE)),
                        argThat(Map::isEmpty));

        verify(mProxy, never()).onDiscoveryPreferenceChanged(any(), argThat(map -> !map.isEmpty()));
    }

    @Test
    public void perAppPreferences_providerWithPermissionSeesPerAppPrefs() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);

        getProviderServiceInstance();
        mService.setProxy(mProxy);

        launchActivity(
                PER_APP_DISCOVERY_CONSUMER_APP1_PACKAGE, PER_APP_DISCOVERY_CONSUMER_APP1_ACTIVITY);

        verify(mProxy, timeout(TIMEOUT_MS).atLeastOnce())
                .onDiscoveryPreferenceChanged(
                        argThat(preferredFeaturesContains(APP1_PREFERRED_FEATURE)),
                        argThat(
                                perAppPrefsContains(
                                        PER_APP_DISCOVERY_CONSUMER_APP1_PACKAGE,
                                        /* activeScan= */ false,
                                        APP1_PREFERRED_FEATURE)));

        launchActivity(
                PER_APP_DISCOVERY_CONSUMER_APP2_PACKAGE, PER_APP_DISCOVERY_CONSUMER_APP2_ACTIVITY);

        verify(mProxy, timeout(TIMEOUT_MS).atLeastOnce())
                .onDiscoveryPreferenceChanged(
                        argThat(preferredFeaturesContains(APP2_PREFERRED_FEATURE)),
                        argThat(
                                perAppPrefsContains(
                                        PER_APP_DISCOVERY_CONSUMER_APP2_PACKAGE,
                                        /* activeScan= */ true,
                                        APP2_PREFERRED_FEATURE)));
    }

    /** Helper for verifying that a composite RouteDiscoveryPreference contains a given feature */
    private static ArgumentMatcher<RouteDiscoveryPreference> preferredFeaturesContains(
            String feature) {
        return (preference) ->
                preference != null && preference.getPreferredFeatures().contains(feature);
    }

    /**
     * Helper for verifying that a per-app RouteDiscoveryPreference map contains an entry for a
     * given packageName mapped to a RouteDiscoveryPreference with a given activeScan value and
     * preferredFeature.
     */
    private static ArgumentMatcher<Map<String, RouteDiscoveryPreference>> perAppPrefsContains(
            String packageName, boolean activeScan, String preferredFeature) {
        return (map) -> {
            RouteDiscoveryPreference preference = map.get(packageName);
            if (preference == null) {
                return false;
            }
            return preference.shouldPerformActiveScan() == activeScan
                    && preference.getPreferredFeatures().contains(preferredFeature);
        };
    }

    private void getProviderServiceInstance() {
        // The callback needs to be registered from an app in the foreground in order to start the
        // service.
        assertThat(mScreenOnActivity).isNotNull();

        mRouter.registerRouteCallback(
                mExecutor,
                mEmptyCallback,
                new RouteDiscoveryPreference.Builder(List.of("empty_callback"), false).build());

        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                mService = PerAppProvider.getInstance();
                return mService != null;
            }
        }.run();
        assertThat(mService).isNotNull();
    }

    private void launchActivity(String packageName, String className) {
        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
