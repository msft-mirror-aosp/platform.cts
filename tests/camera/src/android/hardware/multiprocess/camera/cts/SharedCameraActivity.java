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

package android.hardware.multiprocess.camera.cts;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ResultReceiver;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Activity implementing basic access of the shared camera API.
 *
 * <p>This will log all errors to {@link
 * android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class SharedCameraActivity extends Activity {
    private static final String TAG = "SharedCameraActivity";
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    CameraManager mCameraManager;
    StateCallback mStateCallback;
    Handler mCameraHandler;
    HandlerThread mCameraHandlerThread;
    String mCameraId;
    Messenger mMessenger;
    CameraDevice mCameraDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called: uid " + Process.myUid() + ".");
        super.onCreate(savedInstanceState);

        mMessenger = new Messenger(new IncomingHandler());
        ResultReceiver resultReceiver = getIntent().getParcelableExtra(
                TestConstants.EXTRA_RESULT_RECEIVER);
        Bundle resultData = new Bundle();
        resultData.putParcelable(TestConstants.EXTRA_REMOTE_MESSENGER, mMessenger);
        resultReceiver.send(RESULT_OK, resultData);
        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();
        mCameraManager = getSystemService(CameraManager.class);
        if (mCameraManager == null) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                    + " could not connect camera service");
            return;
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called.");
        super.onDestroy();

        mCameraHandlerThread.quitSafely();

        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
    }

    private class StateCallback extends CameraDevice.StateCallback {
        String mChosenCameraId;

        StateCallback(String camId) {
            mChosenCameraId = camId;
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is opened");
        }

        @Override
        public void onOpenedInSharedMode(CameraDevice cameraDevice, boolean isPrimary) {
            mCameraDevice = cameraDevice;
            if (isPrimary) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT_SHARED_PRIMARY,
                        mChosenCameraId);
            } else {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CAMERA_CONNECT_SHARED_SECONDARY, mChosenCameraId);
            }
            Log.i(TAG, "Camera " + mChosenCameraId + " is opened in shared mode primary=" + isPrimary);
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CLOSED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is closed");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_DISCONNECTED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is disconnected");
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG + " Camera "
                    + mChosenCameraId + " experienced error " + i);
            Log.e(TAG, "Camera " + mChosenCameraId + " onError called with error " + i);
        }

        @Override
        public void onClientSharedAccessPriorityChanged(CameraDevice camera,
                boolean isPrimary) {
            if (isPrimary) {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_PRIMARY,
                        mChosenCameraId);
            } else {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_SECONDARY, mChosenCameraId);
            }
            Log.i(TAG, "Camera " + mChosenCameraId + " onClientSharedAccessPriorityChanged primary="
                    + isPrimary);
        }
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TestConstants.OP_OPEN_CAMERA_SHARED:
                    mCameraId = msg.getData().getString(TestConstants.EXTRA_CAMERA_ID);
                    try {
                        boolean sharingEnabled = mCameraManager.isCameraDeviceSharingSupported(
                                mCameraId);
                        if (!sharingEnabled) {
                            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                                    + " camera device does not support shared mode");
                            Log.e(TAG, "camera device does not support shared mode");
                            return;
                        }
                        if (mStateCallback == null || mStateCallback.mChosenCameraId!= mCameraId) {
                            mStateCallback = new StateCallback(mCameraId);
                            final Executor executor = new HandlerExecutor(mCameraHandler);
                            mCameraManager.openSharedCamera(mCameraId, executor, mStateCallback);
                        }
                    } catch (CameraAccessException e) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                                + " camera exception during connection: " + e);
                        Log.e(TAG, "Access exception: " + e);
                    }
                    break;

                case TestConstants.OP_CLOSE_CAMERA:
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
