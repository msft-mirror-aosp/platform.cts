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
import android.mediapc.cts.common.Precondition.Companion.createLazy

/**
 * The BASELINE set of preconditions for MPC.
 *
 * This includes minimum memory, DPI, and other fast to test conditions.
 * See [Utils.meetsPerformanceClassPreconditions].
 */
@JvmField
val BASELINE: Precondition =
    createLazy(
        message = "Default precondition failed",
        fn = Utils::meetsPerformanceClassPreconditions
    )

@JvmField
val EMPTY: Precondition =
    create("No preconditions", true)
