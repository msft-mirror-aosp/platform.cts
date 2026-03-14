/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.content.pm.PackageManager.FEATURE_LIVE_WALLPAPER;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SET_WALLPAPER;
import static com.android.bedstead.permissions.CommonPermissions.READ_WALLPAPER_INTERNAL;
import static com.android.bedstead.permissions.CommonPermissions.SET_WALLPAPER;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;

import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.enterprise.policies.Wallpaper;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BitmapUtils;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO (b/284309054): Add test for WallpaperManager#setResource
@RunWith(BedsteadJUnit4.class)
@RequireFeature(value = FEATURE_LIVE_WALLPAPER, reason = "WallpaperManager depends on this feature")
@RequireNotAutomotive(reason = "AAOS doesn't support wallpaper but gsi_car reports that it does. "
        + "See b/328312997")
public final class WallpaperTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Bitmap sOriginalWallpaper = TestApis.wallpaper().getBitmap();

    private static final TestApp sTestApp = testApps(sDeviceState).any();

    private static final Bitmap sReferenceWallpaper = BitmapUtils.generateRandomBitmap(97, 73);

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @PolicyAppliesTest(policy = Wallpaper.class)
    public void setBitmap_viaDpc_disallowed_canSet() throws Exception {
        try (CleanUpWallpaperWithDpcResource cleanUpWallpaperResource =
                new CleanUpWallpaperWithDpcResource()) {
            dpc(sDeviceState).wallpaperManager().setBitmap(sReferenceWallpaper);

            assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                    sReferenceWallpaper)).isTrue();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @PolicyDoesNotApplyTest(policy = Wallpaper.class)
    public void setBitmap_viaDpc_disallowed_cannotSet() throws Exception {
        try (CleanUpWallpaperWithTestApiResource cleanUpWallpaperResource =
                new CleanUpWallpaperWithTestApiResource()) {
            dpc(sDeviceState).wallpaperManager().setBitmap(sReferenceWallpaper);

            assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                    sOriginalWallpaper)).isTrue();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SET_WALLPAPER)
    @Ignore("b/447077454")
    @Test
    public void setBitmap_allowed_canSet() throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install();
                CleanUpWallpaperWithTestApiResource cleanUpWallpaperResource =
                        new CleanUpWallpaperWithTestApiResource()) {
            testAppInstance.wallpaperManager().setBitmap(sReferenceWallpaper);

            assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                    sReferenceWallpaper)).isTrue();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setBitmap_disallowed_cannotSet() throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install()) {
            testAppInstance.wallpaperManager().setBitmap(sReferenceWallpaper);

            assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                    sOriginalWallpaper)).isTrue();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setStream")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SET_WALLPAPER)
    @Ignore("b/442331697")
    @Test
    public void setStream_allowed_canSet() throws Exception {
        try (CleanUpWallpaperWithTestApiResource cleanUpWallpaperResource =
                new CleanUpWallpaperWithTestApiResource()) {
            TestApis.wallpaper().setStream(BitmapUtils.bitmapToInputStream(sReferenceWallpaper));

            assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                    sReferenceWallpaper)).isTrue();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setStream")
    @EnsureHasPermission({SET_WALLPAPER, /* Android.U+ */ READ_WALLPAPER_INTERNAL})
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setStream_disallowed_cannotSet() {
        TestApis.wallpaper().setStream(BitmapUtils.bitmapToInputStream(sReferenceWallpaper));

        assertThat(BitmapUtils.compareBitmaps(TestApis.wallpaper().getBitmap(),
                sOriginalWallpaper)).isTrue();
    }

    private class CleanUpWallpaperWithTestApiResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            TestApis.wallpaper().setBitmap(sOriginalWallpaper);
        }
    }

    private class CleanUpWallpaperWithDpcResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            dpc(sDeviceState).wallpaperManager().setBitmap(sOriginalWallpaper);
        }
    }
}
