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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ondeviceintelligence.Content;
import android.app.ondeviceintelligence.Part;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.embedding.EmbeddingVector;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EmbeddingRequestResponseTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testEmbeddingRequestResponseMethods() {
        // EmbeddingRequest
        List<Part> parts1 = List.of(Part.createText("text1"));
        EmbeddingRequest request1 = new EmbeddingRequest(List.of(new Content(parts1)));
        assertThat(request1.getContent().get(0).getParts().get(0).getText()).isEqualTo("text1");

        List<Part> parts2 = List.of(Part.createText("text2"));
        EmbeddingRequest request2 = new EmbeddingRequest(List.of(new Content(parts2)));
        assertThat(request2.getContent().get(0).getParts().get(0).getText()).isEqualTo("text2");

        // EmbeddingVector
        EmbeddingVector vector = new EmbeddingVector(new float[] {1.0f}, new int[]{1});
        assertThat(vector.getVector()).isEqualTo(new float[] {1.0f});
        assertThat(vector.getShape()).isEqualTo(new int[]{1});

        EmbeddingVector vector2 = new EmbeddingVector(new float[] {1.0f}, new int[] {1});
        assertThat(vector2.getShape()).isEqualTo(new int[] {1});
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testEmbeddingRequestClose() throws Exception {
        ParcelFileDescriptor mockPfd = mock(ParcelFileDescriptor.class);
        Part part = Part.createBlob(mockPfd, 3);
        Content content = new Content(List.of(part));
        EmbeddingRequest request = new EmbeddingRequest(List.of(content));
        try {
            request.close();
        } catch (Exception e) {
            // ignore
        }
        verify(mockPfd).close();
    }
}
