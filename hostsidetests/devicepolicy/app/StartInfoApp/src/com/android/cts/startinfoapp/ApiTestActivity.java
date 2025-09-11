/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.startinfoapp;

import static com.android.cts.startinfoapp.TestHelper.REPLY_ACTION_COMPLETE;
import static com.android.cts.startinfoapp.TestHelper.REPLY_EXTRA_FAILURE_VALUE;
import static com.android.cts.startinfoapp.TestHelper.REPLY_EXTRA_SUCCESS_VALUE;
import static com.android.cts.startinfoapp.TestHelper.REPLY_STATUS_NONE;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_KEY_ACTION;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_KEY_TIMESTAMP_KEY_FIRST;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_KEY_TIMESTAMP_KEY_LAST;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_KEY_TIMESTAMP_VALUE_FIRST;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_KEY_TIMESTAMP_VALUE_LAST;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_ADD_TIMESTAMP;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_CRASH;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_LISTENER_ADD_MULTIPLE;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_LISTENER_ADD_ONE;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_LISTENER_ADD_REMOVE;
import static com.android.cts.startinfoapp.TestHelper.REQUEST_VALUE_QUERY_START;
import static com.android.cts.startinfoapp.TestHelper.reply;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.content.Intent;
import android.os.Bundle;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * An activity to install to test ApplicationStartInfo.
 *
 * Specific test cases can be requested by putting an intent bundle extra with
 * {@link REQUEST_KEY_ACTION} paired to one of the supported REQUEST_VALUE_ cases.
 *
 * A result will be provided back via a broadcast with action {@link REPLY_ACTION_COMPLETE} set for
 * all cases, success and failure.
 */
public class ApiTestActivity extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Intent intent = getIntent();
        if (intent == null) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        int action = extras.getInt(REQUEST_KEY_ACTION, -1);
        if (action == -1) {
            return;
        }
        switch (action) {
            case REQUEST_VALUE_QUERY_START:
                queryStart();
                break;
            case REQUEST_VALUE_ADD_TIMESTAMP:
                addTimestamp(extras);
                break;
            case REQUEST_VALUE_LISTENER_ADD_ONE:
                addOneListener();
                break;
            case REQUEST_VALUE_LISTENER_ADD_MULTIPLE:
                addMultipleListeners();
                break;
            case REQUEST_VALUE_LISTENER_ADD_REMOVE:
                addAndRemoveListener();
                break;
            case REQUEST_VALUE_CRASH:
                throw new RuntimeException("This is a test");
        }
    }

    /**
     * Query a single start from historical records and confirms it exists.
     *
     * Records are expected to be available, though not necessarily complete, after the start begins
     * even if the start has not completed yet.
     *
     * Sends broadcast with  {@link REPLY_EXTRA_SUCCESS_VALUE} if a start record was successfully
     * received, and {@link REPLY_EXTRA_FAILURE_VALUE} if not.
     */
    private void queryStart() {
        ActivityManager am = getSystemService(ActivityManager.class);
        List<ApplicationStartInfo> starts = am.getHistoricalProcessStartReasons(1);

        boolean success = starts != null && starts.size() == 1;
        reply(
                ApiTestActivity.this,
                success ? REPLY_EXTRA_SUCCESS_VALUE : REPLY_EXTRA_FAILURE_VALUE);
    }

    /**
     * Adds provided timestamps to the ongoing start record. Does not confirm that they were added
     * successfully.
     *
     * Sends broadcast with no status when complete.
     */
    private void addTimestamp(Bundle extras) {
        int keyFirst = extras.getInt(REQUEST_KEY_TIMESTAMP_KEY_FIRST, 21);
        long valFirst = extras.getLong(REQUEST_KEY_TIMESTAMP_VALUE_FIRST, 123456789L);
        int keyLast = extras.getInt(REQUEST_KEY_TIMESTAMP_KEY_LAST, 30);
        long valLast = extras.getLong(REQUEST_KEY_TIMESTAMP_VALUE_LAST, 123456789L);

        ActivityManager am = getSystemService(ActivityManager.class);
        am.addStartInfoTimestamp(keyFirst, valFirst);
        am.addStartInfoTimestamp(keyLast, valLast);

        reply(ApiTestActivity.this, REPLY_STATUS_NONE);
    }

    /**
     * Add 1 listener.
     *
     * Listener is expected to be triggered upon completion of start, or immediately if the start
     * is already complete.
     *
     * Result will be broadcast when listener is triggered.
     */
    private void addOneListener() {
        ActivityManager am = getSystemService(ActivityManager.class);
        Consumer<ApplicationStartInfo> listener =
                new Consumer<ApplicationStartInfo>() {
                    @Override
                    public void accept(ApplicationStartInfo info) {
                        reply(ApiTestActivity.this, REPLY_EXTRA_SUCCESS_VALUE);
                    }
                };
        am.addApplicationStartInfoCompletionListener(Executors.newSingleThreadScheduledExecutor(),
                listener);
    }

    /**
     * Add 2 listeners.
     *
     * Listener is expected to be triggered upon completion of start, or immediately if the start
     * is already complete.
     *
     * Result will be broadcast for each listener when triggered.
     */
    private void addMultipleListeners() {
        ActivityManager am = getSystemService(ActivityManager.class);
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Consumer<ApplicationStartInfo> listenerFirst =
                new Consumer<ApplicationStartInfo>() {
                    @Override
                    public void accept(ApplicationStartInfo info) {
                        reply(ApiTestActivity.this, REPLY_EXTRA_SUCCESS_VALUE);
                    }
                };
        Consumer<ApplicationStartInfo> listenerSecond =
                new Consumer<ApplicationStartInfo>() {
                    @Override
                    public void accept(ApplicationStartInfo info) {
                        reply(ApiTestActivity.this, REPLY_EXTRA_SUCCESS_VALUE);
                    }
                };

        am.addApplicationStartInfoCompletionListener(executor, listenerFirst);
        am.addApplicationStartInfoCompletionListener(executor, listenerSecond);
    }

    /**
     * Add 2 listeners and then remove 1.
     *
     * Listener is expected to be triggered upon completion of start, or immediately if the start
     * is already complete.
     *
     * Result will be broadcast for each listener if triggered. Result status will be
     * {@link REPLY_EXTRA_SUCCESS_VALUE} when listener that was not removed is triggered, and
     * {@link REPLY_EXTRA_FAILURE_VALUE} if listener that was removed is triggered. This method is
     * intended to be called during startup so that the first listener has time to be removed.
     */
    private void addAndRemoveListener() {
        ActivityManager am = getSystemService(ActivityManager.class);
        final Object mLock = new Object();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Consumer<ApplicationStartInfo> listenerToRemove =
                new Consumer<ApplicationStartInfo>() {
                    @Override
                    public void accept(ApplicationStartInfo info) {
                        synchronized (mLock) {
                            reply(ApiTestActivity.this, REPLY_EXTRA_FAILURE_VALUE);
                        }
                    }
                };
        Consumer<ApplicationStartInfo> listenerToTrigger =
                new Consumer<ApplicationStartInfo>() {
                    @Override
                    public void accept(ApplicationStartInfo info) {
                        synchronized (mLock) {
                            reply(ApiTestActivity.this, REPLY_EXTRA_SUCCESS_VALUE);
                        }
                    }
                    ;
                };

        am.addApplicationStartInfoCompletionListener(executor, listenerToRemove);
        am.addApplicationStartInfoCompletionListener(executor, listenerToTrigger);

        am.removeApplicationStartInfoCompletionListener(listenerToRemove);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }
}
