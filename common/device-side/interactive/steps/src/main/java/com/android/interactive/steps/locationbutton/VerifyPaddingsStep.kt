/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.interactive.steps.locationbutton

import com.android.interactive.annotations.NotFullyAutomated
import com.android.interactive.steps.YesNoStep

/**
 * A manual verification step that asks the user to verify the location button respects padding.
 */
@NotFullyAutomated(
    reason =
        "Visual properties of the Location Button are rendered remotely by SystemUI " +
            "and cannot be reliably verified via automation."
)
class VerifyPaddingsStep :
    YesNoStep(
        "Verify that two stacked, icon-only location buttons are displayed. " +
                "Compared to the top button, is the bottom button visibly smaller " +
                "with less padding around its icon?"
    )
