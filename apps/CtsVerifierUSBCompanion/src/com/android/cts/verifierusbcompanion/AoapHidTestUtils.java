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

package com.android.cts.verifierusbcompanion;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Helper class for handling Android Open Accessory Protocol (AOAP) HID operations. Provides
 * utilities for registering, unregistering, sending report descriptors in chunks, and sending HID
 * events for various device types (Keyboard, System, Stylus).
 */
public class AoapHidTestUtils {

    private static final String TAG = AoapHidTestUtils.class.getSimpleName();
    private static final int NUMBER_OF_CHUNKS = 3;
    private static final int CONTROL_TRANSFER_TIMEOUT_MS = 10000;

    // --- HID Report Descriptors ---
    private static final byte[] KEYBOARD_HID_DESCRIPTOR = {
        (byte) 0x05, (byte) 0x01, // Usage Page (Generic Desktop)
        (byte) 0x09, (byte) 0x06, // Usage (Keyboard)
        (byte) 0xA1, (byte) 0x01, // Collection (Application)
        (byte) 0x05, (byte) 0x07, //   Usage Page (Key Codes)
        (byte) 0x19, (byte) 0xE0, //   Usage Minimum (Left Control)
        (byte) 0x29, (byte) 0xE7, //   Usage Maximum (Right GUI)
        (byte) 0x15, (byte) 0x00, //   Logical Minimum (0)
        (byte) 0x25, (byte) 0x01, //   Logical Maximum (1)
        (byte) 0x75, (byte) 0x01, //   Report Size (1)
        (byte) 0x95, (byte) 0x08, //   Report Count (8)
        (byte) 0x81, (byte) 0x02, //   Input (Data, Variable, Absolute) - Modifier keys
        (byte) 0x19, (byte) 0x00, //   Usage Minimum (0)
        (byte) 0x29, (byte) 0x65, //   Usage Maximum (101)
        (byte) 0x15, (byte) 0x00, //   Logical Minimum (0)
        (byte) 0x25, (byte) 0x65, //   Logical Maximum (101)
        (byte) 0x75, (byte) 0x08, //   Report Size (8)
        (byte) 0x95, (byte) 0x01, //   Report Count (1) - Reserved byte
        (byte) 0x81, (byte) 0x00, //   Input (Data, Array, Absolute) - Key codes
        (byte) 0xC0, // End Collection
    };

    // --- Key List "Ab 01 @-_+."---
    private static final List<AoaKey> KEY_LIST_1 =
            Collections.unmodifiableList(
                    Arrays.asList(
                            new AoaKey(0x04, AoaKey.Modifier.SHIFT), // 'A' (shifted 'a')
                            new AoaKey(0x05), // 'b'
                            new AoaKey(0x2C), // ' ' (Spacebar)
                            new AoaKey(0x27), // '0'
                            new AoaKey(0x1E), // '1'
                            new AoaKey(0x2C), // ' ' (Spacebar)
                            new AoaKey(0x1F, AoaKey.Modifier.SHIFT), // '@'
                            new AoaKey(0x2D), // '-'
                            new AoaKey(0x2D, AoaKey.Modifier.SHIFT), // '_'
                            new AoaKey(0x2E, AoaKey.Modifier.SHIFT), // '+'
                            new AoaKey(0x37) // '.'
                            ));

    // --- Key List "Ab 01"---
    private static final List<AoaKey> KEY_LIST_2 =
            Collections.unmodifiableList(
                    Arrays.asList(
                            new AoaKey(0x04, AoaKey.Modifier.SHIFT), // 'A' (shifted 'a')
                            new AoaKey(0x05), // 'b'
                            new AoaKey(0x2C), // ' ' (Spacebar)
                            new AoaKey(0x27), // '0'
                            new AoaKey(0x1E) // '1'
                            ));

    /**
     * Control request for registering a HID device. Upon registering, a unique ID is sent by the
     * accessory in the value parameter. This ID will be used for future commands for the device
     *
     * <p>requestType: USB_DIR_OUT | USB_TYPE_VENDOR request: ACCESSORY_REGISTER_HID_DEVICE value:
     * Accessory assigned ID for the HID device index: total length of the HID report descriptor
     * data none
     */
    private static final int ACCESSORY_REGISTER_HID = 54;

    /**
     * Control request for unregistering a HID device.
     *
     * <p>requestType: USB_DIR_OUT | USB_TYPE_VENDOR request: ACCESSORY_REGISTER_HID value:
     * Accessory assigned ID for the HID device index: 0 data none
     */
    private static final int ACCESSORY_UNREGISTER_HID = 55;

