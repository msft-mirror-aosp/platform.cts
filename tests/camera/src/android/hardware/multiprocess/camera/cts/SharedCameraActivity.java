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

import static android.hardware.camera2.cts.CameraTestUtils.*;

import static org.mockito.Mockito.*;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraSharedCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.Camera2SurfaceViewCtsActivity;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration.SharedOutputConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ResultReceiver;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.utils.StateWaiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Activity implementing basic access of the shared camera API.
 *
 * <p>This will log all errors to {@link
 * android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class SharedCameraActivity extends Camera2SurfaceViewCtsActivity {
    private static final String TAG = "SharedCameraActivity";
    private static final int CAPTURE_RESULT_TIMEOUT_MS = 3000;
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    CameraManager mCameraManager;
    StateCallback mStateCallback;
    SessionCallback mSessionCallback;
    Handler mCameraHandler;
    HandlerThread mCameraHandlerThread;
    String mCameraId;
    Messenger mMessenger;
    CameraDevice mCameraDevice;
    CameraSharedCaptureSession mSession;
    BlockingSessionCallback mSessionMockListener;
    CaptureRequest.Builder mCaptureRequestBuilder;
    Surface mPreviewSurface;
    int mCaptureSequenceId;
    SimpleCaptureCallback mCaptureListener;
    SharedSessionConfiguration mSharedSessionConfig;

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

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
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
            mCameraDevice = cameraDevice;
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
            Log.i(
                    TAG,
                    "Camera " + mChosenCameraId + " is opened in shared mode primary=" + isPrimary);
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CLOSED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is closed");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_DISCONNECTED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is disconnected");
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_ERROR,
                    TAG + " Camera " + mChosenCameraId + " experienced error " + i);
            Log.e(TAG, "Camera " + mChosenCameraId + " onError called with error " + i);
        }

        @Override
        public void onClientSharedAccessPriorityChanged(CameraDevice camera, boolean isPrimary) {
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

    private class SessionCallback extends CameraCaptureSession.StateCallback {
        String mChosenCameraId;

        SessionCallback(String camId) {
            mChosenCameraId = camId;
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            mSession = null;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CLOSED, mChosenCameraId);
            Log.i(TAG, "Camera capture session for camera " + mChosenCameraId + " is closed");
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mSession = (CameraSharedCaptureSession) session;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CONFIGURED, mChosenCameraId);
            Log.i(TAG, "Camera capture session for camera " + mChosenCameraId + " is configured");
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            mSession = null;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED, mChosenCameraId);
            Log.i(TAG, "Camera capture session creation for camera " + mChosenCameraId + " failed");
        }
    }

    private void updatePreviewSurface() {
        List<SharedOutputConfiguration> configs =
                mSharedSessionConfig.getOutputStreamsInformation();
        for (SharedOutputConfiguration sharedConfig : configs) {
            if (sharedConfig.getSurfaceType() == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                final SurfaceHolder holder = getSurfaceView().getHolder();
                mPreviewSurface = holder.getSurface();
                Size sz = sharedConfig.getSize();
                holder.setFixedSize(sz.getWidth(), sz.getHeight());
                break;
            }
        }
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TestConstants.OP_OPEN_CAMERA:
                    mCameraId = msg.getData().getString(TestConstants.EXTRA_CAMERA_ID);
                    try {
                        if (mStateCallback == null || mStateCallback.mChosenCameraId != mCameraId) {
                            mStateCallback = new StateCallback(mCameraId);
                            final Executor executor = new HandlerExecutor(mCameraHandler);
                            mCameraManager.openCamera(mCameraId, executor, mStateCallback);
                        }
                    } catch (CameraAccessException e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " camera exception during connection: " + e);
                        Log.e(TAG, "Access exception: " + e);
                    }
                    break;

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
                            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(
                                    mCameraId);
                            mSharedSessionConfig = props.get(
                                    CameraCharacteristics.SHARED_SESSION_CONFIGURATION);
                            if (mSharedSessionConfig == null) {
                                mErrorServiceConnection.logAsync(
                                        TestConstants.EVENT_CAMERA_ERROR,
                                        TAG + " shared session config is null");
                                Log.e(TAG, "shared session config is null");
                                return;
                            }
                            updatePreviewSurface();
                        }
                    } catch (CameraAccessException e) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                                + " camera exception during connection: " + e);
                        Log.e(TAG, "Access exception: " + e);
                    }
                    break;

                case TestConstants.OP_CLOSE_CAMERA:
                    if (mSession != null) {
                        mSession.close();
                        mSession = null;
                        mSessionMockListener = null;
                        mCaptureListener = null;
                        mSessionCallback = null;
                    }
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                        mStateCallback = null;
                    }
                    break;

                case TestConstants.OP_CREATE_SHARED_SESSION:
                    if (mCameraDevice == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + "camera device is null");
                        Log.e(TAG, "camera device is null");
                        return;

                    }
                    List<Integer> sharedStreamArray = msg.getData().getIntegerArrayList(
                            TestConstants.EXTRA_SHARED_STREAM_ARRAY);
                    if (sharedStreamArray == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " shared stream index array is null");
                        Log.e(TAG, "shared stream index array is null");
                        return;
                    }
                    try {
                        List<OutputConfiguration> outputs = new ArrayList<>();
                        mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        for (int i = 0; i < sharedStreamArray.size(); i++) {
                            Integer streamIdx = sharedStreamArray.get(i);
                            SharedOutputConfiguration sharedStreamInfo =
                                    mSharedSessionConfig.getOutputStreamsInformation()
                                    .get(streamIdx);
                            if (sharedStreamInfo.getSurfaceType()
                                    == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                                outputs.add(new OutputConfiguration(mPreviewSurface));
                                mCaptureRequestBuilder.addTarget(mPreviewSurface);
                            }
                        }
                        if (outputs.isEmpty()) {
                            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                    TAG + " shared output configuration is empty");
                            Log.e(TAG, "shared output configuration is empty");
                            return;
                        }
                        if (mSessionCallback == null
                                || mSessionCallback.mChosenCameraId != mCameraId) {
                            mSessionCallback = new SessionCallback(mCameraId);
                        }
                        mSessionMockListener = spy(new BlockingSessionCallback(mSessionCallback));
                        StateWaiter sessionWaiter = mSessionMockListener.getStateWaiter();
                        final Executor executor = new HandlerExecutor(mCameraHandler);
                        SessionConfiguration sessionConfig = new SessionConfiguration(
                                SessionConfiguration.SESSION_SHARED, outputs, executor,
                                mSessionMockListener);
                        sessionConfig.setSessionParameters(mCaptureRequestBuilder.build());
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiter.waitForState(BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during creating shared session: " + e);
                        Log.e(TAG, "exception during creating shared session: " + e);
                    }
                    break;

                case TestConstants.OP_START_PREVIEW:
                    if (mCameraDevice == null || mSession == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + "No active camera device or session is present");
                        Log.e(TAG, "No active camera device or session is present");
                        return;
                    }
                    try {
                        mCaptureListener = new SimpleCaptureCallback();
                        mCaptureSequenceId = mSession.setRepeatingRequest(
                                mCaptureRequestBuilder.build(), mCaptureListener, mCameraHandler);
                        mCaptureListener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_PREVIEW_STARTED,
                                Integer.toString(mCaptureSequenceId));
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during creating shared session: " + e);
                        Log.e(TAG, "exception during creating shared session: " + e);
                    }
                    break;

                case TestConstants.OP_STOP_PREVIEW:
                    if (mCameraDevice == null || mSession == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + "No active camera device or session is present");
                        Log.e(TAG, "No active camera device or session is present");
                        return;
                    }
                    try {
                        mSession.stopRepeating();
                        mCaptureListener.getCaptureSequenceLastFrameNumber(
                                mCaptureSequenceId, CAPTURE_RESULT_TIMEOUT_MS);
                        mCaptureListener.drain();
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_PREVIEW_COMPLETED,
                                Integer.toString(mCaptureSequenceId));
                        mCaptureSequenceId = -1;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during creating shared session: " + e);
                        Log.e(TAG, "exception during creating shared session: " + e);
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }
}
