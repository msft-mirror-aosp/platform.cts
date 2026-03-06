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

package android.mediapc.cts.common;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link PreconditionConstants}. */
@RunWith(JUnit4.class)
public class PreconditionConstantsTest {

    @Test
    public void constantsExist() {
        // Verify a few constants exist and have expected values
        Truth.assertThat(PreconditionConstants.R7_6_1__H_1_1_PHYSICAL_MEMORY_MB_MPC_30)
                .isEqualTo(5120L);
        Truth.assertThat(PreconditionConstants.R7_1_1_3__H_1_1_DISPLAY_DENSITY_DPI_MPC_30)
                .isEqualTo(400);
        Truth.assertThat(PreconditionConstants.R7_1_1_1__H_1_1_LONG_RESOLUTION_PIXELS_MPC_30)
                .isEqualTo(1920);
        Truth.assertThat(PreconditionConstants.R7_1_1_1__H_1_1_SHORT_RESOLUTION_PIXELS_MPC_30)
                .isEqualTo(1080);
    }
}
