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

package android.trusttoken.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;
import android.security.trusttoken.TrustTokenRequest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenRequestTest {
    private static final List<byte[]> TEST_ATTESTATION =
            Collections.singletonList(new byte[] {0, 1});
    private static final List<byte[]> TEST_PUBLIC_KEYS =
            Collections.singletonList(new byte[] {2, 3});
    private static final byte[] TEST_BATCH_HASH = new byte[] {4, 5};
    private static final byte[] TEST_SIGNATURE = new byte[] {6, 7};

    @Test
    public void build_nullAttestation_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new TrustTokenRequest.Builder()
                                .setPublicKeys(TEST_PUBLIC_KEYS)
                                .setBatchHash(TEST_BATCH_HASH)
                                .setSignature(TEST_SIGNATURE)
                                .build());
    }

    @Test
    public void build_nullPublicKeys_doesNotThrow() {
        TrustTokenRequest request =
                new TrustTokenRequest.Builder()
                        .setAttestation(TEST_ATTESTATION)
                        .setBatchHash(TEST_BATCH_HASH)
                        .setSignature(TEST_SIGNATURE)
                        .build();
        assertEquals(0, request.getPublicKeys().size());
    }

    @Test
    public void build_emptyPublicKeys_doesNotThrow() {
        new TrustTokenRequest.Builder()
                .setAttestation(TEST_ATTESTATION)
                .setPublicKeys(Collections.emptyList())
                .setBatchHash(TEST_BATCH_HASH)
                .setSignature(TEST_SIGNATURE)
                .build();
    }

    @Test
    public void build_nullBatchHash_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new TrustTokenRequest.Builder()
                                .setAttestation(TEST_ATTESTATION)
                                .setPublicKeys(TEST_PUBLIC_KEYS)
                                .setSignature(TEST_SIGNATURE)
                                .build());
    }

    @Test
    public void build_nullSignature_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new TrustTokenRequest.Builder()
                                .setAttestation(TEST_ATTESTATION)
                                .setPublicKeys(TEST_PUBLIC_KEYS)
                                .setBatchHash(TEST_BATCH_HASH)
                                .build());
    }

    @Test
    public void getters() {
        TrustTokenRequest request =
                new TrustTokenRequest.Builder()
                        .setAttestation(TEST_ATTESTATION)
                        .setPublicKeys(TEST_PUBLIC_KEYS)
                        .setBatchHash(TEST_BATCH_HASH)
                        .setSignature(TEST_SIGNATURE)
                        .build();

        assertEquals(TEST_ATTESTATION, request.getAttestation());
        assertEquals(TEST_PUBLIC_KEYS, request.getPublicKeys());
        assertArrayEquals(TEST_BATCH_HASH, request.getBatchHash());
        assertArrayEquals(TEST_SIGNATURE, request.getSignature());
    }

    @Test
    public void parcelable() {
        TrustTokenRequest request =
                new TrustTokenRequest.Builder()
                        .setAttestation(TEST_ATTESTATION)
                        .setPublicKeys(TEST_PUBLIC_KEYS)
                        .setBatchHash(TEST_BATCH_HASH)
                        .setSignature(TEST_SIGNATURE)
                        .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TrustTokenRequest fromParcel = TrustTokenRequest.CREATOR.createFromParcel(parcel);
        assertEquals(request.getAttestation().size(), fromParcel.getAttestation().size());
        assertArrayEquals(request.getAttestation().get(0), fromParcel.getAttestation().get(0));
        assertEquals(request.getPublicKeys().size(), fromParcel.getPublicKeys().size());
        assertArrayEquals(request.getPublicKeys().get(0), fromParcel.getPublicKeys().get(0));
        assertArrayEquals(request.getBatchHash(), fromParcel.getBatchHash());
        assertArrayEquals(request.getSignature(), fromParcel.getSignature());
        parcel.recycle();
    }

    @Test
    public void parcelable_corruptedParcel_throws() {
        Parcel parcel = Parcel.obtain();
        // Write some corrupted data to the parcel.
        parcel.writeString("corrupted data");
        parcel.setDataPosition(0);

        assertThrows(Exception.class, () -> TrustTokenRequest.CREATOR.createFromParcel(parcel));
        parcel.recycle();
    }

    @Test
    public void parcelable_noPublicKeys() {
        TrustTokenRequest request =
                new TrustTokenRequest.Builder()
                        .setBatchHash(TEST_BATCH_HASH)
                        .setSignature(TEST_SIGNATURE)
                        .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TrustTokenRequest fromParcel = TrustTokenRequest.CREATOR.createFromParcel(parcel);
        assertEquals(0, fromParcel.getAttestation().size());
        assertEquals(0, fromParcel.getPublicKeys().size());
        assertArrayEquals(request.getBatchHash(), fromParcel.getBatchHash());
        assertArrayEquals(request.getSignature(), fromParcel.getSignature());
        parcel.recycle();
    }
}
