/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts.service;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.stubs.shared.LocalForegroundService.COMMAND_START_FOREGROUND;
import static android.app.stubs.shared.LocalForegroundService.COMMAND_START_FOREGROUND_DEFER_NOTIFICATION;
import static android.app.stubs.shared.LocalForegroundService.COMMAND_STOP_FOREGROUND_DETACH_NOTIFICATION;
import static android.app.stubs.shared.LocalForegroundService.COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.cts.CtsAppTestUtils;
import android.app.stubs.shared.IsolatedService;
import android.app.stubs.shared.LaunchpadActivity;
import android.app.stubs.shared.LaunchpadHelper;
import android.app.stubs.shared.LocalDeniedService;
import android.app.stubs.shared.LocalForegroundService;
import android.app.stubs.shared.LocalGrantedService;
import android.app.stubs.shared.LocalPhoneCallService;
import android.app.stubs.shared.LocalService;
import android.app.stubs.shared.LocalStoppedService;
import android.app.stubs.shared.NullService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.Presubmit;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.IBinderParcelable;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.nano.ActivityManagerServiceDumpProcessesProto;
import com.android.server.am.nano.ProcessRecordProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Presubmit
@RunWith(AndroidJUnit4.class)
public final class ServiceTest {
    private static final String TAG = "ServiceTest";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    private static final int STATE_START_1 = 0;
    private static final int STATE_START_2 = 1;
    private static final int STATE_START_3 = 2;
    private static final int STATE_UNBIND = 3;
    private static final int STATE_DESTROY = 4;
    private static final int STATE_REBIND = 5;
    private static final int STATE_UNBIND_ONLY = 6;
    private static final int STATE_STOP_SELF_SUCCESS_UNBIND = 6;
    private static final int DELAY = 5000;
    private static final String EXIST_CONN_TO_RECEIVE_SERVICE =
            "existing connection to receive service";
    private static final String EXIST_CONN_TO_LOSE_SERVICE = "existing connection to lose service";
    private static final String EXTERNAL_SERVICE_PACKAGE = "com.android.app2";
    private static final String EXTERNAL_SERVICE_COMPONENT =
            EXTERNAL_SERVICE_PACKAGE + "/android.app.stubs.shared.LocalService";
    private static final String DELAYED_SERVICE_PACKAGE = "com.android.delayed_start";
    private static final String DELAYED_SERVICE_COMPONENT =
            DELAYED_SERVICE_PACKAGE + "/android.app.stubs.shared.LocalService";
    private static final String APP_ZYGOTE_PROCESS_NAME = "android.app.stubs_zygote";
    private static final String KEY_MAX_SERVICE_CONNECTIONS_PER_PROCESS =
            "max_service_connections_per_process";
    private int mExpectedServiceState;
    private Context mContext;
    private Intent mLocalService;
    private Intent mLocalDeniedService;
    private Intent mLocalForegroundService;
    private Intent mLocalPhoneCallService;
    private Intent mLocalGrantedService;
    private Intent mLocalService_ApplicationHasPermission;
    private Intent mLocalService_ApplicationDoesNotHavePermission;
    private Intent mIsolatedService;
    private Intent mExternalService;
    private Intent mDelayedService;
    private Executor mContextMainExecutor;
    private HandlerThread mBackgroundThread;
    private Executor mBackgroundThreadExecutor;

    private IBinder mStateReceiver;
    private LaunchpadHelper mLaunchpadHelper;

