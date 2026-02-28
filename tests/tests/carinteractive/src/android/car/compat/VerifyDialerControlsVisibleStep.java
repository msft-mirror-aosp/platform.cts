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

import com.android.interactive.Step;
import com.android.interactive.annotations.NotFullyAutomated;

@NotFullyAutomated(reason = "Manual notification UI verification required")
public class VerifyDialerControlsVisibleStep extends Step<Boolean> {

    @Override
    public void interact() {
        show(
                "Are the alternative dialer controls visible on the screen with controls? Controls"
                    + " should display the caller name (Caller), and number (1234567890), as well"
                    + " as include a way to end the call and mute the microphone");
        addButton("Yes", () -> pass(true));
        addButton("No", () -> pass(false));
        addSwapButton();
    }
}
