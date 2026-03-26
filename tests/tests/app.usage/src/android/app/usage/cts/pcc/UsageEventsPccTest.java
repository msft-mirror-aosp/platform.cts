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

package android.app.usage.cts.pcc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.cts.Activities.ActivityOne;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * PCC variant of UsageStats tests. Validates that UsageEvents and UsageStats metadata are correctly
 * handled within the Private Compute Core (PCC) sandbox environment.
 */
@AppModeFull(reason = "No usage events access in instant apps")
@RunWith(AndroidJUnit4.class)
public class UsageEventsPccTest {
    private UsageStatsManager mUsageStatsManager;
    private Context mContext;
    private String mPackageName;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUsageStatsManager = mContext.getSystemService(UsageStatsManager.class);
        mPackageName = mContext.getPackageName();
    }

    /**
     * Verifies that basic activity launch events are recorded and all required metadata fields are
     * accessible within the sandbox.
     */
    @Test
    public void testUsageEventsCoverage() throws Exception {
        long startTime = System.currentTimeMillis();
        launchTestActivity();

        boolean packageNameFound = false;
        long timeout = SystemClock.elapsedRealtime() + 15000;

        while (SystemClock.elapsedRealtime() < timeout) {
            UsageEvents events =
                    mUsageStatsManager.queryEventsForSelf(
                            startTime - 5000, System.currentTimeMillis());

            Event event = new Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (mPackageName.equals(event.getPackageName())) {
                    packageNameFound = true;
                    assertNotNull("Package name should not be null", event.getPackageName());
                    assertTrue("Event type should be valid", event.getEventType() >= 0);
                    assertTrue("Timestamp should be recorded", event.getTimeStamp() > 0);
                    if (event.getEventType() == Event.ACTIVITY_RESUMED) {
                        assertNotNull(
                                "Class name should be recorded for activity events",
                                event.getClassName());
                    }
                    // For standard activity launches, shortcut ID is expected to be null
                    assertNull(
                            "Shortcut ID should be null for activity launch",
                            event.getShortcutId());
                }
            }
            if (packageNameFound) break;
            SystemClock.sleep(2000);
        }

        assertTrue(
                "Should find usage events for the test package in PCC sandbox", packageNameFound);
    }

    /**
     * Verifies that aggregated UsageStats and associated timestamps are accessible within the
     * sandbox.
     */
    @Test
    public void testUsageStatsCoverage() throws Exception {
        launchTestActivity();

        long startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

        UsageStats testPackageStats = null;
        long timeout = SystemClock.elapsedRealtime() + 15000;

        while (SystemClock.elapsedRealtime() < timeout) {
            List<UsageStats> stats =
                    mUsageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY,
                            startTime,
                            System.currentTimeMillis());

            if (stats != null) {
                for (UsageStats s : stats) {
                    if (mPackageName.equals(s.getPackageName()) && s.getLastTimeUsed() > 0) {
                        testPackageStats = s;
                        break;
                    }
                }
            }
            if (testPackageStats != null) break;
            SystemClock.sleep(2000);
        }

        assertNotNull(
                "Should find UsageStats for the test package in PCC sandbox", testPackageStats);
        assertEquals(mPackageName, testPackageStats.getPackageName());
        assertTrue("First timestamp should be valid", testPackageStats.getFirstTimeStamp() > 0);
        assertTrue("Last timestamp should be valid", testPackageStats.getLastTimeStamp() > 0);
        assertTrue("Last time used should be valid", testPackageStats.getLastTimeUsed() > 0);
        assertTrue(
                "Foreground time should be tracked",
                testPackageStats.getTotalTimeInForeground() >= 0);
    }

    private void launchTestActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mPackageName, ActivityOne.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
