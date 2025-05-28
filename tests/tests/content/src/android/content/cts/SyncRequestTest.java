/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.cts;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncRequest;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class SyncRequestTest {
    private static final String AUTHORITY = "authority1";
    private static final Account NULL_ACCOUNT = null;

    @Test
    public void testBuilder_normal() {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_PRIORITY, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
        new SyncRequest.Builder()
                .setSyncAdapter(NULL_ACCOUNT, AUTHORITY)
                .syncOnce()
                .setExtras(extras)
                .setExpedited(true)
                .setManual(true)
                .build();
    }

    @Test
    public void testBuilder_scheduleAsEj() {
        new SyncRequest.Builder()
                .setSyncAdapter(NULL_ACCOUNT, AUTHORITY)
                .setScheduleAsExpeditedJob(true)
                .build();
    }

    @Test
    public void testBuilder_throwsException() {
        assertThrows(
                "cannot both schedule as an expedited job and set the expedited extra",
                IllegalArgumentException.class,
                () ->
                        new SyncRequest.Builder()
                                .setSyncAdapter(NULL_ACCOUNT, AUTHORITY)
                                .setExpedited(true)
                                .setScheduleAsExpeditedJob(true)
                                .build());

        final Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true);
        assertThrows(
                "periodic syncs cannot be scheduled as EJs",
                IllegalArgumentException.class,
                () ->
                        new SyncRequest.Builder()
                                .setSyncAdapter(NULL_ACCOUNT, AUTHORITY)
                                .syncPeriodic(1, 1)
                                .setExtras(extras)
                                .build());

        assertThrows(
                "cannot require charging if scheduled as an EJ",
                IllegalArgumentException.class,
                () ->
                        new SyncRequest.Builder()
                                .setSyncAdapter(NULL_ACCOUNT, AUTHORITY)
                                .setRequiresCharging(true)
                                .setExtras(extras)
                                .build());
    }
}
