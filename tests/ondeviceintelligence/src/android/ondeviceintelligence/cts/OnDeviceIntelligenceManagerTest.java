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
import android.app.ondeviceintelligence.StreamingProcessingCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
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
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Test the OnDeviceIntelligenceManager API. Run with "atest OnDeviceIntelligenceManagerTest"
 * .
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize OnDeviceIntelligenceManagerService in instantMode.")
public class OnDeviceIntelligenceManagerTest {
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
    public void resultPopulatedWhenAttemptingProcessRequestStreaming() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(2);

        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1,
                null, null, EXECUTOR,
                new StreamingProcessingCallback() {
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
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
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
}
