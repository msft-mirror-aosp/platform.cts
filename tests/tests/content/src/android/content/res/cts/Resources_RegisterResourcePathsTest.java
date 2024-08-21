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

package android.content.res.cts;

import android.annotation.NonNull;
import android.app.ResourcesManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ApkAssets;
import android.content.res.AssetManager;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Flags;
import android.content.res.Resources;
import android.content.res.ResourcesImpl;
import android.content.res.ResourcesKey;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.RavenwoodFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.ArraySet;
import android.util.DisplayMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
// This test requires installing separate APKs and call PackageManager.getApplicationInfo(),
// which is not supported on Ravenwood yet.
@DisabledOnRavenwood(blockedBy = PackageManager.class)
public class Resources_RegisterResourcePathsTest {
    @Rule
    public final RavenwoodRule mRavenwoodRule = new RavenwoodRule.Builder().build();

    private static final String APP_ONE_RES_DIR = "app_one.apk";
    private static final String TEST_LIB = "android.content.cts";

    private ResourcesManager mResourcesManager;
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mResourcesManager = new ResourcesManager() {
            @Override
            protected AssetManager createAssetManager(@NonNull ResourcesKey key) {
                return new AssetManager();
            }

            @Override
            protected AssetManager createAssetManager(@NonNull final ResourcesKey key,
                    ResourcesManager.ApkAssetsSupplier apkSupplier) {
                return createAssetManager(key);
            }
        };

        mPackageManager = InstrumentationRegistry.getContext().getPackageManager();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = RavenwoodRule.isOnRavenwood()
            ? RavenwoodFlagsValueProvider.createAllOnCheckFlagsRule()
            : DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testExistingResourcesAfterRegistration()
            throws PackageManager.NameNotFoundException {
        // Inject ResourcesManager instance from this test to the ResourcesManager class so that all
        // the static method can interact with this test smoothly.
        ResourcesManager oriResourcesManager = ResourcesManager.getInstance();
        ResourcesManager.setInstance(mResourcesManager);

        // Create a Resources before register resources' paths for a package.
        Resources resources = mResourcesManager.getResources(
                null, APP_ONE_RES_DIR, null, null, null, null, null, null,
                CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, null, null);
        Assert.assertNotNull(resources);
        ResourcesImpl oriResImpl = resources.getImpl();

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_LIB, 0);
        Resources.registerResourcePaths(TEST_LIB, appInfo);

        Assert.assertNotSame(oriResImpl, resources.getImpl());

        String[] resourcePaths = appInfo.getAllApkPaths();
        resourcePaths = removeDuplicates(resourcePaths);
        ApkAssets[] loadedAssets = resources.getAssets().getApkAssets();
        Assert.assertTrue(allResourcePathsLoaded(resourcePaths, loadedAssets));

        // Package resources' paths should be cached in ResourcesManager.
        Assert.assertNotNull(ResourcesManager.getInstance()
                        .getRegisteredResourcePaths().get(TEST_LIB));

