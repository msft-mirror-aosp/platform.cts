/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.usb.accessory;

import static com.android.cts.verifier.usb.Util.runAndAssertException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.hardware.usb.flags.Flags;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.usb.AccessoryTestConstants;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Guide the user to run test for the USB accessory interface. */
public class UsbAccessoryTestActivity extends PassFailButtons.Activity
        implements AccessoryAttachmentHandler.AccessoryAttachmentObserver {
    private static final String LOG_TAG = UsbAccessoryTestActivity.class.getSimpleName();
    private static final int MAX_BUFFER_SIZE = 16384;

    private static final int TEST_DATA_SIZE_THRESHOLD = 100 * 1024 * 1024; // 100MB

    private static final String KEYBOARD_1_INPUT = "Ab 01 @-_+.";
    private static final String KEYBOARD_2_INPUT = "Ab 01";

    private TextView mStatus;
    private ProgressBar mProgress;
    private EditText mInputTextField; // New UI elements for text input from HID

    private BroadcastReceiver mUsbAccessoryHandshakeReceiver;

    private Boolean mAccessoryStart = false;
    private boolean mHasSendStringCount = false;
    private boolean mHasAccessoryConnectionStartTime = false;
    private CompletableFuture<Void> mAccessoryHandshakeIntent = new CompletableFuture<>();

    /* Test buffers */
    private final byte[] mOrigBuffer32 = new byte[32];

    private final byte[] mOrigBufferMax = new byte[MAX_BUFFER_SIZE];

    private final byte[] mBufferMax = new byte[MAX_BUFFER_SIZE];
    private final byte[] mBuffer32 = new byte[32];
    private final byte[] mBuffer16 = new byte[16];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.usb_main);
        setInfoResources(R.string.usb_accessory_test, R.string.usb_accessory_test_info, -1);

        setPassFailButtonClickListeners();
        mStatus = (TextView) findViewById(R.id.status);
        mProgress = (ProgressBar) findViewById(R.id.progress_bar);
        mStatus.setText(R.string.usb_accessory_test_step1);
        getPassButton().setEnabled(false);

        mInputTextField = findViewById(R.id.input_text_field);
        mInputTextField.setVisibility(View.GONE);

        AccessoryAttachmentHandler.addObserver(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_HANDSHAKE);

        mUsbAccessoryHandshakeReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        synchronized (UsbAccessoryTestActivity.this) {
                            mAccessoryStart =
                                    intent.getBooleanExtra(UsbManager.EXTRA_ACCESSORY_START, false);

                            // check SENDSTRING event
                            if (intent.hasExtra(UsbManager.EXTRA_ACCESSORY_STRING_COUNT)) {
                                mHasSendStringCount = true;
                            }

                            // check GETPROTOCOL event
                            if (intent.hasExtra(UsbManager.EXTRA_ACCESSORY_UEVENT_TIME)) {
                                mHasAccessoryConnectionStartTime = true;
                            }
                            mAccessoryHandshakeIntent.complete(null);
                        }
                    }
                };

        registerReceiver(mUsbAccessoryHandshakeReceiver, filter, Context.RECEIVER_EXPORTED);

        // initialise buffers
        (new Random()).nextBytes(mOrigBuffer32);
        (new Random()).nextBytes(mOrigBufferMax);
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    /**
     * Helper to open a stream with retries. Polls for 1 seconds (10 attempts * 100ms) if the result
     * is null.
     */
    private <T> T openStreamWithRetry(Callable<T> streamOpener) throws InterruptedException {
        int attempts = 0;
        while (attempts < 10) {
            try {
                T stream = streamOpener.call();
                if (stream != null) {
                    return stream;
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "Stream open failed, retrying... (Attempt " + attempts + ")", e);
            }
            Thread.sleep(100);
            attempts++;
        }
        return null;
    }

    /**
     * Helper to handle the Stream API test logic. Returns immediately if a stream fails to open,
     * allowing the caller to proceed to cleanup.
     */
    private void runAccessoryStreamTests(UsbManager usbManager, UsbAccessory accessory)
            throws Exception {
        Log.i(LOG_TAG, "Using stream APIs...");

        InputStream is = openStreamWithRetry(() -> usbManager.openAccessoryInputStream(accessory));
        if (is == null) {
            fail("Could not open accessory input stream after retries.", null);
            return;
        }

        try (InputStream input = is) {
            OutputStream os =
                    openStreamWithRetry(() -> usbManager.openAccessoryOutputStream(accessory));
            if (os == null) {
                fail("Could not open accessory output stream after retries.", null);
                return;
            }
            try (OutputStream output = os) {
                runTestsForAccessory(input, output);
                nextTest(input, output, AccessoryTestConstants.DONE);
            }
        }
    }

    @Override
    public void onAttached(UsbAccessory accessory) {
        mStatus.setText(R.string.usb_accessory_test_step2);
        mProgress.setVisibility(View.VISIBLE);

        AccessoryAttachmentHandler.removeObserver(this);

        UsbManager usbManager = getSystemService(UsbManager.class);

        new AsyncTask<Void, Void, Throwable>() {
            @Override
            protected Throwable doInBackground(Void... params) {
                try {
                    assertEquals("Android CTS", accessory.getManufacturer());
                    assertEquals("Android CTS test companion device", accessory.getModel());
                    assertEquals("Android device running CTS verifier", accessory.getDescription());
                    assertEquals("2", accessory.getVersion());
                    assertEquals(
                            "https://source.android.com/compatibility/cts/verifier.html",
                            accessory.getUri());
                    assertEquals("0", accessory.getSerial());

                    assertTrue(Arrays.asList(usbManager.getAccessoryList()).contains(accessory));

                    runAndAssertException(
                            () -> usbManager.openAccessory(null), NullPointerException.class);

                    runAndAssertException(
                            () -> usbManager.openAccessoryInputStream(null),
                            NullPointerException.class);

                    runAndAssertException(
                            () -> usbManager.openAccessoryOutputStream(null),
                            NullPointerException.class);

                    ParcelFileDescriptor accessoryFd = usbManager.openAccessory(accessory);
                    assertNotNull(accessoryFd);

                    try (InputStream is =
                            new ParcelFileDescriptor.AutoCloseInputStream(accessoryFd)) {
                        try (OutputStream os =
                                new ParcelFileDescriptor.AutoCloseOutputStream(accessoryFd)) {

                            runTestsForAccessory(is, os);

                            // don't end the test if stream APIs are available
                            if (!Flags.enableAccessoryStreamApi()) {
                                nextTest(is, os, AccessoryTestConstants.DONE);
                            }
                        }
                    }

                    accessoryFd.close();

                    if (Flags.enableAccessoryStreamApi()) {
                        runAccessoryStreamTests(usbManager, accessory);
                    }

                    unregisterReceiver(mUsbAccessoryHandshakeReceiver);
                    mUsbAccessoryHandshakeReceiver = null;

                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }

            @Override
            protected void onPostExecute(Throwable t) {
                if (t == null) {
                    setTestResultAndFinish(true);
                } else {
                    fail(null, t);
                }
            }
        }.execute();
    }

    /**
     * Signal to the companion device that we want to switch to the next test.
     *
     * @param is The input stream from the companion device
     * @param os The output stream from the companion device
     * @param testName The name of the new test
     */
    private boolean nextTest(
            @NonNull InputStream is, @NonNull OutputStream os, @NonNull String testName)
            throws IOException {
        Log.i(LOG_TAG, "Init new test " + testName);

        ByteBuffer nameBuffer = Charset.forName("UTF-8").encode(CharBuffer.wrap(testName));
        byte[] sizeBuffer = {(byte) nameBuffer.limit()};

        os.write(sizeBuffer);
        os.write(Arrays.copyOf(nameBuffer.array(), nameBuffer.limit()));

        int ret = is.read();
        if (ret <= 0) {
            Log.i(LOG_TAG, "Last test failed " + ret);
            return false;
        }

        os.write(0);

        Log.i(LOG_TAG, "Running " + testName);

        return true;
    }

    @Override
    protected void onDestroy() {
        AccessoryAttachmentHandler.removeObserver(this);

        if (mUsbAccessoryHandshakeReceiver != null) {
            unregisterReceiver(mUsbAccessoryHandshakeReceiver);
            mUsbAccessoryHandshakeReceiver = null;
        }

        super.onDestroy();
    }

    /** Indicate that the test failed. */
    private void fail(@Nullable String s, @Nullable Throwable e) {
        Log.e(LOG_TAG, s, e);
        setTestResultAndFinish(false);
    }

    private void testEchoTransfer(InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_32_BYTES);

        os.write(mOrigBuffer32);

        int numRead = is.read(mBuffer32);
        assertEquals(32, numRead);
        assertArrayEquals(mOrigBuffer32, mBuffer32);
    }

    private void testReceiveLessDataThanAvailable(InputStream is, OutputStream os)
            throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_32_BYTES);

        os.write(mOrigBuffer32);

        int numRead = is.read(mBuffer16);
        assertEquals(16, numRead);
        assertArrayEquals(Arrays.copyOf(mOrigBuffer32, 16), mBuffer16);

        // If a transfer was only partially read, the rest of the transfer is
        // lost. We cannot read the second part.

    }

    private void testSendTwoTransfersInARow(InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_TWO_16_BYTES_AS_ONE);

        os.write(Arrays.copyOf(mOrigBuffer32, 16));
        os.write(Arrays.copyOfRange(mOrigBuffer32, 16, 32));

        int numRead = is.read(mBuffer32);
        assertEquals(32, numRead);
        assertArrayEquals(mOrigBuffer32, mBuffer32);
    }

    private void testReceiveTwoTransfersInARowInBufferBiggerThanTransfer(
            InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_32_BYTES_AS_TWO_16_BYTES);

        os.write(mOrigBuffer32);

        // Even though the buffer would hold 32 bytes the input stream will read
        // the transfers individually
        int numRead = is.read(mBuffer32);
        assertEquals(16, numRead);
        assertArrayEquals(Arrays.copyOf(mOrigBuffer32, 16), Arrays.copyOf(mBuffer32, 16));

        numRead = is.read(mBuffer32);
        assertEquals(16, numRead);
        assertArrayEquals(Arrays.copyOfRange(mOrigBuffer32, 16, 32), Arrays.copyOf(mBuffer32, 16));
    }

    private void testMeasureInTransferSpeed(InputStream is, OutputStream os) throws IOException {
        byte[] result = new byte[1];
        double speedKBPS;
        long timeStart;

        nextTest(is, os, AccessoryTestConstants.MEASURE_IN_TRANSFER_SPEED);

        long bytesRead = 0;
        timeStart = SystemClock.elapsedRealtime();
        while (bytesRead < TEST_DATA_SIZE_THRESHOLD) {
            int numRead = is.read(mBufferMax);
            bytesRead += numRead;
        }

        int numRead = is.read(result);
        speedKBPS = (bytesRead * 8 * 1000. / 1024.) / (SystemClock.elapsedRealtime() - timeStart);
        assertEquals(1, numRead);
        assertEquals(1, result[0]);
        // We don't mandate min speed for now, let's collect data on what it is.
        getReportLog()
                .setSummary(
                        "Input USB accessory transfer speed",
                        speedKBPS,
                        ResultType.HIGHER_BETTER,
                        ResultUnit.KBPS);
        Log.i(LOG_TAG, "Read data transfer speed is " + speedKBPS + "KBPS");
    }

    private void testMeasureOutTransferSpeed(InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.MEASURE_OUT_TRANSFER_SPEED);

        byte[] result = new byte[1];
        long bytesSent = 0;
        long timeStart = SystemClock.elapsedRealtime();
        while (bytesSent < TEST_DATA_SIZE_THRESHOLD) {
            os.write(mOrigBufferMax);
            bytesSent += MAX_BUFFER_SIZE;
        }
        int numRead = is.read(result);
        double speedKBPS =
                (bytesSent * 8 * 1000. / 1024.) / (SystemClock.elapsedRealtime() - timeStart);
        assertEquals(1, numRead);
        assertEquals(1, result[0]);
        // We don't mandate min speed for now, let's collect data on what it is.
        getReportLog()
                .setSummary(
                        "Output USB accessory transfer speed",
                        speedKBPS,
                        ResultType.HIGHER_BETTER,
                        ResultUnit.KBPS);
        Log.i(LOG_TAG, "Write data transfer speed is " + speedKBPS + "KBPS");
    }

    private void testEchoBufferTwiceMaxSize(InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_MAX_2_BYTES);

        byte[] oversizeBuffer = new byte[MAX_BUFFER_SIZE * 2];
        System.arraycopy(mOrigBufferMax, 0, oversizeBuffer, 0, MAX_BUFFER_SIZE);
        System.arraycopy(mOrigBufferMax, 0, oversizeBuffer, MAX_BUFFER_SIZE, MAX_BUFFER_SIZE);
        os.write(oversizeBuffer);

        // The other side can not write more than the maximum size at once,
        // hence we get two transfers in return
        int numRead = is.read(mBufferMax);
        assertEquals(MAX_BUFFER_SIZE, numRead);
        assertArrayEquals(mOrigBufferMax, mBufferMax);

        numRead = is.read(mBufferMax);
        assertEquals(MAX_BUFFER_SIZE, numRead);
        assertArrayEquals(mOrigBufferMax, mBufferMax);
    }

    private void testEchoBufferMaxSize(InputStream is, OutputStream os) throws IOException {
        nextTest(is, os, AccessoryTestConstants.ECHO_MAX_BYTES);

        os.write(mOrigBufferMax);

        int numRead = is.read(mBufferMax);
        assertEquals(MAX_BUFFER_SIZE, numRead);
        assertArrayEquals(mOrigBufferMax, mBufferMax);
    }

    private void testReceiveAccessoryHandshakeIntent(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        nextTest(is, os, AccessoryTestConstants.RECEIVE_USB_ACCESSORY_HANDSHAKE);

        mAccessoryHandshakeIntent.get(
                3 * 1000, // 3 s
                TimeUnit.MILLISECONDS);
        assertTrue(mAccessoryStart);
        assertTrue(mHasSendStringCount);
        assertTrue(mHasAccessoryConnectionStartTime);
    }

    private void hideKeyboard(Activity activity) {
        InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * Brings focus to the input text field so it can receive key inputs and reset the text field.
     */
    private void prepareInputTextFieldForNewTest(final Context context) {
        if (mInputTextField == null) {
            Log.e(LOG_TAG, "mInputTextField is null.");
            return;
        }
        runOnUiThread(
                () -> {
                    mInputTextField.setVisibility(View.VISIBLE);
                    mInputTextField.setText("");
                    mInputTextField.requestFocus();
                    hideKeyboard((Activity) context);
                });
    }

    private void testSendDescriptorInFull(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_SEND_DESCRIPTOR_FULL);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals(KEYBOARD_1_INPUT, inputText);
    }

    private void testSendDescriptorInChunks(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_SEND_DESCRIPTOR_CHUNKS);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals(KEYBOARD_1_INPUT, inputText);
    }

    private void testSendInterleavedHidDescriptors(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_SEND_INTERLEAVED_DESCRIPTOR);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals(KEYBOARD_1_INPUT + KEYBOARD_2_INPUT, inputText);
    }

    private void testSendDescriptorToUnregisteredHid(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_UNREGISTERED_DESCRIPTOR);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals("", inputText);
    }

    private void testSendEventToUnregisteredHid(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_UNREGISTERED_EVENT);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals("", inputText);
    }

    private void testDescriptorOverflow(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_DESCRIPTOR_OVERFLOW);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals("", inputText);
    }

    private void testDescriptorIncomplete(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        prepareInputTextFieldForNewTest(UsbAccessoryTestActivity.this);
        nextTest(is, os, AccessoryTestConstants.TEST_DESCRIPTOR_INCOMPLETE);
        int numRead = is.read(mBuffer32);
        assertEquals(1, numRead);
        String inputText = mInputTextField.getText().toString();
        Log.i(LOG_TAG, "Received from companion: " + inputText);
        assertEquals("", inputText);
    }

    private void testHID(InputStream is, OutputStream os)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Base case of registering an HID Keyboard and checking the text.
        testSendDescriptorInFull(is, os);

        // Sending Key Descriptor in chunks and checking the text.
        testSendDescriptorInChunks(is, os);

        // Sending 2 keyboard Descriptors interleaved and checking the 2 text
        testSendInterleavedHidDescriptors(is, os);

        // Sending descriptor without Hid registration, text should be empty
        testSendDescriptorToUnregisteredHid(is, os);

        // Sending event without Hid registration, text should be empty
        testSendEventToUnregisteredHid(is, os);

        // Sending descriptor with more length than registered
        testDescriptorOverflow(is, os);

        // Sending descriptor with less length than registered
        testDescriptorIncomplete(is, os);
    }

    private void runTestsForAccessory(InputStream is, OutputStream os)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Echo a transfer
        testEchoTransfer(is, os);

        // Receive less data than available
        testReceiveLessDataThanAvailable(is, os);

        // Send two transfers in a row
        testSendTwoTransfersInARow(is, os);

        // Receive two transfers in a row into a buffer that is bigger than the
        // transfer
        testReceiveTwoTransfersInARowInBufferBiggerThanTransfer(is, os);

        // Echo a buffer with the maximum size
        testEchoBufferMaxSize(is, os);

        // Echo a buffer with twice the maximum size
        testEchoBufferTwiceMaxSize(is, os);

        // Measure out transfer speed
        testMeasureOutTransferSpeed(is, os);

        // Measure in transfer speed
        testMeasureInTransferSpeed(is, os);

        // Receive accessory handshake intent
        testReceiveAccessoryHandshakeIntent(is, os);

        // Test the HID protocols
        testHID(is, os);
    }
}
