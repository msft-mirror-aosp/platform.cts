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

package android.contentsafety.cts;

import static android.Manifest.permission.CHECK_CONTENT_SAFETY;
import static android.app.contentsafety.flags.Flags.FLAG_ENABLE_CONTENTSAFETY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.contentsafety.ContentSafetyManager;
import android.app.contentsafety.FeatureException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Test the ContentSafetyManager API. Run with "atest ContentSafetyManagerTest" */
@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "PM will not recognize ContentSafetyManagerService in instantMode.")
public class ContentSafetyManagerTest {
    private static final String TAG = "ContentSafetyManagerTest";
    private static final String CTS_PACKAGE_NAME =
            android.contentsafety.cts.CtsContentSafetyService.class.getPackageName();
    private static final String CTS_CONTENT_SAFETY_SERVICE_NAME =
            CTS_PACKAGE_NAME
                    + "/"
                    + android.contentsafety.cts.CtsContentSafetyService.class.getCanonicalName();
    private static final String CTS_CONTENT_SAFETY_PCC_SERVICE_NAME =
            CTS_PACKAGE_NAME
                    + "/"
                    + android.contentsafety.cts.CtsContentSafetyPccService.class.getCanonicalName();
    private static final String CTS_CONTENT_SAFETY_ISOLATED_SERVICE_NAME = "''";
    private static final String CTS_CONTENT_SAFETY_SETTINGS_SERVICE_NAME =
            CTS_PACKAGE_NAME
                    + "/"
                    + android.contentsafety.cts.CtsContentSafetySettingsService.class
                            .getCanonicalName();
    private static final int TEMPORARY_SERVICE_DURATION = 20000;

