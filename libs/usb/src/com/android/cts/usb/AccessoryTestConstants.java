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

package com.android.cts.usb;

/**
 * Shared constants for USB Accessory (AOA) tests in CTS-Verifier.
 *
 * <p>This class acts as the contract between the CtsVerifier (acting as the USB Host) and the
 * CtsVerifierUSBCompanion (acting as the USB Accessory).
 *
 * <p>It defines:
 *
 * <ul>
 *   <li>The <b>command strings</b> used to trigger specific test cases.
 * </ul>
 */
public final class AccessoryTestConstants {
    private AccessoryTestConstants() {}

    // ==========================================
    // Section 1: Data Transfer & Echo Tests
    // ==========================================
    public static final String ECHO_32_BYTES = "echo 32 bytes";
    public static final String ECHO_TWO_16_BYTES_AS_ONE = "echo two 16 byte transfers as one";
    public static final String ECHO_32_BYTES_AS_TWO_16_BYTES =
            "echo 32 bytes as two 16 byte transfers";
    public static final String MEASURE_OUT_TRANSFER_SPEED = "measure out transfer speed";
    public static final String MEASURE_IN_TRANSFER_SPEED = "measure in transfer speed";
    public static final String ECHO_MAX_BYTES = "echo max bytes";
    public static final String ECHO_MAX_2_BYTES = "echo max*2 bytes";
    public static final String RECEIVE_USB_ACCESSORY_HANDSHAKE =
            "Receive USB_ACCESSORY_HANDSHAKE intent";
    public static final String DONE = "done";

    // ==========================================
    // Section 2: HID Protocol Tests
    // ==========================================
    public static final String TEST_SEND_DESCRIPTOR_FULL = "test send descriptor in full";
    public static final String TEST_SEND_DESCRIPTOR_CHUNKS = "test send descriptor in chunks";
    public static final String TEST_SEND_INTERLEAVED_DESCRIPTOR =
            "test send interleaved descriptors";
    public static final String TEST_UNREGISTERED_DESCRIPTOR =
            "test send descriptor to unregistered hid";
    public static final String TEST_UNREGISTERED_EVENT = "test send event to unregistered hid";
    public static final String TEST_DESCRIPTOR_OVERFLOW = "test descriptor overflow";
    public static final String TEST_DESCRIPTOR_INCOMPLETE = "test descriptor incomplete";
}
