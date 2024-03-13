/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.ondeviceintelligence.cts;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ENABLE_ON_DEVICE_INTELLIGENCE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.ProcessingCallback;
import android.app.ondeviceintelligence.ProcessingSignal;
import android.app.ondeviceintelligence.StreamingProcessingCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Test the OnDeviceIntelligenceManager API. Run with "atest OnDeviceIntelligenceManagerTest"
 * .
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize OnDeviceIntelligenceManagerService in instantMode.")
public class OnDeviceIntelligenceManagerTest {
    public static final int REQUEST_TYPE_GET_FILE_FROM_NON_ISOLATED = 1001;
    public static final String TEST_FILE_NAME = "test_file.txt";
    public static final String TEST_KEY = "test_key";

    public static final String TEST_CONTENT = "test_content";


    private static final String TAG = OnDeviceIntelligenceManagerTest.class.getSimpleName();
    private static final String CTS_PACKAGE_NAME =
            android.ondeviceintelligence.cts.CtsIntelligenceService.class.getPackageName();
    private static final String CTS_INTELLIGENCE_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/"
                    + android.ondeviceintelligence.cts.CtsIntelligenceService.class.getCanonicalName();
    private static final String CTS_INFERENCE_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/"
                    + android.ondeviceintelligence.cts.CtsIsolatedInferenceService.class.getCanonicalName();
    private static final int TEMPORARY_SERVICE_DURATION = 10000;
    private static final String NAMESPACE_ON_DEVICE_INTELLIGENCE = "ondeviceintelligence";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    public static final int REQUEST_TYPE_GET_PACKAGE_NAME = 1000;
    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();

    private Context mContext;
    private OnDeviceIntelligenceManager mOnDeviceIntelligenceManager;

    @Rule
    public final DeviceConfigStateChangerRule mDeviceConfigStateChangerRule =
            new DeviceConfigStateChangerRule(
                    getInstrumentation().getTargetContext(),
                    NAMESPACE_ON_DEVICE_INTELLIGENCE,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mOnDeviceIntelligenceManager =
                (OnDeviceIntelligenceManager)
                        mContext.getSystemService(Context.ON_DEVICE_INTELLIGENCE_SERVICE);
        clearTestableOnDeviceIntelligenceService();
        bindToTestableOnDeviceIntelligenceServices();
    }

