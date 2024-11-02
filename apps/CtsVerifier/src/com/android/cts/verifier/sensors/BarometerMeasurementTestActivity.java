/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.util.Pair;
import java.util.Map.Entry;

/** Semi-automated test that focuses on characteristics associated with Barometer measurements. */
public class BarometerMeasurementTestActivity extends SensorCtsVerifierTestActivity {
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
                        /* samplePeriodUs= */ 100000,
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

       // Instruct the user to turn off the flashlight/
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
