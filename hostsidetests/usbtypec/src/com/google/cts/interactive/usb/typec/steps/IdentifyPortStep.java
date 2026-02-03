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

package com.google.cts.interactive.usb.typec.steps;

import com.android.interactive.steps.ActAndWaitStep;

import com.google.cts.interactive.usb.typec.CtsUsbTypecTestCases;

public final class IdentifyPortStep extends ActAndWaitStep {
    static String sINSTRUCTION =
            "Please insert any USB charger into the port named "
                    + CtsUsbTypecTestCases.getPortName();

    public IdentifyPortStep() {
        super(sINSTRUCTION, CtsUsbTypecTestCases::getPortIdentified);
    }

    @Override
    public void interact() {
        show(sINSTRUCTION);
        addButton("Skip", this::pass);

        addSwapButton();
    }
}
