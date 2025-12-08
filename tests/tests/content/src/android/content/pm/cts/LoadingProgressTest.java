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

package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.incfs.install.IncrementalInstallSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "This test needs to access files on device and install apps incrementally")
public class LoadingProgressTest {
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String HELLO_WORLD_PACKAGE_NAME = "com.example.helloworld";
    private static final String HELLO_WORLD_APK = SAMPLE_APK_BASE + "HelloWorld5.apk";
    private static final String HELLO_WORLD_IDSIG = SAMPLE_APK_BASE + "HelloWorld5.apk.idsig";
    private static final String HELLO_WORLD_APK_SPLIT0 =
            SAMPLE_APK_BASE + "HelloWorld5_mdpi-v4.apk";
    private static final String HELLO_WORLD_APK_SPLIT0_IDSIG =
            SAMPLE_APK_BASE + "HelloWorld5_mdpi-v4.apk.idsig";
    private static final int WAIT_FOR_LOADING_PROGRESS_UPDATE_MS = 2000;
    private static final Predicate<Float> PARTIALLY_LOADED_CONDITION =
            loadingProgress -> loadingProgress < 1.0f && loadingProgress > 0;
    private static final Predicate<Float> FULLY_LOADED_CONDITION =
            loadingProgress -> 1 - loadingProgress < 0.001;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final int mUserId = mContext.getUserId();
    private final LauncherApps mLauncherApps = mContext.getSystemService(LauncherApps.class);
    private final HandlerThread mCallbackThread = new HandlerThread("callback");
    private final PackageManager mPackageManager = mContext.getPackageManager();

    @Before
    public void setup() throws Exception {
        uninstallPackage(HELLO_WORLD_PACKAGE_NAME, mUserId);
        assertThat(mLauncherApps).isNotNull();
        mCallbackThread.start();
    }

    @After
    public void tearDown() throws Exception {
        mCallbackThread.quit();
        uninstallPackage(HELLO_WORLD_PACKAGE_NAME, mUserId);
    }

    @Test
    public void testNonIncrementalGetLoadingProgressSuccess() throws Exception {
        installPackage(HELLO_WORLD_APK, mUserId);
        assertThat(isAppInstalledForUser(HELLO_WORLD_PACKAGE_NAME, mUserId)).isTrue();
        assertFullyLoaded(HELLO_WORLD_PACKAGE_NAME, mUserId);
    }

