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
import android.util.DisplayMetrics
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
 * Setting the minimum memory to 2.5G so we get statistics on "Mid Tier Devices"
 *
 * As of 2025 Q1 this is about 80% of daily active devices.
 */
private val AT_LEAST_2_5GB_MEMORY = Precondition.create(
    "At least 2.5Gb of memory",
    { Utils.getTotalMemoryMb() > (2.5 * 1024L).toLong() }
)

/**
 * MPC requires 400 DPI. lowering to HIGH (320) to report statistics on
 * "mid tier" devices
 *
 * As of 2025 Q1 this is about 85% of daily active devices.
 */
private val DISPLAY_DPI = Precondition.create(
   "Requires default display DPI greater than ${DisplayMetrics.DENSITY_HIGH}",
    { Utils.getDisplayDpi() > DisplayMetrics.DENSITY_HIGH }
)

/**
 *  Require resolution of at least 1280x720
 *
 *  MPC requires 1920x1080 lowering to 1280x720 to report statistics on "mid tier" devices
 *  As of 2025 Q1 this is about 99% of daily active devices.
 */
private val LARGEST_DISPLAY_RESOLUTION = Precondition.create(
    "Largest display resolution must at least HD",
    { Utils.getMaxDisplayDim() >= 1280 && Utils.getMinDisplayDim() >= 720 }
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
val BASELINE =
    Precondition.lazy( // BASELINE is called often enough to use lazy and cache the results.
        Precondition.group(
    "baseline",
    IS_HANDHELD,
            AT_LEAST_2_5GB_MEMORY,
            DISPLAY_DPI,
            LARGEST_DISPLAY_RESOLUTION,
    )
)

@JvmField
val EMPTY = create("No preconditions", true)
