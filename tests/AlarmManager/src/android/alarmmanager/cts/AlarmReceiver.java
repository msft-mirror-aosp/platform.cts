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

package android.alarmmanager.cts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.test.InstrumentationRegistry;

import java.io.File;
import java.util.function.LongConsumer;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = AlarmReceiver.class.getSimpleName();
    private static final String ALARM_ACTION = "android.alarmmanager.cts.ALARM";
    private static final String EXTRA_ALARM_ID = "android.alarmmanager.cts.extra.ALARM_ID";
    private static final String EXTRA_QUOTAED = "android.alarmmanager.cts.extra.QUOTAED";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    /** Package global history of quotaed alarms received -- useful in quota calculations */
    private static final PersistableEventHistory sHistory = new PersistableEventHistory(
            new File(sContext.getFilesDir(), "alarm-history.xml"));
    /** Listener alarms or older apps use a lower quota that is managed separately **/
    private static final PersistableEventHistory sCompatHistory = new PersistableEventHistory(
            new File(sContext.getFilesDir(), "alarm-compat-history.xml"));

    private static final PersistableEventHistory sListenerAwiHistory =
            new PersistableEventHistory(
                    new File(sContext.getFilesDir(), "alarm-listener-awi-history.xml"));

    private static Object sWaitLock = new Object();
    @GuardedBy("sWaitLock")
    private static int sLastAlarmId;

    static void onAlarm(int id, LongConsumer historyRecorder) {
        Log.d(TAG, "Alarm " + id + " received");

        historyRecorder.accept(SystemClock.elapsedRealtime());
        synchronized (sWaitLock) {
            sLastAlarmId = id;
            sWaitLock.notifyAll();
        }
    }

    /**
     * Creates an {@link AlarmManager.OnAlarmListener} that records its trigger time in the
     * appropriate history bucket for quota verification.
     *
     * @param id The unique identifier for this alarm.
     * @param isRelaxedQuota If true, the alarm trigger is recorded in the dedicated 'relaxed'
     *     history bucket (72/hr). If false, it uses the legacy compat bucket (7/hr).
     * @param quotaed If false, the alarm is treated as unrestricted and not recorded in any
     *     history.
     * @return A listener that logs delivery and notifies any threads waiting on the alarm ID.
     */
    static AlarmManager.OnAlarmListener createListener(
            int id, boolean isRelaxedQuota, boolean quotaed) {
        final LongConsumer historyRecorder;
        if (quotaed) {
            historyRecorder =
                    isRelaxedQuota
                            ? sListenerAwiHistory::recordLatestEvent
                            : sCompatHistory::recordLatestEvent;
        } else {
            historyRecorder = (t -> {});
        }
        return () -> onAlarm(id, historyRecorder);
    }

    static PendingIntent getAlarmSender(int id, boolean quotaed) {
        final Intent alarmAction = new Intent(ALARM_ACTION)
                .setClass(sContext, AlarmReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(EXTRA_ALARM_ID, id)
                .putExtra(EXTRA_QUOTAED, quotaed);
        return PendingIntent.getBroadcast(sContext, 0, alarmAction,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ALARM_ACTION.equals(intent.getAction())) {
            final int id = intent.getIntExtra(EXTRA_ALARM_ID, -1);
            final boolean quotaed = intent.getBooleanExtra(EXTRA_QUOTAED, false);
            onAlarm(id, quotaed ? sHistory::recordLatestEvent : (t -> {}));
        }
    }

    static long getNthLastAlarmTime(int n) {
        return sHistory.getNthLastEventTime(n);
    }

    static long getNthLastCompatAlarmTime(int n) {
        return sCompatHistory.getNthLastEventTime(n);
    }

    /**
     * Returns the trigger time of the n-th last listener-based Allow-While-Idle (Awi) alarm. This
     * method queries the history bucket for relaxed listener alarms to help calculate quota
     * replenishment windows.
     *
     * @param n The 1-based index of the historical alarm event to retrieve (1 is the latest).
     * @return The elapsed realtime in milliseconds when the alarm was received by the app.
     */
    static long getNthLastListenerAwiAlarmTime(int n) {
        return sListenerAwiHistory.getNthLastEventTime(n);
    }

    static boolean waitForAlarm(int alarmId, long timeOut) throws InterruptedException {
        final long deadline = SystemClock.elapsedRealtime() + timeOut;
        synchronized (sWaitLock) {
            while (sLastAlarmId != alarmId && SystemClock.elapsedRealtime() < deadline) {
                sWaitLock.wait(timeOut);
            }
            return sLastAlarmId == alarmId;
        }
    }

    /**
     * Used to dump debugging information when the test fails.
     */
    static void dumpState() {
        synchronized (sWaitLock) {
            Log.i(TAG, "Last alarm id: " + sLastAlarmId);
        }
        sHistory.dump("History of quotaed alarms");
        sCompatHistory.dump("History of quotaed compat alarms");
        sListenerAwiHistory.dump("History of quotaed listener alarms");
    }
}
