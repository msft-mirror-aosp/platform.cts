/*
 * Copyright 2024 The Android Open Source Project
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

import android.os.Build;

public class HdrDisplayRequirement extends Requirement {
    private static final String TAG = HdrDisplayRequirement.class.getSimpleName();

    private HdrDisplayRequirement(String id, RequiredMeasurement<?>... reqs) {
        super(id, reqs);
    }

    public void setIsHdr(boolean isHdr) {
        this.setMeasuredValue(RequirementConstants.IS_HDR, isHdr);
    }

    /** Set the display luminance in nits. */
    public void setDisplayLuminance(float luminance) {
        this.setMeasuredValue(RequirementConstants.DISPLAY_LUMINANCE_NITS, luminance);
    }

    /**
     * [7.1.1.3/H-3-1] MUST have a HDR display supporting at least 1000 nits
     * average.
     */
    public static HdrDisplayRequirement createR7_1_1_3__H_3_1() {
        var isHdr = RequiredMeasurement
                .<Boolean>builder()
                .setId(RequirementConstants.IS_HDR)
                .setPredicate(RequirementConstants.BOOLEAN_EQ)
                .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                .build();
        var luminance = RequiredMeasurement
                .<Float>builder()
                .setId(RequirementConstants.DISPLAY_LUMINANCE_NITS)
                .setPredicate(RequirementConstants.FLOAT_GTE)
                .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1000.0f)
                .build();

        return new HdrDisplayRequirement(
                RequirementConstants.R7_1_1_3__H_3_1, isHdr, luminance);
    }
}
