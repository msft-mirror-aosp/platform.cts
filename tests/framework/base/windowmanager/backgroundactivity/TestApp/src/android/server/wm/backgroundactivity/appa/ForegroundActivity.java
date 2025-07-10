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
 * limitations under the License
 */

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityAction.ACTION_FINISH_ACTIVITY_SUFFIX;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityAction.ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.ACTIVITY_ID;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.ALLOW_CROSS_UID;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.FINISH_FIRST;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.LAUNCH_FOR_RESULT_AND_FINISH;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.LAUNCH_INTENTS;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.LAUNCH_PENDING_INTENTS;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.RELAUNCH_FOREGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appa.Components.buildFullActionName;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.server.wm.backgroundactivity.common.TestService;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Foreground activity that makes AppA as foreground. */
public class ForegroundActivity extends Activity {
    private static final String TAG = ForegroundActivity.class.getName();
    private String mAppPackageName;
    private String mActionLaunchBackgroundActivities;
    private String mActionFinishActivity;

    private int mActivityId = -1;
    private boolean mRelaunch = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int activityId = intent.getIntExtra(ACTIVITY_ID, mActivityId);
            if (activityId != mActivityId) {
                return;
            }

            if (mActionFinishActivity.equals(action)
                    || intent.getBooleanExtra(FINISH_FIRST, false)) {
                finish();
            }

            if (mActionLaunchBackgroundActivities.equals(action)) {
                if (intent.hasExtra(LAUNCH_INTENTS)) {
                    // Need to copy as a new array instead of just casting to Intent[] since
                    // a new array of type Parcelable[] is created when deserializing.
                    Intent[] intents =
                            intent.getParcelableArrayExtra(LAUNCH_INTENTS, Intent.class);
                    Log.d(TAG, mAppPackageName + ":start " + Arrays.asList(intents));
                    startActivities(intents);
                }
                if (intent.hasExtra(LAUNCH_PENDING_INTENTS)) {
                    // Need to copy as a new array instead of just casting to Intent[] since
                    // a new array of type Parcelable[] is created when deserializing.
                    PendingIntent[] pendingIntents =
                            intent.getParcelableArrayExtra(
                                    LAUNCH_PENDING_INTENTS, PendingIntent.class);
                    Log.d(TAG, mAppPackageName + ":start " + Arrays.asList(pendingIntents));
                    if (intent.getBooleanExtra(LAUNCH_FOR_RESULT_AND_FINISH, false)) {
                        // launch and then finish the activity
                        int nextRequestCode = 123;
                        List<Integer> requestCodesUsed = new ArrayList<>();
                        for (PendingIntent pi : pendingIntents) {
                            try {
                                int requestCode = nextRequestCode++;
                                startIntentSenderForResult(
                                        pi.getIntentSender(), requestCode, null, 0, 0, 0);
                                requestCodesUsed.add(requestCode);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        SystemClock.sleep(Duration.ofSeconds(1).toMillis());
                        for (int requestCode : requestCodesUsed) {
                            finishActivity(requestCode);
                        }
                    } else {
                        for (PendingIntent pi : pendingIntents) {
                            try {
                                startIntentSender(pi.getIntentSender(), null, 0, 0, 0);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        TestService.onForegroundActivityCreated(mActivityId, this);
        mAppPackageName = getApplicationContext().getPackageName();
        mActionLaunchBackgroundActivities =
                buildFullActionName(mAppPackageName, ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX);
        mActionFinishActivity = buildFullActionName(mAppPackageName, ACTION_FINISH_ACTIVITY_SUFFIX);

        Intent intent = getIntent();
        mRelaunch = intent.getBooleanExtra(RELAUNCH_FOREGROUND_ACTIVITY_EXTRA, false);
        mActivityId = intent.getIntExtra(ACTIVITY_ID, -1);
        setAllowCrossUidActivitySwitchFromBelow(intent.getBooleanExtra(ALLOW_CROSS_UID, false));

        IntentFilter filter = new IntentFilter();
        filter.addAction(mActionLaunchBackgroundActivities);
        filter.addAction(mActionFinishActivity);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mRelaunch) {
            mRelaunch = false;
            SystemClock.sleep(50);
            startActivity(getIntent());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        TestService.onForegroundActivityDestroyed(mActivityId, this);
    }
}
