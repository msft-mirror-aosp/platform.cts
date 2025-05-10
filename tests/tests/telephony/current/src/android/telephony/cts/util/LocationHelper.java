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

package android.telephony.cts.util;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertTrue;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LocationHelper {
    private static final String TAG = LocationHelper.class.getSimpleName();
    private static final int SETTING_CHANGE_TIMEOUT_MS = 1000;

    @NonNull private Context mContext;
    private boolean mRevertOnTearDown = false;

    public LocationHelper(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
    }

    @Override
    protected void finalize() throws Throwable {
        if (mRevertOnTearDown) {
            throw new IllegalStateException("Revert flag is still set - tearDown wasn't called");
        }
    }

    /**
     * Restore location settings to the original state.
     *
     * <p>Note: This must be called in test's tearDown method.
     */
    public void tearDown() {
        if (mRevertOnTearDown) setEnabled(false);
        mRevertOnTearDown = false;
    }

    /** Enable location setting without granting location permissions to test's package. */
    public void enableWithoutPermissions() {
        if (!setEnabled(true)) mRevertOnTearDown = true;
    }

    /** Enable location setting and grant location permissions to test's package. */
    public void enable() {
        grantPermissions();
        enableWithoutPermissions();
    }

    private void grantPermissions() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String packageName = mContext.getPackageName();
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_COARSE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_FINE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_BACKGROUND_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.WRITE_SECURE_SETTINGS);
    }

    /**
     * Enable/disable location for current user.
     *
     * @return true if location was previously enabled, false if disabled.
     */
    private boolean setEnabled(boolean setEnabled) {
        LocationManager locationManager = mContext.getSystemService(LocationManager.class);
        boolean wasEnabled = locationManager.isLocationEnabledForUser(mContext.getUser());
        if (wasEnabled == setEnabled) return wasEnabled;

        CountDownLatch locationChangeLatch = new CountDownLatch(1);
        BroadcastReceiver locationModeChangeReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!LocationManager.MODE_CHANGED_ACTION.equals(intent.getAction())) return;
                        boolean isEnabled =
                                intent.getBooleanExtra(
                                        LocationManager.EXTRA_LOCATION_ENABLED, !setEnabled);
                        if (setEnabled == isEnabled) {
                            locationChangeLatch.countDown();
                        }
                    }
                };

        Log.d(TAG, "Setting location " + (setEnabled ? "enabled" : "disabled"));

        mContext.registerReceiver(
                locationModeChangeReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        locationManager.setLocationEnabledForUser(setEnabled, mContext.getUser());
                    });
            assertTrue(locationChangeLatch.await(SETTING_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while waiting for location settings change.");
        } finally {
            mContext.unregisterReceiver(locationModeChangeReceiver);
        }

        return wasEnabled;
    }
}
