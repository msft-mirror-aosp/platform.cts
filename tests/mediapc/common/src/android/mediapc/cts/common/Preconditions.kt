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

/**
 * Collection of useful preconditions.
 */
@file:JvmName("Preconditions")

package android.mediapc.cts.common

import android.mediapc.cts.common.Precondition.Companion.create
import com.android.compatibility.common.util.MediaUtils

/**
 * Detect if the device is handheld
 *
 * This uses [com.android.compatibility.common.util.MediaUtils.isHandheld].
 */
@JvmField
val IS_HANDHELD = Precondition.create(
    "is_handheld",
    MediaUtils.isHandheld()
)

/**
 * MPC 10 memory threshold (80% of 3.2GB)
 */
private val MPC_10_BASELINE_MEMORY = Precondition.create(
    "At least 80% of MPC 10 memory",
    { Utils.getTotalMemoryMb() >=
            PreconditionConstants.R7_6_1__H_2_1_PHYSICAL_MEMORY_MB_MPC_10 * 8 / 10 }
)

/**
 * MPC 10 DPI threshold (80% of 240 DPI)
 */
private val MPC_10_BASELINE_DPI = Precondition.create(
    "At least 80% of MPC 10 DPI",
    { Utils.getDisplayDpi() >=
            PreconditionConstants.R7_1_1_3__H_2_1_DISPLAY_DENSITY_DPI_MPC_10 * 8 / 10 }
)

/**
 * MPC 10 resolution threshold (80% of 1280x720)
 */
private val MPC_10_BASELINE_RESOLUTION = Precondition.create(
    "At least 80% of MPC 10 resolution",
    {
        Utils.getMaxDisplayDim() >=
            PreconditionConstants.R7_1_1_1__H_2_1_LONG_RESOLUTION_PIXELS_MPC_10 * 8 / 10 &&
            Utils.getMinDisplayDim() >=
            PreconditionConstants.R7_1_1_1__H_2_1_SHORT_RESOLUTION_PIXELS_MPC_10 * 8 / 10
    }
)

/**
 * The baseline set of preconditions for MPC 10.
 */
@JvmField
val MPC_10_BASELINE =
    Precondition.lazy(
        Precondition.group(
            "mpc_10_baseline",
            IS_HANDHELD,
            MPC_10_BASELINE_MEMORY,
            MPC_10_BASELINE_DPI,
            MPC_10_BASELINE_RESOLUTION,
        )
    )

/**
 * The baseline set of preconditions for MPC.
 *
 * This includes minimum memory, DPI, and other fast to test conditions.
 *
 * Failing to meet these thresholds means we know that the device can't meet any performance
 * class requirement. If the device doesn't meet these, we save time for everyone by skipping
 * the tests that we know the device will fail.
 *
 * The numbers here are reduced from the strict thresholds so that we can gather
 * some information about most devices. This won't impact CTS results, but
 * will increase CTS runtime for those devices.
 */
@JvmField
val BASELINE = MPC_10_BASELINE

@JvmField
val EMPTY = create("No preconditions", true)
