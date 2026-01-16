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
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.trusttoken.TrustTokenResponse;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenResponseTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final List<byte[]> TEST_ENCODED_TOKENS =
            Arrays.asList(new byte[] {0, 1}, new byte[] {2, 3});
    private static final List<byte[]> TEST_ROOT_AUTHORITY_KEYS =
            Arrays.asList(new byte[] {4, 5}, new byte[] {6, 7});
    private static final List<byte[]> TEST_INTERMEDIATE_CERTIFICATES =
            Arrays.asList(new byte[] {8, 9}, new byte[] {10, 11});
    private static final Instant TEST_UPDATE_TIME = Instant.ofEpochMilli(1234L);

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void addEncodedToken_null_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new TrustTokenResponse.Builder().addEncodedToken(null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void addRootAuthorityKey_null_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new TrustTokenResponse.Builder().addRootAuthorityKey(null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void addIntermediateCertificate_null_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new TrustTokenResponse.Builder().addIntermediateCertificate(null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void setUpdateTime_null_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new TrustTokenResponse.Builder().setUpdateTime(null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void builderAndGetters() {
        TrustTokenResponse response =
                new TrustTokenResponse.Builder()
                        .addEncodedToken(TEST_ENCODED_TOKENS.get(0))
                        .addEncodedToken(TEST_ENCODED_TOKENS.get(1))
                        .addRootAuthorityKey(TEST_ROOT_AUTHORITY_KEYS.get(0))
                        .addRootAuthorityKey(TEST_ROOT_AUTHORITY_KEYS.get(1))
                        .addIntermediateCertificate(TEST_INTERMEDIATE_CERTIFICATES.get(0))
                        .addIntermediateCertificate(TEST_INTERMEDIATE_CERTIFICATES.get(1))
                        .build();

        assertEquals(TEST_ENCODED_TOKENS, response.getEncodedTokens());
        assertEquals(TEST_ROOT_AUTHORITY_KEYS, response.getRootAuthorityKeys());
        assertEquals(TEST_INTERMEDIATE_CERTIFICATES, response.getIntermediateCertificates());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void parcelable() {
        TrustTokenResponse response =
                new TrustTokenResponse.Builder()
                        .addEncodedToken(TEST_ENCODED_TOKENS.get(0))
                        .addEncodedToken(TEST_ENCODED_TOKENS.get(1))
                        .addRootAuthorityKey(TEST_ROOT_AUTHORITY_KEYS.get(0))
                        .addRootAuthorityKey(TEST_ROOT_AUTHORITY_KEYS.get(1))
                        .addIntermediateCertificate(TEST_INTERMEDIATE_CERTIFICATES.get(0))
                        .addIntermediateCertificate(TEST_INTERMEDIATE_CERTIFICATES.get(1))
                        .setUpdateTime(TEST_UPDATE_TIME)
                        .build();

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TrustTokenResponse fromParcel = TrustTokenResponse.CREATOR.createFromParcel(parcel);

        assertEquals(response.getEncodedTokens().size(), fromParcel.getEncodedTokens().size());
        assertArrayEquals(response.getEncodedTokens().get(0), fromParcel.getEncodedTokens().get(0));
        assertArrayEquals(response.getEncodedTokens().get(1), fromParcel.getEncodedTokens().get(1));

        assertEquals(
                response.getRootAuthorityKeys().size(), fromParcel.getRootAuthorityKeys().size());
        assertArrayEquals(
                response.getRootAuthorityKeys().get(0), fromParcel.getRootAuthorityKeys().get(0));
        assertArrayEquals(
                response.getRootAuthorityKeys().get(1), fromParcel.getRootAuthorityKeys().get(1));

        assertEquals(
                response.getIntermediateCertificates().size(),
                fromParcel.getIntermediateCertificates().size());
        assertArrayEquals(
                response.getIntermediateCertificates().get(0),
                fromParcel.getIntermediateCertificates().get(0));
        assertArrayEquals(
                response.getIntermediateCertificates().get(1),
                fromParcel.getIntermediateCertificates().get(1));

        assertEquals(response.getUpdateTime(), fromParcel.getUpdateTime());

        parcel.recycle();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void parcelable_emptyResponse() {
        TrustTokenResponse response =
                new TrustTokenResponse.Builder().setUpdateTime(TEST_UPDATE_TIME).build();

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TrustTokenResponse fromParcel = TrustTokenResponse.CREATOR.createFromParcel(parcel);

        assertEquals(0, fromParcel.getEncodedTokens().size());
        assertEquals(0, fromParcel.getRootAuthorityKeys().size());
        assertEquals(0, fromParcel.getIntermediateCertificates().size());
        assertEquals(TEST_UPDATE_TIME, fromParcel.getUpdateTime());

        parcel.recycle();
    }
}
