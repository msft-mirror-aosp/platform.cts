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

package com.android.server.cts.device.statsdatom;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.os.Process;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** An activity which performs ADPF actions. */
public class ADPFActivity extends Activity {
    private static final String TAG = ADPFActivity.class.getSimpleName();

    public static final String KEY_ACTION = "action";
    public static final String ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND =
            "action.create_dead_tids";
    public static final String KEY_RESULT_TIDS = "result_tids";
    public static final String KEY_UID = "result_uid";

    private final Map<String, Bundle> mResult = new ArrayMap<>();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = this.getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent is null.");
            finish();
        }
        final String action = intent.getStringExtra(KEY_ACTION);
        switch (action) {
            case ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND:
                try {
                    final int[] tids = createHintSessionWithExitedThreads();
                    final Bundle retBundle = new Bundle();
                    final StringJoiner sb = new StringJoiner(",");
                    for (int tid : tids) {
                        sb.add(String.valueOf(tid));
                    }
                    retBundle.putString(KEY_RESULT_TIDS, sb.toString());
                    retBundle.putString(KEY_UID, String.valueOf(Process.myUid()));
                    synchronized (mResult) {
                        mResult.put(ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND, retBundle);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                moveTaskToBack(true);
                break;
        }
    }

    /**
     * Read the run result of a specific action
     */
    public Bundle getRunResult(String actionKey) {
        synchronized (mResult) {
            return mResult.get(actionKey);
        }
    }

    private int[] createHintSessionWithExitedThreads() throws InterruptedException {
        PerformanceHintManager hintManager = getApplicationContext().getSystemService(
                PerformanceHintManager.class);
        assertNotNull(hintManager);
        CountDownLatch stopLatch = new CountDownLatch(1);
        int[] tids = createThreads(5, stopLatch);
        hintManager.createHintSession(tids, 100);
        stopLatch.countDown();
        return tids;
    }

    private int[] createThreads(int tidCnt, CountDownLatch stopLatch) throws InterruptedException {
        int[] tids = new int[tidCnt];
        CountDownLatch latch = new CountDownLatch(tidCnt);
        AtomicInteger k = new AtomicInteger(0);
        for (int i = 0; i < tidCnt; i++) {
            final Thread t = new Thread(() -> {
                tids[k.getAndIncrement()] = android.os.Process.myTid();
                try {
                    latch.countDown();
                    stopLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
        }
        latch.await();
        return tids;
    }
}
