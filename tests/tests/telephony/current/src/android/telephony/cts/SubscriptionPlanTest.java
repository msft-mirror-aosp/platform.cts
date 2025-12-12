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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.util.Range;
import android.util.RecurrenceRule;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

public class SubscriptionPlanTest {

    private static final ZonedDateTime START =
            ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
    private static final ZonedDateTime END = START.plusDays(30);
    private static final Period PERIOD = Period.ofMonths(1);
    private static final String TITLE = "test title";
    private static final String SUMMARY = "test summary";
    private static final long DATA_LIMIT_BYTES = 1024;
    private static final int DATA_LIMIT_BEHAVIOR = SubscriptionPlan.LIMIT_BEHAVIOR_BILLED;
    private static final long DATA_USAGE_BYTES = 512;
    private static final long DATA_USAGE_TIME = 1672531200000L; // 2023-01-01 00:00:00 UTC

    private static final ZonedDateTime DATA_USAGE_RESET_TIME =
            ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
    private static final Period NON_RECURRING_DURATION = Period.ofDays(7);
    private static final int[] NETWORK_TYPES = new int[] {TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_NR};
    private static final int SUBSCRIPTION_STATUS = SubscriptionPlan.SUBSCRIPTION_STATUS_ACTIVE;
    private static final int[] PLAN_TYPES =
            new int[] {SubscriptionPlan.PLAN_TYPE_CELLULAR, SubscriptionPlan.PLAN_TYPE_POSTPAID};
    private static final int ID = 123;

    private Clock mOriginalClock;

    @Before
    public void setUp() {
        mOriginalClock = RecurrenceRule.sClock;
    }

