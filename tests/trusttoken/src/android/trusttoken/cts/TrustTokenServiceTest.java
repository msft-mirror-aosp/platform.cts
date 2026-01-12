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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.os.IBinder;
import android.security.trusttoken.TrustTokenCallback;
import android.security.trusttoken.TrustTokenRequest;
import android.security.trusttoken.TrustTokenResponse;
import android.security.trusttoken.TrustTokenService;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenServiceTest {
    private TestTrustTokenService mService;

    private static class TestTrustTokenService extends TrustTokenService {
        TrustTokenRequest mLastRequest;
        TrustTokenCallback mLastCallback;
        boolean mThrowOnRequest;

        @Override
        public void onRequestTrustTokens(
                @NonNull TrustTokenRequest request, @NonNull TrustTokenCallback callback) {
            if (mThrowOnRequest) {
                throw new UnsupportedOperationException("Not implemented");
            }
            mLastRequest = request;
            mLastCallback = callback;
        }
    }

    @Before
    public void setUp() {
        mService = new TestTrustTokenService();
    }

    @Test
    public void getTrustTokens_unimplemented_throws() {
        mService.mThrowOnRequest = true;
        assertThrows(
                UnsupportedOperationException.class,
                () -> mService.onRequestTrustTokens(null, null));
    }

    @Test
    public void onBind_correctAction_returnsBinder() {
        Intent intent = new Intent(TrustTokenService.SERVICE_INTERFACE);
        IBinder binder = mService.onBind(intent);
        assertNotNull(binder);
    }

    @Test
    public void onBind_wrongAction_returnsNull() {
        Intent intent = new Intent("wrong.action");
        IBinder binder = mService.onBind(intent);
        assertNull(binder);
    }

    @Test
    public void onBind_nullIntent_returnsNull() {
        IBinder binder = mService.onBind(null);
        assertNull(binder);
    }

    @Test
    public void onRequestTrustTokens_requestAndCallbackAreSet() {
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        TrustTokenCallback callback =
                new TrustTokenCallback() {
                    @Override
                    public void onSuccess(@NonNull TrustTokenResponse response) {}

                    @Override
                    public void onFailure(int code) {}
                };

        mService.onRequestTrustTokens(request, callback);

        assertEquals(request, mService.mLastRequest);
        assertEquals(callback, mService.mLastCallback);
    }

    @Test
    public void onRequestTrustTokens_invokesCallbackSuccess() {
        final AtomicReference<TrustTokenResponse> responseRef = new AtomicReference<>();
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        TrustTokenCallback callback =
                new TrustTokenCallback() {
                    @Override
                    public void onSuccess(@NonNull TrustTokenResponse response) {
                        responseRef.set(response);
                    }

                    @Override
                    public void onFailure(int code) {}
                };
        mService.onRequestTrustTokens(request, callback);
        assertNotNull(mService.mLastCallback);

        TrustTokenResponse expectedResponse = new TrustTokenResponse.Builder().build();
        mService.mLastCallback.onSuccess(expectedResponse);

        assertEquals(expectedResponse, responseRef.get());
    }

    @Test
    public void onRequestTrustTokens_invokesCallbackFailure() {
        final AtomicInteger errorCodeRef = new AtomicInteger(-1);
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        TrustTokenCallback callback =
                new TrustTokenCallback() {
                    @Override
                    public void onSuccess(@NonNull TrustTokenResponse response) {}

                    @Override
                    public void onFailure(int code) {
                        errorCodeRef.set(code);
                    }
                };
        mService.onRequestTrustTokens(request, callback);
        assertNotNull(mService.mLastCallback);

        mService.mLastCallback.onFailure(TrustTokenCallback.ERROR_INTERNAL);

        assertEquals(TrustTokenCallback.ERROR_INTERNAL, errorCodeRef.get());
    }
}
