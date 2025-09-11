/*
 * Copyright 2025 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.SystemUtil;

import java.io.Closeable;
import java.io.IOException;

/**
 * A utility class to fix device orientation during a test.
 *
 * <p>This class disables auto-rotation and sets the device to a specific orientation. When closed,
 * it restores the original auto-rotation setting.
 *
 * <p>This class also checks whether the device is capable to support rotation and throws an
 * assumption failure if it isn't.
 */
public class FixedDeviceOrientationSession implements Closeable {
    private static final String TAG = FixedDeviceOrientationSession.class.getSimpleName();

    private static final String FIXED_TO_USER_ROTATION_CMD = "cmd window fixed-to-user-rotation";
    private static final String ACCELEROMETER_ROTATION_DISABLED = "0";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    @Nullable private final String mInitialAutoRotate;

    /** Defines the target orientation for the device. */
    public enum Orientation {
        /** Sets the device to portrait orientation. */
        PORTRAIT,
        /** Sets the device to landscape orientation. */
        LANDSCAPE,
    }

    /**
     * Constructs a new FixedDeviceOrientationSession.
     *
     * <p>TODO - consider to support non-default display.
     *
     * @param targetOrientation The desired orientation to fix the device to.
     */
    public FixedDeviceOrientationSession(Orientation targetOrientation)
            throws RemoteException, IOException {
        PackageManager pm = mInstrumentation.getTargetContext().getPackageManager();
        assumeFalse(
                "Screen rotation is not supported on AAOS.",
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT));
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE));

        boolean isFixedToUserRotation =
                "enabled".equals(SystemUtil.runShellCommand(FIXED_TO_USER_ROTATION_CMD).trim());
        assumeFalse("Device shouldn't have fixed rotation.", isFixedToUserRotation);

        // Store initial auto-rotate setting.
        mInitialAutoRotate =
                Settings.System.getString(
                        mInstrumentation.getTargetContext().getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION);

        // Disable auto-rotate screen and set the explicit rotation.
        setAccelerometerRotation(ACCELEROMETER_ROTATION_DISABLED);
        setDeviceOrientation(targetOrientation);
    }

    /** Restores the initial auto-rotation setting. */
    @Override
    public void close() throws IOException {
        try {
            mUiDevice.unfreezeRotation();
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't unfreeze the device rotation", e);
        }
        setAccelerometerRotation(mInitialAutoRotate);
    }

    /** Sets the device orientation and wait for device idle. */
    public void setDeviceOrientation(Orientation targetOrientation) throws RemoteException {
        switch (targetOrientation) {
            case PORTRAIT -> mUiDevice.setOrientationPortrait();
            case LANDSCAPE -> mUiDevice.setOrientationLandscape();
        }
        mInstrumentation.waitForIdleSync();
    }

    private void setAccelerometerRotation(String value) throws IOException {
        if (value == null) {
            // Cannot delete via Settings.System API. Use shell command instead.
            mUiDevice.executeShellCommand(
                    "settings delete system " + Settings.System.ACCELEROMETER_ROTATION);
        } else {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        Settings.System.putString(
                                mInstrumentation.getTargetContext().getContentResolver(),
                                Settings.System.ACCELEROMETER_ROTATION,
                                value);
                    },
                    Manifest.permission.WRITE_SECURE_SETTINGS);
        }
    }
}
