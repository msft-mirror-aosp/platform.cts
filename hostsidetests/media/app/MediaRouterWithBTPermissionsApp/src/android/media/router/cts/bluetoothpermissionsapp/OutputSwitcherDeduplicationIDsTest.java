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

package android.media.router.cts.bluetoothpermissionsapp;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_DEDUPLICATION_PROVIDER_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_DEDUPLICATION_PROVIDER_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_DEDUPLICATION_SAME_PROVIDER_NON_SYSTEM;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_DEDUPLICATION_SAME_PROVIDER_SYSTEM;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_DIFFERENT_DEDUPLICATION_ID_SAME_NAME;
import static android.media.cts.MediaRouterTestConstants.SYSTEM_UI_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.cts.app.common.MediaRouter2TestUtils;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.UiAutomatorUtils2;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Device-side tests for {@link MediaRouter2} route deduplication in the Output Switcher.
 *
 * <p>Runs in the context of {@code android.media.router.cts.bluetoothpermissionsapp}, triggered by
 * {@code MediaRouter2HostSideTest}. Verifies that the System UI correctly deduplicates routes
 * sharing the same deduplication ID, prioritizes non-system routes over system routes, and displays
 * routes with different deduplication IDs as distinct entries.
 */
public class OutputSwitcherDeduplicationIDsTest {
    private ExecutorService mExecutor;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private MediaRouter2 mRouter2;
    private MediaRouter2.RouteCallback mEmptyCallback;
    private static final int TIMEOUT_MS = 15000;

    @Rule public MockitoRule initMocksRule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mExecutor = Executors.newSingleThreadExecutor();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mEmptyCallback = new MediaRouter2.RouteCallback() {};
        MediaRouter2TestUtils.launchScreenOnActivity(mContext);
    }

    @After
    public void tearDown() {
        if (mRouter2 != null) {
            mRouter2.unregisterRouteCallback(mEmptyCallback);
        }
        mInstrumentation
                .getContext()
                .sendBroadcast(
                        new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        mExecutor.shutdown();
        UiAutomatorUtils2.waitUntilObjectGone(By.pkg(MEDIA_ROUTER_TEST_PACKAGE).focused(true));
    }

    @Test
    public void deduplication_sameIdAcrossProviders_onlyShowOne() throws Exception {
        RouteDiscoveryPreference discoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        mRouter2.registerRouteCallback(mExecutor, mEmptyCallback, discoveryPreference);

        mRouter2.showSystemOutputSwitcher();
        UiObject2 foundRouteUiFromProvider2 =
                UiAutomatorUtils2.waitFindObjectOrNull(
                        By.text(ROUTE_NAME_OSW_DEDUPLICATION_PROVIDER_2).pkg(SYSTEM_UI_PACKAGE),
                        TIMEOUT_MS);
        UiObject2 foundRouteUiFromProvider3 =
                UiAutomatorUtils2.waitFindObjectOrNull(
                        By.text(ROUTE_NAME_OSW_DEDUPLICATION_PROVIDER_3).pkg(SYSTEM_UI_PACKAGE),
                        TIMEOUT_MS);
        // Assert that exactly one of the routes is found, as they should be deduplicated.
        assertThat((foundRouteUiFromProvider2 != null) ^ (foundRouteUiFromProvider3 != null))
                .isTrue();
    }

    @Test
    public void deduplication_sameIdSystemAndNonSystem_onlyShowNonSystem() throws Exception {
        RouteDiscoveryPreference discoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        mRouter2.registerRouteCallback(mExecutor, mEmptyCallback, discoveryPreference);

        mRouter2.showSystemOutputSwitcher();
        UiAutomatorUtils2.waitFindObject(
                By.text(ROUTE_NAME_OSW_DEDUPLICATION_SAME_PROVIDER_NON_SYSTEM)
                        .pkg(SYSTEM_UI_PACKAGE),
                TIMEOUT_MS);
        UiObject2 unexpectedRoute =
                UiAutomatorUtils2.waitFindObjectOrNull(
                        By.text(ROUTE_NAME_OSW_DEDUPLICATION_SAME_PROVIDER_SYSTEM)
                                .pkg(SYSTEM_UI_PACKAGE),
                        TIMEOUT_MS);
        assertThat(unexpectedRoute).isNull();
    }

    @Test
    public void deduplication_differentIdsSameName_showBoth() throws Exception {
        RouteDiscoveryPreference discoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        mRouter2.registerRouteCallback(mExecutor, mEmptyCallback, discoveryPreference);

        String[] mFullRouteId = new String[2];
        waitForRoutesWithSameName(
                ROUTE_NAME_OSW_DIFFERENT_DEDUPLICATION_ID_SAME_NAME, mFullRouteId);
        RouteListingPreference.Item customSubtextItem1 =
                new RouteListingPreference.Item.Builder(mFullRouteId[0])
                        .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM)
                        .setCustomSubtextMessage("message1")
                        .build();
        RouteListingPreference.Item customSubtextItem2 =
                new RouteListingPreference.Item.Builder(mFullRouteId[1])
                        .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM)
                        .setCustomSubtextMessage("message2")
                        .build();
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder()
                        .setItems(List.of(customSubtextItem1, customSubtextItem2))
                        .build();
        mRouter2.setRouteListingPreference(routeListingPreference);

        mRouter2.showSystemOutputSwitcher();
        UiAutomatorUtils2.waitFindObject(By.text("message1").pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text("message2").pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
    }

    /**
     * Waits for the router to discover a specific number of routes with the given name. populates
     * the outIds array with the IDs of the found routes.
     *
     * @param routeName The name of the routes to look for.
     * @param outIds An array to store the IDs of the found routes. The length of this array
     *     determines the expected number of routes.
     */
    private void waitForRoutesWithSameName(String routeName, String[] outIds) {
        PollingCheck.waitFor(
                TIMEOUT_MS,
                () -> {
                    int routeCount = 0;
                    List<MediaRoute2Info> routes = mRouter2.getRoutes();
                    for (MediaRoute2Info route : routes) {
                        if (routeName.equals(route.getName().toString())) {
                            assertThat(routeCount).isLessThan(outIds.length);
                            outIds[routeCount++] = route.getId();
                        }
                    }
                    return routeCount == outIds.length;
                });
    }
}
