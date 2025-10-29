/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.security.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class PccProcessTest {
    static final String TAG = PccProcessTest.class.getSimpleName();

    private static final long BIND_SERVICE_TIMEOUT = 5000;

    private CountDownLatch mLatch;
    private IPccService mService;
    private final ServiceConnection mServiceConnection =
            new ServiceConnection() {

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.e(TAG, "PCC service " + name + " died abruptly");
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mService = IPccService.Stub.asInterface(service);
                    mLatch.countDown();
                }
            };

    private static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void setUp() throws InterruptedException {
        mLatch = new CountDownLatch(1);
        Intent serviceIntent = new Intent("android.security.cts.PccProcessTest");
        serviceIntent.setPackage(getInstrumentation().getContext().getPackageName());
        getInstrumentation()
                .getContext()
                .bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Assert.assertTrue(
                "Timed out while waiting to bind to PCC service",
                mLatch.await(BIND_SERVICE_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testGetProcessIsIsolated() throws RemoteException {
        Assert.assertFalse(Process.isIsolated());
        Assert.assertFalse(mService.getProcessIsIsolated());
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccServiceUidRange() throws RemoteException {
        Assert.assertTrue(Process.isPccUid(mService.getUid()));
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCorrectSelinuxDomain() throws RemoteException {
        final String selinuxContext = mService.getSeLinuxContext();
        assertThat(selinuxContext).contains("u:r:pcc_component");
    }

    @After
    public void tearDown() {
        getInstrumentation().getContext().unbindService(mServiceConnection);
    }
}
