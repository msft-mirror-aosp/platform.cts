/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.nfc.cts;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class NfcUtils {
    private NfcUtils() {}

    static boolean enableNfc(NfcAdapter nfcAdapter, Context context) {
        try {
            if (nfcAdapter.isEnabled()) {
                return true;
            }
            CountDownLatch countDownLatch = new CountDownLatch(1);
            AtomicInteger state = new AtomicInteger(NfcAdapter.STATE_OFF);
            BroadcastReceiver nfcChangeListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int s = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                            NfcAdapter.STATE_OFF);
                    if (s == NfcAdapter.STATE_TURNING_ON) {
                        return;
                    }
                    context.unregisterReceiver(this);
                    state.set(s);
                    countDownLatch.countDown();
                }
            };
            HandlerThread handlerThread = new HandlerThread("nfc_cts_listener");
            handlerThread.start();
            Handler handler = new Handler(handlerThread.getLooper());
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            context.registerReceiver(nfcChangeListener, intentFilter, null,
                    handler);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity(WRITE_SECURE_SETTINGS);
            if (!nfcAdapter.enable()) {
                return false;
            }
            countDownLatch.await(2000, TimeUnit.MILLISECONDS);
            return state.get() == NfcAdapter.STATE_ON;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static boolean disableNfc(NfcAdapter nfcAdapter, Context context) {
        return disableNfc(nfcAdapter, context, null);
    }

    static boolean disableNfc(NfcAdapter nfcAdapter, Context context, @Nullable Boolean persist) {
        try {
            if (!nfcAdapter.isEnabled()) {
                return true;
            }
            CountDownLatch countDownLatch = new CountDownLatch(1);
            AtomicInteger state = new AtomicInteger(NfcAdapter.STATE_ON);
            BroadcastReceiver nfcChangeListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int s =  intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                            NfcAdapter.STATE_ON);
                    if (s == NfcAdapter.STATE_TURNING_OFF) {
                        return;
                    }
                    context.unregisterReceiver(this);
                    state.set(s);
                    countDownLatch.countDown();
                }
            };
            HandlerThread handlerThread = new HandlerThread("nfc_cts_listener");
            handlerThread.start();
            Handler handler = new Handler(handlerThread.getLooper());
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            context.registerReceiver(nfcChangeListener, intentFilter, null,
                    handler);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity(WRITE_SECURE_SETTINGS);
            boolean result = false;
            if (persist != null) {
                result = nfcAdapter.disable(persist);
            } else {
                result = nfcAdapter.disable();
            }
            if (!result) return false;
            countDownLatch.await(2000, TimeUnit.MILLISECONDS);
            return state.get() == NfcAdapter.STATE_OFF;
        } catch (Exception e) {
            return false;
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

}