    @Test
    public void testIncrementalGetLoadingProgressSuccess() throws Exception {
        // TODO(b/453894069): Fix the test for auto or tv form factors.
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return;
        }
        installPackageWithIncremental(
                HELLO_WORLD_APK,
                HELLO_WORLD_IDSIG,
                HELLO_WORLD_APK_SPLIT0,
                HELLO_WORLD_APK_SPLIT0_IDSIG,
                mUserId);
        assertPartiallyLoaded(HELLO_WORLD_PACKAGE_NAME, mUserId);
        // Check that "loadingProgress" is shown in the dumpsys of on a partially loaded app
        final String loadingPercentageString =
                getLoadingProgressFromDumpsys(HELLO_WORLD_PACKAGE_NAME);
        assertThat(loadingPercentageString).isNotNull();
        final int loadingPercentage = Integer.parseInt(loadingPercentageString);
        assertThat(loadingPercentage > 0 && loadingPercentage < 100).isTrue();
        // Register a progress callback to be called on loading progress fully loaded
        ConditionVariable loadingCompleted = new ConditionVariable();
        LauncherAppsCallback callback =
                new LauncherAppsCallback(FULLY_LOADED_CONDITION, loadingCompleted);
        try {
            mLauncherApps.registerCallback(callback, new Handler(mCallbackThread.getLooper()));
            // Now trigger a full loading
            testReadAllBytes(HELLO_WORLD_PACKAGE_NAME);
            // Wait for loading progress to update
            assertThat(loadingCompleted.block(WAIT_FOR_LOADING_PROGRESS_UPDATE_MS)).isTrue();
            // Check full loading progress
            assertFullyLoaded(HELLO_WORLD_PACKAGE_NAME, mUserId);
            assertThat(getLoadingCompletedTimeFromDumpsys(HELLO_WORLD_PACKAGE_NAME)).isNotNull();
        } finally {
            mLauncherApps.unregisterCallback(callback);
        }
    }

    @Test
    public void testIncrementalToNonIncrementalMigrationGetLoadingProgressSuccess()
            throws Exception {
        installPackageWithIncremental(
                HELLO_WORLD_APK,
                HELLO_WORLD_IDSIG,
                HELLO_WORLD_APK_SPLIT0,
                HELLO_WORLD_APK_SPLIT0_IDSIG,
                mUserId);
        assertThat(isAppInstalledForUser(HELLO_WORLD_PACKAGE_NAME, mUserId)).isTrue();
        assertPartiallyLoaded(HELLO_WORLD_PACKAGE_NAME, mUserId);
        // Trigger app migration through normal package installation.
        installPackage(HELLO_WORLD_APK, mUserId);
        assertFullyLoaded(HELLO_WORLD_PACKAGE_NAME, mUserId);
    }

    class LauncherAppsCallback extends LauncherApps.Callback {
        private final Predicate<Float> mCondition;
        private final ConditionVariable mCalled;

        LauncherAppsCallback(Predicate<Float> progressCondition, ConditionVariable called) {
            mCondition = progressCondition;
            mCalled = called;
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {}

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {}

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {}

        @Override
        public void onPackagesAvailable(
                String[] packageNames, UserHandle user, boolean replacing) {}

        @Override
        public void onPackagesUnavailable(
                String[] packageNames, UserHandle user, boolean replacing) {}

        @Override
        public void onPackageLoadingProgressChanged(
                @NonNull String packageName, @NonNull UserHandle user, float progress) {
            if (mCondition.test(progress)) {
                // Only release when progress meets the expected condition
                mCalled.open();
            }
        }
    }

    private void installPackageWithIncremental(
            String baseApk, String baseApkIdsig, String splitApk, String splitApkIdsig, int userId)
            throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            // Use splits to mitigate on-device digesters that read all base apk and trigger
            // full loading.
            IncrementalInstallSession incrementalInstallSession =
                    new IncrementalInstallSession.Builder()
                            .addApk(Paths.get(baseApk), Paths.get(baseApkIdsig))
                            .addApk(Paths.get(splitApk), Paths.get(splitApkIdsig))
                            .addExtraArgs("-t", "--user", String.valueOf(userId))
                            .build();
            incrementalInstallSession.start(
                    Executors.newSingleThreadExecutor(),
                    IncrementalDeviceConnection.Factory.reliable());
            incrementalInstallSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private static void installPackage(String apkPath, int userId) {
        SystemUtil.runShellCommand("pm install -t --user " + userId + " " + apkPath);
    }

    private void uninstallPackage(String packageName, int userId) {
        SystemUtil.runShellCommand("pm uninstall --user " + userId + " " + packageName);
    }

    private void assertPartiallyLoaded(String packageName, int userId) throws IOException {
        assertThat(isAppInstalledForUser(packageName, userId)).isTrue();
        // Package is installed but only partially streamed
        checkLoadingProgress(HELLO_WORLD_PACKAGE_NAME, userId, PARTIALLY_LOADED_CONDITION);
    }

    private void assertFullyLoaded(String packageName, int userId) throws IOException {
        assertThat(isAppInstalledForUser(packageName, userId)).isTrue();
        // Package should be fully streamed
        checkLoadingProgress(HELLO_WORLD_PACKAGE_NAME, userId, FULLY_LOADED_CONDITION);
    }

    private void checkLoadingProgress(
            String packageName, int userId, Predicate<Float> progressCondition) {
        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(packageName, UserHandle.of(userId));
        assertThat(activities).hasSize(1);
        LauncherActivityInfo activity = activities.getFirst();
        assertThat(activity.getComponentName().getPackageName()).isEqualTo(packageName);
        final float progress = activity.getLoadingProgress();
        assertThat(progressCondition.test(progress)).isTrue();
        assertThat(activity.getUser().getIdentifier()).isEqualTo(userId);
    }

    private static boolean isAppInstalledForUser(String packageName, int userId)
            throws IOException {
        final String command =
                userId < 0
                        ? "pm list packages " + packageName
                        : "pm list packages --user " + userId + " " + packageName;
        final String commandResult = SystemUtil.runShellCommand(command);
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.equals("package:" + packageName));
    }

    private void testReadAllBytes(String packageName) throws Exception {
        ApplicationInfo appInfo =
                mContext.getPackageManager().getApplicationInfo(packageName, /* flags= */ 0);
        final String codePath = appInfo.sourceDir;
        final String apkDir = codePath.substring(0, codePath.lastIndexOf('/'));
        for (String apkName : new File(apkDir).list()) {
            final String apkPath = apkDir + "/" + apkName;
            assertThat(new File(apkPath).exists()).isTrue();
            byte[] apkContentBytes = Files.readAllBytes(Paths.get(apkPath));
            assertThat(apkContentBytes).isNotNull();
            assertThat(apkContentBytes).isNotEmpty();
        }
    }

    private String getLoadingProgressFromDumpsys(String packageName) throws Exception {
        return getStringFromDumpsys(packageName, "loadingProgress=(\\d+)%");
    }

    private String getLoadingCompletedTimeFromDumpsys(String packageName) throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return getStringFromDumpsys(packageName, "loadingCompletedTime=(.*)");
    }

    private String getStringFromDumpsys(String packageName, String regex) throws Exception {
        final String output = SystemUtil.runShellCommand("dumpsys package " + packageName);
        // Expecting output like "loadingProgress=50%"
        final Matcher matcher = Pattern.compile(regex).matcher(output);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        return matcher.group(1);
    }
}
