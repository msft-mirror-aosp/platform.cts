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

package android.nativeservice.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nativeservice.INativeServiceListener;
import android.nativeservice.INativeServiceWrapper;
import android.net.Uri;
import android.os.RemoteException;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public abstract class NativeServiceTestBase {
    static final String TARGET_PACKAGE = "android.nativeservice.test";
    static final long TIMEOUT_MS = 10000;
    static final String TEST_ACTION_KEEPALIVE = "TEST_ACTION_KEEPALIVE";
    static final String TEST_ACTION_UTF8 = "TEST_ACTION 🂡 🂢 🂣";
    static final String TEST_ACTION_NOREBIND = "TEST_ACTION_NOREBIND";
    static final String TEST_ACTION_REBIND = "TEST_ACTION_REBIND";
    static final Uri TEST_DATA = Uri.parse("content://com.example/people/");
    static final String TEST_DATA_UTF8_PART = "🂡 🂢 🂣";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    protected boolean mUseAppZygote;
    protected String mNativeServiceClassName;
    protected Uri mData;
    protected Context mContext;

    private NativeServiceTestConnection mConnKeepAlive;

    @Before
    public void baseSetup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mData = TEST_DATA.buildUpon().appendPath(TEST_DATA_UTF8_PART).build();

        // Isolated processes get killed aggressively by AMS if there's no connecton.
        // Create a connection to keep the process alive.
        Intent intent = new Intent(TEST_ACTION_KEEPALIVE);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, mNativeServiceClassName));
        mConnKeepAlive = new NativeServiceTestConnection(/* listener= */ null, /* id= */ 0);
        assertTrue(mContext.bindService(intent, mConnKeepAlive, Context.BIND_AUTO_CREATE));
    }

    @After
    public void baseTearDown() {
        mContext.unbindService(mConnKeepAlive);
    }

    private void assertMarkedPreloaded(NativeServiceTestConnection conn) throws RemoteException {
        INativeServiceWrapper service = conn.getService();
        assertTrue(service.isLibraryMarkedPreloaded());
    }

    void assertSameParentPid(NativeServiceTestConnection conn1, NativeServiceTestConnection conn2)
            throws RemoteException {
        assertSameParentPid(conn1.getService().getParentPid(), conn2);
    }

    private void assertSameParentPid(int ppid, NativeServiceTestConnection conn)
            throws RemoteException {
        assertEquals(ppid, conn.getService().getParentPid());
    }

    protected Intent createServiceIntent(String action) {
        return createServiceIntent(action, mNativeServiceClassName);
    }

    protected Intent createServiceIntent(String action, String className) {
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, className));
        intent.setData(mData);
        return intent;
    }

    // @ApiTest = ANativeService_setOnBindCallback|ANativeService_setOnUnbindCallback
    @Test
    public void testLifeCycle() throws InterruptedException, RemoteException {
        Intent intent = createServiceIntent(TEST_ACTION_UTF8);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn);
        }
        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();
    }

    // @ApiTest = ANativeService_setOnBindCallback
    //            | ANativeService_setOnRebindCallback
    //            | ANativeService_setOnUnbindCallback
    @Test
    public void testNoRebind() throws InterruptedException, RemoteException {
        Intent intent = createServiceIntent(TEST_ACTION_NOREBIND);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        int ppid = conn.getService().getParentPid();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn);
        }

        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);

        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn2);
            assertSameParentPid(ppid, conn2);
        }
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
        Intent intent = createServiceIntent(TEST_ACTION_REBIND);
        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn = new NativeServiceTestConnection(mockListener, 1);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        int ppid = conn.getService().getParentPid();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn);
        }

        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);

        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();
        verify(mockListener, timeout(TIMEOUT_MS)).onRebind();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn2);
            assertSameParentPid(ppid, conn2);
        }

        mContext.unbindService(conn2);
        // onUnbind MUST be called again.
        verify(mockListener2, timeout(TIMEOUT_MS)).onUnbind();
    }

    // @ApiTest = ANativeService_setOnBindCallback|ANativeService_setOnUnbindCallback
    @Test
    public void testMultipleClients() throws InterruptedException, RemoteException {
        Intent intent = createServiceIntent(TEST_ACTION_UTF8);

        INativeServiceListener mockListener1 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn1 = new NativeServiceTestConnection(mockListener1, 1);
        assertTrue(mContext.bindService(intent, conn1, Context.BIND_AUTO_CREATE));
        verify(mockListener1, timeout(TIMEOUT_MS)).onRegister();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn1);
        }

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);
        assertTrue(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();
        if (mUseAppZygote) {
            assertMarkedPreloaded(conn2);
            assertSameParentPid(conn1, conn2);
        }

        mContext.unbindService(conn1);
        verify(mockListener1, never()).onUnbind();
        verify(mockListener2, never()).onUnbind();

        mContext.unbindService(conn2);
        verify(mockListener1, timeout(TIMEOUT_MS)).onUnbind();
        verify(mockListener2, timeout(TIMEOUT_MS)).onUnbind();
    }
}
