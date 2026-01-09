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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nativeservice.INativeServiceListener;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE,
    android.os.Flags.FLAG_NATIVE_APP_ZYGOTE
})
public final class NativeAppZygoteServiceTest extends NativeServiceTestBase {
    private final String mNativeServiceClassName2;
    private NativeServiceTestConnection mConnKeepAlive2;

    public NativeAppZygoteServiceTest() {
        mUseAppZygote = true;
        mNativeServiceClassName = "android.nativeservice.test.NativeAppZygoteService";
        mNativeServiceClassName2 = "android.nativeservice.test.NativeAppZygoteService2";
    }

    @Before
    public void setup() throws RemoteException {
        // Isolated processes get killed aggressively by AMS if there's no connecton.
        // Create a connection to keep the process alive.
        Intent intent = new Intent(TEST_ACTION_KEEPALIVE);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, mNativeServiceClassName2));
        INativeServiceListener listener = mock(INativeServiceListener.class);
        mConnKeepAlive2 = new NativeServiceTestConnection(listener, 0);
        assertTrue(mContext.bindService(intent, mConnKeepAlive2, Context.BIND_AUTO_CREATE));
        verify(listener, timeout(TIMEOUT_MS)).onRegister();
    }

    @After
    public void tearDown() {
        mContext.unbindService(mConnKeepAlive2);
    }

    @Test
    public void testMultipleServicesShareZygote() throws InterruptedException, RemoteException {
        Intent intent1 = createServiceIntent(TEST_ACTION_UTF8);

        INativeServiceListener mockListener1 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn1 = new NativeServiceTestConnection(mockListener1, 1);
        assertTrue(mContext.bindService(intent1, conn1, Context.BIND_AUTO_CREATE));
        verify(mockListener1, timeout(TIMEOUT_MS)).onRegister();

        Intent intent2 = createServiceIntent(TEST_ACTION_UTF8, mNativeServiceClassName2);

        INativeServiceListener mockListener2 = mock(INativeServiceListener.class);
        NativeServiceTestConnection conn2 = new NativeServiceTestConnection(mockListener2, 2);
        assertTrue(mContext.bindService(intent2, conn2, Context.BIND_AUTO_CREATE));
        verify(mockListener2, timeout(TIMEOUT_MS)).onRegister();

        assertSameParentPid(conn1, conn2);

        mContext.unbindService(conn1);
        verify(mockListener1, timeout(TIMEOUT_MS)).onUnbind();
        verify(mockListener2, never()).onUnbind();

        mContext.unbindService(conn2);
        verify(mockListener2, timeout(TIMEOUT_MS)).onUnbind();
    }
}