    // ContentSafety constants, which are not exposed.
    private static final int SENSITIVE_VIDEO = 1;
    private static final int CONTENT_SAFETY_SUCCESS_SENSITIVE = 2;

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private Context mContext;
    private ContentSafetyManager mContentSafetyManager;
    private PackageManager mPackageManager;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() throws Exception {
        Log.i(TAG, "SetUp: Start Test Setup");
        mContext = getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        assumeUnhandledDevice();
        mContentSafetyManager = mContext.getSystemService(ContentSafetyManager.class);
        Log.i(TAG, "SetUp: Clear testable services");
        bindToTestableContentSafetyServices();
        Log.i(TAG, "SetUp: Finish binding");
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown: started");
        clearTestableContentSafetyService();
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureDoesNotHavePermission(CHECK_CONTENT_SAFETY)
    public void noAccessWhenAttemptingCheckContent() {
        Log.i(TAG, "Test noAccessWhenAttemptingCheckContent:  started");
        assertThat(mContext.checkCallingOrSelfPermission(CHECK_CONTENT_SAFETY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Test non system app throws SecurityException
        Consumer<Map<Integer, List<Integer>>> checkContentConsumer =
                result -> {
                    // If the SecurityException is thrown synchronously as expected,
                    // this consumer will never be invoked.
                    fail(
                            "Consumer callback should not be invoked when SecurityException is"
                                    + " expected during the API call.");
                };
        assertThrows(
                "no access to checkContent from non system component",
                SecurityException.class,
                () ->
                        mContentSafetyManager.requestCheckContent(
                                SENSITIVE_VIDEO,
                                new HashMap<Integer, List<ParcelFileDescriptor>>(),
                                new CancellationSignal(),
                                EXECUTOR,
                                checkContentConsumer));
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureDoesNotHavePermission(CHECK_CONTENT_SAFETY)
    public void noAccessWhenAttemptingIsFeatureEnabled() {
        Log.i(TAG, "Test noAccessWhenAttemptingIsFeatureEnabled:  started");
        assertThat(mContext.checkCallingOrSelfPermission(CHECK_CONTENT_SAFETY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Test non system app throws SecurityException
        assertThrows(
                "no access to isFeatureEnabled from non system component",
                SecurityException.class,
                () ->
                        mContentSafetyManager.requestIsFeatureEnabled(
                                SENSITIVE_VIDEO, null, EXECUTOR, null));
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureDoesNotHavePermission(CHECK_CONTENT_SAFETY)
    public void noAccessWhenAttemptingGetRemoteSettingsServicePackageName() {
        Log.i(TAG, "Test noAccessWhenAttemptingGetRemoteSettingsServicePackageName:  started");
        assertThat(mContext.checkCallingOrSelfPermission(CHECK_CONTENT_SAFETY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Test non system app throws SecurityException
        assertThrows(
                "no access to getRemoteSettingsServicePackageName from non system component",
                SecurityException.class,
                () -> mContentSafetyManager.getRemoteSettingsServicePackageName());
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureDoesNotHavePermission(CHECK_CONTENT_SAFETY)
    public void noAccessWhenAttemptingGetRemoteSandboxedServicePackageName() {
        Log.i(TAG, "Test noAccessWhenAttemptingGetSandboxedSettingsServicePackageName:  started");
        assertThat(mContext.checkCallingOrSelfPermission(CHECK_CONTENT_SAFETY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Test non system app throws SecurityException
        assertThrows(
                "no access to getRemoteSandboxedServicePackageName from non system component",
                SecurityException.class,
                () -> mContentSafetyManager.getRemoteSandboxedServicePackageName());
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureDoesNotHavePermission(CHECK_CONTENT_SAFETY)
    public void noAccessWhenAttemptingGetRemoteServicePackageName() {
        Log.i(TAG, "Test noAccessWhenAttemptingGetRemoveServicePackageName:  started");

        // getRemoveServicePackageName is exposed as it's required for testing.
        assertThat(mContentSafetyManager.getRemoteServicePackageName()).isNotEmpty();
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureHasPermission(CHECK_CONTENT_SAFETY)
    public void checkContentWithFilesPass() throws IOException, InterruptedException {
        Log.i(TAG, "Test checkContentTestServicesPass:  started");
        CountDownLatch latch = new CountDownLatch(1);
        File content = File.createTempFile("content_safety", "checkContentWithFilesPass");
        try (ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(content, ParcelFileDescriptor.MODE_READ_ONLY)) {
            Files.writeString(Paths.get(content.getAbsolutePath()), "42");
            Consumer<Map<Integer, List<Integer>>> checkContentConsumer =
                    result -> {
                        assertThat(result)
                                .containsExactly(
                                        SENSITIVE_VIDEO, List.of(CONTENT_SAFETY_SUCCESS_SENSITIVE));
                        latch.countDown();
                    };
            mContentSafetyManager.requestCheckContent(
                    SENSITIVE_VIDEO,
                    Map.of(SENSITIVE_VIDEO, List.of(pfd)),
                    null,
                    EXECUTOR,
                    checkContentConsumer);
        }
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @RequireFlagsEnabled(FLAG_ENABLE_CONTENTSAFETY)
    @EnsureHasPermission(CHECK_CONTENT_SAFETY)
    public void isFeatureEnabledPass() throws InterruptedException {
        Log.i(TAG, "Test isFeatureEnabledPass:  started");

        CountDownLatch latch = new CountDownLatch(1);
        mContentSafetyManager.requestIsFeatureEnabled(
                SENSITIVE_VIDEO,
                null,
                EXECUTOR,
                new OutcomeReceiver<Boolean, FeatureException>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isTrue();
                        latch.countDown();
                    }

                    @Override
                    public void onError(FeatureException ex) {
                        assertWithMessage("onFailure: " + ex.toString()).fail();
                    }
                });
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    private void clearTestableContentSafetyService() {
        Log.i(TAG, "clearTestableContentSafetyService: Started");
        runShellCommand("cmd content_safety set-temporary-services");
    }

    private void bindToTestableContentSafetyServices() {
        Log.i(TAG, "bindToTestableContentSafetyServices: Started");
        setTestableContentSafetyServiceNames(
                new String[] {
                    CTS_CONTENT_SAFETY_SERVICE_NAME,
                    CTS_CONTENT_SAFETY_SETTINGS_SERVICE_NAME,
                    CTS_CONTENT_SAFETY_PCC_SERVICE_NAME,
                    CTS_CONTENT_SAFETY_ISOLATED_SERVICE_NAME
                });
        assertThat(CTS_CONTENT_SAFETY_PCC_SERVICE_NAME).contains(getContentSafetyPackageName());
        Log.i(TAG, "bindToTestableContentSafetyServices: finished");
    }

    private String getContentSafetyPackageName() {
        Log.i(TAG, "getContentSafetyPackageName: Started");
        return mContentSafetyManager.getRemoteServicePackageName();
    }

    private void setTestableContentSafetyServiceNames(String[] serviceNames) {
        runShellCommand(
                "cmd content_safety set-temporary-services %s %s %s %s %d",
                serviceNames[0],
                serviceNames[1],
                serviceNames[2],
                serviceNames[3],
                TEMPORARY_SERVICE_DURATION);
    }

    private void assumeUnhandledDevice() {
        assumeFalse(
                "Skipping test on PC (ChromeOS) and Auto devices",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                        || mPackageManager.hasSystemFeature(PackageManager.FEATURE_PC));
    }
}
