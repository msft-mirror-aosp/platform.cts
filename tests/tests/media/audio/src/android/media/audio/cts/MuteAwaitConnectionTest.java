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

package android.media.audio.cts;

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioProfile;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.DeviceState;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Class to exercise AudioManager.muteAwaitConnection / cancelMuteAwaitConnection APIs
 */
@RunWith(AndroidJUnit4.class)
public class MuteAwaitConnectionTest {
    private static final String TAG = MuteAwaitConnectionTest.class.getSimpleName();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final AudioDeviceAttributes TEST_DEVICE = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_AUX_LINE, "bla");
    // arguments to create a device similar in type and address to TEST_DEVICE, but with
    // non-empty AudioProfile list to correspond to a "real" AudioDeviceAttributes that would be
    // reported by the framework when connected
    private static final AudioProfile FAKE_PROFILE =
            new AudioProfile(AudioFormat.ENCODING_PCM_16BIT,
                    new int[]{ 44100 }, new int[]{ AudioFormat.CHANNEL_OUT_STEREO},
                    new int[]{}, AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE);
    private static final AudioDeviceAttributes TEST_DEVICE_REAL = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_AUX_LINE, "bla", "bli",
            Arrays.asList(FAKE_PROFILE), new ArrayList<>());

    private static final int[] TEST_USAGES = { AudioAttributes.USAGE_MEDIA };
    private static final long TEST_MUTE_TIMEOUT_MS = 1000;
    /**
     * timing tolerance for receiving an expected callback
     */
    private static final long TEST_CALLBACK_TOLERANCE_MS = 400;

    private AudioManager mAudioManager;

    private MyMuteAwaitConnectionCallback mCallback = new MyMuteAwaitConnectionCallback();

    /** Test setup */
    @Before
    public void setUp() throws Exception {
        mAudioManager = (AudioManager) InstrumentationRegistry.getTargetContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mCallback.reset();
    }

    /** Test teardown */
    @After
    public void tearDown() throws Exception {
        try {
            mAudioManager.unregisterMuteAwaitConnectionCallback(mCallback);
        } catch (IllegalArgumentException e) { }
        // disconnect test device
        mAudioManager.setTestDeviceConnectionState(TEST_DEVICE, false /*connected*/);
    }


    /**
     * Test API parameter check
     * @throws Exception on errors
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_ROUTING")
    @EnsureHasPermission(MODIFY_AUDIO_ROUTING)
    public void testParameterChecks() throws Exception {
        Log.i(TAG, "testParameterChecks");
        // check parameters of muteAwaitConnection
        try {
            mAudioManager.muteAwaitConnection(null, TEST_DEVICE, 1, TimeUnit.MILLISECONDS);
            fail("muteAwaitConnection should require non-null usage array");
        } catch (NullPointerException e) { }
        try {
            mAudioManager.muteAwaitConnection(TEST_USAGES, null, 1, TimeUnit.MILLISECONDS);
            fail("muteAwaitConnection should require non-null audio device");
        } catch (NullPointerException e) { }
        try {
            mAudioManager.muteAwaitConnection(TEST_USAGES, TEST_DEVICE, 0, TimeUnit.SECONDS);
            fail("muteAwaitConnection should require strictly positive timeout");
        } catch (IllegalArgumentException e) { }
        try {
            mAudioManager.muteAwaitConnection(TEST_USAGES, TEST_DEVICE, 1, null);
            fail("muteAwaitConnection should require non-null time unit");
        } catch (NullPointerException e) { }

        // check parameters of cancelMuteAwaitConnection
        try {
            mAudioManager.cancelMuteAwaitConnection(null);
            fail("cancelMuteAwaitConnection should require non-null device");
        } catch (Exception e) { }

        // check parameters of registerMuteAwaitConnectionCallback
        try {
            mAudioManager.registerMuteAwaitConnectionCallback(null, mCallback);
            fail("registerMuteAwaitConnectionCallback should require non-null Executor");
        } catch (Exception e) { }
        try {
            mAudioManager.registerMuteAwaitConnectionCallback(
                    Executors.newSingleThreadExecutor(), null);
            fail("registerMuteAwaitConnectionCallback should require non-null callback");
        } catch (Exception e) { }

        // check parameters of unregisterMuteAwaitConnectionCallback
        try {
            mAudioManager.unregisterMuteAwaitConnectionCallback(null);
            fail("unregisterMuteAwaitConnectionCallback should require non-null callback");
        } catch (Exception e) { }
        try {
            mAudioManager.unregisterMuteAwaitConnectionCallback(mCallback);
            fail("unregisterMuteAwaitConnectionCallback should require a previously registered"
                    + " callback");
        } catch (Exception e) { }
    }

    /**
     * Test callbacks for muting and unmuting are called after calling muteAwaitConnection but
     * letting the timeout run its course.
     * @throws Exception on errors
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_ROUTING")
    @EnsureHasPermission(MODIFY_AUDIO_ROUTING)
    public void testMuteCancellationCallback() throws Exception {
        Log.i(TAG, "testMuteCancellationCallback");
        mAudioManager.registerMuteAwaitConnectionCallback(
                Executors.newCachedThreadPool(), mCallback);

        execute(() -> mAudioManager.muteAwaitConnection(
                TEST_USAGES, TEST_DEVICE, TEST_MUTE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // wait until the onMutedUntilConnection callback is called
        mCallback.await(true/*mute*/, TEST_MUTE_TIMEOUT_MS);
        assertTrue("onMutedUntilConnection wasn't called", mCallback.mMutedCalled);
        assertEquals("onMutedUntilConnection wrong device", TEST_DEVICE, mCallback.mOnMutedDevice);
        // wait until the onUnmutedEvent callback is called with EVENT_TIMEOUT
        mCallback.await(false/*mute*/, TEST_MUTE_TIMEOUT_MS + TEST_CALLBACK_TOLERANCE_MS);
        assertTrue("mute didn't timeout", mCallback.mUnmutedCalled);
        assertTrue("timeout wasn't signaled as expected, got " + mCallback.mUnmutedEvent,
                (mCallback.mUnmutedEvent == AudioManager.MuteAwaitConnectionCallback.EVENT_TIMEOUT)
                || (mCallback.mUnmutedEvent
                        == AudioManager.MuteAwaitConnectionCallback.EVENT_CANCEL));
        assertEquals("onUnmutedEvent wrong device", TEST_DEVICE, mCallback.mOnUnmutedDevice);
    }

    /**
     * Test callbacks for muting and unmuting are called after calling muteAwaitConnection and
     * then cancelling it
     * @throws Exception on errors
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_ROUTING")
    @EnsureHasPermission(MODIFY_AUDIO_ROUTING)
    public void testMuteCancelCallback() throws Exception {
        Log.i(TAG, "testMuteCancelCallback start");
        mAudioManager.registerMuteAwaitConnectionCallback(
                Executors.newCachedThreadPool(), mCallback);
        execute(() -> mAudioManager.muteAwaitConnection(
                TEST_USAGES, TEST_DEVICE, TEST_MUTE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // wait before cancelling
        SystemClock.sleep(TEST_MUTE_TIMEOUT_MS / 2);
        execute(() -> mAudioManager.cancelMuteAwaitConnection(TEST_DEVICE_REAL));
        // wait until the onUnmutedEvent callback is called with EVENT_CANCEL
        mCallback.await(false/*mute*/, (TEST_MUTE_TIMEOUT_MS / 2) + TEST_CALLBACK_TOLERANCE_MS);
        assertTrue("unmute wasn't signalled", mCallback.mUnmutedCalled);
        assertEquals("cancel wasn't signalled",
                mCallback.mUnmutedEvent, AudioManager.MuteAwaitConnectionCallback.EVENT_CANCEL);
        assertTrue("onUnmutedEvent wrong device",
                TEST_DEVICE.equalTypeAddress(mCallback.mOnUnmutedDevice));
        Log.i(TAG, "testMuteCancelCallback end");
    }

    /**
     * Test callbacks for muting and unmuting are called after calling muteAwaitConnection and
     * when the device connects
     * @throws Exception on errors
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot hold android.permission.MODIFY_AUDIO_ROUTING")
    @EnsureHasPermission(MODIFY_AUDIO_ROUTING)
    public void testMuteConnectionCallback() throws Exception {
        Log.i(TAG, "testMuteConnectionCallback start");
        mAudioManager.registerMuteAwaitConnectionCallback(
                Executors.newCachedThreadPool(), mCallback);
        execute(() -> mAudioManager.muteAwaitConnection(
                TEST_USAGES, TEST_DEVICE, TEST_MUTE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // wait before connecting the device
        SystemClock.sleep(TEST_MUTE_TIMEOUT_MS / 2);
        execute(() -> mAudioManager.setTestDeviceConnectionState(TEST_DEVICE_REAL,
                true /*connected*/));
        // wait until the onUnmutedEvent callback is called with EVENT_CONNECTION
        mCallback.await(false/*mute*/, (TEST_MUTE_TIMEOUT_MS / 2) + TEST_CALLBACK_TOLERANCE_MS);
        assertTrue("unmute wasn't signalled", mCallback.mUnmutedCalled);
        assertEquals("connection wasn't signalled",
                mCallback.mUnmutedEvent, AudioManager.MuteAwaitConnectionCallback.EVENT_CONNECTION);
        assertEquals("onUnmutedEvent wrong device", TEST_DEVICE, mCallback.mOnUnmutedDevice);
        Log.i(TAG, "testMuteConnectionCallback end");
    }

    private static class MyMuteAwaitConnectionCallback extends
            AudioManager.MuteAwaitConnectionCallback {
        boolean mMutedCalled, mUnmutedCalled;
        private CountDownLatch mMutedCountDownLatch;
        private CountDownLatch mUnmutedCountDownLatch;
        private int mUnmutedEvent = -1;
        private AudioDeviceAttributes mOnMutedDevice = null;
        private AudioDeviceAttributes mOnUnmutedDevice = null;

        MyMuteAwaitConnectionCallback() {
            reset();
        }

        @Override
        public void onMutedUntilConnection(AudioDeviceAttributes device, int[] mutedUsages) {
            Log.i(TAG, "onMutedUntilConnection dev:" + device);
            mMutedCalled = true;
            mOnMutedDevice = device;
            mMutedCountDownLatch.countDown();
        }

        @Override
        public void onUnmutedEvent(int unmuteEvent, AudioDeviceAttributes device,
                int[] mutedUsages) {
            Log.i(TAG, "onUnmutedEvent event:" + unmuteEvent + " dev:" + device);
            mUnmutedCalled = true;
            mUnmutedEvent = unmuteEvent;
            mOnUnmutedDevice = device;
            mUnmutedCountDownLatch.countDown();
        }

        void await(boolean mute, long timeoutMs) {
            try {
                if (mute) {
                    mMutedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
                } else {
                    mUnmutedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
            }
        }

        void reset() {
            mUnmutedCountDownLatch = new CountDownLatch(1);
            mMutedCountDownLatch = new CountDownLatch(1);
            mMutedCalled = false;
            mUnmutedCalled = false;
            mUnmutedEvent = -1;
            mOnMutedDevice = null;
            mOnUnmutedDevice = null;
        }
    }

    /**
     * Utility to run a command delayed and from a different thread.
     * Used to avoid race conditions from callbacks being called right away before even having
     * a chance to wait on a CountDownLatch.
     * @param command the command to run from a new thread after a delay
     */
    private void execute(Runnable command) {
        Thread executionThread = new Thread(() -> {
            SystemClock.sleep(50);
            command.run();
        });
        executionThread.start();
    }

}