        // Revert the ResourcesManager instance back.
        ResourcesManager.setInstance(oriResourcesManager);
    }

    @Test
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testNewResourcesAfterRegistration()
            throws PackageManager.NameNotFoundException {
        // Inject ResourcesManager instance from this test to the ResourcesManager class so that all
        // the static method can interact with this test smoothly.
        ResourcesManager oriResourcesManager = ResourcesManager.getInstance();
        ResourcesManager.setInstance(mResourcesManager);

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_LIB, 0);
        Resources.registerResourcePaths(TEST_LIB, appInfo);

        // Create a Resources after register resources' paths for a package.
        Resources resources = mResourcesManager.getResources(
                null, APP_ONE_RES_DIR, null, null, null, null, null, null,
                CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, null, null);
        Assert.assertNotNull(resources);

        String[] resourcePaths = appInfo.getAllApkPaths();
        resourcePaths = removeDuplicates(resourcePaths);
        ApkAssets[] loadedAssets = resources.getAssets().getApkAssets();
        Assert.assertTrue(allResourcePathsLoaded(resourcePaths, loadedAssets));

        // Package resources' paths should be cached in ResourcesManager.
        Assert.assertNotNull(ResourcesManager.getInstance()
                        .getRegisteredResourcePaths().get(TEST_LIB));

        // Revert the ResourcesManager instance back.
        ResourcesManager.setInstance(oriResourcesManager);
    }

    @Test
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testExistingResourcesCreatedByConstructorAfterResourcePathsRegistration()
            throws PackageManager.NameNotFoundException {
        // Inject ResourcesManager instance from this test to the ResourcesManager class so that all
        // the static method can interact with this test smoothly.
        ResourcesManager oriResourcesManager = ResourcesManager.getInstance();
        ResourcesManager.setInstance(mResourcesManager);

        // Create a Resources through constructor directly before register resources' paths.
        final DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        final Configuration config = new Configuration();
        config.setToDefaults();
        Resources resources = new Resources(new AssetManager(), metrics, config);
        Assert.assertNotNull(resources);

        ResourcesImpl oriResImpl = resources.getImpl();

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_LIB, 0);
        Resources.registerResourcePaths(TEST_LIB, appInfo);

        Assert.assertNotSame(oriResImpl, resources.getImpl());

        String[] resourcePaths = appInfo.getAllApkPaths();
        resourcePaths = removeDuplicates(resourcePaths);
        ApkAssets[] loadedAssets = resources.getAssets().getApkAssets();
        Assert.assertTrue(allResourcePathsLoaded(resourcePaths, loadedAssets));

        // Package resources' paths should be cached in ResourcesManager.
        Assert.assertNotNull(ResourcesManager.getInstance()
                        .getRegisteredResourcePaths().get(TEST_LIB));

        // Revert the ResourcesManager instance back.
        ResourcesManager.setInstance(oriResourcesManager);
    }

    @Test
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testNewResourcesWithOutdatedImplAfterResourcePathsRegistration()
            throws PackageManager.NameNotFoundException {
        ResourcesManager oriResourcesManager = ResourcesManager.getInstance();
        ResourcesManager.setInstance(mResourcesManager);

        Resources old_resources = mResourcesManager.getResources(
                null, APP_ONE_RES_DIR, null, null, null, null, null, null,
                CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, null, null);
        Assert.assertNotNull(old_resources);
        ResourcesImpl oldImpl = old_resources.getImpl();

        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_LIB, 0);
        Resources.registerResourcePaths(TEST_LIB, appInfo);

        // Create another resources with identical parameters.
        Resources resources = mResourcesManager.getResources(
                null, APP_ONE_RES_DIR, null, null, null, null, null, null,
                CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, null, null);
        Assert.assertNotNull(resources);
        // For a normal ResourcesImpl redirect, new Resources may find an old ResourcesImpl cache
        // and reuse it based on the ResourcesKey. But for shared library ResourcesImpl redirect,
        // new created Resources should never reuse any old impl, it has to recreate a new impl
        // which has proper asset paths appended.
        Assert.assertNotSame(oldImpl, resources.getImpl());

        String[] resourcePaths = appInfo.getAllApkPaths();
        resourcePaths = removeDuplicates(resourcePaths);
        ApkAssets[] loadedAssets = resources.getAssets().getApkAssets();
        Assert.assertTrue(allResourcePathsLoaded(resourcePaths, loadedAssets));

        // Package resources' paths should be cached in ResourcesManager.
        Assert.assertNotNull(ResourcesManager.getInstance()
                        .getRegisteredResourcePaths().get(TEST_LIB));

        // Revert the ResourcesManager instance back.
        ResourcesManager.setInstance(oriResourcesManager);
    }

    private static boolean allResourcePathsLoaded(String[] resourcePaths,
            ApkAssets[] loadedAssets) {
        for (int i = 0; i < resourcePaths.length; i++) {
            if (!resourcePaths[i].endsWith(".apk")) {
                continue;
            }
            boolean found = false;
            for (int j = 0; j < loadedAssets.length; j++) {
                if (loadedAssets[j].getAssetPath().equals(resourcePaths[i])) {
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static String[] removeDuplicates(String[] paths) {
        var pathList = new ArrayList<String>();
        var pathSet = new ArraySet<String>();
        final int pathsLen = paths.length;
        for (int i = 0; i < pathsLen; i++) {
            if (pathSet.add(paths[i])) {
                pathList.add(paths[i]);
            }
        }
        return pathList.toArray(new String[0]);
    }
}
