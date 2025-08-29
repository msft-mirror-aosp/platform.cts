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
package android.app.cts;

import static android.Manifest.permission.SET_ACTIVITY_WATCHER;
import static android.Manifest.permission.KILL_BACKGROUND_PROCESSES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.REAL_GET_TASKS;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class AActivityManagerTest {
    private Context mContext;

    static {
        System.loadLibrary("aactivity_manager_test_jni");
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule chain = RuleChain.outerRule(
            DeviceFlagsValueProvider.createCheckFlagsRule()).around(sDeviceState);

    public static class RunningAppProcessInfo {
        public final int pid;
        public final int uid;
        public final String processName;
        public final List<String> pkgList;
        public final int importance;

        public RunningAppProcessInfo(int pid, int uid, String processName, List<String> pkgList,
                int importance) {
            this.pid = pid;
            this.uid = uid;
            this.processName = processName;
            this.pkgList = pkgList;
            this.importance = importance;
        }
    }

    @Test
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, REAL_GET_TASKS})
    public void testGetRunningAppProcesses() {
        List<RunningAppProcessInfo> processes = nativeGetRunningAppProcesses();
        assertThat(processes).isNotNull();
        assertThat(processes).isNotEmpty();

        int myPid = Process.myPid();
        boolean found = false;
        for (RunningAppProcessInfo info : processes) {
            if (info.pid == myPid) {
                found = true;
                assertThat(info.uid).isEqualTo(Process.myUid());
                assertThat(info.processName).isEqualTo(mContext.getPackageName());
                assertThat(info.pkgList).contains(mContext.getPackageName());
                break;
            }
        }
        assertThat(found).isTrue();
    }

    public interface ProcessObserverListener {
        void onProcessStarted(int pid, int processUid, int packageUid, String packageName,
                String processName);
        void onProcessDied(int pid, int uid);
    }

    private static class TestProcessObserverListener implements ProcessObserverListener {
        CountDownLatch processStartedLatch = new CountDownLatch(1);
        CountDownLatch processDiedLatch = new CountDownLatch(1);

        @Override
        public void onProcessStarted(int pid, int processUid, int packageUid, String packageName,
                String processName) {
            processStartedLatch.countDown();
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            processDiedLatch.countDown();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold KILL_BACKGROUND_PROCESSES permission")
    @EnsureHasPermission({SET_ACTIVITY_WATCHER, KILL_BACKGROUND_PROCESSES})
    public void testProcessObserver() throws InterruptedException {
        TestProcessObserverListener listener = new TestProcessObserverListener();
        long observer = nativeRegisterProcessObserver(listener);
        assertThat(observer).isNotEqualTo(0);

        final ConditionVariable serviceConnected = new ConditionVariable();
        Intent intent = new Intent(mContext, TestService.class);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceConnected.open();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Do nothing.
            }
        };
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        if (!listener.processStartedLatch.await(5, TimeUnit.SECONDS)) {
            fail("onProcessStarted callback was not received");
        }

        if (!serviceConnected.block(10000)) {
            fail("onServiceConnected was not called");
        }
        mContext.unbindService(connection);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        final int nAttempts = 5;
        for (int trials = 0; trials < nAttempts; trials++) {
            am.killBackgroundProcesses(mContext.getPackageName());
            if (listener.processDiedLatch.await(1, TimeUnit.SECONDS)) {
                break;
            }
        }

        if (listener.processDiedLatch.getCount() != 0) {
            fail("onProcessDied callback was not received");
        }

        nativeUnregisterProcessObserver(observer);
    }

    public static class TestService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            return new Binder();
        }
    }

    private static native List<RunningAppProcessInfo> nativeGetRunningAppProcesses();
    private static native long nativeRegisterProcessObserver(ProcessObserverListener listener);
    private static native void nativeUnregisterProcessObserver(long observerPtr);
}