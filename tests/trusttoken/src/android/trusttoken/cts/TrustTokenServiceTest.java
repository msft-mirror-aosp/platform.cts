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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.trusttoken.TrustTokenRequest;
import android.security.trusttoken.TrustTokenResponse;
import android.security.trusttoken.TrustTokenService;
import android.security.trusttoken.TrustTokenServiceClient;
import android.security.trusttoken.TrustTokenServiceException;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestTrustTokenService mService;
    private TrustTokenServiceClient mClient;

    private static class TestTrustTokenService extends TrustTokenService {
        CountDownLatch mCalled = new CountDownLatch(1);
        TrustTokenRequest mLastRequest;
        OutcomeReceiver<TrustTokenResponse, TrustTokenServiceException> mLastCallback;
        CancellationSignal mLastCancellationSignal;
        boolean mThrowOnRequest;

        @Override
        public void onRequestTrustTokens(
                @NonNull TrustTokenRequest request,
                @NonNull CancellationSignal cancellationSignal,
                @NonNull OutcomeReceiver<TrustTokenResponse, TrustTokenServiceException> callback) {
            mCalled.countDown();
            if (mThrowOnRequest) {
                throw new UnsupportedOperationException("Not implemented");
            }
            mLastRequest = request;
            mLastCancellationSignal = cancellationSignal;
            mLastCallback = callback;
        }

        boolean isCalled() {
            try {
                return mCalled.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    @Before
    public void setUp() {
        mService = new TestTrustTokenService();
        mClient =
                new TrustTokenServiceClient(
                        mService.onBind(new Intent(TrustTokenService.SERVICE_INTERFACE)));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void getTrustTokens_unimplemented_throws() {
        mService.mThrowOnRequest = true;
        assertThrows(
                UnsupportedOperationException.class,
                () -> mService.onRequestTrustTokens(null, null, null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onBind_correctAction_returnsBinder() {
        Intent intent = new Intent(TrustTokenService.SERVICE_INTERFACE);
        IBinder binder = mService.onBind(intent);
        assertNotNull(binder);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onBind_wrongAction_returnsNull() {
        Intent intent = new Intent("wrong.action");
        IBinder binder = mService.onBind(intent);
        assertNull(binder);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onBind_nullIntent_returnsNull() {
        IBinder binder = mService.onBind(null);
        assertNull(binder);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onRequestTrustTokens_invokesCallbackSuccess() {
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        var callback = mock(OutcomeReceiver.class);
        mClient.requestTrustTokens(request, callback);
        assertTrue(mService.isCalled());
        TrustTokenResponse response = new TrustTokenResponse.Builder().build();
        mService.mLastCallback.onResult(response);
        verify(callback, timeout(1000)).onResult(eq(response));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onRequestTrustTokens_invokesCallbackFailure() {
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        var callback = mock(OutcomeReceiver.class);
        mClient.requestTrustTokens(request, callback);
        assertTrue(mService.isCalled());
        mService.mLastCallback.onError(
                new TrustTokenServiceException(TrustTokenServiceException.ERROR_INTERNAL, ""));
        verify(callback, timeout(1000))
                .onError(
                        argThat(
                                (err) ->
                                        ((TrustTokenServiceException) err).getErrorCode()
                                                == TrustTokenServiceException.ERROR_INTERNAL));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TALISMAN_SERVICE_API)
    public void onRequestTrustTokens_cancellation() throws Exception {
        TrustTokenRequest request = new TrustTokenRequest.Builder().build();
        var callback = mock(OutcomeReceiver.class);
        CancellationSignal cancellation = mClient.requestTrustTokens(request, callback);
        assertTrue(mService.isCalled());
        assertFalse(mService.mLastCancellationSignal.isCanceled());
        cancellation.cancel();
        var cancelled = new CountDownLatch(1);
        mService.mLastCancellationSignal.setOnCancelListener(
                new OnCancelListener() {
                    @Override
                    public void onCancel() {
                        cancelled.countDown();
                    }
                });
        assertTrue(cancelled.await(1, TimeUnit.SECONDS));
    }
}
