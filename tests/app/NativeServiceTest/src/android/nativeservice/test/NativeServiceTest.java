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

package android.nativeservice.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nativeservice.INativeServiceListener;
import android.net.Uri;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE
})
public final class NativeServiceTest {
    private static final String TARGET_PACKAGE = "android.nativeservice.test";
    private static final String NATIVE_SERVICE_CLASS = "android.nativeservice.test.NativeService";
    private static final long TIMEOUT_MS = 10000;
    private static final String TEST_ACTION_KEEPALIVE = "TEST_ACTION_KEEPALIVE";
    private static final String TEST_ACTION_UTF8 = "TEST_ACTION 🂡 🂢 🂣";
    private static final String TEST_ACTION_NOREBIND = "TEST_ACTION_NOREBIND";
    private static final String TEST_ACTION_REBIND = "TEST_ACTION_REBIND";
    private static final Uri TEST_DATA = Uri.parse("content://com.example/people/");
    private static final String TEST_DATA_UTF8_PART = "🂡 🂢 🂣";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private Uri mData;
    private NativeServiceTestConnection mConnKeepAlive;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mData = TEST_DATA.buildUpon().appendPath(TEST_DATA_UTF8_PART).build();

        // Isolated processes get killed aggressively by AMS if there's no connecton.
        // Create a connection to keep the process alive.
        Intent intent = new Intent(TEST_ACTION_KEEPALIVE);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        mConnKeepAlive = new NativeServiceTestConnection(null, 0);
        assertTrue(mContext.bindService(intent, mConnKeepAlive, Context.BIND_AUTO_CREATE));
    }

    @After
    public void tearDown() {
        mContext.unbindService(mConnKeepAlive);
    }

    // @ApiTest = ANativeService_setOnBindCallback|ANativeService_setOnUnbindCallback
    @Test
    public void testLifeCycle() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION_UTF8);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(mData);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        ServiceConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();
    }

    // @ApiTest = ANativeService_setOnBindCallback
    //            | ANativeService_setOnRebindCallback
    //            | ANativeService_setOnUnbindCallback
    @Test
    public void testNoRebind() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION_NOREBIND);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(mData);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        ServiceConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();

        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);

        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();
        verify(mockListener, never()).onRebind();

        mContext.unbindService(conn2);
        // onUnbind MUST NOT be called again.
        verify(mockListener2, never()).onUnbind();
    }

    // @ApiTest = ANativeService_setOnBindCallback
    //            | ANativeService_setOnRebindCallback
    //            | ANativeService_setOnUnbindCallback
    @Test
    public void testRebind() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION_REBIND);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(mData);
        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();

        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);

        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();
        verify(mockListener, timeout(TIMEOUT_MS)).onRebind();

        mContext.unbindService(conn2);
        // onUnbind MUST be called again.
        verify(mockListener2, timeout(TIMEOUT_MS)).onUnbind();
    }

    // @ApiTest = ANativeService_setOnBindCallback|ANativeService_setOnUnbindCallback
    @Test
    public void testMultipleClients() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION_UTF8);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(mData);

        INativeServiceListener mockListener1 = mock(INativeServiceListener.class);
        ServiceConnection conn1 = new NativeServiceTestConnection(mockListener1, 1);
        assertTrue(mContext.bindService(intent, conn1, Context.BIND_AUTO_CREATE));
        verify(mockListener1, timeout(TIMEOUT_MS)).onRegister();

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        ServiceConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);
        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();

        mContext.unbindService(conn1);
        verify(mockListener1, never()).onUnbind();
        verify(mockListener2, never()).onUnbind();

        mContext.unbindService(conn2);
        verify(mockListener1, timeout(TIMEOUT_MS)).onUnbind();
        verify(mockListener2, timeout(TIMEOUT_MS)).onUnbind();
    }
}