    /**
     * Control request for sending the HID report descriptor. If the HID descriptor is longer than
     * the endpoint zero max packet size, the descriptor will be sent in multiple
     * ACCESSORY_SET_HID_REPORT_DESC commands. The data for the descriptor must be sent sequentially
     * if multiple packets are needed.
     *
     * <p>requestType: USB_DIR_OUT | USB_TYPE_VENDOR request: ACCESSORY_SET_HID_REPORT_DESC value:
     * Accessory assigned ID for the HID device index: offset of data in descriptor (needed when HID
     * descriptor is too big for one packet) data the HID report descriptor
     */
    private static final int ACCESSORY_SET_HID_REPORT_DESC = 56;

    /**
     * Control request for sending HID events.
     *
     * <p>requestType: USB_DIR_OUT | USB_TYPE_VENDOR request: ACCESSORY_SEND_HID_EVENT value:
     * Accessory assigned ID for the HID device index: 0 data the HID report for the event
     */
    private static final int ACCESSORY_SEND_HID_EVENT = 57;

    // --- Delays and HID IDs ---
    private static final int STEP_DELAY = 50;
    private static final int ACTION_DELAY = 1000;
    private static final int KEYBOARD_1_HID_ID = 1;
    private static final int KEYBOARD_2_HID_ID = 2;
    private static final int UNREGISTERED_HID_ID = 99;

