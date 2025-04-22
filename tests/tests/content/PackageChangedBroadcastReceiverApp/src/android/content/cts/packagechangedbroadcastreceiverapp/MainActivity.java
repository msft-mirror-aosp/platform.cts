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

package android.content.cts.packagechangedbroadcastreceiverapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PatternMatcher;
import android.os.RemoteCallback;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final String TAG = "packagechangedbroadcastreceiverapp.MainActivity";

    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_READY = 1;
    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_FAILED = 2;
    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_BROADCAST_NUM = 3;
    private static final int EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE_WAKEUP = 1;

    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT_TYPE =
            "extra_remote_callback_result_type";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT_VALUE =
            "extra_remote_callback_result_value";
    private static final String EXTRA_ACTIVITY_REMOTE_CALLBACK = "extra_activity_remote_callback";
    private static final String EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE =
            "extra_activity_remote_callback_result_type";

    private static final String EXTRA_TEST_PACKAGE_NAME = "extra_test_package_name";
    private static final String SLEEP_ACTION = "android.content.broadcast.cts.SLEEP_ACTION";

    private BroadcastReceiver mPackageChangedReceiver;
    private BroadcastReceiver mSleepReceiver;
    private Handler mHandler;
    private Handler mReceiverHandler;
    private HandlerThread mReceiverThread;
    private RemoteCallback mRemoteCallback;
    private AtomicInteger mBroadcastNumber = new AtomicInteger(0);
    private final CountDownLatch mSleepTimeoutLatch = new CountDownLatch(1 /* count */);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler(getMainLooper());
        mRemoteCallback = getIntent().getParcelableExtra(EXTRA_REMOTE_CALLBACK);

        if (mRemoteCallback == null) {
            finish();
            return;
        }

        mReceiverThread = new HandlerThread("testReceiverThread");
        mReceiverThread.start();
        mReceiverHandler = new Handler(mReceiverThread.getLooper());

        final Bundle failedBundle = new Bundle();
        failedBundle.putInt(
                EXTRA_REMOTE_CALLBACK_RESULT_TYPE,
                EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_FAILED);

        final Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            mRemoteCallback.sendResult(failedBundle);
            finish();
            return;
        }
        final String packageName = bundle.getString(EXTRA_TEST_PACKAGE_NAME, null);
        if (TextUtils.isEmpty(packageName)) {
            mRemoteCallback.sendResult(failedBundle);
            finish();
            return;
        }

        startWatchingSleepBroadcast();
        startWatchingPackageChangedBroadcast(packageName);

        final RemoteCallback activityCallback =
                new RemoteCallback(
                        result -> {
                            Log.d(TAG, "Get callback from the test case");
                            switch (result.getInt(EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE)) {
                                case EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE_WAKEUP:
                                    Log.d(TAG, "unlock the broadcast receiver");
                                    // Set mBroadcastNumber to 0 here due to it will start to
                                    // receive the PACKAGE_CHANGED broadcast after unlocking the
                                    // broadcast receiver.
                                    mBroadcastNumber.set(0);
                                    mSleepTimeoutLatch.countDown();
                                    waitAndResponsePackageChangedBroadcastNum();
                                    break;
                            }
                        });

        final Bundle activityBundle = new Bundle();
        activityBundle.putInt(
                EXTRA_REMOTE_CALLBACK_RESULT_TYPE,
                EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_READY);
        activityBundle.putParcelable(EXTRA_ACTIVITY_REMOTE_CALLBACK, activityCallback);
        mRemoteCallback.sendResult(activityBundle);
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopWatchingSleepBroadcast();
        stopWatchingPackageChangedBroadcast();
        if (mReceiverThread != null) {
            mReceiverThread.quit();
        }
    }

    /**
     * When this receiver receives the sleep intent, it will block all the broadcast receivers of
     * this app until completing the action in this receiver, therefore delay the delivery of the
     * PACKAGE_CHANGED broadcasts.
     */
    void startWatchingSleepBroadcast() {
        mSleepReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.d(TAG, "onReceive action=" + intent.getAction());
                        try {
                            if (!mSleepTimeoutLatch.await(10_000, TimeUnit.MILLISECONDS)) {
                                Log.d(TAG, "block the receiver for at most 10 seconds.");
                            }
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Got InterruptedException e: " + e);
                        }
                    }
                };

        final IntentFilter filter = new IntentFilter(SLEEP_ACTION);
        registerReceiver(mSleepReceiver, filter, null, mReceiverHandler, Context.RECEIVER_EXPORTED);
    }

    void stopWatchingSleepBroadcast() {
        unregisterReceiver(mSleepReceiver);
    }

    void startWatchingPackageChangedBroadcast(String packageName) {
        mPackageChangedReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.d(TAG, "onReceive action=" + intent.getAction());
                        if (TextUtils.equals(intent.getAction(), Intent.ACTION_PACKAGE_CHANGED)) {
                            final Bundle bundle = intent.getExtras();
                            if (bundle != null
                                    && TextUtils.equals(
                                            bundle.getString(Intent.EXTRA_CHANGED_COMPONENT_NAME),
                                            packageName)) {
                                final int number = mBroadcastNumber.incrementAndGet();
                                Log.d(TAG, "receive the broadcast number=" + number);
                            }
                        }
                    }
                };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
        registerReceiver(
                mPackageChangedReceiver, filter, null, mReceiverHandler, Context.RECEIVER_EXPORTED);
    }

    void stopWatchingPackageChangedBroadcast() {
        unregisterReceiver(mPackageChangedReceiver);
    }

    void waitAndResponsePackageChangedBroadcastNum() {
        mHandler.postDelayed(
                () -> {
                    Log.d(TAG, "timeout to receive broadcast number=" + mBroadcastNumber.get());

                    stopWatchingPackageChangedBroadcast();
                    final Bundle resultBundle = new Bundle();
                    resultBundle.putInt(
                            EXTRA_REMOTE_CALLBACK_RESULT_TYPE,
                            EXTRA_REMOTE_CALLBACK_RESULT_TYPE_BROADCAST_NUM);
                    resultBundle.putInt(EXTRA_REMOTE_CALLBACK_RESULT_VALUE, mBroadcastNumber.get());
                    mRemoteCallback.sendResult(resultBundle);
                },
                5_000);
    }
}
