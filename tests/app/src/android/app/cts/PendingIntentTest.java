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

package android.app.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.MockActivity;
import android.app.stubs.MockReceiver;
import android.app.stubs.MockService;
import android.app.stubs.PendingIntentStubActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class PendingIntentTest {
    private static final int WAIT_TIME = 10000;
    private PendingIntent mPendingIntent;
    private Intent mIntent;
    private Context mContext;
    private boolean mFinishResult;
    private boolean mHandleResult;
    private String mResultAction;
    private final Object mFinishLock = new Object();
    private final PendingIntent.OnFinished mFinish =
            (pi, intent, resultCode, resultData, resultExtras) -> {
                synchronized (mFinishLock) {
                    mFinishResult = true;
                    if (intent != null) {
                        mResultAction = intent.getAction();
                    }
                    mFinishLock.notifyAll();
                }
            };
    private boolean mLooperStart;
    private Looper mLooper;
    private Handler mHandler;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new Thread(
                        () -> {
                            Looper.prepare();
                            mLooper = Looper.myLooper();
                            mLooperStart = true;
                            Looper.loop();
                        })
                .start();
        while (!mLooperStart) {
            Thread.sleep(50);
        }
        mHandler =
                new Handler(mLooper) {
                    @Override
                    public void dispatchMessage(@NonNull Message msg) {
                        synchronized (mFinishLock) {
                            mHandleResult = true;
                        }
                        super.dispatchMessage(msg);
                    }

                    @Override
                    public boolean sendMessageAtTime(@NonNull Message msg, long uptimeMillis) {
                        synchronized (mFinishLock) {
                            mHandleResult = true;
                        }
                        return super.sendMessageAtTime(msg, uptimeMillis);
                    }

                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        synchronized (mFinishLock) {
                            mHandleResult = true;
                        }
                        super.handleMessage(msg);
                    }
                };
    }

    @After
    public void tearDown() throws Exception {
        mLooper.quit();
    }

    private void prepareFinish() {
        synchronized (mFinishLock) {
            mFinishResult = false;
            mHandleResult = false;
        }
    }

    public boolean waitForFinish(long timeout) {
        long now = SystemClock.elapsedRealtime();
        final long endTime = now + timeout;
        synchronized (mFinishLock) {
            while (!mFinishResult && now < endTime) {
                try {
                    mFinishLock.wait(endTime - now);
                } catch (InterruptedException e) {
                    // Expected
                }
                now = SystemClock.elapsedRealtime();
            }
            return mFinishResult;
        }
    }

    @Test
    public void testGetActivity() throws CanceledException {
        PendingIntentStubActivity.prepare();
        mPendingIntent = null;
        mIntent = new Intent();

        mIntent.setClass(mContext, PendingIntentStubActivity.class);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent.getTargetPackage()).isEqualTo(mContext.getPackageName());

        mPendingIntent.send(ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());

        PendingIntentStubActivity.waitForCreate(WAIT_TIME);
        assertThat(mPendingIntent).isNotNull();
        assertThat(PendingIntentStubActivity.status).isEqualTo(PendingIntentStubActivity.ON_CREATE);

        // test getActivity return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isNull();

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError();

        try {
            mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            assertWithMessage(
                            "Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the"
                                    + " PendingIntent")
                    .fail();
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            assertWithMessage("Shouldn't accept new mutable implicit PendingIntent").fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetActivities() throws CanceledException {
        PendingIntentStubActivity.prepare();
        mPendingIntent = null;
        Intent[] mIntents = new Intent[]{new Intent(), new Intent()};

        for (Intent intent : mIntents) {
            intent.setClass(mContext, PendingIntentStubActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent.getTargetPackage()).isEqualTo(mContext.getPackageName());

        mPendingIntent.send(ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());

        PendingIntentStubActivity.waitForCreate(WAIT_TIME);
        assertThat(mPendingIntent).isNotNull();
        assertThat(PendingIntentStubActivity.status).isEqualTo(PendingIntentStubActivity.ON_CREATE);

        // test getActivities return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isNull();

        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError();

        try {
            mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            assertWithMessage(
                            "Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the"
                                    + " PendingIntent")
                    .fail();
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE);

        // make mIntents implicit
        for (Intent intent : mIntents) {
            intent.setComponent(null);
            intent.setPackage(null);
        }

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                    PendingIntent.FLAG_MUTABLE);
            assertWithMessage("Shouldn't accept new mutable implicit PendingIntent").fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    private void pendingIntentSendError() {
        try {
            // From the doc send function will throw CanceledException if the PendingIntent
            // is no longer allowing more intents to be sent through it. So here call it twice then
            // a CanceledException should be caught.
            mPendingIntent.send();
            mPendingIntent.send();
            assertWithMessage("CanceledException expected, but not thrown").fail();
        } catch (PendingIntent.CanceledException e) {
            // expected
        }
    }

    @Test
    public void testGetBroadcast() throws CanceledException {
        MockReceiver.prepareReceive(null, 0);
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // test getBroadcast return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isNull();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError();

        try {
            mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            assertWithMessage(
                            "Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the"
                                    + " PendingIntent")
                    .fail();
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            assertWithMessage("Shouldn't accept new mutable implicit PendingIntent").fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    // Local receiver for examining delivered broadcast intents
    private static final class ExtraReceiver extends BroadcastReceiver {
        private final String extraName;

        public volatile int extra = 0;
        public CountDownLatch latch = null;

        public ExtraReceiver(String name) {
            extraName = name;
        }

        public void onReceive(Context ctx, Intent intent) {
            extra = intent.getIntExtra(extraName, 0);
            latch.countDown();
        }

        public void reset() {
            extra = 0;
            latch = new CountDownLatch(1);
        }

        public boolean waitForReceipt() throws InterruptedException {
            return latch.await(WAIT_TIME, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    public void testUpdateCurrent() throws InterruptedException, CanceledException {
        final int EXTRA_1 = 50;
        final int EXTRA_2 = 38;
        final String EXTRA_NAME = "test_extra";
        final String BROADCAST_ACTION = "testUpdateCurrent_action";

        final ExtraReceiver br = new ExtraReceiver(EXTRA_NAME);
        final IntentFilter filter = new IntentFilter(BROADCAST_ACTION);
        mContext.registerReceiver(br, filter, Context.RECEIVER_EXPORTED_UNAUDITED);

        // Baseline: establish that we get the extra properly
        PendingIntent pi;
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra(EXTRA_NAME, EXTRA_1);

        pi = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        try {
            br.reset();
            pi.send();
            assertThat(br.waitForReceipt()).isTrue();
            assertThat(br.extra).isEqualTo(EXTRA_1);

            // Change the extra in the Intent
            intent.putExtra(EXTRA_NAME, EXTRA_2);

            // Repeat PendingIntent.getBroadcast() *without* UPDATE_CURRENT, so we expect
            // the underlying Intent to still be the initial one with EXTRA_1
            pi = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            br.reset();
            pi.send();
            assertThat(br.waitForReceipt()).isTrue();
            assertThat(br.extra).isEqualTo(EXTRA_1);

            // This time use UPDATE_CURRENT, and expect to get the updated extra when the
            // PendingIntent is sent
            pi =
                    PendingIntent.getBroadcast(
                            mContext,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            br.reset();
            pi.send();
            assertThat(br.waitForReceipt()).isTrue();
            assertThat(br.extra).isEqualTo(EXTRA_2);
        } finally {
            pi.cancel();
            mContext.unregisterReceiver(br);
        }
    }

    @Test
    public void testGetService() throws CanceledException {
        MockService.prepareStart();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockService.waitForStart(WAIT_TIME);
        assertThat(MockService.result).isTrue();

        // test getService return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isNull();

        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError();

        try {
            mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            assertWithMessage(
                            "Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the"
                                    + " PendingIntent")
                    .fail();
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            assertWithMessage("Shouldn't accept new mutable implicit PendingIntent").fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testStartServiceOnFinishedHandler() throws CanceledException {
        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null, mFinish, null);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertThat(MockService.result).isTrue();

        assertThat(mFinishResult).isTrue();
        assertThat(mHandleResult).isFalse();
        mPendingIntent.cancel();

        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null, mFinish, mHandler);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertThat(MockService.result).isTrue();

        assertThat(mFinishResult).isTrue();
        assertThat(mHandleResult).isTrue();
        mPendingIntent.cancel();

    }

    @Test
    public void testCreatePendingResult() {
        Intent intent = new Intent(mContext, MockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);

        // creating a mutable explicit PendingResult works fine
        mPendingIntent = activity.createPendingResult(1, mIntent, PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingResult works fine
        mPendingIntent = activity.createPendingResult(1, mIntent, PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingResult with NO_CREATE works fine
        mPendingIntent =
                activity.createPendingResult(
                        1, mIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingResult with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent =
                activity.createPendingResult(
                        1,
                        mIntent,
                        PendingIntent.FLAG_MUTABLE
                                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingResult works fine
        mPendingIntent = activity.createPendingResult(1, mIntent, PendingIntent.FLAG_MUTABLE);
    }

    @Test
    public void testCancel() throws CanceledException {
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    private void pendingIntentSendShouldFail(PendingIntent pendingIntent) {
        try {
            pendingIntent.send();
            assertWithMessage("CanceledException expected, but not thrown").fail();
        } catch (CanceledException e) {
            // expected
        }
    }

    @Test
    public void testSend() throws CanceledException {
        MockReceiver.prepareReceive(null, -1);
        mIntent = new Intent();
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);

        // send function to send default code 0
        assertThat(MockReceiver.sResultCode).isEqualTo(0);
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        mPendingIntent.cancel();

        pendingIntentSendShouldFail(mPendingIntent);
    }

    @Test
    public void testSendWithParamInt() throws CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        // send result code 1.
        mPendingIntent.send(1);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // assert the result code
        assertThat(MockReceiver.sResultCode).isEqualTo(1);
        assertThat(mResultAction).isNull();

        MockReceiver.prepareReceive(null, 0);
        // send result code 2
        mPendingIntent.send(2);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // assert the result code
        assertThat(MockReceiver.sResultCode).isEqualTo(2);
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        assertThat(mResultAction).isNull();
        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    @Test
    public void testSendWithParamContextIntIntent() throws CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        MockReceiver.prepareReceive(null, 0);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        assertThat(MockReceiver.sResultCode).isEqualTo(1);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);

        mPendingIntent.send(mContext, 2, mIntent);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        assertThat(MockReceiver.sResultCode).isEqualTo(2);
        mPendingIntent.cancel();
    }

    @Test
    public void testSendWithParamIntOnFinishedHandler() throws CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(1, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertThat(mFinishResult).isFalse();
        assertThat(mHandleResult).isFalse();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // assert result code
        assertThat(MockReceiver.sResultCode).isEqualTo(1);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(2, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertThat(mFinishResult).isTrue();
        assertThat(mHandleResult).isFalse();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // assert result code
        assertThat(MockReceiver.sResultCode).isEqualTo(2);
        mPendingIntent.cancel();

        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        mPendingIntent.send(3, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertThat(mHandleResult).isTrue();
        assertThat(mFinishResult).isTrue();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);

        // assert result code
        assertThat(MockReceiver.sResultCode).isEqualTo(3);
        mPendingIntent.cancel();
    }

    @Test
    public void testSendWithParamContextIntIntentOnFinishedHandler() throws CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertThat(mFinishResult).isFalse();
        assertThat(mHandleResult).isFalse();
        assertThat(mResultAction).isNull();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertThat(mFinishResult).isTrue();
        assertThat(mResultAction).isEqualTo(MockReceiver.MOCKACTION);
        assertThat(mHandleResult).isFalse();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertThat(mHandleResult).isTrue();
        assertThat(mResultAction).isEqualTo(MockReceiver.MOCKACTION);
        assertThat(mFinishResult).isTrue();
        assertThat(MockReceiver.sAction).isEqualTo(MockReceiver.MOCKACTION);
        mPendingIntent.cancel();
    }

    @Test
    public void testSendNoReceiverOnFinishedHandler() throws CanceledException {
        // This action won't match anything, so no receiver will run but we should
        // still get a finish result.
        final String BAD_ACTION = MockReceiver.MOCKACTION + "_bad";
        mIntent = new Intent(BAD_ACTION);
        mIntent.setAction(BAD_ACTION);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertThat(mFinishResult).isTrue();
        assertThat(mResultAction).isEqualTo(BAD_ACTION);
        assertThat(mHandleResult).isFalse();
        assertThat(MockReceiver.sAction).isNull();
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertThat(mHandleResult).isTrue();
        assertThat(mResultAction).isEqualTo(BAD_ACTION);
        assertThat(mFinishResult).isTrue();
        assertThat(MockReceiver.sAction).isNull();
        mPendingIntent.cancel();
    }

    @Test
    public void testGetTargetPackage() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent.getTargetPackage()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void testIsImmutable() {
        mIntent = new Intent();
        mIntent.setPackage(mContext.getPackageName()); // explicit intent

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent.isImmutable()).isTrue();

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        assertThat(mPendingIntent.isImmutable()).isFalse();
    }

    @Test
    public void testEquals() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent target = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        assertThat(mPendingIntent.equals(target)).isFalse();
        assertThat(mPendingIntent.hashCode()).isNotEqualTo(target.hashCode());
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);

        target = PendingIntent.getActivity(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isEqualTo(target);

        mIntent = new Intent(MockReceiver.MOCKACTION);
        target = PendingIntent.getBroadcast(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent).isNotEqualTo(target);
        assertThat(mPendingIntent.hashCode()).isNotEqualTo(target.hashCode());

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        target = PendingIntent.getActivity(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);

        assertThat(mPendingIntent.equals(target)).isTrue();
        assertThat(target.hashCode()).isEqualTo(mPendingIntent.hashCode());
    }

    @Test
    public void testDescribeContents() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final int expected = 0;
        assertThat(mPendingIntent.describeContents()).isEqualTo(expected);
    }

    @Test
    public void testWriteToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Parcel parcel = Parcel.obtain();

        mPendingIntent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PendingIntent pendingIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        assertThat(mPendingIntent.equals(pendingIntent)).isTrue();
    }

    @Test
    public void testReadAndWritePendingIntentOrNullToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(mPendingIntent.toString()).isNotNull();

        Parcel parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        parcel.setDataPosition(0);
        PendingIntent target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertThat(target).isEqualTo(mPendingIntent);
        assertThat(target.getTargetPackage()).isEqualTo(mPendingIntent.getTargetPackage());

        mPendingIntent = null;
        parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertThat(target).isNull();
    }

    @Test
    public void testGetIntentComponentAndType() {
        Intent broadcastReceiverIntent = new Intent(MockReceiver.MOCKACTION);
        broadcastReceiverIntent.setClass(mContext, MockReceiver.class);
        PendingIntent broadcastReceiverPI = PendingIntent.getBroadcast(mContext, 1,
                broadcastReceiverIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(broadcastReceiverPI.isBroadcast()).isTrue();
        assertThat(broadcastReceiverPI.isActivity()).isFalse();
        assertThat(broadcastReceiverPI.isForegroundService()).isFalse();
        assertThat(broadcastReceiverPI.isService()).isFalse();

        List<ResolveInfo> broadcastReceiverResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(broadcastReceiverPI,
                        (pi) -> pi.queryIntentComponents(0));
        if (broadcastReceiverResolveInfos != null && !broadcastReceiverResolveInfos.isEmpty()) {
            ResolveInfo resolveInfo = broadcastReceiverResolveInfos.get(0);
            assertThat(resolveInfo.activityInfo).isNotNull();
            assertThat(resolveInfo.activityInfo.packageName)
                    .isEqualTo(MockReceiver.class.getPackageName());
            assertThat(resolveInfo.activityInfo.name).isEqualTo(MockReceiver.class.getName());
        } else {
            assertWithMessage("Cannot resolve broadcast receiver pending intent").fail();
        }

        Intent activityIntent = new Intent();
        activityIntent.setClass(mContext, MockActivity.class);
        PendingIntent activityPI = PendingIntent.getActivity(mContext, 1,
                activityIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(activityPI.isActivity()).isTrue();
        assertThat(activityPI.isBroadcast()).isFalse();
        assertThat(activityPI.isForegroundService()).isFalse();
        assertThat(activityPI.isService()).isFalse();

        List<ResolveInfo> activityResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(activityPI,
                        (pi) -> pi.queryIntentComponents(0));
        if (activityResolveInfos != null && !activityResolveInfos.isEmpty()) {
            ResolveInfo resolveInfo = activityResolveInfos.get(0);
            assertThat(resolveInfo.activityInfo).isNotNull();
            assertThat(resolveInfo.activityInfo.packageName)
                    .isEqualTo(MockActivity.class.getPackageName());
            assertThat(resolveInfo.activityInfo.name).isEqualTo(MockActivity.class.getName());
        } else {
            assertWithMessage("Cannot resolve activity pending intent").fail();
        }

        Intent serviceIntent = new Intent();
        serviceIntent.setClass(mContext, MockService.class);
        PendingIntent servicePI = PendingIntent.getService(mContext, 1, serviceIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(servicePI.isService()).isTrue();
        assertThat(servicePI.isActivity()).isFalse();
        assertThat(servicePI.isBroadcast()).isFalse();
        assertThat(servicePI.isForegroundService()).isFalse();

        List<ResolveInfo> serviceResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(servicePI,
                        (pi) -> pi.queryIntentComponents(0));
        if (serviceResolveInfos != null && !serviceResolveInfos.isEmpty()) {
            ResolveInfo resolveInfo = serviceResolveInfos.get(0);
            assertThat(resolveInfo.serviceInfo).isNotNull();
            assertThat(resolveInfo.serviceInfo.packageName)
                    .isEqualTo(MockService.class.getPackageName());
            assertThat(resolveInfo.serviceInfo.name).isEqualTo(MockService.class.getName());
        } else {
            assertWithMessage("Cannot resolve service pending intent").fail();
        }

        PendingIntent foregroundServicePI = PendingIntent.getForegroundService(mContext, 1,
                serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(foregroundServicePI.isForegroundService()).isTrue();
        assertThat(foregroundServicePI.isActivity()).isFalse();
        assertThat(foregroundServicePI.isBroadcast()).isFalse();
        assertThat(foregroundServicePI.isService()).isFalse();

        List<ResolveInfo> foregroundServiceResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(foregroundServicePI,
                        (pi) -> pi.queryIntentComponents(0));
        if (foregroundServiceResolveInfos != null && !foregroundServiceResolveInfos.isEmpty()) {
            ResolveInfo resolveInfo = serviceResolveInfos.get(0);
            assertThat(resolveInfo.serviceInfo).isNotNull();
            assertThat(resolveInfo.serviceInfo.packageName)
                    .isEqualTo(MockService.class.getPackageName());
            assertThat(resolveInfo.serviceInfo.name).isEqualTo(MockService.class.getName());
        } else {
            assertWithMessage("Cannot resolve foreground service pending intent").fail();
        }
    }

    @Test
    public void testCancelListener() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        // To make sure the executor is used, we count the number of times the executor
        // is invoked.
        final AtomicInteger executorCount = new AtomicInteger();
        final Executor e = (runnable) -> {
            executorCount.incrementAndGet();
            runnable.run();
        };

        // Add 4 listeners and remove the first one and the last one.
        PendingIntent.CancelListener listener1 =
                (pi) -> {
                    called.add("listener1");
                    assertThat(pi).isEqualTo(pi1);
                };
        PendingIntent.CancelListener listener2 =
                (pi) -> {
                    called.add("listener2");
                    assertThat(pi).isEqualTo(pi1);
                };
        PendingIntent.CancelListener listener3 =
                (pi) -> {
                    called.add("listener3");
                    assertThat(pi).isEqualTo(pi1);
                };
        PendingIntent.CancelListener listener4 =
                (pi) -> {
                    called.add("listener4");
                    assertThat(pi).isEqualTo(pi1);
                };
        assertThat(pi1.addCancelListener(e, listener1)).isTrue();
        assertThat(pi1.addCancelListener(e, listener2)).isTrue();
        assertThat(pi1.addCancelListener(e, listener3)).isTrue();
        assertThat(pi1.addCancelListener(e, listener4)).isTrue();

        pi1.removeCancelListener(listener1);
        pi1.removeCancelListener(listener4);

        pi1.cancel();

        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener2") && called.contains("listener3"));
        // Wait a bit more just in case, and make sure the last one isn't called.
        Thread.sleep(200);
        assertThat(called).doesNotContain("listener1");
        assertThat(called).doesNotContain("listener4");
        assertThat(executorCount.get()).isEqualTo(2);

        // It's already canceled, so more calls should return false.
        assertThat(pi1.addCancelListener(e, (pi) -> assertThat(pi).isEqualTo(pi1))).isFalse();
        // Should still return false.
        assertThat(pi1.addCancelListener(e, (pi) -> assertThat(pi).isEqualTo(pi1))).isFalse();

        // Clear the trackers.
        called.clear();
        executorCount.set(0);

        // Try with a new PI using the same intent.
        final PendingIntent pi2 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertThat(
                        pi2.addCancelListener(
                                e,
                                (pi) -> {
                                    called.add("listener1");
                                    assertThat(pi).isEqualTo(pi2);
                                }))
                .isTrue();
        pi2.cancel();

        TestUtils.waitUntil("listener1 not called",
                () -> called.contains("listener1"));
        assertThat(executorCount.get()).isEqualTo(1);
    }

    @Test
    public void testCancelListener_cancelCurrent() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);

        // Create the first PI.
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        PendingIntent.CancelListener listener1 =
                (pi) -> {
                    called.add("listener1");
                    assertThat(pi).isEqualTo(pi1);
                };
        assertThat(pi1.addCancelListener(Runnable::run, listener1)).isTrue();

        // Update-current won't cancel the previous PI.
        final PendingIntent pi2 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent.CancelListener listener2 =
                (pi) -> {
                    called.add("listener2");
                    assertThat(pi).isEqualTo(pi2);
                };
        assertThat(pi2.addCancelListener(Runnable::run, listener2)).isTrue();

        // So this shouldn't be called. (oops I don't want to use sleep(), but...)
        Thread.sleep(200);
        assertThat(called).doesNotContain("listener1");

        // Cancel-current will cancel both pi1 and pi2
        final PendingIntent unused =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        i,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener1") && called.contains("listener2"));
    }

    @Test
    public void testCancelListener_oneShot() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);

        // Create the first PI.
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        PendingIntent.CancelListener listener1 =
                (pi) -> {
                    called.add("listener1");
                    assertThat(pi).isEqualTo(pi1);
                };
        assertThat(pi1.addCancelListener(Runnable::run, listener1)).isTrue();

        pi1.send();

        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener1"));
    }
}
