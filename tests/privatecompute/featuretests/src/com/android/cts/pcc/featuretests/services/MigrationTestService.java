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

package com.android.cts.pcc.featuretests.services;

import android.app.privatecompute.DataMigrationToPccService;
import android.app.privatecompute.MigrationRequestResult;
import android.os.PersistableBundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Service for testing data migration to PCC. */
public class MigrationTestService extends DataMigrationToPccService {
    /** Latch to wait for migration request. */
    public static CountDownLatch sLatch = new CountDownLatch(1);

    /** Status to return in the migration result. */
    public static int sResponseStatus = MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED;

    /** Handles the migration request. */
    @Override
    public void onMigrationRequested(Consumer<MigrationRequestResult> callback) {
        sLatch.countDown();
        callback.accept(new MigrationRequestResult(sResponseStatus, new PersistableBundle()));
    }

    /** Waits for the migration request to be received. */
    public static boolean waitForMigration(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sLatch.await(timeout, unit);
    }

    /** Resets the latch and response status. */
    public static void reset() {
        sLatch = new CountDownLatch(1);
        sResponseStatus = MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED;
    }
}
