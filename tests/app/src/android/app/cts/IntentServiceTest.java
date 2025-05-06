/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.app.stubs.IntentServiceStub;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class IntentServiceTest {
    private static final int TIMEOUT_MSEC = 30000;
    private Intent mIntent;
    private boolean mConnected;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        IntentServiceStub.reset();
        mIntent = new Intent(mContext, IntentServiceStub.class);
    }

    @After
    public void tearDown() throws Exception {
        mContext.stopService(mIntent);
    }

    @Test
    public void testIntents() throws Throwable {
        final int value = 42;
        final int adds = 3;

        Intent addIntent = new Intent(mContext, IntentServiceStub.class);

        addIntent.setAction(IntentServiceStub.ISS_ADD);
        addIntent.putExtra(IntentServiceStub.ISS_VALUE, 42);

        for (int i = 0; i < adds; i++) {
            mContext.startService(addIntent);
        }

        PollingCheck.check(
                "onHandleIntentCalled not called enough",
                TIMEOUT_MSEC,
                () -> IntentServiceStub.getOnHandleIntentCalledCount() == adds);

        PollingCheck.check(
                "accumulator not correct",
                TIMEOUT_MSEC,
                () -> IntentServiceStub.getAccumulator() == adds * value);

        PollingCheck.check(
                "onDestroyCalled not called", TIMEOUT_MSEC, IntentServiceStub::isOnDestroyCalled);
    }

    @Test
    public void testIntentServiceLifeCycle() throws Throwable {
        // start service
        mContext.startService(mIntent);
        new PollingCheck(TIMEOUT_MSEC) {
            protected boolean check() {
                return IntentServiceStub.getOnHandleIntentCalledCount() > 0;
            }
        }.run();
        assertThat(IntentServiceStub.isOnCreateCalled()).isTrue();
        assertThat(IntentServiceStub.isOnStartCalled()).isTrue();

        // bind service
        ServiceConnection conn = new TestConnection();
        mContext.bindService(mIntent, conn, Context.BIND_AUTO_CREATE);
        new PollingCheck(TIMEOUT_MSEC) {
            protected boolean check() {
                return mConnected;
            }
        }.run();
        assertThat(IntentServiceStub.isOnBindCalled()).isTrue();

        // unbind service
        mContext.unbindService(conn);
        // stop service
        mContext.stopService(mIntent);
        IntentServiceStub.waitToFinish(TIMEOUT_MSEC);
    }

    private final class TestConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder service) {
            mConnected = true;
        }

        public void onServiceDisconnected(ComponentName name) {
        }
    }
}
