/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.NonApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AppCompatTests {
    private static final String TAG = "AppCompatTests";
    private static final long FIND_TIMEOUT = 5000L;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Before
    public void setUp() throws RemoteException {
        mDevice.setOrientationNatural();
    }

    @After
    public void tearDown() throws RemoteException {
        mDevice.setOrientationNatural();
    }

    /**
     * Helper test to trigger size compat mode to log SizeCompatRestartButtonEventReported atoms.
     */
    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    @Test
    public void testClickSizeCompatRestartButton() throws RemoteException {
        final Context context = getApplicationContext();
        final Intent intent = new Intent(context, MinAspectRatioPortraitActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        mDevice.wait(Until.hasObject(By.clazz(MinAspectRatioPortraitActivity.class)), FIND_TIMEOUT);

        mDevice.setOrientationLeft();
        mDevice.waitForIdle();

        UiObject2 sizeCompatRestartButton = mDevice.wait(Until.findObject(
                By.res(SYSTEM_UI_PACKAGE, "size_compat_restart_button")), FIND_TIMEOUT);
        // Can't enforce existence of size compat restart button, exit early.
        assumeNotNull(sizeCompatRestartButton);
        sizeCompatRestartButton.click();
        mDevice.waitForIdle();

        UiObject2 confirmRestart = mDevice.wait(Until.findObject(
                By.res(SYSTEM_UI_PACKAGE, "letterbox_restart_dialog_restart_button")),
                FIND_TIMEOUT);
        // If confirm button is not found, restart dialog must have been disabled in configuration.
        // Exit early as click metric for restart button is already triggered.
        assumeNotNull(confirmRestart);
        confirmRestart.click();
    }
}
