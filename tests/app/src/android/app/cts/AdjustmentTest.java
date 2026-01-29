/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.cts;


import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.os.Bundle;
import android.os.Parcel;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.Adjustment;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AdjustmentTest {
    private static final String ADJ_PACKAGE = "com.foo.bar";
    private static final String ADJ_KEY = "foo_key";
    private static final String ADJ_EXPLANATION = "I just feel like adjusting this";
    private static final UserHandle ADJ_USER = UserHandle.CURRENT;

    private final Bundle mSignals = new Bundle();

    private Adjustment mAdjustment;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mSignals.putString("foobar", "Hello, world!");
        mSignals.putInt("chirp", 47);
        mAdjustment = new Adjustment(ADJ_PACKAGE, ADJ_KEY, mSignals, ADJ_EXPLANATION, ADJ_USER);
    }

    @Test
    public void testGetPackage() {
        assertThat(mAdjustment.getPackage()).isEqualTo(ADJ_PACKAGE);
    }

    @Test
    public void testGetKey() {
        assertThat(mAdjustment.getKey()).isEqualTo(ADJ_KEY);
    }

    @Test
    public void testGetExplanation() {
        assertThat(mAdjustment.getExplanation().toString()).isEqualTo(ADJ_EXPLANATION);
    }

    @Test
    public void testGetUser() {
        assertThat(mAdjustment.getUserHandle()).isEqualTo(ADJ_USER);
    }

    @Test
    public void testGetSignals() {
        assertThat(mAdjustment.getSignals()).isEqualTo(mSignals);
        assertThat(mAdjustment.getSignals().getString("foobar")).isEqualTo("Hello, world!");
        assertThat(mAdjustment.getSignals().getInt("chirp")).isEqualTo(47);
    }

    @Test
    public void testDescribeContents() {
        assertThat(mAdjustment.describeContents()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testOriginatingRuleData() {
        mAdjustment.setOriginatingRuleId(101);
        mAdjustment.setOriginatingRuleOrder(6);

        assertThat(mAdjustment.getOriginatingRuleId()).isEqualTo(101);
        assertThat(mAdjustment.getOriginatingRuleOrder()).isEqualTo(6);
    }

    @Test
    public void testParcelling() {
        if (android.app.Flags.nmContextualDisplayLaunch()) {
            mAdjustment.setOriginatingRuleId(101);
            mAdjustment.setOriginatingRuleOrder(6);
        }

        final Parcel outParcel = Parcel.obtain();
        mAdjustment.writeToParcel(outParcel, 0);
        outParcel.setDataPosition(0);
        final Adjustment unparceled = Adjustment.CREATOR.createFromParcel(outParcel);

        assertThat(unparceled.getPackage()).isEqualTo(mAdjustment.getPackage());
        assertThat(unparceled.getKey()).isEqualTo(mAdjustment.getKey());
        assertThat(unparceled.getExplanation().toString()).isEqualTo(mAdjustment.getExplanation());
        assertThat(unparceled.getUserHandle()).isEqualTo(mAdjustment.getUserHandle());

        assertThat(unparceled.getSignals().getString("foobar")).isEqualTo("Hello, world!");
        assertThat(unparceled.getSignals().getInt("chirp")).isEqualTo(47);

        if (android.app.Flags.nmContextualDisplayLaunch()) {
            assertThat(unparceled.getOriginatingRuleId()).isEqualTo(101);
            assertThat(unparceled.getOriginatingRuleOrder()).isEqualTo(6);
        }
    }
}
