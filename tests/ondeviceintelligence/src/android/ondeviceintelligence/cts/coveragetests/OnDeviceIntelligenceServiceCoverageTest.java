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

package android.ondeviceintelligence.cts.coveragetests;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2;

import static com.google.common.truth.Truth.assertThat;

import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.embedding.EmbeddingModel;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.ondeviceintelligence.cts.CtsDefaultIntelligenceService;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OnDeviceIntelligenceServiceCoverageTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testOnFetchEmbeddingModel_Default() throws InterruptedException {
        CtsDefaultIntelligenceService service = new CtsDefaultIntelligenceService();
        CountDownLatch latch = new CountDownLatch(1);
        service.onFetchEmbeddingModel(123, "sig", new OutcomeReceiver<>() {
            @Override
            public void onResult(EmbeddingModel result) {}

            @Override
            public void onError(OnDeviceIntelligenceException e) {
                assertThat(e.getErrorCode()).isEqualTo(
                        OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE);
                latch.countDown();
            }
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testOnFetchImageDescriptionModel_Default() throws InterruptedException {
        CtsDefaultIntelligenceService service = new CtsDefaultIntelligenceService();
        CountDownLatch latch = new CountDownLatch(1);
        service.onFetchImageDescriptionModel(123, "sig", new OutcomeReceiver<>() {
            @Override
            public void onResult(ImageDescriptionModel result) {}

            @Override
            public void onError(OnDeviceIntelligenceException e) {
                assertThat(e.getErrorCode()).isEqualTo(
                        OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE);
                latch.countDown();
            }
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testOnListEmbeddingModels_Default() throws InterruptedException {
        CtsDefaultIntelligenceService service = new CtsDefaultIntelligenceService();
        CountDownLatch latch = new CountDownLatch(1);
        service.onListEmbeddingModels(123, new OutcomeReceiver<>() {
            @Override
            public void onResult(List<EmbeddingModel> result) {}

            @Override
            public void onError(OnDeviceIntelligenceException e) {
                assertThat(e.getErrorCode()).isEqualTo(
                        OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE);
                latch.countDown();
            }
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testOnListImageDescriptionModels_Default() throws InterruptedException {
        CtsDefaultIntelligenceService service = new CtsDefaultIntelligenceService();
        CountDownLatch latch = new CountDownLatch(1);
        service.onListImageDescriptionModels(123, new OutcomeReceiver<>() {
            @Override
            public void onResult(List<ImageDescriptionModel> result) {}

            @Override
            public void onError(OnDeviceIntelligenceException e) {
                assertThat(e.getErrorCode()).isEqualTo(
                        OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE);
                latch.countDown();
            }
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
