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

package android.telecom.cts;

import android.content.Context;
import android.os.Parcel;
import android.telecom.ParcelableCallAnalytics;
import android.test.InstrumentationTestCase;

import java.util.ArrayList;
import java.util.List;

public class ParcelableCallAnalyticsTest extends InstrumentationTestCase {

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
    }

    public void testAnalyticsEventParceling() {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }

        ParcelableCallAnalytics.AnalyticsEvent originalEvent =
                new ParcelableCallAnalytics.AnalyticsEvent(
                        ParcelableCallAnalytics.AnalyticsEvent.SET_ACTIVE, 12345L);

        Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ParcelableCallAnalytics.AnalyticsEvent newEvent =
                ParcelableCallAnalytics.AnalyticsEvent.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalEvent.getEventName(), newEvent.getEventName());
        assertEquals(originalEvent.getTimeSinceLastEvent(), newEvent.getTimeSinceLastEvent());
    }

    public void testEventTimingParceling() {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }

        ParcelableCallAnalytics.EventTiming originalTiming =
                new ParcelableCallAnalytics.EventTiming(
                        ParcelableCallAnalytics.EventTiming.ACCEPT_TIMING, 54321L);

        Parcel parcel = Parcel.obtain();
        originalTiming.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ParcelableCallAnalytics.EventTiming newTiming =
                ParcelableCallAnalytics.EventTiming.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalTiming.getName(), newTiming.getName());
        assertEquals(originalTiming.getTime(), newTiming.getTime());
    }

    public void testParcelableCallAnalyticsParceling() {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }

        List<ParcelableCallAnalytics.AnalyticsEvent> analyticsEvents = new ArrayList<>();
        analyticsEvents.add(
                new ParcelableCallAnalytics.AnalyticsEvent(
                        ParcelableCallAnalytics.AnalyticsEvent.SET_ACTIVE, 100L));
        analyticsEvents.add(
                new ParcelableCallAnalytics.AnalyticsEvent(
                        ParcelableCallAnalytics.AnalyticsEvent.SET_DISCONNECTED, 200L));

        List<ParcelableCallAnalytics.EventTiming> eventTimings = new ArrayList<>();
        eventTimings.add(
                new ParcelableCallAnalytics.EventTiming(
                        ParcelableCallAnalytics.EventTiming.ACCEPT_TIMING, 50L));
        eventTimings.add(
                new ParcelableCallAnalytics.EventTiming(
                        ParcelableCallAnalytics.EventTiming.DISCONNECT_TIMING, 150L));

        ParcelableCallAnalytics originalAnalytics =
                new ParcelableCallAnalytics(
                        System.currentTimeMillis(), // startTimeMillis
                        5000L, // callDurationMillis
                        ParcelableCallAnalytics.CALLTYPE_OUTGOING, // callType
                        false, // isAdditionalCall
                        false, // isInterrupted
                        ParcelableCallAnalytics.IMS_PHONE, // callTechnologies
                        1, // callTerminationCode
                        false, // isEmergencyCall
                        "com.android.cts.telecom", // connectionService
                        false, // isCreatedFromExistingConnection
                        analyticsEvents,
                        eventTimings);

        Parcel parcel = Parcel.obtain();
        originalAnalytics.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ParcelableCallAnalytics newAnalytics =
                ParcelableCallAnalytics.CREATOR.createFromParcel(parcel);
        ParcelableCallAnalytics constructed = new ParcelableCallAnalytics(parcel);
        assertNotNull(constructed);
        parcel.recycle();

        assertEquals(originalAnalytics.getStartTimeMillis(), newAnalytics.getStartTimeMillis());
        assertEquals(
                originalAnalytics.getCallDurationMillis(), newAnalytics.getCallDurationMillis());
        assertEquals(originalAnalytics.getCallType(), newAnalytics.getCallType());
        assertEquals(originalAnalytics.isAdditionalCall(), newAnalytics.isAdditionalCall());
        assertEquals(originalAnalytics.isInterrupted(), newAnalytics.isInterrupted());
        assertEquals(originalAnalytics.getCallTechnologies(), newAnalytics.getCallTechnologies());
        assertEquals(
                originalAnalytics.getCallTerminationCode(), newAnalytics.getCallTerminationCode());
        assertEquals(originalAnalytics.isEmergencyCall(), newAnalytics.isEmergencyCall());
        assertEquals(originalAnalytics.getConnectionService(), newAnalytics.getConnectionService());
        assertEquals(
                originalAnalytics.isCreatedFromExistingConnection(),
                newAnalytics.isCreatedFromExistingConnection());

        // Verify AnalyticsEvents list
        assertEquals(
                originalAnalytics.analyticsEvents().size(), newAnalytics.analyticsEvents().size());
        for (int i = 0; i < originalAnalytics.analyticsEvents().size(); i++) {
            assertEquals(
                    originalAnalytics.analyticsEvents().get(i).getEventName(),
                    newAnalytics.analyticsEvents().get(i).getEventName());
            assertEquals(
                    originalAnalytics.analyticsEvents().get(i).getTimeSinceLastEvent(),
                    newAnalytics.analyticsEvents().get(i).getTimeSinceLastEvent());
        }

        // Verify EventTimings list
        assertEquals(
                originalAnalytics.getEventTimings().size(), newAnalytics.getEventTimings().size());
        for (int i = 0; i < originalAnalytics.getEventTimings().size(); i++) {
            assertEquals(
                    originalAnalytics.getEventTimings().get(i).getName(),
                    newAnalytics.getEventTimings().get(i).getName());
            assertEquals(
                    originalAnalytics.getEventTimings().get(i).getTime(),
                    newAnalytics.getEventTimings().get(i).getTime());
        }
    }
}
