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

package android.media.router.cts.required_permissions_app;

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_PERMISSIONS;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_PERMISSIONS;
import static android.media.cts.app.common.MediaRouter2TestUtils.launchScreenOnActivity;
import static android.media.cts.app.common.MediaRouter2TestUtils.waitForAndGetRoutes;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Device-side test for {@link MediaRouter2} routes that have visibility restricted by permissions
 * held by the app requesting them.
 */
public class MediaRouter2DeviceTestRequiredPermissions {

    private ExecutorService mExecutor;
    private Context mContext;
    private MediaRouter2 mRouter;
    private Activity mScreenOnActivity;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.dropShellPermissionIdentity();
        mRouter = MediaRouter2.getInstance(mContext);
    }

    @After
    public void tearDown() {
        mUiAutomation.clearOverridePermissionStates(myUid());
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    @Test
    public void requiredPermissions_routeNotVisibleWhenPermissionNotHeld() throws TimeoutException {
        mUiAutomation.addOverridePermissionState(
                myUid(),
                Manifest.permission.POST_NOTIFICATIONS,
                PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_APP_1_ROUTE_1),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_PERMISSIONS)).isNull();
    }

    @Test
    public void requiredPermissions_routeVisibleWhenPermissionIsHeld() throws TimeoutException {
        mUiAutomation.addOverridePermissionState(
                myUid(),
                Manifest.permission.POST_NOTIFICATIONS,
                PackageManager.PERMISSION_GRANTED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_REQUIRES_PERMISSIONS),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_PERMISSIONS).getName()).isEqualTo(
                ROUTE_NAME_REQUIRES_PERMISSIONS);
    }
}
