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

package com.android.security.cts.bug_467684755_test;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.runner.AndroidJUnit4;

import com.android.systemui.wallet.controller.IWalletCardsUpdatedListener;
import com.android.systemui.wallet.controller.IWalletContextualLocationsService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final String SERVICE_PACKAGE = "com.android.systemui";
    private static final String SERVICE_CLASS =
            SERVICE_PACKAGE + ".wallet.controller.WalletContextualLocationsService";
    private static final long TIMEOUT_MS = 10_000;

    /**
     * Verifies that a {@link java.lang.SecurityException} is thrown when a normal 3P app attempts
     * to register a {@link
     * com.android.systemui.wallet.controller.WalletContextualLocationsService#listener} to receive
     * card information.
     */
    @Test
    public void testWalletContextualLocationsServiceGetCardInfo() throws Exception {
        Context context = getApplicationContext();
        CompletableFuture<IWalletContextualLocationsService> serviceFuture =
                new CompletableFuture<>();
        ServiceConnection serviceConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        serviceFuture.complete(
                                IWalletContextualLocationsService.Stub.asInterface(service));
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS));
        boolean bindResult =
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        assumeTrue(String.format("Could not bind to %s.", SERVICE_CLASS), bindResult);

        try {
            IWalletContextualLocationsService service =
                    serviceFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            assertThrows(
                    "A normal 3P app should not be able to register a listener",
                    SecurityException.class,
                    () ->
                            service.addWalletCardsUpdatedListener(
                                    new IWalletCardsUpdatedListener.Stub() {
                                        @Override
                                        public void registerNewWalletCards(List cards)
                                                throws RemoteException {}
                                    }));
        } finally {
            context.unbindService(serviceConnection);
        }
    }
}
