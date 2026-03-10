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

package android.virtualdevice.cts.common;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.companion.virtual.VirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;

import androidx.annotation.NonNull;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Creates a {@link VirtualDevice} owned by a different app, allowing for cross-package testing.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
public class VirtualDeviceSession implements AutoCloseable {

    private static final String VIRTUAL_DEVICE_SESSION_PACKAGE =
            "android.virtualdevice.cts.virtualdevicesessionapp";
    private static final String VIRTUAL_DEVICE_SESSION_SERVICE =
            VIRTUAL_DEVICE_SESSION_PACKAGE + ".VirtualDeviceSessionService";

    private final Context mContext;
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private IVirtualDeviceSession mSession;
    private final VirtualDevice mVirtualDevice;
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSession = IVirtualDeviceSession.Stub.asInterface(service);
            mCountDownLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSession = null;
        }
    };

    public VirtualDeviceSession(@NonNull Instrumentation instrumentation, @NonNull String name) {
        mContext = instrumentation.getTargetContext();

        final Intent intent = new Intent()
                .setClassName(VIRTUAL_DEVICE_SESSION_PACKAGE, VIRTUAL_DEVICE_SESSION_SERVICE);
        if (!mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Failed to bind to VirtualDeviceSessionService");
        }

        try {
            if (!mCountDownLatch.await(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for VirtualDeviceSessionService");
            }
            String cmd = mSession.getCreateDeviceCommand(name);
            int deviceId = Integer.parseInt(SystemUtil.runShellCommandOrThrow(cmd).trim());
            VirtualDeviceManager vdm = mContext.getSystemService(VirtualDeviceManager.class);
            mVirtualDevice = vdm.getVirtualDevice(deviceId);
            if (mVirtualDevice == null) {
                throw new RuntimeException("Failed to create virtual device: " + cmd);
            }
        } catch (InterruptedException | RemoteException e) {
            throw new RuntimeException("Interrupted while waiting for VirtualDeviceSessionService");
        }
    }

    public int getDeviceId() {
        return mVirtualDevice.getDeviceId();
    }

    /** Creates a trusted display for the device and launches the given activity there. */
    public void launchActivity(@NonNull ComponentName componentName) {
        int displayId = Integer.parseInt(SystemUtil.runShellCommandOrThrow(
                "cmd virtualdevice create-display " + getDeviceId())
                .trim());
        Bundle options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
        Intent intent =
                new Intent().setComponent(componentName).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, options);
        mWmState.waitAndAssertActivityState(componentName, WindowManagerState.STATE_RESUMED);
    }

    /** Creates a service binding from the virtual device owner to the given service. */
    public void bindService(@NonNull ComponentName componentName) {
        try {
            mSession.bindService(componentName);
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to create service binding to " + componentName);
        }
    }

    @Override
    public void close() {
        try {
            SystemUtil.runShellCommandOrThrow("cmd virtualdevice close-device " + getDeviceId());
        } finally {
            mContext.unbindService(mServiceConnection);
        }
    }
}
