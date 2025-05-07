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

package android.app.stubs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** A helper class for LaunchpadActivity. */
public final class LaunchpadHelper implements LaunchpadActivity.CallingTest {
    private static final String ORIGINAL_ERROR_WAS_HERE = "Original error was here";
    private static final String UNABLE_TO_LAUNCH = "Unable to launch";
    private static final int TIMEOUT_MS = 60 * 1000;
    private final Context mContext;
    private Intent mIntent;
    private String mExpecting;
    private boolean mFinished;
    private int mResultCode = 0;
    private Intent mData;
    private Activity mActivity;
    private RuntimeException mResultStack = null;

    public LaunchpadHelper(Context context) {
        mContext = context;
        mIntent = new Intent(mContext, LaunchpadActivity.class);
    }

    @Override
    public void activityRunning(Activity activity) {
        finishWithActivity(activity);
    }

    @Override
    public void activityFinished(int resultCode, Intent data, RuntimeException where) {
        finishWithResult(resultCode, data, null, where);
    }

    public Intent editIntent() {
        return mIntent;
    }

    public void setIntent(Intent intent) {
        mIntent = intent;
    }

    public void finishGood() {
        finishWithResult(Activity.RESULT_OK, null);
    }

    public void finishBad(String error) {
        finishWithResult(Activity.RESULT_CANCELED, new Intent().setAction(error));
    }

    public void finishWithActivity(Activity activity) {
        final RuntimeException where = new RuntimeException(ORIGINAL_ERROR_WAS_HERE);
        where.fillInStackTrace();
        finishWithResult(Activity.RESULT_OK, null, activity, where);
    }

    public void finishWithResult(int resultCode, Intent data) {
        final RuntimeException where = new RuntimeException(ORIGINAL_ERROR_WAS_HERE);
        where.fillInStackTrace();
        finishWithResult(resultCode, data, null, where);
    }

    public void finishWithResult(
            int resultCode, Intent data, Activity activity, RuntimeException where) {
        synchronized (this) {
            mResultCode = resultCode;
            mData = data;
            mActivity = activity;
            mResultStack = where;
            mFinished = true;
            notifyAll();
        }
    }

    public int runLaunchpad(String action) {
        startLaunchpadActivity(action);
        return waitForResultOrThrow(TIMEOUT_MS);
    }

    public void startLaunchpadActivity(String action) {
        LaunchpadActivity.setCallingTest(this);
        synchronized (this) {
            mIntent.setAction(action);
            mFinished = false;
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(mIntent);
        }
    }

    public int waitForResultOrThrow(int timeoutMs) {
        return waitForResultOrThrow(timeoutMs, null);
    }

    public int waitForResultOrThrow(int timeoutMs, String expected) {
        final int res = waitForResult(timeoutMs, expected);

        if (res == Activity.RESULT_CANCELED) {
            if (mResultStack != null) {
                throw new RuntimeException(
                        mData != null ? mData.toString() : UNABLE_TO_LAUNCH, mResultStack);
            } else {
                throw new RuntimeException(mData != null ? mData.toString() : UNABLE_TO_LAUNCH);
            }
        }
        return res;
    }

    public int waitForResult(int timeoutMs, String expected) {
        mExpecting = expected;

        final long endTime = System.currentTimeMillis() + timeoutMs;

        boolean timeout = false;
        synchronized (this) {
            while (!mFinished) {
                final long delay = endTime - System.currentTimeMillis();
                if (delay < 0) {
                    timeout = true;
                    break;
                }

                try {
                    wait(delay);
                } catch (final InterruptedException e) {
                    // do nothing
                }
            }
        }

        mFinished = false;

        if (timeout) {
            mResultCode = Activity.RESULT_CANCELED;
            onTimeout();
        }
        return mResultCode;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public Intent getResultData() {
        return mData;
    }

    public RuntimeException getResultStack() {
        return mResultStack;
    }

    public Activity getRunningActivity() {
        return mActivity;
    }

    public void onTimeout() {
        final String msg = mExpecting == null ? "Timeout" : "Timeout while expecting " + mExpecting;
        finishWithResult(Activity.RESULT_CANCELED, new Intent().setAction(msg));
    }
}
