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

import android.content.pm.PackageManager
import android.mediapc.cts.common.Precondition.Companion.create
import android.mediapc.cts.common.Precondition.Companion.createLazy
import android.mediapc.cts.common.Precondition.Companion.forbidSystemFeature
import android.mediapc.cts.common.Precondition.Companion.requireSystemFeature

/**
 * Setting the minimum memory to 2.5G so we get statistics on "Mid Tier Devices"
 *
 * As of 2025 Q1 this is about 80% of daily active devices.
 */
@JvmField
val AT_LEAST_2_5GB_MEMORY: Precondition = Precondition.usingContext("At least 2.5Gb of memory") {
    Utils.getTotalMemoryMb(it) > (2.5 * 1024L).toLong()
}
val IS_HANDHELD = Precondition.group(
    // handheld nature is not exposed to package manager, for now
    // we check for touchscreen and NOT watch, tv or automotive
    "is_handheld",
    requireSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
    forbidSystemFeature(PackageManager.FEATURE_WATCH),
    forbidSystemFeature(PackageManager.FEATURE_TELEVISION),
    forbidSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
    )

/**
 * Meets [Utils.meetsPerformanceClassPreconditions].
 */
@Deprecated("Use [BASELINE] instead.")
val LEGACY_MEETS_PC_PRECONDITIONS = createLazy(
    message = "Default precondition failed",
    fn = Utils::meetsPerformanceClassPreconditions
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
            LEGACY_MEETS_PC_PRECONDITIONS
    )
)

@JvmField
val EMPTY = create("No preconditions", true)
