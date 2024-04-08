/*
 * Copyright 2024 The Android Open Source Project
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

package android.input.cts

import com.android.cts.input.UinputDevice

private const val EV_KEY = 1
private const val KEY_DOWN = 1
private const val KEY_UP = 0
private const val EV_SYN = 0
private const val SYN_REPORT = 0

fun injectEvents(device: UinputDevice, events: IntArray) {
    device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
}

fun injectKeyDown(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, KEY_DOWN, EV_SYN, SYN_REPORT, 0))
}

fun injectKeyUp(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, KEY_UP, EV_SYN, SYN_REPORT, 0))
}
