/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.verifier.sensors;

import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;
import android.os.SystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.google.common.math.Quantiles;

import android.os.Bundle;
import android.view.View;
import junit.framework.Assert;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.TestSensorEvent;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.RadioGroup;

import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.view.Gravity;
import android.sysprop.SensorProperties;

import android.view.View;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import android.util.DisplayMetrics;

import android.text.InputType;
import java.util.Random;
import android.util.Log;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import android.widget.Button;
import android.widget.ViewSwitcher;
import android.widget.FrameLayout;
import android.os.HandlerThread;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.TimeZone;
import android.view.WindowManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.bluetooth.BluetoothAdapter;
import com.android.cts.verifier.bluetooth.BluetoothChatService;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;

import android.util.Pair;
import java.util.Map.Entry;
import com.android.internal.annotations.GuardedBy;

/** Semi-automated test that focuses on characteristics associated with Barometer measurements. */
public class BarometerMeasurementTestActivity extends SensorCtsVerifierTestActivity {
    public static int SAMPLE_PERIOD_US = 100000;
    private boolean endMessage = false;

    @GuardedBy("this")
    private StringBuilder messages = new StringBuilder();

    public BarometerMeasurementTestActivity() {
        super(BarometerMeasurementTestActivity.class, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!SensorProperties.isHighQualityBarometerImplemented().isPresent()
                || !SensorProperties.isHighQualityBarometerImplemented().get()) {
            getTestLogger().logMessage(R.string.snsr_baro_not_implemented);
            finish();
        }
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        waitForUserToContinue();
    }

    @Override
    protected void activityCleanUp() {
        closeGlSurfaceView();
    }

