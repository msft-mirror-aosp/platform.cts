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

package android.media.router.cts.bluetoothpermissionsapp;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_OSW_PACKAGE_RESTRICTED_TO_BT_APP;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_VISIBILITY_RESTRICTED;
import static android.media.cts.MediaRouterTestConstants.SYSTEM_UI_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.media.cts.app.common.MediaRouter2TestUtils;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;

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
 * Device-side tests for {@link MediaRouter2} package-based route visibility.
 *
 * <p>Runs in the context of {@code android.media.router.cts.bluetoothpermissionsapp}, triggered by
 * {@code MediaRouter2HostSideTest}. Verifies that routes restricted to this package (e.g., {@code
 * ROUTE_ID_OSW_PACKAGE_RESTRICTED_TO_BT_APP}) are visible, while other restricted routes (e.g.,
 * {@code ROUTE_ID_VISIBILITY_RESTRICTED}) are hidden.
 */
public class OutputSwitcherPackageVisibilityTest {
    private ExecutorService mExecutor;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private MediaRouter2 mRouter2;
    private MediaRouter2.RouteCallback mEmptyCallback;
    private static final int TIMEOUT_MS = 5000;

    @Rule public MockitoRule initMocksRule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mExecutor = Executors.newSingleThreadExecutor();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mEmptyCallback = new MediaRouter2.RouteCallback() {};
    }

    @After
    public void tearDown() {
        if (mRouter2 != null) {
            mRouter2.unregisterRouteCallback(mEmptyCallback);
        }
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        mInstrumentation
                .getContext()
                .sendBroadcast(
                        new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        mExecutor.shutdown();
        UiAutomatorUtils2.waitUntilObjectGone(By.pkg(MEDIA_ROUTER_TEST_PACKAGE).focused(true));
    }

    @Test
    public void outputSwitcherPackageRestrictedRouteIsVisible() throws UiObjectNotFoundException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        MediaRouter2TestUtils.launchScreenOnActivity(mContext);

        RouteDiscoveryPreference discoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        mRouter2.registerRouteCallback(mExecutor, mEmptyCallback, discoveryPreference);

        mRouter2.showSystemOutputSwitcher();
        UiAutomatorUtils2.waitFindObject(
                By.text(ROUTE_NAME_OSW_PACKAGE_RESTRICTED_TO_BT_APP).pkg(SYSTEM_UI_PACKAGE),
                TIMEOUT_MS);
        UiObject2 unexpectedRoute =
                UiAutomatorUtils2.waitFindObjectOrNull(
                        By.text(ROUTE_NAME_VISIBILITY_RESTRICTED).pkg(SYSTEM_UI_PACKAGE),
                        TIMEOUT_MS);
        assertThat(unexpectedRoute).isNull();
    }
}