    private static final class EmptyConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    }

    private static final class NullServiceConnection implements ServiceConnection {
        boolean mNullBinding = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}

        @Override
        public void onNullBinding(ComponentName name) {
            synchronized (this) {
                mNullBinding = true;
                this.notifyAll();
            }
        }

        public void waitForNullBinding(final long timeout) {
            long now = SystemClock.uptimeMillis();
            final long end = now + timeout;
            synchronized (this) {
                while (!mNullBinding && (now < end)) {
                    try {
                        this.wait(end - now);
                    } catch (InterruptedException e) {
                        // Expected
                    }
                    now = SystemClock.uptimeMillis();
                }
            }
        }

        public boolean nullBindingReceived() {
            synchronized (this) {
                return mNullBinding;
            }
        }
    }

    private static final class LatchedConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        LatchedConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    }

    private class TestConnection implements ServiceConnection {
        private final boolean mExpectDisconnect;
        private final boolean mSetReporter;
        private boolean mMonitor;
        private int mCount;
        private Thread mOnServiceConnectedThread;

        TestConnection(boolean expectDisconnect, boolean setReporter) {
            mExpectDisconnect = expectDisconnect;
            mSetReporter = setReporter;
            mMonitor = !setReporter;
        }

        void setMonitor(boolean v) {
            mMonitor = v;
        }

        public Thread getOnServiceConnectedThread() {
            return mOnServiceConnectedThread;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mOnServiceConnectedThread = Thread.currentThread();
            if (mSetReporter) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
                data.writeStrongBinder(mStateReceiver);
                try {
                    service.transact(LocalService.SET_REPORTER_CODE, data, null, 0);
                } catch (RemoteException e) {
                    mLaunchpadHelper.finishBad("DeadObjectException when sending reporting object");
                }
                data.recycle();
            }

            if (mMonitor) {
                mCount++;
                if (mExpectedServiceState == STATE_START_1) {
                    if (mCount == 1) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onServiceConnected() again on an object when it "
                                        + "should have been the first time");
                    }
                } else if (mExpectedServiceState == STATE_START_2) {
                    if (mCount == 2) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onServiceConnected() the first time on an object "
                                        + "when it should have been the second time");
                    }
                } else {
                    mLaunchpadHelper.finishBad("onServiceConnected() called unexpectedly");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mMonitor) {
                if (mExpectedServiceState == STATE_DESTROY) {
                    if (mExpectDisconnect) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onServiceDisconnected() when it shouldn't have been");
                    }
                } else {
                    mLaunchpadHelper.finishBad("onServiceDisconnected() called unexpectedly");
                }
            }
        }
    }

    private final class TestStopSelfConnection extends TestConnection {
        private IBinder mService;

        TestStopSelfConnection() {
            super(false /* expectDisconnect */, true /* setReporter */);
        }

        private void executeTransact(int code) {
            Parcel data = Parcel.obtain();
            data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
            try {
                mService.transact(code, data, null /* reply */, 0);
            } catch (RemoteException e) {
                mLaunchpadHelper.finishBad("DeadObjectException when sending reporting object");
            }
            data.recycle();
        }

        public void stopSelf() {
            executeTransact(LocalService.STOP_SELF_CODE);
        }

        public void stopSelfResult() {
            executeTransact(LocalService.STOP_SELF_RESULT_CODE);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = service;
            super.onServiceConnected(name, service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                mService = null;
            }
        }
    }

    private final class IsolatedConnection implements ServiceConnection {
        private IBinder mService;
        private int mUid;
        private int mPid;
        private int mPpid;
        private Thread mOnServiceConnectedThread;

        IsolatedConnection() {
            mUid = mPid = -1;
        }

        public void waitForService(int timeoutMs) {
            final long endTime = System.currentTimeMillis() + timeoutMs;

            boolean timeout = false;
            synchronized (this) {
                while (mService == null) {
                    final long delay = endTime - System.currentTimeMillis();
                    if (delay < 0) {
                        timeout = true;
                        break;
                    }

                    try {
                        wait(delay);
                    } catch (final java.lang.InterruptedException e) {
                        // do nothing
                    }
                }
            }

            if (timeout) {
                throw new RuntimeException("Timed out waiting for connection");
            }
        }

        public int getUid() {
            return mUid;
        }

        public int getPid() {
            return mPid;
        }

        public int getPpid() {
            return mPpid;
        }

        public boolean zygotePreloadCalled() {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
            try {
                mService.transact(LocalService.GET_ZYGOTE_PRELOAD_CALLED, data, reply, 0);
            } catch (RemoteException e) {
                mLaunchpadHelper.finishBad("DeadObjectException when sending reporting object");
            }
            boolean value = reply.readBoolean();
            reply.recycle();
            data.recycle();
            return value;
        }

        public void setValue(int value) {
            Parcel data = Parcel.obtain();
            data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
            data.writeInt(value);
            try {
                mService.transact(LocalService.SET_VALUE_CODE, data, null, 0);
            } catch (RemoteException e) {
                mLaunchpadHelper.finishBad("DeadObjectException when sending reporting object");
            }
            data.recycle();
        }

        public int getValue(int transactCode) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
            try {
                mService.transact(transactCode, data, reply, 0);
            } catch (RemoteException e) {
                mLaunchpadHelper.finishBad("DeadObjectException when sending reporting object");
            }
            int value = reply.readInt();
            reply.recycle();
            data.recycle();
            return value;
        }

        public int getValue() {
            return getValue(LocalService.GET_VALUE_CODE);
        }

        public int getPidIpc() {
            return getValue(LocalService.GET_PID_CODE);
        }

        public int getPpidIpc() {
            return getValue(LocalService.GET_PPID_CODE);
        }

        public int getUidIpc() {
            return getValue(LocalService.GET_UID_CODE);
        }

        public Thread getOnServiceConnectedThread() {
            return mOnServiceConnectedThread;
        }

        public int getOnCreateCalledCount() {
            return getValue(LocalService.GET_ON_CREATE_CALLED_COUNT);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mOnServiceConnectedThread = Thread.currentThread();
                mService = service;
                mUid = getUidIpc();
                mPid = getPidIpc();
                mPpid = getPpidIpc();
                notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                mService = null;
            }
        }
    }

    private byte[] executeShellCommand(String cmd) {
        try {
            ParcelFileDescriptor pfd =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand(cmd);
            byte[] buf = new byte[512];
            int bytesRead;
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                while ((bytesRead = fis.read(buf)) != -1) {
                    stdout.write(buf, 0, bytesRead);
                }
                return stdout.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ActivityManagerServiceDumpProcessesProto getActivityManagerProcesses() {
        byte[] dump = executeShellCommand("dumpsys activity --proto processes");
        try {
            return ActivityManagerServiceDumpProcessesProto.parseFrom(dump);
        } catch (InvalidProtocolBufferNanoException e) {
            throw new RuntimeException("Failed parsing proto", e);
        }
    }

    private void startExpectResult(Intent service) {
        startExpectResult(service, new Bundle());
    }

    private void startExpectResult(Intent service, Bundle bundle) {
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(mStateReceiver));

        boolean success = false;
        try {
            mExpectedServiceState = STATE_START_1;
            mContext.startService(new Intent(service).putExtras(bundle));
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            mExpectedServiceState = STATE_START_2;
            mContext.startService(new Intent(service).putExtras(bundle));
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start second time");
            success = true;
        } finally {
            if (!success) {
                mContext.stopService(service);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(service);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    private NotificationManager getNotificationManager() {
        return mContext.getSystemService(NotificationManager.class);
    }

    private void sendNotification(int id, String title) {
        Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(title)
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .build();
        getNotificationManager().notify(id, notification);
    }

    private void cancelNotification(int id) {
        getNotificationManager().cancel(id);
    }

    private void assertNotification(int id, String expectedTitle, boolean shouldHaveFgsFlag) {
        String packageName = mContext.getPackageName();
        String titleErrorMsg;
        String flagErrorMsg;
        int i = 0;
        while (true) {
            titleErrorMsg = null;
            flagErrorMsg = null;
            StatusBarNotification[] sbns = getNotificationManager().getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id && sbn.getPackageName().equals(packageName)) {
                    Notification n = sbn.getNotification();
                    // check title first to make sure the update has propagated
                    String actualTitle = n.extras.getString(Notification.EXTRA_TITLE);
                    if (expectedTitle.equals(actualTitle)) {
                        // make sure notification and service state is in sync
                        if (shouldHaveFgsFlag
                                == ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0)) {
                            // both title and flag matches.
                            return;
                        } else {
                            // title match, flag not match.
                            flagErrorMsg =
                                    String.format(
                                            "Wrong flag for notification #%d: " + " actual '%d'",
                                            id, n.flags);
                        }
                    } else {
                        // It's possible the notification hasn't been updated yet, so save the error
                        // message to only fail after retrying.
                        titleErrorMsg =
                                String.format(
                                        "Wrong title for notification #%d: "
                                                + "expected '%s', actual '%s'",
                                        id, expectedTitle, actualTitle);
                    }
                    // id and packageName are found, break now.
                    break;
                }
            }
            // allow two more retries.
            if (++i > 2) {
                break;
            }
            // Notification might not be rendered yet, wait and try again...
            SystemClock.sleep(DELAY); // 5 seconds delay
        }
        if (flagErrorMsg != null) {
            assertWithMessage(flagErrorMsg).fail();
        }
        if (titleErrorMsg != null) {
            assertWithMessage(titleErrorMsg).fail();
        }
        assertWithMessage("No notification with id " + id + " for package " + packageName).fail();
    }

    private void assertNoNotification(int id) {
        String packageName = mContext.getPackageName();
        StatusBarNotification found = null;
        for (int i = 1; i <= 2; i++) {
            found = null;
            StatusBarNotification[] sbns = getNotificationManager().getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id && sbn.getPackageName().equals(packageName)) {
                    found = sbn;
                    break;
                }
            }
            if (found != null) {
                // Notification might not be canceled yet, wait and try again...
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        assertWithMessage(
                        "Found notification with id "
                                + id
                                + " for package "
                                + packageName
                                + ": "
                                + found)
                .that(found)
                .isNull();
    }

    /**
     * test the service lifecycle, a service can be used in two ways:
     * 1  It can be started and allowed to run until someone stops it or it stops itself.
     *    In this mode, it's started by calling Context.startService()
     *    and stopped by calling Context.stopService().
     *    It can stop itself by calling Service.stopSelf() or Service.stopSelfResult().
     *    Only one stopService() call is needed to stop the service,
     *    no matter how many times startService() was called.
     * 2  It can be operated programmatically using an interface that it defines and exports.
     *    Clients establish a connection to the Service object
     *    and use that connection to call into the service.
     *    The connection is established by calling Context.bindService(),
     *    and is closed by calling Context.unbindService().
     *    Multiple clients can bind to the same service.
     *    If the service has not already been launched, bindService() can optionally launch it.
     */
    private void bindExpectResult(Intent service) {
        TestConnection conn = new TestConnection(true, false);
        TestConnection conn2 = new TestConnection(false, false);
        boolean success = false;
        try {
            // Expect to see the TestConnection connected.
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            // Expect to see the second TestConnection connected.
            mContext.bindService(service, conn2, 0);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "new connection to receive service");

            mContext.unbindService(conn2);
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.unbindService(conn2);
                mContext.stopService(service);
            }
        }

        // Expect to see the TestConnection disconnected.
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(service);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);

        conn = new TestConnection(true, true);
        success = false;
        try {
            // Expect to see the TestConnection connected.
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }

        // Expect to see the service unbind and then destroyed.
        conn.setMonitor(false);
        mExpectedServiceState = STATE_UNBIND;
        mContext.stopService(service);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);

        conn = new TestConnection(true, true);
        success = false;
        try {
            // Expect to see the TestConnection connected.
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }

        // Expect to see the service unbind but not destroyed.
        conn.setMonitor(false);
        mExpectedServiceState = STATE_UNBIND_ONLY;
        mContext.unbindService(conn);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "existing connection to unbind service");

        // Expect to see the service rebound.
        mExpectedServiceState = STATE_REBIND;
        mContext.bindService(service, conn, 0);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "existing connection to rebind service");

        // Expect to see the service unbind and then destroyed.
        mExpectedServiceState = STATE_UNBIND;
        mContext.stopService(service);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);
    }

    /**
     * test automatically create the service as long as the binding exists and disconnect from an
     * application service
     */
    private void bindAutoExpectResult(Intent service) {
        TestConnection conn = new TestConnection(false, true);
        boolean success = false;
        try {
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, Context.BIND_AUTO_CREATE);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "connection to start and receive service");
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
            }
        }
        mExpectedServiceState = STATE_UNBIND;
        mContext.unbindService(conn);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "disconnecting from service");
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLaunchpadHelper = new LaunchpadHelper(mContext);
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);
        mLocalService = new Intent(mContext, LocalService.class);
        mExternalService = new Intent();
        mDelayedService = new Intent();
        mExternalService.setComponent(
                ComponentName.unflattenFromString(EXTERNAL_SERVICE_COMPONENT));
        mDelayedService.setComponent(ComponentName.unflattenFromString(DELAYED_SERVICE_COMPONENT));
        mLocalForegroundService = new Intent(mContext, LocalForegroundService.class);
        mLocalPhoneCallService = new Intent(mContext, LocalPhoneCallService.class);
        mLocalPhoneCallService.putExtra(
                LocalForegroundService.EXTRA_FOREGROUND_SERVICE_TYPE,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        mLocalDeniedService = new Intent(mContext, LocalDeniedService.class);
        mLocalGrantedService = new Intent(mContext, LocalGrantedService.class);
        mLocalService_ApplicationHasPermission =
                new Intent(
                        LocalService.SERVICE_LOCAL_GRANTED,
                        null /*uri*/,
                        mContext,
                        LocalService.class);
        mLocalService_ApplicationDoesNotHavePermission =
                new Intent(
                        LocalService.SERVICE_LOCAL_DENIED,
                        null /*uri*/,
                        mContext,
                        LocalService.class);
        mIsolatedService = new Intent(mContext, IsolatedService.class);
        mStateReceiver = new MockBinder();
        getNotificationManager()
                .createNotificationChannel(
                        new NotificationChannel(
                                NOTIFICATION_CHANNEL_ID,
                                "name",
                                NotificationManager.IMPORTANCE_DEFAULT));
        mContextMainExecutor = mContext.getMainExecutor();
        executeShellCommand("cmd activity fgs-notification-rate-limit disable");
    }

    private void setupBackgroundThread() {
        HandlerThread thread = new HandlerThread("ServiceTestBackgroundThread");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        mBackgroundThread = thread;
        mBackgroundThreadExecutor = handler::post;
    }

    @After
    public void tearDown() throws Exception {
        executeShellCommand("cmd activity fgs-notification-rate-limit enable");
        getNotificationManager().deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        mContext.stopService(mLocalService);
        mContext.stopService(mLocalForegroundService);
        mContext.stopService(mLocalGrantedService);
        mContext.stopService(mLocalService_ApplicationHasPermission);
        mContext.stopService(mExternalService);
        mContext.stopService(mDelayedService);
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
        }
        mBackgroundThread = null;
        mBackgroundThreadExecutor = null;
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mContext.getSystemService(PermissionManager.class)
                                .revokePostNotificationPermissionWithoutKillForTest(
                                        mContext.getPackageName(),
                                        Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
    }

    private final class MockBinder extends Binder {
        @Override
        protected boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == LocalService.STARTED_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                int count = data.readInt();
                if (mExpectedServiceState == STATE_START_1) {
                    if (count == 1) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onStart() again on an object when it "
                                        + "should have been the first time");
                    }
                } else if (mExpectedServiceState == STATE_START_2) {
                    if (count == 2) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onStart() the first time on an object when it "
                                        + "should have been the second time");
                    }
                } else if (mExpectedServiceState == STATE_START_3) {
                    if (count == 3) {
                        mLaunchpadHelper.finishGood();
                    } else {
                        mLaunchpadHelper.finishBad(
                                "onStart() the first time on an object when it "
                                        + "should have been the third time");
                    }
                } else {
                    mLaunchpadHelper.finishBad(
                            "onStart() was called when not expected (state="
                                    + mExpectedServiceState
                                    + ")");
                }
                return true;
            } else if (code == LocalService.DESTROYED_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_DESTROY) {
                    mLaunchpadHelper.finishGood();
                } else {
                    mLaunchpadHelper.finishBad(
                            "onDestroy() was called when not expected (state="
                                    + mExpectedServiceState
                                    + ")");
                }
                return true;
            } else if (code == LocalService.UNBIND_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_UNBIND) {
                    mExpectedServiceState = STATE_DESTROY;
                } else if (mExpectedServiceState == STATE_UNBIND_ONLY) {
                    mLaunchpadHelper.finishGood();
                } else {
                    mLaunchpadHelper.finishBad(
                            "onUnbind() was called when not expected (state="
                                    + mExpectedServiceState
                                    + ")");
                }
                return true;
            } else if (code == LocalService.REBIND_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_REBIND) {
                    mLaunchpadHelper.finishGood();
                } else {
                    mLaunchpadHelper.finishBad(
                            "onRebind() was called when not expected (state="
                                    + mExpectedServiceState
                                    + ")");
                }
                return true;
            } else if (code == LocalService.STOP_SELF_SUCCESS_UNBIND_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_STOP_SELF_SUCCESS_UNBIND) {
                    mLaunchpadHelper.finishGood();
                } else {
                    mLaunchpadHelper.finishBad(
                            "onUnbind() was called when not expected (state="
                                    + mExpectedServiceState
                                    + ")");
                }
                return true;
            } else {
                return super.onTransact(code, data, reply, flags);
            }
        }
    }

    @Test
    public void testStopSelf() {
        TestStopSelfConnection conn = new TestStopSelfConnection();
        boolean success = false;
        final Intent service = new Intent(mContext, LocalStoppedService.class);
        try {
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }
        // Expect to see the service unbind and then destroyed.
        mExpectedServiceState = STATE_UNBIND;
        conn.stopSelf();
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);
    }

    @Test
    public void testStopSelfResult() {
        TestStopSelfConnection conn = new TestStopSelfConnection();
        boolean success = false;
        final Intent service = new Intent(mContext, LocalStoppedService.class);
        try {
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }
        // Expect to see the service unbind and then destroyed.
        mExpectedServiceState = STATE_STOP_SELF_SUCCESS_UNBIND;
        conn.stopSelfResult();
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);
    }

    @Test
    public void testLocalStartClass() {
        startExpectResult(mLocalService);
    }

    @Test
    public void testLocalStartAction() {
        startExpectResult(
                new Intent(LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    @Test
    public void testLocalBindClass() {
        bindExpectResult(mLocalService);
    }

    @Test
    public void testBindServiceWithExecutor() {
        setupBackgroundThread();

        TestConnection conn = new TestConnection(true, false);
        mExpectedServiceState = STATE_START_1;
        mContext.bindService(
                mLocalService, Context.BIND_AUTO_CREATE, mBackgroundThreadExecutor, conn);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);
        assertThat(conn.getOnServiceConnectedThread()).isEqualTo(mBackgroundThread);

        mContext.unbindService(conn);
    }

    private Intent foregroundServiceIntent(Intent intent, int command) {
        return new Intent(intent)
                .putExtras(LocalForegroundService.newCommand(mStateReceiver, command));
    }

    /* Just the Intent for a foreground service */
    private Intent foregroundServiceIntent(int command) {
        return foregroundServiceIntent(mLocalForegroundService, command);
    }

    private void startForegroundService(Intent intent, int command) {
        mContext.startService(foregroundServiceIntent(intent, command));
    }

    private void startForegroundService(int command) {
        mContext.startService(foregroundServiceIntent(command));
    }

    @MediumTest
    @Test
    public void testForegroundService_canUpdateNotification() throws Exception {
        boolean success = false;
        try {
            // Start service as foreground - it should show notification #1
            mExpectedServiceState = STATE_START_1;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

            // Sends another notification reusing the same notification id.
            String newTitle = "YODA I AM";
            sendNotification(1, newTitle);
            assertNotification(1, newTitle, true);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
        assertNoNotification(1);
    }

    @MediumTest
    @Test
    public void testForegroundService_dontRemoveNotificationOnStop() {
        boolean success = false;
        try {
            // Start service as foreground - it should show notification #1
            mExpectedServiceState = STATE_START_1;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

            // Stop foreground without removing notification - it should still show notification #1
            mExpectedServiceState = STATE_START_2;
            startForegroundService(COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), false);

            // Sends another notification reusing the same notification id.
            String newTitle = "YODA I AM";
            sendNotification(1, newTitle);
            assertNotification(1, newTitle, false);

            // Start service as foreground again - it should kill notification #1 and show #2
            mExpectedServiceState = STATE_START_3;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start foreground 2nd time");
            assertNoNotification(1);
            assertNotification(2, LocalForegroundService.getNotificationTitle(2), true);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
        assertNoNotification(1);
        assertNoNotification(2);
    }

    @MediumTest
    @Test
    public void testForegroundService_removeNotificationOnStop() throws Exception {
        testForegroundServiceRemoveNotificationOnStop(false);
    }

    @MediumTest
    @Test
    public void testForegroundService_removeNotificationOnStopUsingFlags() throws Exception {
        testForegroundServiceRemoveNotificationOnStop(true);
    }

    private void testForegroundServiceRemoveNotificationOnStop(boolean usingFlags)
            throws Exception {
        boolean success = false;
        try {
            // Start service as foreground - it should show notification #1
            Log.d(TAG, "Expecting first start state...");
            mExpectedServiceState = STATE_START_1;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

            // Stop foreground removing notification
            Log.d(TAG, "Expecting second start state...");
            mExpectedServiceState = STATE_START_2;
            if (usingFlags) {
                startForegroundService(
                        LocalForegroundService
                                .COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION_USING_FLAGS);
            } else {
                startForegroundService(
                        LocalForegroundService.COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION);
            }
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNoNotification(1);

            // Start service as foreground again - it should show notification #2
            mExpectedServiceState = STATE_START_3;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start as foreground 2nd time");
            assertNotification(2, LocalForegroundService.getNotificationTitle(2), true);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
        assertNoNotification(1);
        assertNoNotification(2);
    }

    @FlakyTest
    @Test
    public void testRunningServices() throws Exception {
        final int maxReturnedServices = 10;
        final Bundle bundle = new Bundle();
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(mStateReceiver));

        boolean success = false;

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        // Put target app on whitelist so we can start its services.
        SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist +" + EXTERNAL_SERVICE_PACKAGE);

        // No services should be reported back at the beginning
        assertThat(am.getRunningServices(maxReturnedServices)).isEmpty();
        try {
            mExpectedServiceState = STATE_START_1;
            // Start external service.
            mContext.startService(new Intent(mExternalService).putExtras(bundle));
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "external service to start first time");

            // Ensure we can't see service.
            assertThat(am.getRunningServices(maxReturnedServices)).isEmpty();

            // Start local service.
            mContext.startService(new Intent(mLocalService).putExtras(bundle));
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "local service to start first time");
            success = true;

            // Ensure we can see service and it is ours.
            List<ActivityManager.RunningServiceInfo> services =
                    am.getRunningServices(maxReturnedServices);
            assertThat(services).hasSize(1);
            assertThat(services.get(0).uid).isEqualTo(android.os.Process.myUid());
        } finally {
            SystemUtil.runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    "cmd deviceidle whitelist -" + EXTERNAL_SERVICE_PACKAGE);
            if (!success) {
                mContext.stopService(mLocalService);
                mContext.stopService(mExternalService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;

        mContext.stopService(mExternalService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "external service to be destroyed");

        mContext.stopService(mLocalService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "local service to be destroyed");

        // Once our service has stopped, make sure we can't see any services.
        assertThat(am.getRunningServices(maxReturnedServices)).isEmpty();
    }

    @MediumTest
    @Test
    public void testForegroundService_detachNotificationOnStop() {
        String newTitle;
        boolean success = false;
        try {

            // Start service as foreground - it should show notification #1
            mExpectedServiceState = STATE_START_1;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

            // Detaching notification
            mExpectedServiceState = STATE_START_2;
            startForegroundService(COMMAND_STOP_FOREGROUND_DETACH_NOTIFICATION);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1), false);

            // Sends another notification reusing the same notification id.
            newTitle = "YODA I AM";
            sendNotification(1, newTitle);
            assertNotification(1, newTitle, false);

            // Start service as foreground again - it should show notification #2..
            mExpectedServiceState = STATE_START_3;
            startForegroundService(COMMAND_START_FOREGROUND);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start as foreground 2nd time");
            assertNotification(2, LocalForegroundService.getNotificationTitle(2), true);
            // ...but keeping notification #1
            assertNotification(1, newTitle, false);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
        if (newTitle == null) {
            assertNoNotification(1);
        } else {
            assertNotification(1, newTitle, false);
            cancelNotification(1);
            assertNoNotification(1);
        }
        assertNoNotification(2);
    }

    @Test
    public void testForegroundService_notificationChannelDeletion() {
        NotificationManager noMan = mContext.getSystemService(NotificationManager.class);

        // Start service as foreground - it should show notification #1
        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
        assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

        try {
            final String channel = LocalForegroundService.NOTIFICATION_CHANNEL_ID;
            noMan.deleteNotificationChannel(channel);
            assertWithMessage("Deleting FGS notification channel did not throw").fail();
        } catch (SecurityException se) {
            // Expected outcome, i.e. success case
        } catch (Exception e) {
            assertWithMessage("Deleting FGS notification threw unexpected failure " + e).fail();
        }

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_deferredNotificationChannelDeletion() {
        NotificationManager noMan = mContext.getSystemService(NotificationManager.class);

        // Start service as foreground - it should show notification #1
        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
        assertNoNotification(1);

        try {
            final String channel = LocalForegroundService.NOTIFICATION_CHANNEL_ID;
            noMan.deleteNotificationChannel(channel);
            assertWithMessage("Deleting FGS deferred notification channel did not throw").fail();
        } catch (SecurityException se) {
            // Expected outcome
        } catch (Exception e) {
            assertWithMessage("Deleting deferred FGS notification threw unexpected failure " + e)
                    .fail();
        }

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_typeImmediateNotification() {
        // expect that an FGS with phoneCall type has its notification displayed
        // immediately even without explicit request by the app
        mExpectedServiceState = STATE_START_1;
        startForegroundService(mLocalPhoneCallService, COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "phoneCall service to start");
        assertNotification(1, LocalPhoneCallService.getNotificationTitle(1), true);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalPhoneCallService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    private void waitMillis(long timeMillis) {
        final long stopTime = SystemClock.uptimeMillis() + timeMillis;
        while (SystemClock.uptimeMillis() < stopTime) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                /* ignore */
            }
        }
    }

    @Test
    public void testForegroundService_deferredNotification() {
        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start with deferred notification");
        assertNoNotification(1);

        // Wait ten seconds and verify that the notification is now visible
        waitMillis(10_000L);
        assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_deferredExistingNotification() throws Exception {
        // First, post the notification outright as not-FGS-related
        final NotificationManager nm = getNotificationManager();
        final String channelId = LocalForegroundService.getNotificationChannelId();
        nm.createNotificationChannel(
                new NotificationChannel(
                        channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder =
                new Notification.Builder(mContext, channelId)
                        .setContentTitle("Before FGS")
                        .setSmallIcon(android.R.drawable.sym_def_app_icon);
        nm.notify(1, builder.build());

        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start with existing notification");

        // Normally deferred but should display immediately because the notification
        // was already showing
        assertNotification(1, LocalForegroundService.getNotificationTitle(1), true);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_deferThenImmediateNotify() {
        final String notificationTitle = "deferThenImmediateNotify";

        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start with deferred notification");
        assertNoNotification(1);

        // Explicitly post a new Notification with the same id, still deferrable
        final NotificationManager nm = getNotificationManager();
        final String channelId = LocalForegroundService.getNotificationChannelId();
        nm.createNotificationChannel(
                new NotificationChannel(
                        channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder =
                new Notification.Builder(mContext, channelId)
                        .setContentTitle(notificationTitle)
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        nm.notify(1, builder.build());

        // Verify that the notification is immediately shown with the new content
        assertNotification(1, notificationTitle, true);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_deferThenDeferrableNotify() {
        final String notificationTitle = "deferThenDeferrableNotify";

        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start with deferred notification");
        // Pause a moment and ensure that the notification has still not appeared
        waitMillis(1000L);
        assertNoNotification(1);

        // Explicitly post a new Notification with the same id, still deferrable
        final NotificationManager nm = getNotificationManager();
        final String channelId = LocalForegroundService.getNotificationChannelId();
        nm.createNotificationChannel(
                new NotificationChannel(
                        channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder =
                new Notification.Builder(mContext, channelId)
                        .setContentTitle(notificationTitle)
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED);
        nm.notify(1, builder.build());

        // Normally would have displayed, but should only have been taken as the eventual
        // deferred notification.  Verify that it isn't shown yet, then re-verify after
        // the ten second deferral period that it's both visible and has the correct
        // (updated) title.
        assertNoNotification(1);
        waitMillis(10_000L);
        assertNotification(1, notificationTitle, true);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    @Test
    public void testForegroundService_deferThenKeepNotification() {
        // Start FGS with deferred notification; it should not display
        mExpectedServiceState = STATE_START_1;
        startForegroundService(COMMAND_START_FOREGROUND_DEFER_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
        assertNoNotification(1);

        // Exit foreground but keep notification - it should display immediately
        mExpectedServiceState = STATE_START_2;
        startForegroundService(COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to stop foreground");
        assertNotification(1, LocalForegroundService.getNotificationTitle(1), false);

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    private static final class TestSendCallback implements PendingIntent.OnFinished {
        public volatile int result = -1;

        @Override
        public void onSendFinished(
                PendingIntent pendingIntent,
                Intent intent,
                int resultCode,
                String resultData,
                Bundle resultExtras) {
            Log.i(TAG, "foreground service PendingIntent callback got " + resultCode);
            this.result = resultCode;
        }
    }

    @MediumTest
    @Test
    public void testForegroundService_pendingIntentForeground() throws Exception {
        boolean success = false;

        PendingIntent pi =
                PendingIntent.getForegroundService(
                        mContext,
                        1,
                        foregroundServiceIntent(COMMAND_START_FOREGROUND),
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        TestSendCallback callback = new TestSendCallback();

        try {
            mExpectedServiceState = STATE_START_1;
            pi.send(5038, callback, null);
            mLaunchpadHelper.waitForResultOrThrow(DELAY, "service to start first time");
            assertThat(callback.result).isGreaterThan(-1);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        mLaunchpadHelper.waitForResultOrThrow(DELAY, "pendingintent service to be destroyed");
    }

    @MediumTest
    @Test
    public void testLocalBindAction() {
        bindExpectResult(
                new Intent(LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    @MediumTest
    @Test
    public void testLocalBindAutoClass() {
        bindAutoExpectResult(mLocalService);
    }

    @MediumTest
    @Test
    public void testLocalBindAutoAction() throws Exception {
        bindAutoExpectResult(
                new Intent(LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    @MediumTest
    @Test
    public void testLocalStartClassPermissions() {
        startExpectResult(mLocalGrantedService);
        startExpectResult(mLocalDeniedService);
    }

    @MediumTest
    @Test
    public void testLocalStartActionPermissions() {
        startExpectResult(mLocalService_ApplicationHasPermission);
        startExpectResult(mLocalService_ApplicationDoesNotHavePermission);
    }

    @MediumTest
    @Test
    public void testLocalBindClassPermissions() {
        bindExpectResult(mLocalGrantedService);
        bindExpectResult(mLocalDeniedService);
    }

    @MediumTest
    @Test
    public void testLocalBindActionPermissions() {
        bindExpectResult(mLocalService_ApplicationHasPermission);
        bindExpectResult(mLocalService_ApplicationDoesNotHavePermission);
    }

    @MediumTest
    @Test
    public void testLocalBindAutoClassPermissionGranted() throws Exception {
        bindAutoExpectResult(mLocalGrantedService);
    }

    @MediumTest
    @Test
    public void testLocalBindAutoActionPermissionGranted() throws Exception {
        bindAutoExpectResult(mLocalService_ApplicationHasPermission);
    }

    @MediumTest
    @Test
    public void testLocalUnbindTwice() throws Exception {
        EmptyConnection conn = new EmptyConnection();
        mContext.bindService(mLocalService_ApplicationHasPermission, conn, 0);
        mContext.unbindService(conn);
        try {
            mContext.unbindService(conn);
            assertWithMessage("No exception thrown on the second unbind").fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @MediumTest
    @Test
    public void testImplicitIntentFailsOnApiLevel21() {
        Intent intent = new Intent(LocalService.SERVICE_LOCAL);
        EmptyConnection conn = new EmptyConnection();
        try {
            mContext.bindService(intent, conn, 0);
            mContext.unbindService(conn);
            assertWithMessage("Implicit intents should be disallowed for apps targeting API 21+")
                    .fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Verify that when the requested service's onBind() returns null, the connection's
     * onNullBinding() method is invoked.
     */
    @MediumTest
    @Test
    public void testNullServiceBinder() {
        Intent intent = new Intent(mContext, NullService.class);
        intent.setAction("testNullServiceBinder");
        NullServiceConnection conn1 = new NullServiceConnection();
        NullServiceConnection conn2 = new NullServiceConnection();
        try {
            assertThat(mContext.bindService(intent, conn1, Context.BIND_AUTO_CREATE)).isTrue();
            conn1.waitForNullBinding(DELAY);
            assertThat(conn1.nullBindingReceived()).isTrue();

            assertThat(mContext.bindService(intent, conn2, Context.BIND_AUTO_CREATE)).isTrue();
            conn2.waitForNullBinding(DELAY);
            assertThat(conn2.nullBindingReceived()).isTrue();
        } finally {
            mContext.unbindService(conn1);
            mContext.unbindService(conn2);
        }
    }

    /** Verify that we can't use bindIsolatedService() on a non-isolated service. */
    @MediumTest
    @Test
    public void testFailBindNonIsolatedService() {
        EmptyConnection conn = new EmptyConnection();
        try {
            mContext.bindIsolatedService(mLocalService, 0, "isolated", mContextMainExecutor, conn);
            mContext.unbindService(conn);
            assertWithMessage("Didn't get IllegalArgumentException").fail();
        } catch (IllegalArgumentException e) {
            // This is expected.
        }
    }

    /** Verify that certain characters are prohibited in instanceName. */
    @Test
    public void testFailBindIsoaltedServiceWithInvalidInstanceName() {
        String[] badNames = {
            "t\rest", "test\n", "test-three", "test four", "escape\u00a9seq", "\u0164est",
        };
        for (String instanceName : badNames) {
            EmptyConnection conn = new EmptyConnection();
            try {
                mContext.bindIsolatedService(
                        mIsolatedService,
                        Context.BIND_AUTO_CREATE,
                        instanceName,
                        mContextMainExecutor,
                        conn);
                mContext.unbindService(conn);
                assertWithMessage("Didn't get IllegalArgumentException: " + instanceName).fail();
            } catch (IllegalArgumentException e) {
                // This is expected.
            }
        }
    }

    /**
     * Verify that bindIsolatedService() correctly makes different instances when given different
     * instance names.
     */
    @MediumTest
    @Test
    public void testBindIsolatedServiceInstances() {
        IsolatedConnection conn1a = null;
        IsolatedConnection conn1b = null;
        IsolatedConnection conn2 = null;
        try {
            conn1a = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn1a);
            conn1b = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn1b);
            conn2 = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "2", mContextMainExecutor, conn2);

            conn1a.waitForService(DELAY);
            conn1b.waitForService(DELAY);
            conn2.waitForService(DELAY);

            if (conn1a.getPid() != conn1b.getPid()) {
                assertWithMessage("Connections to same service name in different pids").fail();
            }
            if (conn1a.getPid() == conn2.getPid()) {
                assertWithMessage("Connections to different service names in same pids").fail();
            }

            conn1a.setValue(1);
            assertThat(conn1a.getValue()).isEqualTo(1);
            assertThat(conn1b.getValue()).isEqualTo(1);

            conn2.setValue(2);
            assertThat(conn1a.getValue()).isEqualTo(1);
            assertThat(conn1b.getValue()).isEqualTo(1);
            assertThat(conn2.getValue()).isEqualTo(2);

            conn1b.setValue(3);
            assertThat(conn1a.getValue()).isEqualTo(3);
            assertThat(conn1b.getValue()).isEqualTo(3);
            assertThat(conn2.getValue()).isEqualTo(2);
        } finally {
            if (conn2 != null) {
                mContext.unbindService(conn2);
            }
            if (conn1b != null) {
                mContext.unbindService(conn1b);
            }
            if (conn1a != null) {
                mContext.unbindService(conn1a);
            }
        }
    }

    @MediumTest
    @Test
    public void testOnCreateCalledOnce_bindService() {
        IsolatedConnection conn = null;

        try {
            conn = new IsolatedConnection();
            mContext.bindService(
                    mDelayedService, Context.BIND_AUTO_CREATE, mContextMainExecutor, conn);

            // Wait for app to be executing bindApplication
            SystemClock.sleep(1000);

            mContext.bindService(
                    mDelayedService, Context.BIND_AUTO_CREATE, mContextMainExecutor, conn);

            conn.waitForService(DELAY);

            assertThat(conn.getOnCreateCalledCount()).isEqualTo(1);
        } finally {
            if (conn != null) {
                mContext.unbindService(conn);
            }
        }
    }

    @Test
    public void testBindIsolatedServiceOnBackgroundThread() {
        setupBackgroundThread();
        IsolatedConnection conn = new IsolatedConnection();
        mContext.bindIsolatedService(
                mIsolatedService,
                Context.BIND_AUTO_CREATE,
                "background_instance",
                mBackgroundThreadExecutor,
                conn);
        conn.waitForService(DELAY);
        assertThat(conn.getOnServiceConnectedThread()).isEqualTo(mBackgroundThread);
        mContext.unbindService(conn);
    }

    static final int BINDING_WEAK = 0;
    static final int BINDING_STRONG = 1;
    static final int BINDING_ANY = -1;

    private final class IsolatedConnectionInfo {
        final int mStrong;
        final String mInstanceName;
        final String mLabel;
        int mGroup;
        int mImportance;
        IsolatedConnection mConnection;

        IsolatedConnectionInfo(int group, int importance, int strong) {
            this(group, importance, strong, "");
        }

        IsolatedConnectionInfo(int group, int importance, int strong, String instanceNamePostfix) {
            mGroup = group;
            mImportance = importance;
            mStrong = strong;
            mInstanceName = group + "_" + importance + instanceNamePostfix;
            StringBuilder b = new StringBuilder(mInstanceName);
            b.append('_');
            if (strong == BINDING_WEAK) {
                b.append('W');
            } else if (strong == BINDING_STRONG) {
                b.append('S');
            } else {
                b.append(strong);
            }
            mLabel = b.toString();
        }

        void setGroup(int group) {
            mGroup = group;
        }

        void setImportance(int importance) {
            mImportance = importance;
        }

        boolean match(int group, int strong) {
            return (group < 0 || mGroup == group) && (strong == BINDING_ANY || mStrong == strong);
        }

        boolean bind(Context context) {
            if (mConnection != null) {
                return true;
            }
            Log.i(TAG, "Binding " + mLabel + ": conn=" + mConnection + " context=" + context);
            mConnection = new IsolatedConnection();
            boolean result =
                    context.bindIsolatedService(
                            mIsolatedService,
                            Context.BIND_AUTO_CREATE
                                    | Context.BIND_DEBUG_UNBIND
                                    | (mStrong == BINDING_STRONG
                                            ? 0
                                            : Context.BIND_ALLOW_OOM_MANAGEMENT),
                            mInstanceName,
                            mContextMainExecutor,
                            mConnection);
            if (!result) {
                mConnection = null;
            }
            return result;
        }

        IsolatedConnection getConnection() {
            return mConnection;
        }

        void unbind(Context context) {
            if (mConnection != null) {
                Log.i(TAG, "Unbinding " + mLabel + ": conn=" + mConnection + " context=" + context);
                context.unbindService(mConnection);
                mConnection = null;
            }
        }
    }

    private static final class LruOrderItem {
        static final int FLAG_SKIP_UNKNOWN = 1 << 0;

        final IsolatedConnectionInfo mInfo;
        final int mUid;
        final int mFlags;

        LruOrderItem(IsolatedConnectionInfo info, int flags) {
            mInfo = info;
            mUid = -1;
            mFlags = flags;
        }

        LruOrderItem(int uid, int flags) {
            mInfo = null;
            mUid = uid;
            mFlags = flags;
        }

        int getUid() {
            return mInfo != null ? mInfo.getConnection().getUid() : mUid;
        }

        int getUserId() {
            return UserHandle.getUserHandleForUid(getUid()).getIdentifier();
        }

        int getAppId() {
            return UserHandle.getAppId(getUid());
        }

        boolean isEquivalentTo(ProcessRecordProto proc) {
            int procAppId =
                    proc.isolatedAppId != 0 ? proc.isolatedAppId : UserHandle.getAppId(proc.uid);

            // Compare appid and userid separately because UserHandle.getUid is @hide.
            return procAppId == getAppId() && proc.userId == getUserId();
        }

        int getFlags() {
            return mFlags;
        }
    }

    private void doBind(
            Context context, IsolatedConnectionInfo[] connections, int group, int strong) {
        for (IsolatedConnectionInfo ci : connections) {
            if (ci.match(group, strong)) {
                ci.bind(context);
            }
        }
    }

    private void doBind(Context context, IsolatedConnectionInfo[] connections, int[] selected) {
        for (int i : selected) {
            boolean result = connections[i].bind(context);
            if (!result) {
                assertWithMessage("Unable to bind connection " + connections[i].mLabel).fail();
            }
        }
    }

    private void doBindAndWaitForService(
            Context context, IsolatedConnectionInfo[] connections, int group, int strong) {
        for (IsolatedConnectionInfo ci : connections) {
            if (ci.match(group, strong)) {
                ci.bind(context);
                ci.mConnection.waitForService(DELAY);
            }
        }
    }

    private void doWaitForService(IsolatedConnectionInfo[] connections, int group, int strong) {
        for (IsolatedConnectionInfo ci : connections) {
            if (ci.match(group, strong)) {
                ci.mConnection.waitForService(DELAY);
            }
        }
    }

    private boolean doWaitWhile(BooleanSupplier condition, long pause, long timeout) {
        final long endTime = System.currentTimeMillis() + timeout;
        while (condition.getAsBoolean()) {
            if (System.currentTimeMillis() > endTime) {
                return false;
            }
            SystemClock.sleep(pause);
        }
        return true;
    }

    private void doUpdateServiceGroup(
            Context context, IsolatedConnectionInfo[] connections, int group, int strong) {
        for (IsolatedConnectionInfo ci : connections) {
            if (ci.match(group, strong)) {
                context.updateServiceGroup(ci.mConnection, ci.mGroup, ci.mImportance);
            }
        }
    }

    private void doUnbind(
            Context context, IsolatedConnectionInfo[] connections, int group, int strong) {
        for (IsolatedConnectionInfo ci : connections) {
            if (ci.match(group, strong)) {
                ci.unbind(context);
            }
        }
    }

    List<ProcessRecordProto> getLruProcesses() {
        ActivityManagerServiceDumpProcessesProto dump = getActivityManagerProcesses();
        SparseArray<ProcessRecordProto> procs = new SparseArray<>();
        ProcessRecordProto[] procsList = dump.procs;
        for (ProcessRecordProto proc : procsList) {
            procs.put(proc.lruIndex, proc);
        }
        ArrayList<ProcessRecordProto> lruProcs = new ArrayList<>();
        for (int i = 0; i < procs.size(); i++) {
            lruProcs.add(procs.valueAt(i));
        }
        return lruProcs;
    }

    String printProc(int i, ProcessRecordProto proc) {
        return "#"
                + i
                + ": "
                + proc.processName
                + " pid="
                + proc.pid
                + " uid="
                + proc.uid
                + (proc.isolatedAppId != 0 ? " isolated=" + proc.isolatedAppId : "");
    }

    private void logProc(int i, ProcessRecordProto proc) {
        Log.i(TAG, printProc(i, proc));
    }

    private void verifyLruOrder(LruOrderItem[] orderItems) {
        List<ProcessRecordProto> procs = getLruProcesses();
        Log.i(TAG, "Processes:");
        int orderI = 0;
        for (int i = procs.size() - 1; i >= 0; i--) {
            ProcessRecordProto proc = procs.get(i);
            logProc(i, proc);
            final LruOrderItem lru = orderItems[orderI];
            Log.i(TAG, "Expecting uid: " + lru.getUid());
            if (!lru.isEquivalentTo(proc)) {
                if ((lru.getFlags() & LruOrderItem.FLAG_SKIP_UNKNOWN) != 0) {
                    while (i > 0) {
                        i--;
                        proc = procs.get(i);
                        logProc(i, proc);
                        if (lru.isEquivalentTo(proc)) {
                            break;
                        }
                    }
                }
                if (!lru.isEquivalentTo(proc)) {
                    if ((lru.getFlags() & LruOrderItem.FLAG_SKIP_UNKNOWN) != 0) {
                        assertWithMessage("Didn't find expected LRU proc uid=" + lru.getUid())
                                .fail();
                    }
                    assertWithMessage(
                                    "Expected proc uid="
                                            + lru.getUid()
                                            + " at found proc "
                                            + printProc(i, proc))
                            .fail();
                }
            }
            orderI++;
            if (orderI >= orderItems.length) {
                return;
            }
        }
    }

    @MediumTest
    @Test
    public void testAppZygotePreload() {
        IsolatedConnection conn = new IsolatedConnection();
        try {
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn);

            conn.waitForService(DELAY);

            // Verify application preload was done
            assertThat(conn.zygotePreloadCalled()).isTrue();
        } finally {
            if (conn != null) {
                mContext.unbindService(conn);
            }
        }
    }

    @MediumTest
    @Test
    public void testAppZygoteServices() throws Exception {
        IsolatedConnection conn1a = null;
        IsolatedConnection conn1b = null;
        IsolatedConnection conn2 = null;
        int appZygotePid;
        try {
            conn1a = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn1a);
            conn1b = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn1b);
            conn2 = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "2", mContextMainExecutor, conn2);

            conn1a.waitForService(DELAY);
            conn1b.waitForService(DELAY);
            conn2.waitForService(DELAY);

            // Get PPID of each service, and verify they're identical
            int ppid1a = conn1a.getPpid();
            int ppid1b = conn1b.getPpid();
            int ppid2 = conn2.getPpid();

            assertThat(ppid1b).isEqualTo(ppid1a);
            assertThat(ppid2).isEqualTo(ppid1b);
            // Find the app zygote process hosting these
            String result =
                    SystemUtil.runShellCommand(
                            InstrumentationRegistry.getInstrumentation(),
                            "ps -p " + ppid1a + " -o NAME=");
            result = result.replaceAll("\\s+", "");
            assertThat(result).isEqualTo(APP_ZYGOTE_PROCESS_NAME);
            appZygotePid = ppid1a;
        } finally {
            if (conn2 != null) {
                mContext.unbindService(conn2);
            }
            if (conn1b != null) {
                mContext.unbindService(conn1b);
            }
            if (conn1a != null) {
                mContext.unbindService(conn1a);
            }
        }
        // Sleep for 2 seconds and bind a service again, see it uses the same Zygote
        try {
            conn1a = new IsolatedConnection();
            mContext.bindIsolatedService(
                    mIsolatedService, Context.BIND_AUTO_CREATE, "1", mContextMainExecutor, conn1a);

            conn1a.waitForService(DELAY);

            int ppid1a = conn1a.getPpid();
            assertThat(ppid1a).isEqualTo(appZygotePid);
        } finally {
            if (conn1a != null) {
                mContext.unbindService(conn1a);
            }
        }
        // Sleep for 10 seconds, verify the app_zygote is gone
        Thread.sleep(10000);
        String result =
                SystemUtil.runShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        "ps -p " + appZygotePid + " -o NAME=");
        result = result.replaceAll("\\s+", "");
        assertThat(result).isEmpty();
    }

    /** Test that the system properly orders processes bound by an activity within the LRU list. */
    // TODO(b/131059432): Re-enable the test after that bug is fixed.
    @FlakyTest
    @MediumTest
    @Test
    public void testActivityServiceBindingLru() {
        // Bring up the activity we will hang services off of.
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.ACTIVITY_PREPARE);
        final Activity a = mLaunchpadHelper.getRunningActivity();

        final int CONN_0_0_W_0 = 0;
        final int CONN_0_0_S_0 = 1;
        final int CONN_0_0_W_1 = 2;
        final int CONN_0_0_S_1 = 3;
        final int CONN_0_0_W_2 = 4;
        final int CONN_0_0_S_2 = 5;
        final int CONN_0_0_W_3 = 6;
        final int CONN_0_0_S_3 = 7;
        final int CONN_1_1_W = 8;
        final int CONN_1_1_S = 9;
        final int CONN_1_2_W = 10;
        final int CONN_1_2_S = 11;
        final int CONN_2_1_W = 12;
        final int CONN_2_1_S = 13;
        final int CONN_2_2_W = 14;
        final int CONN_2_2_S = 15;
        final int CONN_2_3_W = 16;
        final int CONN_2_3_S = 17;

        // We are going to have both weak and strong references to services, so we can allow
        // some to go down in the LRU list.
        final IsolatedConnectionInfo[] connections =
                new IsolatedConnectionInfo[] {
                    new IsolatedConnectionInfo(0, 0, BINDING_WEAK, "_w0"),
                    new IsolatedConnectionInfo(0, 0, BINDING_STRONG, "_s0"),
                    new IsolatedConnectionInfo(0, 0, BINDING_WEAK, "_w1"),
                    new IsolatedConnectionInfo(0, 0, BINDING_STRONG, "_s1"),
                    new IsolatedConnectionInfo(0, 0, BINDING_WEAK, "_w2"),
                    new IsolatedConnectionInfo(0, 0, BINDING_STRONG, "_s2"),
                    new IsolatedConnectionInfo(0, 0, BINDING_WEAK, "_w3"),
                    new IsolatedConnectionInfo(0, 0, BINDING_STRONG, "_s3"),
                    new IsolatedConnectionInfo(1, 1, BINDING_WEAK),
                    new IsolatedConnectionInfo(1, 1, BINDING_STRONG),
                    new IsolatedConnectionInfo(1, 2, BINDING_WEAK),
                    new IsolatedConnectionInfo(1, 2, BINDING_STRONG),
                    new IsolatedConnectionInfo(2, 1, BINDING_WEAK),
                    new IsolatedConnectionInfo(2, 1, BINDING_STRONG),
                    new IsolatedConnectionInfo(2, 2, BINDING_WEAK),
                    new IsolatedConnectionInfo(2, 2, BINDING_STRONG),
                    new IsolatedConnectionInfo(2, 3, BINDING_WEAK),
                    new IsolatedConnectionInfo(2, 3, BINDING_STRONG),
                };

        final int[] REV_GROUP_1_STRONG = new int[] {CONN_1_2_S, CONN_1_1_S};

        final int[] REV_GROUP_2_STRONG = new int[] {CONN_2_3_S, CONN_2_2_S, CONN_2_1_S};

        final int[] MIXED_GROUP_3_STRONG =
                new int[] {CONN_2_3_S, CONN_1_1_S, CONN_2_1_S, CONN_2_2_S};

        boolean passed = false;

        try {
            // Start the group 0 processes and wait for them to come up.
            doBindAndWaitForService(a, connections, 0, BINDING_ANY);

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_0_0_S_3], 0),
                        new LruOrderItem(connections[CONN_0_0_W_3], LruOrderItem.FLAG_SKIP_UNKNOWN),
                        new LruOrderItem(connections[CONN_0_0_S_2], 0),
                        new LruOrderItem(connections[CONN_0_0_W_2], 0),
                        new LruOrderItem(connections[CONN_0_0_S_1], 0),
                        new LruOrderItem(connections[CONN_0_0_W_1], 0),
                        new LruOrderItem(connections[CONN_0_0_S_0], 0),
                        new LruOrderItem(connections[CONN_0_0_W_0], 0),
                    });

            // Send the app to background.
            PackageManager pm = mContext.getPackageManager();
            if (pm.hasSystemFeature("android.software.car.splitscreen_multitasking")) {
                // Send the app to background by launching another activity of another process.
                // Don't call a.moveTaskToBack(true), as it doesn't necessarily change the task's
                // overall LRU status on builds featuring split-screen multitasking (b/410974806).
                Intent intent = new Intent();
                intent.setPackage("com.android.app1");
                intent.setAction("android.intent.action.SEARCH");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } else if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                // Send the app to background by launching another activity of another process.
                // It prevents the performance degradation or any stability issue on partner
                // devices(b/413155192)
                Intent intent = new Intent();
                intent.setPackage("com.android.app1");
                intent.setClassName(
                        "com.android.app1", "android.app.stubs.MockApplicationActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } else {
                assertWithMessage("Failed to send the app to background")
                        .that(a.moveTaskToBack(true))
                        .isTrue();
            }

            // TODO: b/372710412 - Call a test API to force recomputation, instead of doWaitWhile.
            assertWithMessage(
                            "App is still at the top of the LRU list after getting moved to"
                                    + " background")
                    .that(
                            doWaitWhile(
                                    () ->
                                            new LruOrderItem(Process.myUid(), 0)
                                                    .isEquivalentTo(getLruProcesses().getLast()),
                                    DELAY / 10,
                                    DELAY))
                    .isTrue();

            // bring the app back to foreground
            a.startActivity(a.getIntent());
            // TODO: b/372710412 - Call a test API to force recomputation, instead of doWaitWhile.
            assertWithMessage(
                            "App hasn't come to the top of LRU list after getting back to"
                                    + " foreground")
                    .that(
                            doWaitWhile(
                                    () ->
                                            !new LruOrderItem(Process.myUid(), 0)
                                                    .isEquivalentTo(getLruProcesses().getLast()),
                                    DELAY / 10,
                                    DELAY))
                    .isTrue();

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_0_0_S_3], 0),
                        new LruOrderItem(connections[CONN_0_0_S_2], 0),
                        new LruOrderItem(connections[CONN_0_0_S_1], 0),
                        new LruOrderItem(connections[CONN_0_0_S_0], 0),
                        new LruOrderItem(connections[CONN_0_0_W_3], LruOrderItem.FLAG_SKIP_UNKNOWN),
                        new LruOrderItem(connections[CONN_0_0_W_2], 0),
                        new LruOrderItem(connections[CONN_0_0_W_1], 0),
                        new LruOrderItem(connections[CONN_0_0_W_0], 0),
                    });

            // Stop the group 0 processes.
            doUnbind(a, connections, 0, BINDING_ANY);

            // Start the group 1 processes as weak.
            doBind(a, connections, 1, BINDING_WEAK);
            doUpdateServiceGroup(a, connections, 1, BINDING_WEAK);

            // Wait for them to come up.
            doWaitForService(connections, 1, BINDING_WEAK);

            // Now fully bind to the services.
            doBind(a, connections, 1, BINDING_STRONG);
            doWaitForService(connections, 1, BINDING_STRONG);

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_1_1_W], 0),
                        new LruOrderItem(connections[CONN_1_2_W], 0),
                    });

            // Now remove the full binding, leaving only the weak.
            doUnbind(a, connections, 1, BINDING_STRONG);

            // Start the group 2 processes as weak.
            doBind(a, connections, 2, BINDING_WEAK);

            // Wait for them to come up.
            doWaitForService(connections, 2, BINDING_WEAK);

            // Set the group and index.  In this case we do it after we know the process
            // is started, to make sure setting it directly works.
            doUpdateServiceGroup(a, connections, 2, BINDING_WEAK);

            // Now fully bind to group 2
            doBind(a, connections, REV_GROUP_2_STRONG);

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_2_1_W], 0),
                        new LruOrderItem(connections[CONN_2_2_W], 0),
                        new LruOrderItem(connections[CONN_2_3_W], 0),
                        new LruOrderItem(connections[CONN_1_1_W], LruOrderItem.FLAG_SKIP_UNKNOWN),
                        new LruOrderItem(connections[CONN_1_2_W], 0),
                    });

            // Bring group 1 back to the foreground, but in the opposite order.
            doBind(a, connections, REV_GROUP_1_STRONG);

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_1_1_W], 0),
                        new LruOrderItem(connections[CONN_1_2_W], 0),
                        new LruOrderItem(connections[CONN_2_1_W], LruOrderItem.FLAG_SKIP_UNKNOWN),
                        new LruOrderItem(connections[CONN_2_2_W], 0),
                        new LruOrderItem(connections[CONN_2_3_W], 0),
                    });

            // Now remove all full bindings, keeping only weak.
            doUnbind(a, connections, 1, BINDING_STRONG);
            doUnbind(a, connections, 2, BINDING_STRONG);

            // Change the grouping and importance to make sure that gets reflected.
            connections[CONN_1_1_W].setGroup(3);
            connections[CONN_1_1_W].setImportance(1);
            connections[CONN_2_1_W].setGroup(3);
            connections[CONN_2_1_W].setImportance(2);
            connections[CONN_2_2_W].setGroup(3);
            connections[CONN_2_2_W].setImportance(3);
            connections[CONN_2_3_W].setGroup(3);
            connections[CONN_2_3_W].setImportance(4);

            doUpdateServiceGroup(a, connections, 3, BINDING_WEAK);

            // Now bind them back up in an interesting order.
            doBind(a, connections, MIXED_GROUP_3_STRONG);

            verifyLruOrder(
                    new LruOrderItem[] {
                        new LruOrderItem(Process.myUid(), 0),
                        new LruOrderItem(connections[CONN_1_1_W], 0),
                        new LruOrderItem(connections[CONN_2_1_W], 0),
                        new LruOrderItem(connections[CONN_2_2_W], 0),
                        new LruOrderItem(connections[CONN_2_3_W], 0),
                        new LruOrderItem(connections[CONN_1_2_W], LruOrderItem.FLAG_SKIP_UNKNOWN),
                    });

            passed = true;

        } finally {
            if (!passed) {
                List<ProcessRecordProto> procs = getLruProcesses();
                Log.i(TAG, "Processes:");
                for (int i = procs.size() - 1; i >= 0; i--) {
                    ProcessRecordProto proc = procs.get(i);
                    logProc(i, proc);
                }
            }
            doUnbind(a, connections, -1, BINDING_ANY);
        }
    }

    /** Test per process's max outgoing bindService() service connections. */
    @FlakyTest(bugId = 329918252)
    @Test
    public void testMaxServiceConnections() throws Exception {
        final ArrayList<LatchedConnection> connections = new ArrayList<>();
        final int max = 1000;
        final int extra = 10;
        try (DeviceConfigStateHelper helper = new DeviceConfigStateHelper("activity_manager")) {
            helper.set(KEY_MAX_SERVICE_CONNECTIONS_PER_PROCESS, Integer.toString(max));
            // bindService() adds max number of ServiceConnections.
            for (int i = 0; i < max; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedConnection connection = new LatchedConnection(latch);
                connections.add(connection);
                assertThat(
                                mContext.bindService(
                                        mLocalService, connection, Context.BIND_AUTO_CREATE))
                        .isTrue();
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            }
            // bindService() adds "extra" number of ServiceConnections, it should fail.
            for (int i = 0; i < extra; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedConnection connection = new LatchedConnection(latch);
                assertThat(
                                mContext.bindService(
                                        mLocalService, connection, Context.BIND_AUTO_CREATE))
                        .isFalse();
            }
            // unbindService removes max/4 number of ServiceConnections.
            for (int i = 0; i < max / 4; ++i) {
                final LatchedConnection connection = connections.remove(0);
                mContext.unbindService(connection);
            }
            // bindService adds max/4 number of ServiceConnections.
            for (int i = 0; i < max / 4; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedConnection connection = new LatchedConnection(latch);
                connections.add(connection);
                assertThat(
                                mContext.bindService(
                                        mLocalService, connection, Context.BIND_AUTO_CREATE))
                        .isTrue();
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            for (ServiceConnection connection : connections) {
                mContext.unbindService(connection);
            }
        }
    }

    /** Test bindService() can accept long flags. */
    @Test
    public void testBindServiceLongFlags() throws Exception {
        long flags = Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT;
        final CountDownLatch latch = new CountDownLatch(1);
        final LatchedConnection connection = new LatchedConnection(latch);
        try {
            assertThat(
                            mContext.bindService(
                                    mLocalService, connection, Context.BindServiceFlags.of(flags)))
                    .isTrue();
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            final String dumpCommand =
                    "dumpsys activity services "
                            + "android.app.stubs"
                            + "/android.app.stubs.shared.LocalService";
            String[] dumpLines =
                    CtsAppTestUtils.executeShellCmd(
                                    InstrumentationRegistry.getInstrumentation(), dumpCommand)
                            .split("\n");
            assertThat(CtsAppTestUtils.findLine(dumpLines, "flags=0x" + Long.toHexString(flags)))
                    .isNotNull();
        } finally {
            mContext.unbindService(connection);
        }
    }

    @Test
    public void testLruWhenSwitchingBetweenAppsInFreeFormWindows() {
        assumeTrue("Multi window is not supported",
                ActivityTaskManager.supportsMultiWindow(mContext));

        Consumer<ComponentName> startActivityInFreeFormWindow = componentName -> {
            Intent intent = new Intent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(componentName.getPackageName(), componentName.getClassName());

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

            mContext.startActivity(intent, options.toBundle());
        };

        BooleanSupplier isApp1Top = () -> new LruOrderItem(Process.myUid(), 0)
                .isEquivalentTo(getLruProcesses().getLast());

        ComponentName app1 = new ComponentName("android.app.stubs",
                "android.app.stubs.shared.LaunchpadActivity");
        ComponentName app2 = new ComponentName("com.android.app1",
                "android.app.stubs.MockApplicationActivity");


        // start app1 in free form windowing mode
        startActivityInFreeFormWindow.accept(app1);

        // assert app1 being at top position in process LRU list
        assertWithMessage("App hasn't come to the top of LRU list after being started")
                .that(doWaitWhile(() -> !isApp1Top.getAsBoolean(), DELAY / 10, DELAY))
                .isTrue();


        // start app2 in free form windowing mode
        startActivityInFreeFormWindow.accept(app2);

        // assert app1 losing its top position in process LRU list
        assertWithMessage("App is still at the top of the LRU list after losing focus")
                .that(doWaitWhile(isApp1Top, DELAY / 10, DELAY))
                .isTrue();


        // focus back app1's window again
        startActivityInFreeFormWindow.accept(app1);

        // assert app1 gaining back its top position in process LRU list
        assertWithMessage("App hasn't come to the top of LRU list after gaining back focus again")
                .that(doWaitWhile(() -> !isApp1Top.getAsBoolean(), DELAY / 10, DELAY))
                .isTrue();

        // bind several isolated services from app1 and assert that app2 doesn't get in between them
        // in the LRU list by "not" providing the FLAG_SKIP_UNKNOWN option.
        final IsolatedConnectionInfo[] connections = new IsolatedConnectionInfo[] {
            new IsolatedConnectionInfo(0, 0, BINDING_ANY, "0"),
            new IsolatedConnectionInfo(0, 0, BINDING_ANY, "1"),
            new IsolatedConnectionInfo(0, 0, BINDING_ANY, "2"),
        };
        doBindAndWaitForService(mContext, connections, 0, BINDING_ANY);
        verifyLruOrder(new LruOrderItem[] {
            new LruOrderItem(Process.myUid(), 0),
            new LruOrderItem(connections[2], 0),
            new LruOrderItem(connections[1], com.android.server.am.Flags.removeLruSpamPrevention()
                    ? 0 : LruOrderItem.FLAG_SKIP_UNKNOWN),
            new LruOrderItem(connections[0], 0),
        });
        doUnbind(mContext, connections, 0, BINDING_ANY);
    }
}
