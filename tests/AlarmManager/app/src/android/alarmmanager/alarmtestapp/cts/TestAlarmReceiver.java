/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.alarmmanager.alarmtestapp.cts;

import static android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler.EXTRA_ALARM_ID;

import android.alarmmanager.alarmtestapp.cts.common.FgsTester;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TestAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = TestAlarmReceiver.class.getSimpleName();
    private static final String PACKAGE_NAME = "android.alarmmanager.alarmtestapp.cts";
    private static final String INSTRUMENTATION_PACKAGE = "android.alarmmanager.cts";

    public static final String ACTION_REPORT_ALARM_EXPIRED = PACKAGE_NAME + ".action.ALARM_EXPIRED";
    public static final String EXTRA_ALARM_COUNT = PACKAGE_NAME + ".extra.ALARM_COUNT";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int count = intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 1);
        final long alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L);
        Log.d(TAG, "Alarm " + alarmId + " expired " + count + " times");

        final Intent reportAlarmIntent =
                new Intent(ACTION_REPORT_ALARM_EXPIRED)
                        .putExtra(EXTRA_ALARM_COUNT, count)
                        .putExtra(EXTRA_ALARM_ID, alarmId)
                        .setPackage(INSTRUMENTATION_PACKAGE)
                        .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        if (intent.getBooleanExtra(TestAlarmScheduler.EXTRA_TEST_FGS, false)) {
            final String result = FgsTester.tryStartingFgs(context);
            Log.d(TAG, "FGS start result: " + result);
            reportAlarmIntent.putExtra(FgsTester.EXTRA_FGS_START_RESULT, result);
        }
        context.sendBroadcast(reportAlarmIntent);
    }

    static AlarmManager.OnAlarmListener createListener(long alarmId, Context context) {
        return () -> {
            final TestAlarmReceiver alarmReceiver = new TestAlarmReceiver();
            final Intent intent = new Intent();
            intent.putExtra(EXTRA_ALARM_ID, alarmId);
            alarmReceiver.onReceive(context, intent);
        };
    }
}
