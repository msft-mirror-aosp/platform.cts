/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.provider.cts.simphonebook;

import static android.provider.SimPhonebookContract.ElementaryFiles.EF_ADN;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.telephony.SubscriptionManager;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_ContentNotificationsTest {

    private static final int DEFAULT_TIMEOUT = 5000;

    @ClassRule
    public static final SimsPowerRule SIMS_POWER_RULE = SimsPowerRule.on();
    private final SimCleanupRule mSimCleanupRule = new SimCleanupRule(ElementaryFiles.EF_ADN);
    @Rule
    public final TestRule mRule = RuleChain
            .outerRule(new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY))
            .around(new SimPhonebookRequirementsRule())
            .around(mSimCleanupRule);

    private int mSubId;
    private ContentResolver mResolver;
    private RecordingContentObserver mObserver;

    @Before
    public void setUp() {
        mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
        mObserver = new RecordingContentObserver();
        mResolver.registerContentObserver(SimPhonebookContract.AUTHORITY_URI, false, mObserver);
        assertThat(mObserver.observed).isEmpty();

        // Make sure the provider has been created.
        mResolver.getType(SimPhonebookContract.SimRecords.getContentUri(1, EF_ADN));

        mSubId = SubscriptionManager.getDefaultSubscriptionId();
    }

    @After
    public void tearDown() {
        mResolver.unregisterContentObserver(mObserver);
    }

    @Test
    public void insert_notifiesObserver() throws Exception {
        ContentValues values = new ContentValues();
        values.put(SimPhonebookContract.SimRecords.PHONE_NUMBER, "5550101");
        mResolver.insert(SimPhonebookContract.SimRecords.getContentUri(mSubId, EF_ADN), values);

        PollingCheck.check(
                "No content notifications observed for insert.",
                DEFAULT_TIMEOUT, () -> !mObserver.observed.isEmpty());
    }

    @Test
    public void update_notifiesObserver() throws Exception {
        ContentValues values = new ContentValues();
        values.put(SimPhonebookContract.SimRecords.PHONE_NUMBER, "5550101");
        Uri itemUri = mResolver.insert(
                SimPhonebookContract.SimRecords.getContentUri(mSubId, EF_ADN), values);
        PollingCheck.check(
                "No content notifications observed for insert.",
                DEFAULT_TIMEOUT, () -> mObserver.observed.size() == 1);

        values.put(SimPhonebookContract.SimRecords.PHONE_NUMBER, "5550102");
        mResolver.update(itemUri, values, null);

        PollingCheck.check(
                "No content notifications observed for update.",
                DEFAULT_TIMEOUT, () -> mObserver.observed.size() > 1);
    }

    @Test
    public void delete_notifiesObserver() throws Exception {
        ContentValues values = new ContentValues();
        values.put(SimPhonebookContract.SimRecords.PHONE_NUMBER, "5550101");
        Uri itemUri = mResolver.insert(
                SimPhonebookContract.SimRecords.getContentUri(mSubId, EF_ADN), values);
        PollingCheck.check(
                "No content notifications observed for insert.",
                DEFAULT_TIMEOUT, () -> mObserver.observed.size() == 1);

        mResolver.delete(itemUri, null);

        PollingCheck.check(
                "No content notifications observed for delete.",
                DEFAULT_TIMEOUT, () -> mObserver.observed.size() > 1);
    }

    @Test
    public void subscriptionsChange_notifiesObserver() throws Exception {
        try {
            // Mimic removal or insertion of a SIM by powering off the slot.
            SIMS_POWER_RULE.powerOff(0);

            PollingCheck.check(
                    "No content notifications observed for SIM removal",
                    DEFAULT_TIMEOUT, () -> mObserver.observed.size() >= 1);
            // It takes some time the SIM state transitions to finish so we sleep a bit to attempt
            // to allow the notifications they trigger to stop so that the notifications we observe
            // for the power on aren't polluted by the power off.
            Thread.sleep(DEFAULT_TIMEOUT);
            mObserver.observed.clear();
        } finally {
            SIMS_POWER_RULE.powerOn(0);
        }
        PollingCheck.check(
                "No content notifications observed for SIM insertion",
                DEFAULT_TIMEOUT, () -> mObserver.observed.size() >= 1);
    }

    private static class RecordingContentObserver extends ContentObserver {

        List<Uri> observed = new CopyOnWriteArrayList<>();

        public RecordingContentObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, @Nullable Uri uri) {
            observed.add(uri);
        }
    }
}
