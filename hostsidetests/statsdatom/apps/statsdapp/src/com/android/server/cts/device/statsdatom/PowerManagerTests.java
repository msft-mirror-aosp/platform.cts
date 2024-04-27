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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.device.collectors.util.SendToInstrumentation;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.os.PowerManager;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PowerManagerTests {
    private static final String TAG = PowerManagerTests.class.getSimpleName();

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
        final Context context = InstrumentationRegistry.getContext();
        PerformanceHintManager perfHintManager = context.getSystemService(
                PerformanceHintManager.class);
        assertNotNull(perfHintManager);
        assumeTrue("ADPF is not supported on this device",
                perfHintManager.getPreferredUpdateRateNanos() >= TimeUnit.MILLISECONDS.toNanos(1));
        final Intent intent = new Intent(context, ADPFActivity.class);
        intent.putExtra(ADPFActivity.KEY_ACTION,
                ADPFActivity.ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ADPFActivity activity =
                (ADPFActivity) mInstrumentation.startActivitySync(intent);
        // this will wait until onCreate finishes
        mInstrumentation.waitForIdleSync();
        final Bundle bundle = activity.getRunResult(
                ADPFActivity.ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND);
        SendToInstrumentation.sendBundle(mInstrumentation, bundle);
    }
}