    @After
    public void tearDown() {
        RecurrenceRule.sClock = mOriginalClock;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testRecurringPlan() {
        SubscriptionPlan plan =
                SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                        .setTitle(TITLE)
                        .setSummary(SUMMARY)
                        .setDataLimit(DATA_LIMIT_BYTES, DATA_LIMIT_BEHAVIOR)
                        .setDataUsage(DATA_USAGE_BYTES, DATA_USAGE_TIME)
                        .setNetworkTypes(NETWORK_TYPES)
                        .setSubscriptionStatus(SUBSCRIPTION_STATUS)
                        .setTypes(PLAN_TYPES)
                        .setId(ID)
                        .build();

        assertThat(plan).isNotNull();
        assertThat(plan.getTitle()).isEqualTo(TITLE);
        assertThat(plan.getSummary()).isEqualTo(SUMMARY);
        assertThat(plan.getDataLimitBytes()).isEqualTo(DATA_LIMIT_BYTES);
        assertThat(plan.getDataLimitBehavior()).isEqualTo(DATA_LIMIT_BEHAVIOR);
        assertThat(plan.getDataUsageBytes()).isEqualTo(DATA_USAGE_BYTES);
        assertThat(plan.getDataUsageTime()).isEqualTo(DATA_USAGE_TIME);
        assertThat(plan.getNetworkTypes()).asList().containsExactlyElementsIn(
                Arrays.stream(NETWORK_TYPES).boxed().collect(Collectors.toList()));
        assertThat(plan.getSubscriptionStatus()).isEqualTo(SUBSCRIPTION_STATUS);
        assertThat(plan.getPlanEndDate()).isNull();
        assertThat(plan.getTypes()).containsExactlyElementsIn(Arrays.stream(PLAN_TYPES)
                .boxed().collect(Collectors.toList()));
        assertThat(plan.getId()).isEqualTo(ID);
    }

    @Test
    public void testNonRecurringPlan() {
        SubscriptionPlan plan =
                SubscriptionPlan.Builder.createNonrecurring(START, END)
                        .setTitle(TITLE)
                        .setSummary(SUMMARY)
                        .build();

        assertThat(plan).isNotNull();
        assertThat(plan.getTitle()).isEqualTo(TITLE);
        assertThat(plan.getSummary()).isEqualTo(SUMMARY);
        assertThat(plan.getPlanEndDate()).isEqualTo(END);

        // A non-recurring plan should have a single cycle defined by its start and end dates.
        Iterator<Range<ZonedDateTime>> iterator = plan.cycleIterator();
        assertThat(iterator.hasNext()).isTrue();
        Range<ZonedDateTime> cycle = iterator.next();
        assertThat(cycle.getLower()).isEqualTo(START);
        assertThat(cycle.getUpper()).isEqualTo(END);
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testParcelable() {
        SubscriptionPlan plan =
                SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                        .setTitle(TITLE)
                        .setSummary(SUMMARY)
                        .setDataLimit(DATA_LIMIT_BYTES, DATA_LIMIT_BEHAVIOR)
                        .setDataUsage(DATA_USAGE_BYTES, DATA_USAGE_TIME)
                        .setNetworkTypes(NETWORK_TYPES)
                        .setSubscriptionStatus(SUBSCRIPTION_STATUS)
                        .setTypes(PLAN_TYPES)
                        .setDataUsageResetTime(DATA_USAGE_RESET_TIME)
                        .setId(ID)
                        .build();

        Parcel parcel = Parcel.obtain();
        plan.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SubscriptionPlan fromParcel = SubscriptionPlan.CREATOR.createFromParcel(parcel);

        // Verify general equality and hash code
        assertThat(fromParcel).isEqualTo(plan);
        assertThat(fromParcel.hashCode()).isEqualTo(plan.hashCode());

        // Verify Cycle Rule (Recurring)
        assertThat(fromParcel.getCycleRule()).isEqualTo(plan.getCycleRule());

        // Verify Basic Info
        assertThat(fromParcel.getTitle()).isEqualTo(TITLE);
        assertThat(fromParcel.getSummary()).isEqualTo(SUMMARY);

        // Verify Data Limits and Usage
        assertThat(fromParcel.getDataLimitBytes()).isEqualTo(DATA_LIMIT_BYTES);
        assertThat(fromParcel.getDataLimitBehavior()).isEqualTo(DATA_LIMIT_BEHAVIOR);
        assertThat(fromParcel.getDataUsageBytes()).isEqualTo(DATA_USAGE_BYTES);
        assertThat(fromParcel.getDataUsageTime()).isEqualTo(DATA_USAGE_TIME);
        assertThat(fromParcel.getDataUsageResetTime()).isEqualTo(DATA_USAGE_RESET_TIME);

        // Verify Network Types
        assertThat(fromParcel.getNetworkTypes()).isEqualTo(NETWORK_TYPES);

        // Assuming the getters follow the standard naming convention based on the setters
        assertThat(fromParcel.getSubscriptionStatus()).isEqualTo(SUBSCRIPTION_STATUS);
        assertThat(fromParcel.getTypes()).containsExactlyElementsIn(Arrays.stream(PLAN_TYPES)
                .boxed().collect(Collectors.toList()));

        parcel.recycle();
        assertThat(fromParcel.getId()).isEqualTo(ID);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testEqualsAndHashCode() {
        SubscriptionPlan plan1 = SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                .setDataUsageResetTime(DATA_USAGE_RESET_TIME)
                .build();
        SubscriptionPlan plan2 = SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                .setDataUsageResetTime(DATA_USAGE_RESET_TIME)
                .build();
        SubscriptionPlan plan3 = SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                .build();
        SubscriptionPlan plan4 = SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                .setDataUsageResetTime(DATA_USAGE_RESET_TIME.plusDays(1))
                .build();

        assertThat(plan1).isEqualTo(plan2);
        assertThat(plan1.hashCode()).isEqualTo(plan2.hashCode());

        assertThat(plan1).isNotEqualTo(plan3);
        assertThat(plan1).isNotEqualTo(plan4);
    }

    @Test
    public void testCycleIterator() {
        // Set the "current time" to be safely within the third cycle of the plan, avoiding
        // boundary conditions in the RecurrenceRule iterator logic.
        RecurrenceRule.sClock =
                Clock.fixed(
                        START.plus(PERIOD).plus(PERIOD).plusDays(1).toInstant(), START.getZone());

        SubscriptionPlan plan = SubscriptionPlan.Builder.createRecurring(START, PERIOD).build();
        Iterator<Range<ZonedDateTime>> iterator = plan.cycleIterator();

        // The iterator starts at the "current" cycle and iterates backwards to the plan start.

        // 1. Check the current (third) cycle.
        assertThat(iterator.hasNext()).isTrue();
        Range<ZonedDateTime> cycle = iterator.next();
        assertThat(cycle.getLower()).isEqualTo(START.plus(PERIOD).plus(PERIOD));
        assertThat(cycle.getUpper()).isEqualTo(START.plus(PERIOD).plus(PERIOD).plus(PERIOD));

        // 2. Check the previous (second) cycle.
        assertThat(iterator.hasNext()).isTrue();
        cycle = iterator.next();
        assertThat(cycle.getLower()).isEqualTo(START.plus(PERIOD));
        assertThat(cycle.getUpper()).isEqualTo(START.plus(PERIOD).plus(PERIOD));

        // 3. Check the first cycle.
        assertThat(iterator.hasNext()).isTrue();
        cycle = iterator.next();
        assertThat(cycle.getLower()).isEqualTo(START);
        assertThat(cycle.getUpper()).isEqualTo(START.plus(PERIOD));

        // 4. There should be no more cycles before the start of the plan.
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testGetTypes() {
        SubscriptionPlan plan =
                SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                        .setTypes(PLAN_TYPES)
                        .build();

        assertThat(plan.getTypes()).containsExactly(
                SubscriptionPlan.PLAN_TYPE_CELLULAR,
                SubscriptionPlan.PLAN_TYPE_POSTPAID);
        assertThat(plan.getTypes().contains(SubscriptionPlan.PLAN_TYPE_CELLULAR)).isTrue();
        assertThat(plan.getTypes().contains(SubscriptionPlan.PLAN_TYPE_POSTPAID)).isTrue();
        assertThat(plan.getTypes().containsAll(Arrays.asList(
                SubscriptionPlan.PLAN_TYPE_CELLULAR,
                SubscriptionPlan.PLAN_TYPE_POSTPAID))).isTrue();
        assertThat(plan.getTypes().containsAll(Arrays.asList(
                SubscriptionPlan.PLAN_TYPE_SATELLITE,
                SubscriptionPlan.PLAN_TYPE_POSTPAID))).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testDataUsageResetTime() {
        SubscriptionPlan plan = SubscriptionPlan.Builder.createRecurring(START, PERIOD)
                .setDataUsageResetTime(DATA_USAGE_RESET_TIME)
                .build();
        assertThat(plan.getDataUsageResetTime()).isEqualTo(DATA_USAGE_RESET_TIME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_PLAN_ENHANCEMENT)
    public void testCreateNonrecurringPlan() {
        SubscriptionPlan plan = SubscriptionPlan.Builder
                .createNonrecurring(NON_RECURRING_DURATION)
                .setTitle(TITLE)
                .build();

        assertThat(plan).isNotNull();
        assertThat(plan.getTitle()).isEqualTo(TITLE);
        assertThat(plan.getCycleRule().start).isNull();
        assertThat(plan.getCycleRule().end).isNull();
        assertThat(plan.getCycleRule().period).isEqualTo(NON_RECURRING_DURATION);
        assertThat(plan.getSubscriptionStatus())
                .isEqualTo(SubscriptionPlan.SUBSCRIPTION_STATUS_INACTIVE);
        assertThat(plan.getDataUsageResetTime()).isNull();
    }
}
