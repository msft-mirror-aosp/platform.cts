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
 * Constructors and measurement setters for Vulkan MPC requirements.
 */
public final class VulkanRequirement extends Requirement {

    /**
     * <b>7.1.4.1/H-1-3</b> MUST support
     * {@code VkPhysicalDeviceProtectedMemoryFeatures.protectedMemory} and
     * {@code VK_EXT_global_priority}.
     */
    public static VulkanRequirement createR7_1_4_1__H_1_3() {
        RequiredMeasurement<Boolean> globalPriority =
                RequiredMeasurement.<Boolean>builder().setId(
                                RequirementConstants.VK_EXT_GLOBAL_PRIORITY)
                        .setPredicate(RequirementConstants.BOOLEAN_EQ)
                        .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                        .build();
        RequiredMeasurement<Boolean> protectedMemory =
                RequiredMeasurement.<Boolean>builder().setId(
                                RequirementConstants.VK_PHYSICAL_DEVICE_PROTECTED_MEMORY)
                        .setPredicate(RequirementConstants.BOOLEAN_EQ)
                        .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                        .build();

        RequiredMeasurement<Integer> count =
                RequiredMeasurement.<Integer>builder().setId(
                                RequirementConstants.VK_NON_CPU_DEVICE_COUNT)
                        .setPredicate(RequirementConstants.INTEGER_GTE)
                        .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1)
                        .build();

        return new VulkanRequirement(RequirementConstants.R7_1_4_1__H_1_3,
                globalPriority, protectedMemory, count);
    }

    private VulkanRequirement(String id, RequiredMeasurement<?>... reqs) {
        super(id, reqs);
    }

    /** Is {@code VK_EXT_global_priority} supported. */
    public void setGlobalPrioritySupported(boolean supported) {
        this.setMeasuredValue(RequirementConstants.VK_EXT_GLOBAL_PRIORITY, supported);
    }

    /** Is {@code VkPhysicalDeviceProtectedMemoryFeatures.protectedMemory} supported. */
    public void setDeviceProtectedMemorySupported(boolean supported) {
        this.setMeasuredValue(RequirementConstants.VK_PHYSICAL_DEVICE_PROTECTED_MEMORY, supported);
    }

    /** The count of non {@code VK_PHYSICAL_DEVICE_TYPE_CPU} Vulcan Devices. */
    public void setNonCpuVulcanDeviceCount(int count) {
        this.setMeasuredValue(RequirementConstants.VK_NON_CPU_DEVICE_COUNT, count);

    }
}
