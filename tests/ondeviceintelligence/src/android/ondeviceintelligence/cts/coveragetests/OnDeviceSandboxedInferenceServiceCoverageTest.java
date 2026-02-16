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

import android.app.ondeviceintelligence.Content;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.Part;
import android.app.ondeviceintelligence.TokenInfo;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionCallback;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionRequest;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse;
import android.ondeviceintelligence.cts.CtsDefaultInferenceService;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OnDeviceSandboxedInferenceServiceCoverageTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testOnTokenInfoRequest_Content_Default() throws InterruptedException {
        OnDeviceSandboxedInferenceService service = new CtsDefaultInferenceService();
        Feature feature = new Feature.Builder(1).build();
        Content content = new Content(List.of(Part.createText("test")));
        CountDownLatch latch = new CountDownLatch(1);

        service.onTokenInfoRequest(123, feature, content, null, new OutcomeReceiver<>() {
            @Override
            public void onResult(TokenInfo result) {}

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
    public void testOnGenerateEmbeddings_Default() throws InterruptedException {
        OnDeviceSandboxedInferenceService service = new CtsDefaultInferenceService();
        Feature feature = new Feature.Builder(1).build();
        EmbeddingRequest request = new EmbeddingRequest(List.of(new Content(List.of(Part.createText("test")))));
        CountDownLatch latch = new CountDownLatch(1);

        service.onGenerateEmbeddings(123, feature, request, null, new OutcomeReceiver<>() {
            @Override
            public void onResult(EmbeddingResponse result) {}

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
    public void testOnGenerateImageDescription_Default() throws InterruptedException {
        OnDeviceSandboxedInferenceService service = new CtsDefaultInferenceService();
        Feature feature = new Feature.Builder(1).build();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        ImageDescriptionRequest request = new ImageDescriptionRequest(bitmap, "prompt");
        CountDownLatch latch = new CountDownLatch(1);

        service.onGenerateImageDescription(123, feature, request, null, new ImageDescriptionCallback() {
            @Override
            public void onResult(ImageDescriptionResponse result) {}

            @Override
            public void onError(OnDeviceIntelligenceException e) {
                assertThat(e.getErrorCode()).isEqualTo(
                        OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE);
                latch.countDown();
            }

            @Override
            public void onNewText(String text) {}
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
