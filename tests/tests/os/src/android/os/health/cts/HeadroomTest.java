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

package android.os.health.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.CpuHeadroomParams;
import android.os.Flags;
import android.os.GpuHeadroomParams;
import android.os.health.SystemHealthManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Provides test cases for android.os.health.SystemHealthManager headroom APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class HeadroomTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private void validateHeadroom(float headroom) {
        if (!Float.isNaN(headroom)) {
            assertTrue("Headroom should be between 0.0 and 100.0, but was " + headroom,
                    headroom >= 0.0f && headroom <= 100.0f);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_default() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        CpuHeadroomParams params = new CpuHeadroomParams();
        assertEquals(params.getCalculationType(),
                CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_MIN);
        try {
            float headroom = healthy.getCpuHeadroom(params);
            validateHeadroom(headroom);
            try {
                headroom = healthy.getCpuHeadroom(null);
                validateHeadroom(headroom);
            } catch (Exception e) {
                fail("getCpuHeadroom(null) shouldn't fail when getCpuHeadroom(params) "
                        + "succeeds: " + e.getMessage());
            }
            try {
                long minInterval = healthy.getCpuHeadroomMinIntervalMillis();
                assertTrue(minInterval > 0);
            } catch (Exception e) {
                fail("getCpuHeadroomMinIntervalMillis() shouldn't fail when getCpuHeadroom(_) "
                        + "succeeds: " + e.getMessage());
            }
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("CPU headroom is not enabled", false);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_average() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        CpuHeadroomParams params = new CpuHeadroomParams();
        params.setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
        assertEquals(params.getCalculationType(),
                CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
        try {
            float headroom = healthy.getCpuHeadroom(params);
            validateHeadroom(headroom);
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("CPU headroom is not enabled", false);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroom_default() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        GpuHeadroomParams params = new GpuHeadroomParams();
        assertEquals(params.getCalculationType(),
                GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_MIN);
        try {
            float headroom = healthy.getGpuHeadroom(params);
            validateHeadroom(headroom);
            try {
                healthy.getGpuHeadroom(null);
                validateHeadroom(headroom);
            } catch (Exception e) {
                fail("getGpuHeadroom(null) shouldn't fail when getGpuHeadroom(params) "
                        + "succeeds: " + e.getMessage());
            }
            try {
                long minInterval = healthy.getGpuHeadroomMinIntervalMillis();
                assertTrue(minInterval > 0);
            } catch (Exception e) {
                fail("getGpuHeadroomMinIntervalMillis() shouldn't fail when getGpuHeadroom(_) "
                        + "succeeds: " + e.getMessage());
            }
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("GPU headroom is not enabled", false);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroom_average() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        GpuHeadroomParams params = new GpuHeadroomParams();
        params.setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
        assertEquals(params.getCalculationType(),
                GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
        try {
            float headroom = healthy.getGpuHeadroom(params);
            validateHeadroom(headroom);
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("GPU headroom is not enabled", false);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroomMinIntervalMillis() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        try {
            long minInterval = healthy.getCpuHeadroomMinIntervalMillis();
            assertTrue(minInterval > 0);
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("CPU headroom is not enabled", false);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroomMinIntervalMillis() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = context.getSystemService(SystemHealthManager.class);
        try {
            long minInterval = healthy.getGpuHeadroomMinIntervalMillis();
            assertTrue(minInterval > 0);
        } catch (UnsupportedOperationException e) {
            Assume.assumeTrue("GPU headroom is not enabled", false);
        }
    }
}
