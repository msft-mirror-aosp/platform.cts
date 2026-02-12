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
package com.android.cts.pcc.service.nonpccclient;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.service.common.TestUtils.addTriggerPfd;
import static com.android.cts.pcc.service.common.TestUtils.getNestedBundleWithDepth100;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.app.privatecompute.PccClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.system.OsConstants;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccServiceTest {
    private static final int TIMEOUT_MS = 5000;
    public static final String IPCC_SERVICE_BINDER_DESCRIPTOR =
            "android.app.privatecompute.IPccService";
    public static final ComponentName PCC_SERVICE_COMPONENT =
            new ComponentName(
                    "com.android.cts.pcc.service.nonpccclient",
                    "com.android.cts.pcc.service.common.TestPccService");
    public static final ComponentName NON_PCC_SERVICE_COMPONENT =
            new ComponentName(
                    "com.android.cts.pcc.service.nonpccclient",
                    "com.android.cts.pcc.service.common.TestPccRegularService");

    private Context mContext;
    private final BlockingQueue<IBinder> mBinderQueue = new LinkedBlockingQueue<>();
    private ServiceConnection mConnection;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        mBinderQueue.offer(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
    }

    @Test
    public void bind_assertPccBinderObject() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(IPCC_SERVICE_BINDER_DESCRIPTOR, binder.getInterfaceDescriptor());
    }

    @Test
    public void bind_regularServiceInPccSandbox_nullBinderReceived() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NON_PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertNull(binder);
    }

    @Test
    public void bind_sendBinder_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        data.putBinder("my_binder", new Binder());
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    pccClient.sendData(data);
                });
    }

    @Test
    public void bind_sendWritablePfd_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        File nullFile = new File("/dev/null");
        ParcelFileDescriptor writablePfd =
                ParcelFileDescriptor.open(nullFile, ParcelFileDescriptor.MODE_READ_WRITE);
        data.putParcelable("pfd", writablePfd);
        assertThrows(
                "sendData was expected to throw IllegalArgumentException",
                IllegalArgumentException.class,
                () -> {
                    pccClient.sendData(data);
                });
    }

    @Test
    public void bind_sendCustomParcelable_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        data.putParcelable("custom_parcelable", Uri.EMPTY);
        assertThrows(
                "sendData was expected to throw IllegalArgumentException",
                IllegalArgumentException.class,
                () -> {
                    pccClient.sendData(data);
                });
    }

    @Test
    public void bind_sendParcelableWithDepth101_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        data.putBundle("nested_bundle", getNestedBundleWithDepth100());
        assertThrows(
                "sendData was expected to throw IllegalArgumentException",
                IllegalArgumentException.class,
                () -> {
                    pccClient.sendData(data);
                });
    }

    @Test
    public void bind_sendWritableSharedMemory_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        SharedMemory sm = SharedMemory.create("my_shared_mem", 5);
        sm.setProtect(OsConstants.PROT_WRITE);
        data.putParcelable("shared_memory", sm);

        assertThrows(
                "sendData was expected to throw IllegalArgumentException",
                IllegalArgumentException.class,
                () -> {
                    pccClient.sendData(data);
                });
    }

    @Test
    public void bind_sendReadOnlySharedMemory_doesNotThrow() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        addTriggerPfd(data);
        SharedMemory sm = SharedMemory.create("my_shared_mem", 5);
        sm.setProtect(OsConstants.PROT_READ);
        data.putParcelable("shared_memory", sm);

        try {
            pccClient.sendData(data);
        } catch (Exception e) {
            throw new AssertionError(
                    "sendData from PCC client to PCC service should not throw.", e);
        }
    }

    @Test
    public void bind_extrasWithBinder_throws() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(PCC_SERVICE_COMPONENT);
        Bundle extras = new Bundle();
        extras.putBinder("my_binder", new Binder());
        intent.putExtras(extras);

        assertThrows(
                SecurityException.class,
                () -> mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE));
    }

    @Test
    public void testNonPccToPccProviderAccess() {
        String processName = mContext.getApplicationInfo().processName;
        int uid = android.os.Process.myUid();
        java.util.List<android.content.pm.ProviderInfo> providers =
                mContext.getPackageManager().queryContentProviders(processName, uid, 0);
        if (providers != null) {
            for (android.content.pm.ProviderInfo info : providers) {
                if ("com.android.cts.pcc.service.pccclient.provider.pcc".equals(info.authority)) {
                    throw new AssertionError(
                            "Non-PCC to PCC provider access should fail (provider NOT in list)");
                }
            }
        }
    }

    @After
    public void tearDown() {
        if (mConnection != null) {
            try {
                mContext.unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                // Ignore if not bound
            }
        }
    }
}
