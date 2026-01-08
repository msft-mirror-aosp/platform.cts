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

import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.nativeservice.INativeServiceListener;
import android.nativeservice.INativeServiceWrapper;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public final class NativeServiceTestConnection implements ServiceConnection {
    private static class GlueListener extends INativeServiceListener.Stub {
        private static final String TAG = "GlueListener";
        private final INativeServiceListener mMock;
        private int mId;

        GlueListener(INativeServiceListener mock, int id) {
            mMock = mock;
            mId = id;
        }

        @Override
        public void onRegister() throws RemoteException {
            Log.i(TAG, "onRegister " + mId);
            mMock.onRegister();
        }

        @Override
        public void onUnbind() throws RemoteException {
            Log.i(TAG, "onUnbind " + mId);
            mMock.onUnbind();
        }

        @Override
        public void onRebind() throws RemoteException {
            Log.i(TAG, "onRebind " + mId);
            mMock.onRebind();
        }
    }

    private static final String TAG = "NativeServiceTestConnection";
    private GlueListener mListener;
    private INativeServiceWrapper mService;
    private boolean mRegistered;

    public NativeServiceTestConnection(INativeServiceListener listener, int id) {
        if (listener != null) {
            mListener = new GlueListener(listener, id);
        }
        mRegistered = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "onServiceConnected");
        if (mListener == null || mRegistered) return;

        mService = INativeServiceWrapper.Stub.asInterface(service);
        try {
            mService.registerListener(mListener);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        mRegistered = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.i(TAG, "onServiceDisconnected");
    }

    public INativeServiceWrapper getService() {
        return mService;
    }
}
