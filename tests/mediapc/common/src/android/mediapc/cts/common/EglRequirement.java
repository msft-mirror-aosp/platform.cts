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

package android.mediapc.cts.common;

import android.os.Build;

/**
 * Constructors and measurement setters for EGL MPC requirements.
 */
public final class EglRequirement extends Requirement {

    /**
     * <b>7.1.4.1/H-1-2</b> MUST support the {@code EGL_IMG_context_priority} and
     * {@code EGL_EXT_protected_content}
     * extensions.
     */
    public static EglRequirement createR7_1_4_1__H_1_2() {
        RequiredMeasurement<Boolean> protected_content =
                RequiredMeasurement.<Boolean>builder().setId(
                                RequirementConstants.EGL_EXT_PROTECTED_CONTENT)
                        .setPredicate(RequirementConstants.BOOLEAN_EQ)
                        .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                        .build();
        RequiredMeasurement<Boolean> img_context_priority =
                RequiredMeasurement.<Boolean>builder().setId(
                                RequirementConstants.EGL_IMG_CONTEXT_PRIORITY)
                        .setPredicate(RequirementConstants.BOOLEAN_EQ)
                        .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                        .build();

        return new EglRequirement(RequirementConstants.R7_1_4_1__H_1_2,
                protected_content, img_context_priority);
    }

    private EglRequirement(String id, RequiredMeasurement<?>... reqs) {
        super(id, reqs);
    }

    /** Is EGL_IMG_context_priority supported. */
    public void setImageContextPrioritySupported(boolean supported) {
        this.setMeasuredValue(RequirementConstants.EGL_IMG_CONTEXT_PRIORITY, supported);
    }

    /** Is EGL_EXT_protected_content supported. */
    public void setProtectedContentSupported(boolean supported) {
        this.setMeasuredValue(RequirementConstants.EGL_EXT_PROTECTED_CONTENT, supported);
    }
}
