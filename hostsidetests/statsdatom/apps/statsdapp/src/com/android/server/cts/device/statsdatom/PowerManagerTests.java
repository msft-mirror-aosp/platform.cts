/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_COLUMN_KEY;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_COLUMN_VALUE;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_URI_STRING;
import static android.adpf.atom.common.ADPFAtomTestConstants.INTENT_ACTION_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.device.collectors.util.SendToInstrumentation;
import android.net.Uri;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PowerManagerTests {
    private static final String TAG = PowerManagerTests.class.getSimpleName();
    private static final String ADPF_ATOM_TEST_PKG = "android.adpf.atom.app";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();


    @Test
    public void testGetCurrentThermalStatus() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        assertNotNull(powerManager);
        powerManager.getCurrentThermalStatus();
    }

    @Test
    public void testGetThermalHeadroom() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        assertNotNull(powerManager);
        powerManager.getThermalHeadroom(10);
    }

    @Test
    public void testGetThermalHeadroomThresholds() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        assertNotNull(powerManager);
        powerManager.getThermalHeadroomThresholds();
    }

    @Test
    public void testAdpfTidCleanup() {
        final Context instContext = InstrumentationRegistry.getContext();
        PerformanceHintManager perfHintManager = instContext.getSystemService(
                PerformanceHintManager.class);
        assertNotNull(perfHintManager);
        assumeTrue("ADPF is not supported on this device",
                perfHintManager.getPreferredUpdateRateNanos() >= TimeUnit.MILLISECONDS.toNanos(1));

        // Initialize UiDevice instance
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // Launch the app
        Context appContext = ApplicationProvider.getApplicationContext();
        final Intent intent = appContext.getPackageManager()
                .getLaunchIntentForPackage(ADPF_ATOM_TEST_PKG);
        intent.putExtra(INTENT_ACTION_KEY, ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(ADPF_ATOM_TEST_PKG).depth(0)), 2000);
        device.waitForIdle();
        Log.d(TAG, "Wait for idle finished");

        Uri uri = Uri.parse(CONTENT_URI_STRING);
        Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null);
        assertTrue("Invalid cursor from querying content resolver",
                cursor != null && cursor.moveToFirst());
        Bundle bundle = new Bundle();
        do {
            int keyIndex = cursor.getColumnIndex(CONTENT_COLUMN_KEY);
            assertNotEquals("data key index is -1", keyIndex, -1);
            assertEquals("unexpected key type", Cursor.FIELD_TYPE_STRING,
                    cursor.getType(keyIndex));

            int valueIndex = cursor.getColumnIndex(CONTENT_COLUMN_VALUE);
            assertNotEquals("data value index is -1", valueIndex, -1);
            assertEquals("unexpected value type", Cursor.FIELD_TYPE_STRING,
                    cursor.getType(valueIndex));
            Log.d(TAG,
                    "Content parsed with key: " + cursor.getString(keyIndex) + ", value: "
                            + cursor.getString(valueIndex));
            bundle.putString(cursor.getString(keyIndex), cursor.getString(valueIndex));
        } while (cursor.moveToNext());
        cursor.close();

        SendToInstrumentation.sendBundle(mInstrumentation, bundle);
    }
}
