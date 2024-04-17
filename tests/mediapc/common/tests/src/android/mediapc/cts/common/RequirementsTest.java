/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.mediapc.cts.common.Requirements.HDRDisplayRequirement;
import android.mediapc.cts.common.Requirements.SequentialWriteRequirement;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for select {@link Requirement} sub classes generated in {@link Requirements}. */
@RunWith(JUnit4.class)
public class RequirementsTest {


    // HDRDisplayRequirement has two required measurements.
    @Test
    public void hdrDisplay_noHdr_1000nits() {
        var req = HDRDisplayRequirement.create();
        req.setIsHdr(false);
        req.setDisplayLuminanceNits(1000);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(0);
    }

    @Test
    public void hdrDisplay_900nits() {
        var req = HDRDisplayRequirement.create();
        req.setIsHdr(true);
        req.setDisplayLuminanceNits(900);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(0);
    }

    @Test
    public void hdrDisplay_1000nits() {
        var req = HDRDisplayRequirement.create();
        req.setIsHdr(true);
        req.setDisplayLuminanceNits(1000);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(35);
    }


    // SequentialWriteRequirement has more than one MPC level.
    @Test
    public void sequentialWrite_90mbps() {
        var req = SequentialWriteRequirement.create();
        req.setFilesystemIoRateMbps(90);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(0);
    }

    @Test
    public void sequentialWrite_100mbps() {
        var req = SequentialWriteRequirement.create();
        req.setFilesystemIoRateMbps(100);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(30);
    }

    @Test
    public void sequentialWrite_125mbps() {
        var req = SequentialWriteRequirement.create();
        req.setFilesystemIoRateMbps(125);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(33);
    }

    @Test
    public void sequentialWrite_150mbps() {
        var req = SequentialWriteRequirement.create();
        req.setFilesystemIoRateMbps(150);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(34);
    }

}