    @After
    public void tearDown() throws Exception {
        clearTestableOnDeviceIntelligenceService();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }


//=====================Tests for Access Denied without Permission on all Manager Methods=========

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetFeature() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        // Test non system app throws SecurityException
        assertThrows("no access to getFeature from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.getFeature(1, EXECUTOR,
                        result -> {
                            Log.i(TAG, "Feature : =" + result);
                        }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetFeatureDetails() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));
        Feature feature = new Feature.Builder(1).build();

        // Test non system app throws SecurityException
        assertThrows("no access to getFeature from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.getFeatureDetails(feature,
                        EXECUTOR,
                        result -> Log.i(TAG, "Feature details : =" + result)));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetVersion() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to getVersion from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                                result -> {
                                    Log.i(TAG, "Version : =" + result);
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingRequestFeatureDownload() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();

        // Test non system app throws SecurityException
        assertThrows(
                "no access to requestFeatureDownload from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.requestFeatureDownload(feature, null, EXECUTOR,
                                new DownloadCallback() {
                                    @Override
                                    public void onDownloadFailed(int failureStatus,
                                            @Nullable String errorMessage,
                                            @NonNull PersistableBundle errorParams) {
                                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                                    }

                                    @Override
                                    public void onDownloadCompleted(
                                            @NonNull PersistableBundle downloadParams) {
                                        Log.i(TAG, "Response : =" + downloadParams.toString());
                                    }
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenRequestTokenInfo() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));


        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to requestTokenInfo from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.requestTokenInfo(feature,
                                new Bundle(), null,
                                EXECUTOR,
                                result -> {
                                    Log.i(TAG, "Response : =" + result.getCount());
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingProcessRequest() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to processRequest from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.processRequest(feature,
                        new Bundle(), 1, null,
                        null, EXECUTOR, new ProcessingCallback() {
                            @Override
                            public void onResult(@NonNull Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Error Occurred", error);
                            }
                        }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingProcessRequestStreaming() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to processRequestStreaming from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                        new Bundle(), 1,
                        null, null, EXECUTOR,
                        new StreamingProcessingCallback() {
                            @Override
                            public void onPartialResult(@NonNull Bundle partialResult) {
                                Log.i(TAG, "New Content : " + partialResult);
                            }

                            @Override
                            public void onResult(Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Final Result : ", error);
                            }
                        }));
    }

//===================== Tests for Result callback invoked on all Manager Methods ==================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetFeature() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.getFeature(1,
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Feature : =" + result);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetFeatureDetails() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.getFeatureDetails(new Feature.Builder(1).build(),
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Feature details : =" + result);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetVersion() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                result -> {
                    Log.i(TAG, "Version : =" + result);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingRequestFeatureDownload() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.requestFeatureDownload(feature, null, EXECUTOR,
                new DownloadCallback() {
                    @Override
                    public void onDownloadFailed(int failureStatus,
                            @Nullable String errorMessage,
                            @NonNull PersistableBundle errorParams) {
                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                    }

                    @Override
                    public void onDownloadCompleted(
                            @NonNull PersistableBundle downloadParams) {
                        Log.i(TAG, "Response : =" + downloadParams);
                        statusLatch.countDown();
                    }
                });
        assertThat(statusLatch.await(2, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenRequestTokenInfo() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.requestTokenInfo(feature,
                new Bundle(), null,
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Response : =" + result.getCount());
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingProcessRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequest(feature,
                new Bundle(), 1, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingProcessRequestStreaming() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1,
                null, null, EXECUTOR,
                new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

//===================== Tests for Processing and Cancellation signals  ==========================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void cancellationPropagatedWhenInvokedDuringRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        CancellationSignal cancellationSignal = new CancellationSignal();
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<Bundle> resultBundle = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, cancellationSignal,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        resultBundle.complete(result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });

        cancellationSignal.cancel(); //cancel

        assertThat(statusLatch.await(2, SECONDS)).isTrue();
        assertThat(resultBundle.get()).isNotNull();
        assertThat(resultBundle.get().containsKey("test_key")).isTrue();
        assertThat(resultBundle.get().getBoolean(TEST_KEY)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void cancellationPropagatedWhenInvokedBeforeMakingRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.cancel(); //cancel
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<Bundle> resultBundle = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, cancellationSignal,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        resultBundle.complete(result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(resultBundle.get()).isNotNull();
        assertThat(resultBundle.get().containsKey("test_key")).isTrue();
        assertThat(resultBundle.get().getBoolean(TEST_KEY)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void signalPropagatedWhenSignalIsInvokedBeforeAndDuringRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(4);
        ProcessingSignal processingSignal = new ProcessingSignal();
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, null,
                processingSignal, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        assertThat(statusLatch.await(2, SECONDS)).isTrue();
    }

    //===================== Tests for Manager Methods When No Service is Configured ==================

    @Test
    @SkipSetupAndTeardown
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void exceptionWhenAttemptingGetVersionWithoutServiceConfigured() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        mOnDeviceIntelligenceManager =
                (OnDeviceIntelligenceManager)
                        mContext.getSystemService(Context.ON_DEVICE_INTELLIGENCE_SERVICE);
        clearTestableOnDeviceIntelligenceService();
        // Test throws IllegalStateException
        assertThrows("no service configured to perform getVersion",
                IllegalStateException.class,
                () -> mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                        result -> Log.i(TAG, "Feature : =" + result)));
    }


    @Test
    @SkipSetupAndTeardown
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void exceptionWhenAttemptingProcessRequestWithoutServiceConfigured() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        mOnDeviceIntelligenceManager =
                (OnDeviceIntelligenceManager)
                        mContext.getSystemService(Context.ON_DEVICE_INTELLIGENCE_SERVICE);
        clearTestableOnDeviceIntelligenceService();
        Feature feature = new Feature.Builder(1).build();
        // Test throws IllegalStateException
        assertThrows(
                "no service configured for processRequestStreaming",
                IllegalStateException.class,
                () -> mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                        new Bundle(), 1,
                        null, null, EXECUTOR,
                        new StreamingProcessingCallback() {
                            @Override
                            public void onPartialResult(@NonNull Bundle partialResult) {
                                Log.i(TAG, "New Content : " + partialResult);
                            }

                            @Override
                            public void onResult(Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Final Result : ", error);
                            }
                        }));
    }

// ========= Test package manager returns parent process package name for isolated_compute_app ====
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void inferenceServiceShouldReturnParentPackageName() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<String> packageNameFuture = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_PACKAGE_NAME, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        packageNameFuture.complete(result.getPairValue());
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(packageNameFuture.get()).isEqualTo(CTS_PACKAGE_NAME);
    }

    //===================== Tests for accessing file from isolated process via non-isolated =======
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void testGetFileDescriptorFromNonIsolatedService() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);
        CompletableFuture<String> fileContents = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_FILE_FROM_NON_ISOLATED, null,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        fileContents.complete(result.getString(TEST_KEY));
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(fileContents.get()).isEqualTo(TEST_CONTENT);
    }

    private void clearTestableOnDeviceIntelligenceService() {
        runShellCommand("cmd on_device_intelligence set-temporary-services");
    }

    private void bindToTestableOnDeviceIntelligenceServices() {
        assertThat(getOnDeviceIntelligencePackageName()).isNotEqualTo(CTS_PACKAGE_NAME);
        setTestableOnDeviceIntelligenceServiceNames(
                new String[]{CTS_INTELLIGENCE_SERVICE_NAME, CTS_INFERENCE_SERVICE_NAME});
        assertThat(CTS_INFERENCE_SERVICE_NAME).contains(getOnDeviceIntelligencePackageName());
    }

    private String getOnDeviceIntelligencePackageName() {
        return mOnDeviceIntelligenceManager.getRemoteServicePackageName();
    }

    private void setTestableOnDeviceIntelligenceServiceNames(String[] serviceNames) {
        runShellCommand(
                "cmd on_device_intelligence set-temporary-services %s %s %d",
                serviceNames[0], serviceNames[1], TEMPORARY_SERVICE_DURATION);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SkipSetupAndTeardown {
    }

    @Rule
    public TestRule skipSetupAndTeardownRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            if (description.getAnnotation(SkipSetupAndTeardown.class) != null) {
                // Skip setup and teardown for annotated tests
                base.evaluate();
            } else {
                // Run setup and teardown for other tests
                setUp();
                try {
                    base.evaluate();
                } finally {
                    tearDown();
                }
            }
        }
    };
}
