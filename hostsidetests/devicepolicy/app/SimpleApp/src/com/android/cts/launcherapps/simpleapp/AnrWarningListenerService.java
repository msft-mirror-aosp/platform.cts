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

package com.android.cts.launcherapps.simpleapp;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A service which registers listener for ANR warning. */
public final class AnrWarningListenerService extends Service {
    private static final String TAG = "AnrWarningListenerService";
    private static final String EXIT_ACTION = "com.android.cts.launchertests.simpleapp.EXIT_ACTION";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MESSENGER = "messenger";
    private static final String KEY_PROCESS_NAME = "process";

    private static final int ACTION_NONE = 0;
    private static final int ACTION_ANR = 3;

    private static final int WAIT_FOR_SETTLE_DOWN_MS = 2000;
    private static final int ANR_SLEEP_DURATION_MS = 60 * 1000;

    private static final int CMD_PID = 1;
    public static final int CMD_ANR_WARNING_LISTENER = 2;

    public static final String KEY_ANR_ID = "ANR_ID";
    public static final String KEY_ANR_TYPE = "ANR_TYPE";
    public static final String KEY_ELAPSED_TIME_MS = "ELAPSED_TIME_MS";
    public static final String KEY_TIMEOUT_MS = "TIMEOUT_MS";
    public static final String KEY_ANR_DESCRIPTION = "DESCRIPTION";
    public static final String KEY_ANR_TIMESTAMP = "ANR_TIMESTAMP";

    private Handler mHandler;
    private Messenger mMessenger;
    private ExecutorService mExecutor;

    @Override
    public void onCreate() {
        mHandler = new Handler(Looper.getMainLooper());
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method initializes the service by establishing a {@link Messenger} connection back to
     * the test process, registering the ANR warning listener, and then scheduling the requested
     * action (e.g., blocking the thread to cause an ANR) to be performed after a short delay.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMessenger = intent.getParcelableExtra(EXTRA_MESSENGER);
        registerAnrWarningListener();

        // send the message and perform the action after return from here,
        // make a delay, otherwise the system might try to restart the service
        // if the process dies before the system realize it's asking for START_NOT_STICKY.
        mHandler.postDelayed(
                () -> {
                    sendPidBack();
                    doAction(intent);
                },
                WAIT_FOR_SETTLE_DOWN_MS);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerAnrWarningListener() {
        ActivityManager am = getSystemService(ActivityManager.class);
        am.registerAnrWarningListener(
                mExecutor,
                result -> {
                    Message msg = Message.obtain();
                    msg.what = CMD_ANR_WARNING_LISTENER;
                    msg.arg1 = Process.myPid();
                    msg.arg2 = Process.myUid();

                    Bundle bundle = new Bundle();
                    bundle.putInt(KEY_ANR_ID, result.getAnrId());
                    bundle.putInt(KEY_ANR_TYPE, result.getAnrType());
                    bundle.putLong(KEY_ELAPSED_TIME_MS, result.getConsumedMillis());
                    bundle.putLong(KEY_TIMEOUT_MS, result.getTimeoutMillis());
                    bundle.putString(KEY_ANR_DESCRIPTION, result.getDescription());
                    bundle.putLong(KEY_ANR_TIMESTAMP, System.currentTimeMillis());

                    msg.obj = bundle;
                    sendMessageBack(msg);
                });
    }

    private void sendMessageBack(Message msg) {
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send the message back", e);
        }
    }

    private void sendPidBack() {
        Message msg = Message.obtain();
        msg.what = CMD_PID;
        msg.arg1 = Process.myPid();
        msg.arg2 = Process.myUid();
        Bundle b = new Bundle();
        b.putString(KEY_PROCESS_NAME, Process.myProcessName());
        msg.obj = b;
        sendMessageBack(msg);
    }

    private void doAction(Intent intent) {
        if (EXIT_ACTION.equals(intent.getAction())) {
            int action = intent.getIntExtra(EXTRA_ACTION, ACTION_NONE);
            switch (action) {
                case ACTION_ANR:
                    // Intentional added wait to cause ANR.
                    SystemClock.sleep(ANR_SLEEP_DURATION_MS);
                    break;
                case ACTION_NONE:
                default:
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mExecutor != null) {
            mExecutor.shutdown();
            mExecutor = null;
        }
    }
}
