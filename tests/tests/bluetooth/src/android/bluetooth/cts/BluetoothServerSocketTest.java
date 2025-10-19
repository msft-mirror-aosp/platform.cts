/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothServerSocketTest {
    private static final int SCAN_STOP_TIMEOUT = 1000;
    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();

    private BluetoothServerSocket mBluetoothServerSocket;
    private boolean mHasBluetooth;

    @Before
    public void setUp() throws IOException {
        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH);
        assumeTrue(mHasBluetooth);

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        mBluetoothServerSocket = mAdapter.listenUsingL2capChannel();
    }

    @After
    public void tearDown() throws IOException {
        if (!mHasBluetooth) {
            return;
        }
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        if (mBluetoothServerSocket != null) {
            mBluetoothServerSocket.close();
        }
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void accept() {
        assertThrows(IOException.class, () -> mBluetoothServerSocket.accept(SCAN_STOP_TIMEOUT));
    }
}