    /**
     * Pauses the current thread for a specified duration. Handles InterruptedException by restoring
     * the thread's interrupted status.
     *
     * @param duration The duration to sleep in milliseconds.
     */
    public static void sleep(final int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    /**
     * Sends a generic HID event to the accessory.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     * @param hidId The Accessory assigned ID for the HID device.
     * @param data The HID report data to send.
     * @param pauseDuration The duration to pause after sending the event in milliseconds.
     * @throws IOException If the control transfer fails.
     * @throws IllegalArgumentException If conn or data is null.
     */
    private static void send(
            final UsbDeviceConnection conn,
            final int hidId,
            final byte[] data,
            final int pauseDuration)
            throws IOException {
        Objects.requireNonNull(conn, "UsbDeviceConnection cannot be null for send.");
        Objects.requireNonNull(data, "HID event data cannot be null for send.");

        final int len =
                conn.controlTransfer(
                        UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR,
                        ACCESSORY_SEND_HID_EVENT,
                        hidId,
                        0,
                        data,
                        data.length,
                        CONTROL_TRANSFER_TIMEOUT_MS);
        if (len < 0) {
            throw new IOException("Control transfer for ACCESSORY_SEND_HID_EVENT failed: " + len);
        }
        if (len != data.length) {
            Log.w(
                    TAG,
                    "Warning: Partial transfer for HID ID "
                            + hidId
                            + " event. Sent "
                            + len
                            + "/"
                            + data.length
                            + " bytes.");
        }
        sleep(pauseDuration);
    }

    /**
     * Presses a sequence of keys using the registered keyboard HID.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     * @param keys The list of AoaKey objects to press.
     * @param hidId The HID ID of the registered keyboard device.
     * @throws IOException If a USB communication error occurs.
     * @throws IllegalArgumentException If conn or keys is null.
     */
    private static void pressKeys(
            final UsbDeviceConnection conn, final List<AoaKey> keys, final int hidId)
            throws IOException {
        Objects.requireNonNull(conn, "UsbDeviceConnection cannot be null for pressKeys.");
        Objects.requireNonNull(keys, "Key list cannot be null for pressKeys.");

        sleep(STEP_DELAY);
        final Iterator<AoaKey> it = keys.stream().filter(Objects::nonNull).iterator();
        while (it.hasNext()) {
            final AoaKey key = it.next();
            send(conn, hidId, key.toHidData(), STEP_DELAY); // Key Pressed
            send(
                    conn,
                    hidId,
                    AoaKey.NOOP.toHidData(),
                    it.hasNext() ? STEP_DELAY : ACTION_DELAY); // Key Released
        }
    }

    /**
     * Registers a HID device with the accessory.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     * @param hidDescriptorLength The total length of the HID report descriptor for this device.
     * @param hidId The desired Accessory assigned ID for the HID device.
     * @throws IOException If the control transfer fails.
     * @throws IllegalArgumentException If conn is null.
     */
    private static void registerHid(
            final UsbDeviceConnection conn, final int hidDescriptorLength, final int hidId)
            throws IOException {
        Objects.requireNonNull(conn, "UsbDeviceConnection cannot be null for registerHid.");

        final int len =
                conn.controlTransfer(
                        UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR,
                        ACCESSORY_REGISTER_HID,
                        hidId,
                        hidDescriptorLength,
                        null,
                        0,
                        CONTROL_TRANSFER_TIMEOUT_MS);

        if (len < 0) {
            throw new IOException("Control transfer for ACCESSORY_REGISTER_HID failed: " + len);
        }
    }

    /**
     * Unregisters a HID device from the accessory.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     * @param hidId The Accessory assigned ID of the HID device to unregister.
     * @throws IOException If the control transfer fails.
     * @throws IllegalArgumentException If conn is null.
     */
    private static void unregisterHid(final UsbDeviceConnection conn, final int hidId)
            throws IOException {
        Objects.requireNonNull(conn, "UsbDeviceConnection cannot be null for unregisterHid.");

        final int len =
                conn.controlTransfer(
                        UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR,
                        ACCESSORY_UNREGISTER_HID,
                        hidId,
                        0,
                        null,
                        0,
                        CONTROL_TRANSFER_TIMEOUT_MS);
        if (len < 0) {
            throw new IOException("Control transfer for ACCESSORY_UNREGISTER_HID failed: " + len);
        }
    }

    /** Helper class to store information for a single HID descriptor chunk. */
    private static class HidChunk {
        final int mHidId;
        final int mOffset;
        final byte[] mData;

        HidChunk(final int hidId, final int offset, final byte[] data) {
            this.mHidId = hidId;
            this.mOffset = offset;
            this.mData = data;
        }

        @Override
        public String toString() {
            return "Chunk [HID=" + mHidId + ", Offset=" + mOffset + ", Len=" + mData.length + "]";
        }
    }

    /** Helper class to track the state of each HID's descriptor sending. */
    private static class HidDescriptorSenderState {
        final int mHidId;
        final byte[] mFullDescriptor;
        int mCurrentOffset = 0;
        int mChunksSent = 0;
        final int mNumChunksDesired;
        final int mBaseChunkSize;
        final int mRemainder;

        HidDescriptorSenderState(
                final int hidId, final byte[] fullDescriptor, final int numChunksDesired) {
            Objects.requireNonNull(fullDescriptor, "Full descriptor cannot be null.");
            if (numChunksDesired <= 0) {
                throw new IllegalArgumentException("numChunksDesired must be greater than 0.");
            }

            this.mHidId = hidId;
            this.mFullDescriptor = fullDescriptor;
            this.mNumChunksDesired = numChunksDesired;
            this.mBaseChunkSize = fullDescriptor.length / numChunksDesired;
            this.mRemainder = fullDescriptor.length % numChunksDesired;
        }

        /**
         * Generates the next chunk for this HID.
         *
         * @return A HidChunk object, or null if no more chunks for this HID or if the next chunk
         *     would be empty.
         */
        private HidChunk getNextChunk() {
            if (mChunksSent >= mNumChunksDesired || mCurrentOffset >= mFullDescriptor.length) {
                return null;
            }

            int calculatedChunkLength = mBaseChunkSize;

            // Distribute remainder bytes to the first 'remainder' chunks
            if (mChunksSent < mRemainder) {
                calculatedChunkLength++;
            }

            // Adjust chunk length if it goes beyond descriptor bounds
            if (mCurrentOffset + calculatedChunkLength > mFullDescriptor.length) {
                calculatedChunkLength = mFullDescriptor.length - mCurrentOffset;
            }

            if (calculatedChunkLength <= 0) {
                Log.w(
                        TAG,
                        "HID ID "
                                + mHidId
                                + ": Calculated empty chunk for chunk "
                                + (mChunksSent + 1)
                                + ". Advancing state.");
                mChunksSent++;
                mCurrentOffset += calculatedChunkLength;
                return null;
            }

            final byte[] chunkData =
                    Arrays.copyOfRange(
                            mFullDescriptor,
                            mCurrentOffset,
                            mCurrentOffset + calculatedChunkLength);
            final HidChunk chunk = new HidChunk(mHidId, mCurrentOffset, chunkData);

            // Update state for the next call
            mCurrentOffset += calculatedChunkLength;
            mChunksSent++;

            return chunk;
        }

        /**
         * Sends the next available chunk for this HID via control transfer.
         *
         * @param conn The UsbDeviceConnection to the accessory.
         * @throws IOException If the control transfer fails or no more chunks are available.
         */
        private void sendNextChunk(final UsbDeviceConnection conn) throws IOException {
            Objects.requireNonNull(conn, "UsbDeviceConnection cannot be null for sendNextChunk.");

            final HidChunk chunkToSend = getNextChunk();

            if (chunkToSend == null) {
                throw new IOException("No valid chunk available to send for HID ID " + mHidId);
            }

            final int len =
                    conn.controlTransfer(
                            UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR,
                            ACCESSORY_SET_HID_REPORT_DESC,
                            chunkToSend.mHidId,
                            chunkToSend.mOffset,
                            chunkToSend.mData,
                            chunkToSend.mData.length,
                            CONTROL_TRANSFER_TIMEOUT_MS);

            if (len < 0) {
                throw new IOException(
                        "Failed to send chunk " + chunkToSend.toString() + " with error: " + len);
            }
            if (len != chunkToSend.mData.length) {
                Log.w(
                        TAG,
                        "Warning: Partial transfer for "
                                + chunkToSend.toString()
                                + ". Sent "
                                + len
                                + "/"
                                + chunkToSend.mData.length
                                + " bytes.");
            }
        }

        /**
         * Checks if there are more chunks remaining to be sent for this HID.
         *
         * @return True if more chunks are available, false otherwise.
         */
        private boolean hasMoreChunks() {
            // Check if we still have chunks to send based on the desired count
            // and if there are still bytes remaining in the full descriptor.
            return mChunksSent < mNumChunksDesired && mCurrentOffset < mFullDescriptor.length;
        }

        @Override
        public String toString() {
            return "HID "
                    + mHidId
                    + " (Sent: "
                    + mChunksSent
                    + "/"
                    + mNumChunksDesired
                    + ", Offset: "
                    + mCurrentOffset
                    + ")";
        }
    }

    /**
     * Represents an active HID registration on the accessory. Automatically unregisters the device
     * when closed.
     */
    private static class HidRegistration implements AutoCloseable {
        private final UsbDeviceConnection mConn;
        private final int mHidId;

        HidRegistration(UsbDeviceConnection conn, int descriptorLength, int hidId)
                throws IOException {
            mConn = conn;
            mHidId = hidId;
            registerHid(conn, descriptorLength, hidId);
        }

        @Override
        public void close() throws IOException {
            sleep(ACTION_DELAY);
            unregisterHid(mConn, mHidId);
        }
    }

    /**
     * Test method to send a single HID descriptor in one full transfer.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendDescriptorInFull(final UsbDeviceConnection conn) {
        try (HidRegistration reg =
                new HidRegistration(conn, KEYBOARD_HID_DESCRIPTOR.length, KEYBOARD_1_HID_ID)) {
            HidDescriptorSenderState senderState =
                    new HidDescriptorSenderState(reg.mHidId, KEYBOARD_HID_DESCRIPTOR, 1);
            while (senderState.hasMoreChunks()) {
                senderState.sendNextChunk(conn);
            }
            sleep(ACTION_DELAY);
            pressKeys(conn, KEY_LIST_1, reg.mHidId);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Test method to send a single HID descriptor in multiple parts (chunks).
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendDescriptorInChunks(final UsbDeviceConnection conn) {
        try (HidRegistration reg =
                new HidRegistration(conn, KEYBOARD_HID_DESCRIPTOR.length, KEYBOARD_1_HID_ID)) {
            HidDescriptorSenderState senderState =
                    new HidDescriptorSenderState(
                            reg.mHidId, KEYBOARD_HID_DESCRIPTOR, NUMBER_OF_CHUNKS);
            while (senderState.hasMoreChunks()) {
                senderState.sendNextChunk(conn);
            }
            sleep(ACTION_DELAY);
            pressKeys(conn, KEY_LIST_1, reg.mHidId);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Registers multiple HID devices and sends their descriptors in a randomly interleaved fashion,
     * ensuring chunks for each device are sent in their correct order.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendInterleavedHidDescriptors(final UsbDeviceConnection conn) {
        // Step 1: Register both HID devices
        try (HidRegistration reg1 =
                        new HidRegistration(
                                conn, KEYBOARD_HID_DESCRIPTOR.length, KEYBOARD_1_HID_ID);
                HidRegistration reg2 =
                        new HidRegistration(
                                conn, KEYBOARD_HID_DESCRIPTOR.length, KEYBOARD_2_HID_ID); ) {
            // Step 2: Initialize sender states for each HID
            final List<HidDescriptorSenderState> activeSenders = new ArrayList<>();
            activeSenders.add(
                    new HidDescriptorSenderState(
                            reg1.mHidId, KEYBOARD_HID_DESCRIPTOR, NUMBER_OF_CHUNKS));
            activeSenders.add(
                    new HidDescriptorSenderState(
                            reg2.mHidId, KEYBOARD_HID_DESCRIPTOR, NUMBER_OF_CHUNKS));
            long seed = System.currentTimeMillis();
            Log.i(TAG, "Shuffle seed for interleaved test: " + seed);
            Random random = new Random(seed);
            // Step 3: Loop and send chunks until all senders are done
            while (!activeSenders.isEmpty()) {
                // Randomly select a sender from the active list
                Collections.shuffle(activeSenders, random);
                final HidDescriptorSenderState currentSender = activeSenders.get(0);
                Log.i(TAG, "Selected sender: " + currentSender.toString());
                currentSender.sendNextChunk(conn);
                if (!currentSender.hasMoreChunks()) {
                    activeSenders.remove(0);
                }
            }
            sleep(ACTION_DELAY);

            pressKeys(conn, KEY_LIST_1, reg1.mHidId);
            pressKeys(conn, KEY_LIST_2, reg2.mHidId);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Test case: Attempt to send a HID report descriptor chunk for an unregistered HID ID. Expects
     * an IOException as the accessory should reject the transfer.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendDescriptorToUnregisteredHid(final UsbDeviceConnection conn) {
        try {
            HidDescriptorSenderState senderState =
                    new HidDescriptorSenderState(UNREGISTERED_HID_ID, KEYBOARD_HID_DESCRIPTOR, 1);
            while (senderState.hasMoreChunks()) {
                senderState.sendNextChunk(conn);
            }
            Log.e(TAG, "FAIL: Expected exception when sending descriptor to unregistered HID");
            return false;
        } catch (IOException e) {
            Log.i(TAG, "SUCCESS: Caught exception when sending descriptor to unregistered HID.");
        }
        return true;
    }

    /**
     * Test case: Attempt to send a HID event for an unregistered HID ID. Expects an IOException as
     * the accessory should reject the transfer.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendEventToUnregisteredHid(final UsbDeviceConnection conn) {
        try {
            pressKeys(conn, KEY_LIST_2, UNREGISTERED_HID_ID);
            Log.e(TAG, "FAIL: Expected exception when sending event to unregistered HID.");
            return false;
        } catch (IOException e) {
            Log.i(TAG, "SUCCESS: Caught exception when sending event to unregistered HID.");
        }
        return true;
    }

    /**
     * Test case: Registers a HID with a specific length, but attempts to send a descriptor payload
     * larger than that length. Expectation is that control transfer should fail.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendDescriptorOverflow(final UsbDeviceConnection conn) {
        // Register with less bytes than descriptor length
        try (HidRegistration reg =
                new HidRegistration(conn, KEYBOARD_HID_DESCRIPTOR.length - 1, KEYBOARD_1_HID_ID)) {
            try {
                HidDescriptorSenderState senderState =
                        new HidDescriptorSenderState(reg.mHidId, KEYBOARD_HID_DESCRIPTOR, 1);
                while (senderState.hasMoreChunks()) {
                    senderState.sendNextChunk(conn);
                }
                // If we reach here, the test FAILED (no exception thrown)
                Log.e(TAG, "FAIL: Expected exception when sending descriptor with more length.");
            } catch (IOException expected) {
                // We caught the exception we WANTED.
                Log.i(TAG, "SUCCESS: Caught exception when sending descriptor with more length.");
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "FAIL: Test failed with registration/cleanup exception", e);
            return false;
        }
        return false;
    }

    /**
     * Test case: Registers a HID with a large length, sends a valid (but shorter) descriptor, and
     * then attempts to send events. Expectation is that device should not process the events as the
     * descriptor is incomplete.
     *
     * @param conn The UsbDeviceConnection to the accessory.
     */
    public static boolean testSendDescriptorIncomplete(final UsbDeviceConnection conn) {
        // Register with extra bytes than descriptor length
        try (HidRegistration reg =
                new HidRegistration(conn, KEYBOARD_HID_DESCRIPTOR.length + 1, KEYBOARD_1_HID_ID)) {
            try {
                HidDescriptorSenderState senderState =
                        new HidDescriptorSenderState(KEYBOARD_1_HID_ID, KEYBOARD_HID_DESCRIPTOR, 1);
                while (senderState.hasMoreChunks()) {
                    senderState.sendNextChunk(conn);
                }
                // Send event should not be processed as device should not be registered
                pressKeys(conn, KEY_LIST_1, KEYBOARD_1_HID_ID);
                Log.e(TAG, "FAIL: Expected exception when sending incomplete descriptor.");
            } catch (IOException expected) {
                // We caught the exception we WANTED.
                Log.i(TAG, "SUCCESS: Caught exception when sending incomplete descriptor.");
                return true;
            }
        } catch (IOException e) {
            // Catches Registration or Cleanup failures
            Log.e(TAG, "FAIL: Test failed with registration/cleanup exception", e);
            return false;
        }
        return false;
    }
}
