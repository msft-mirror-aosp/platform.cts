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

package android.car.compat;

import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.YesNoStep;

@NotFullyAutomated(reason = "Manual notification UI verification required")
public class VerifyMediaReadyStep extends YesNoStep {
    public VerifyMediaReadyStep() {
        super("Did app show up with \"Audio is ready\" title?");
    }
}
