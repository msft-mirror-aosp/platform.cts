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

import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.embedding.EmbeddingVector;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EmbeddingResponseTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testEmbeddingVectorMethods() {
        EmbeddingVector vector = new EmbeddingVector(new float[] {1.0f}, new int[]{1});
        assertThat(vector.getVector()).isEqualTo(new float[] {1.0f});
        assertThat(vector.getShape()).isEqualTo(new int[]{1});

        EmbeddingVector vector2 = new EmbeddingVector(new float[] {1.0f}, new int[] {1});
        assertThat(vector2.getShape()).isEqualTo(new int[] {1});
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testEmbeddingResponse_smallData() {
        EmbeddingVector vector1 = new EmbeddingVector(new float[]{0.1f, 0.2f}, new int[]{2});
        EmbeddingVector vector2 = new EmbeddingVector(new float[]{0.3f, 0.4f}, new int[]{2});
        List<EmbeddingVector> embeddings = List.of(vector1, vector2);
        EmbeddingResponse response = new EmbeddingResponse(embeddings);

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EmbeddingResponse unparceledResponse = EmbeddingResponse.CREATOR.createFromParcel(parcel);
        List<EmbeddingVector> unparceledEmbeddings = unparceledResponse.getEmbeddings();

        assertThat(unparceledEmbeddings).hasSize(2);
        assertThat(unparceledEmbeddings.get(0).getVector()).isEqualTo(vector1.getVector());
        assertThat(unparceledEmbeddings.get(1).getVector()).isEqualTo(vector2.getVector());

        parcel.recycle();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testEmbeddingResponse_largeData() {
        // Create a large embedding response to trigger SharedMemory logic (size > 500KB)
        // Each float is 4 bytes. 131071 floats * 4 bytes = 524284 bytes > 500KB.
        int dim = 131071;
        float[] largeVector = new float[dim];
        for (int i = 0; i < dim; i++) {
            largeVector[i] = (float) i;
        }
        EmbeddingVector vector = new EmbeddingVector(largeVector, new int[]{dim});
        EmbeddingResponse response = new EmbeddingResponse(List.of(vector));

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EmbeddingResponse unparceledResponse = EmbeddingResponse.CREATOR.createFromParcel(parcel);
        List<EmbeddingVector> unparceledEmbeddings = unparceledResponse.getEmbeddings();

        assertThat(unparceledEmbeddings).hasSize(1);
        assertThat(unparceledEmbeddings.get(0).getVector()).isEqualTo(largeVector);

        parcel.recycle();
    }
}