    @SuppressWarnings("unused")
    public String testFlashlightImpact() throws Throwable {
        List<TestSensorEvent> events = new ArrayList<>();
        getTestLogger().logInstructions(R.string.snsr_baro_flashlight_test_prep_instruction);
        waitForUserToContinue();
        // Initial collection to get a baseline reading for barometer measurements without the
        // impact of light.
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 10, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        playSound();

        // Collect data while the flashlight is on
        getTestLogger().logInstructions(R.string.snsr_baro_flashlight_instruction);
        waitForUserToContinue();
        sensorOperation = TestSensorOperation.createOperation(environment, 90, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        playSound();

        // Instruct the user to turn off the flashlight
        getTestLogger().logInstructions(R.string.snsr_baro_restore_to_default);
        waitForUserToContinue();

        // Finish with a final collection to get a baseline reading
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        waitForUserToContinue();
        sensorOperation = TestSensorOperation.createOperation(environment, 10, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));

        playSound();
        getTestLogger().logInstructions(R.string.snsr_baro_proceed);
        waitForUserToContinue();

        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));
        boolean failed =
                minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.12;
        if (failed) {
            Assert.fail("FAILED - Pressure change under flashlight impact is larger than 0.12 hPa");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String testTemperatureCompensation() throws Throwable {
        getTestLogger().logInstructions(R.string.snsr_baro_fridge_wait);
        waitForUserToContinue();
        // Wait for 20 minutes while the device is in the fridge.
        SystemClock.sleep(1200000);
        // Prompt the user to set the date and time to one hour from now to ensure that the
        // reference device and the test device have the same time at second granularity.
        getTestLogger().logInstructions(R.string.snsr_baro_date_time_instruction);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_location_on);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_bluetooth_on);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_fridge_instruction);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothChatService chatService =
                new BluetoothChatService(
                        this,
                        new ChatHandler(Looper.getMainLooper()),
                        BluetoothChatService.SECURE_UUID);
        chatService.start(/* secure= */ true);
        // Prompt the user to pair the reference device and the test device.
        getTestLogger().logInstructions(R.string.snsr_baro_bluetooth_instruction);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_PAIRING_SETTINGS));
        // Sample the barometer at 1 reading per second since the time synchronization is at second
        // granularity.
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(), Sensor.TYPE_PRESSURE, SAMPLE_PERIOD_US * 10, 0);
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 10, TimeUnit.MINUTES);
        getTestLogger().logInstructions(R.string.snsr_baro_temp_test_instruction);
        waitForUserToContinue();
        simulateHighCpuUsageToIncreaseTemperature();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        long[] timestamps = new long[currentEvents.size()];
        float[] readings = new float[currentEvents.size()];
        // Grab the system boot time since the reported timestamps of the events are in nanoseconds
        // since boot.
        long systemBootTimeS = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
        for (int i = 0; i < currentEvents.size(); i++) {
            TestSensorEvent event = currentEvents.get(i);
            timestamps[i] = Math.round(event.timestamp / 1e9) + systemBootTimeS;
            readings[i] = event.values[0];
        }
        int[] commonTimestampsIndices = new int[currentEvents.size()];
        // Keep idling until the reference device ends the connection.
        while (!endMessage) {}
        String[] splitMessages;
        synchronized (this) {
            splitMessages = messages.toString().split(BarometerReferenceDeviceActivity.LINE_BREAK);
        }
        float[] referenceReadings = new float[splitMessages.length];
        long[] referenceTimestamps = new long[splitMessages.length];
        for (int i = 0; i < splitMessages.length; i++) {
            String[] splitMessage =
                    splitMessages[i].split(BarometerReferenceDeviceActivity.DELIMITER);
            // The reference device reports the timestamps in seconds since the epoch.
            referenceTimestamps[i] = Long.parseLong(splitMessage[0]);
            referenceReadings[i] = Float.parseFloat(splitMessage[1]);
        }
        chatService.stop();
        boolean failed =
                validateTemperatureCompensation(
                        timestamps, readings, referenceTimestamps, referenceReadings);
        if (failed) {
            Assert.fail("FAILED - abs(max(p_delta) - min(p_delta)) is larger than 0.2 hPa");
        }
        return failed ? "PASSED" : "FAILED";
    }

    // Finds the maximum and minimum differences between the test device and the reference device
    // readings at the same timestamp and returns true if the difference is less than 0.2 hPa.
    private boolean validateTemperatureCompensation(
            long[] timestamps,
            float[] readings,
            long[] referenceTimestamps,
            float[] referenceReadings) {
        float maxDelta = Float.MIN_VALUE;
        float minDelta = Float.MAX_VALUE;
        int index = 0;
        int referenceIndex = 0;
        while (index < timestamps.length && referenceIndex < referenceTimestamps.length) {
            long timestamp = timestamps[index];
            long referenceTimestamp = referenceTimestamps[referenceIndex];
            if (timestamp == referenceTimestamp) {
                float diff = readings[index] - referenceReadings[referenceIndex];
                if (diff > maxDelta) {
                    maxDelta = diff;
                }
                if (diff < minDelta) {
                    minDelta = diff;
                }
                index++;
                referenceIndex++;
            }
            if (timestamp > referenceTimestamp) {
                referenceIndex++;
            }
            if (timestamp < referenceTimestamp) {
                index++;
            }
        }
        return Math.abs(maxDelta - minDelta) < 0.2;
    }

    // Handler for messages from the reference device via Bluetooth.
    private class ChatHandler extends Handler {
        public ChatHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int state = msg.arg1;
            // We have received a message from the reference device and we are not connected to the
            // reference device anymore.
            synchronized (BarometerMeasurementTestActivity.this) {
                if (!messages.isEmpty() && state != BluetoothChatService.STATE_CONNECTED) {
                    endMessage = true;
                    return;
                }
            }
            if (msg.what == BluetoothChatService.MESSAGE_READ) {
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                if (readMessage.isEmpty()) {
                    return;
                }
                synchronized (BarometerMeasurementTestActivity.this) {
                    messages.append(readMessage);
                }
            }
        }
    }

    private void simulateHighCpuUsageToIncreaseTemperature() {
        for (int i = 0; i < 100; i++) {
            Thread t =
                    new Thread() {
                        public void run() {
                            SystemClock.sleep(1000);
                            var unused = 0;
                            for (float j = 0; j < Float.MAX_VALUE; j++) {
                                unused += Math.sqrt(j);
                            }
                        }
                    };
            t.start();
        }
    }

    private static Map<Long, Float> eventListToTimestampReadingMap(List<TestSensorEvent> events) {
        Map<Long, Float> readings = new HashMap<>();
        for (TestSensorEvent event : events) {
            readings.put(event.receivedTimestamp, computeAveragePressureHpa(event));
        }
        return readings;
    }

    private static float computeAveragePressureHpa(TestSensorEvent event) {
        float[] events = event.values.clone();
        float sum = 0;

        for (int i = 0; i < events.length; i++) {
            sum += events[i];
        }
        return sum / events.length;
    }

    private static Pair<Entry<Long, Float>, Entry<Long, Float>> getMinAndMaxReadings(
            Map<Long, Float> readings) {
        Entry<Long, Float> minEntry = null;
        Entry<Long, Float> maxEntry = null;
        for (Entry<Long, Float> entry : readings.entrySet()) {
            if (minEntry == null || entry.getValue() < minEntry.getValue()) {
                minEntry = entry;
            }
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }
        return Pair.create(minEntry, maxEntry);
    }
}
