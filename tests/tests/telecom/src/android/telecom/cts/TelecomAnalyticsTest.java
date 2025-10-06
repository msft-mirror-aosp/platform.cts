/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telecom.cts;

import android.telecom.ParcelableCallAnalytics;
import android.telecom.TelecomAnalytics;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/**
 * Bare minimum parceling test for the TelecomAnalytics class; this isn't really used any more but
 * we need some bare bones coverage for it.
 */
@RunWith(JUnit4.class)
public class TelecomAnalyticsTest {

    @Test
    public void testParcelable() {
        List<TelecomAnalytics.SessionTiming> sessionTimings = new ArrayList<>();
        List<ParcelableCallAnalytics.AnalyticsEvent> analyticsEvents = new ArrayList<>();
        List<ParcelableCallAnalytics.EventTiming> eventTimings = new ArrayList<>();
        ParcelableCallAnalytics callAnalytics = new ParcelableCallAnalytics(1L, 2L, 3,
                true, false, 4, 5, false, "foo", true, analyticsEvents, eventTimings);
        List<ParcelableCallAnalytics> callAnalyticsList = new ArrayList<>();
        callAnalyticsList.add(callAnalytics);

        TelecomAnalytics analytics = new TelecomAnalytics(sessionTimings, callAnalyticsList);
    }
}
