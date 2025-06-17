/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.audio.Flags.FLAG_DEVICE_VOLUME_APIS;
import static android.media.audio.Flags.FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT;
import static android.media.audio.Flags.unifyAbsoluteVolumeManagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.audio.cts.AudioTestUtil.SleepAssertIntEquals;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@FrameworkSpecificTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class AudioDeviceVolumeManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "AudioDeviceVolumeManagerTest";
    private AudioDeviceVolumeManager mADVmgr;
    private AudioManager mAm;
    private final HashMap<AudioDeviceAttributes, VolumeInfo> mPrevVolume = new HashMap<>();
    private boolean mWasMuted;
    private boolean mUseFixedVolume;
    private boolean mIsTelevision;
    private boolean mIsSingleVolume;
    private boolean mSkipRingerTests;
    private Context mContext;

    private static final AudioDeviceAttributes BT_DEV = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "bla");
    private static final AudioDeviceAttributes BT_DEV2 =
            new AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "bla2");

    private static final AudioDeviceAttributes BT_SCO_DEV =
            new AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "bla");

    private static final AudioDeviceAttributes SPEAKER_DEV =
            new AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "");

    /**
     * Constant for maximum acceptable time before which a volume change needs to be propagated
     * between client request and server update
     */
    private static final int VOLUME_UPDATE_TIME_MAX_MS = 100;

    private static final class AudioDeviceVolumeChangedListener
            implements AudioDeviceVolumeManager.OnAudioDeviceVolumeChangedListener {
        private VolumeInfo mLastVolInfo = null;
        private final CountDownLatch mNewVolInfoLatch;

        AudioDeviceVolumeChangedListener() {
            this(1);
        }

        AudioDeviceVolumeChangedListener(int numberOfCallbacks) {
            mNewVolInfoLatch = new CountDownLatch(numberOfCallbacks);
        }

        @Override
        public void onAudioDeviceVolumeChanged(
                @NonNull AudioDeviceAttributes device, @NonNull VolumeInfo vol) {
            mLastVolInfo = vol;
            mNewVolInfoLatch.countDown();
        }

        @Override
        public void onAudioDeviceVolumeAdjusted(
                @NonNull AudioDeviceAttributes device,
                @NonNull VolumeInfo vol,
                int direction,
                int mode) {
            mLastVolInfo = vol;
            mNewVolInfoLatch.countDown();
        }

        public VolumeInfo waitForVolumeChanged(long timeout, TimeUnit timeUnit) {
            boolean receivedVolInfo = false;
            try {
                receivedVolInfo = mNewVolInfoLatch.await(timeout, timeUnit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return receivedVolInfo ? mLastVolInfo : null;
        }
    }

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        instrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MODIFY_AUDIO_ROUTING,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                        Manifest.permission.STATUS_BAR_SERVICE);
        mADVmgr = mContext.getSystemService(AudioDeviceVolumeManager.class);
        mAm = mContext.getSystemService(AudioManager.class);
        mUseFixedVolume =
                mContext.getResources()
                        .getBoolean(
                                Resources.getSystem()
                                        .getIdentifier("config_useFixedVolume", "bool", "android"));
        PackageManager packageManager = mContext.getPackageManager();
        mIsTelevision =
                packageManager != null
                        && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                                || packageManager.hasSystemFeature(
                                        PackageManager.FEATURE_TELEVISION));
        mIsSingleVolume =
                mContext.getResources()
                        .getBoolean(
                                Resources.getSystem()
                                        .getIdentifier("config_single_volume", "bool", "android"));
        mSkipRingerTests = mUseFixedVolume || mIsTelevision || mIsSingleVolume;

        final VolumeInfo volMedia = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build();
        mPrevVolume.put(BT_DEV, mADVmgr.getDeviceVolume(volMedia, BT_DEV));
        mPrevVolume.put(BT_DEV2, mADVmgr.getDeviceVolume(volMedia, BT_DEV2));
        mPrevVolume.put(BT_SCO_DEV, mADVmgr.getDeviceVolume(volMedia, BT_SCO_DEV));
        mPrevVolume.put(SPEAKER_DEV, mADVmgr.getDeviceVolume(volMedia, SPEAKER_DEV));
        mWasMuted = mAm.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    @After
    public void tearDown() throws Exception {
        // reset the volume
        mADVmgr.setDeviceVolume(mPrevVolume.get(BT_DEV), BT_DEV);
        mADVmgr.setDeviceVolume(mPrevVolume.get(BT_DEV2), BT_DEV2);
        mADVmgr.setDeviceVolume(mPrevVolume.get(BT_SCO_DEV), BT_SCO_DEV);
        mADVmgr.setDeviceVolume(mPrevVolume.get(SPEAKER_DEV), SPEAKER_DEV);
        mAm.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                mWasMuted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                /* flags= */ 0);

        // clean up device behavior
        if (unifyAbsoluteVolumeManagement()) {
            mADVmgr.resetDeviceAbsoluteVolumeBehavior(BT_DEV);
            mADVmgr.resetDeviceAbsoluteVolumeBehavior(BT_DEV2);
            mADVmgr.resetDeviceAbsoluteVolumeBehavior(BT_SCO_DEV);
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Verify calling AudioDeviceVolumeManager.setDeviceVolume/getDeviceVolume with null parameters
     * throws an NPE.
     *
     * @throws Exception
     */
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void testNullability() throws Exception {
        assertThrows(
                "Able to call setDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () -> mADVmgr.setDeviceVolume(null, BT_DEV));
        assertThrows(
                "Able to call setDeviceVolume with null device",
                NullPointerException.class,
                () -> mADVmgr.setDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
        assertThrows(
                "Able to call getDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () -> mADVmgr.getDeviceVolume(null, BT_DEV));
        assertThrows(
                "Able to call getDeviceVolume with null device",
                NullPointerException.class,
                () -> mADVmgr.getDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
    }

    /**
     * Verify calling AudioDeviceVolumeManager.setVolumeForDevice/adjustVolumeForDevice with null
     * parameters throws an NPE.
     *
     * @throws Exception
     */
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#adjustVolumeForDevice"
            })
    @Test
    @RequiresFlagsEnabled(FLAG_DEVICE_VOLUME_APIS)
    public void testNullability_newApi() throws Exception {
        assertThrows(
                "Able to call setVolumeForDevice with null VolumeInfo",
                NullPointerException.class,
                () -> mADVmgr.setVolumeForDevice(null, BT_DEV));
        assertThrows(
                "Able to call setVolumeForDevice with null device",
                NullPointerException.class,
                () -> mADVmgr.setVolumeForDevice(VolumeInfo.getDefaultVolumeInfo(), null));
    }

    /**
     * Verify VolumeInfo used for setting the volume needs to have a volume index
     *
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume"})
    @Test
    public void testVolumeInfoArguments() throws Exception {
        VolumeInfo defVolInfo = VolumeInfo.getDefaultVolumeInfo();
        VolumeInfo vi =
                new VolumeInfo.Builder(defVolInfo).setVolumeIndex(VolumeInfo.INDEX_NOT_SET).build();
        android.util.Log.i(TAG, "testVolumeInfoArguments using VI:" + vi);
        assertThrows(
                "Able to call setDeviceVolume with VolumeInfo without index",
                IllegalArgumentException.class,
                () -> mADVmgr.setDeviceVolume(vi, BT_DEV));
    }

    /**
     * Verify VolumeInfo used for setting the volume needs to have a volume index
     *
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setVolumeForDevice"})
    @Test
    public void testVolumeInfoArguments_newApi() throws Exception {
        VolumeInfo defVolInfo = VolumeInfo.getDefaultVolumeInfo();
        VolumeInfo vi =
                new VolumeInfo.Builder(defVolInfo).setVolumeIndex(VolumeInfo.INDEX_NOT_SET).build();
        android.util.Log.i(TAG, "testVolumeInfoArguments_newApi using VI:" + vi);

        assertThrows(
                "Able to call setVolumeForDevice with VolumeInfo without index",
                IllegalArgumentException.class,
                () -> mADVmgr.setVolumeForDevice(vi, BT_DEV));
    }

    /**
     * Verify VolumeInfo used for setting the volume needs to have a volume index
     *
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setVolumeForDevice"})
    @Test
    @RequiresFlagsEnabled(FLAG_DEVICE_VOLUME_APIS)
    public void testVolumeInfoArguments_setVolumeForDevice() throws Exception {
        VolumeInfo defVolInfo = VolumeInfo.getDefaultVolumeInfo();
        VolumeInfo vi =
                new VolumeInfo.Builder(defVolInfo).setVolumeIndex(VolumeInfo.INDEX_NOT_SET).build();
        android.util.Log.i(TAG, "testVolumeInfoArguments_setVolumeForDevice using VI:" + vi);
        assertThrows(
                "Able to call setVolumeForDevice with VolumeInfo without index",
                IllegalArgumentException.class,
                () -> mADVmgr.setVolumeForDevice(vi, BT_DEV));
    }

    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void testSetGetVolume() throws Exception {
        testSetGetAdjustVolume(/* useVolumeForDevice= */ false);
    }

    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#adjustVolumeForDevice"
            })
    @Test
    public void testSetGetVolume_useVolumeForDevice() throws Exception {
        testSetGetAdjustVolume(/* useVolumeForDevice= */ true);
    }

    public void testSetGetAdjustVolume(boolean useVolumeForDevice) throws Exception {
        if (mSkipRingerTests) {
            return;
        }

        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int midIndex = (minIndex + maxIndex) / 2;
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMinVolumeIndex(minIndex)
                        .setMaxVolumeIndex(maxIndex)
                        .build();
        final VolumeInfo volMin = new VolumeInfo.Builder(volMedia).setVolumeIndex(minIndex).build();
        final VolumeInfo volMid = new VolumeInfo.Builder(volMedia).setVolumeIndex(midIndex).build();

        // safe media can block the raising to volMid, disable it
        mAm.disableSafeMediaVolume();

        // verify set volume is what get returns
        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(volMin, BT_DEV);
        } else {
            mADVmgr.setDeviceVolume(volMin, BT_DEV);
        }
        final SleepAssertIntEquals checkVol =
                new SleepAssertIntEquals(5000 /*maxWaitMs*/, 50 /*periodMs*/, mContext);
        checkVol.assertEqualsSleep(
                volMin.getVolumeIndex() /*expected*/,
                () -> mADVmgr.getDeviceVolume(volMid, BT_DEV).getVolumeIndex(),
                "After setting min volume:");

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(volMid, BT_DEV);
        } else {
            mADVmgr.setDeviceVolume(volMid, BT_DEV);
        }
        checkVol.assertEqualsSleep(
                volMid.getVolumeIndex() /*expected*/,
                () -> mADVmgr.getDeviceVolume(volMid, BT_DEV).getVolumeIndex(),
                "After setting mid volume:");

        if (useVolumeForDevice) {
            mADVmgr.adjustVolumeForDevice(volMid, ADJUST_RAISE, BT_DEV);
            checkVol.assertEqualsSleep(
                    volMid.getVolumeIndex() + 1 /*expected*/,
                    () -> mADVmgr.getDeviceVolume(volMid, BT_DEV).getVolumeIndex(),
                    "After ADJUST_RAISE volume:");
        }

        // verify the range is set, and is correct
        final VolumeInfo currentVol = mADVmgr.getDeviceVolume(volMid, BT_DEV);
        assertFalse(
                "Returned min volume index is not set",
                VolumeInfo.INDEX_NOT_SET == currentVol.getMinVolumeIndex());
        assertFalse(
                "Returned max volume index is not set",
                VolumeInfo.INDEX_NOT_SET == currentVol.getMaxVolumeIndex());
        assertEquals(
                "Min possible volume index unexpected:",
                volMid.getMinVolumeIndex(),
                currentVol.getMinVolumeIndex());
        assertEquals(
                "Max possible volume index unexpected:",
                volMid.getMaxVolumeIndex(),
                currentVol.getMaxVolumeIndex());
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteVolumeBehavior_noMatchingStreamType() {
        testSetDeviceAbsoluteVolumeBehavior_noMatchingStreamType(/* useVolumeForDevice= */ false);
    }

    @RequiresFlagsEnabled({FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT, FLAG_DEVICE_VOLUME_APIS})
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteVolumeBehavior_noMatchingStreamType_useVolumeForDevice() {
        testSetDeviceAbsoluteVolumeBehavior_noMatchingStreamType(/* useVolumeForDevice= */ true);
    }

    public void testSetDeviceAbsoluteVolumeBehavior_noMatchingStreamType(
            boolean useVolumeForDevice) {
        if (mSkipRingerTests) {
            return;
        }
        final VolumeInfo volMedia = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build();
        final VolumeInfo volNotif =
                new VolumeInfo.Builder(AudioManager.STREAM_NOTIFICATION).build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo nextVolume =
                computeNewVolumeWithMute(volMedia, /* mute= */ false, BT_SCO_DEV);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_SCO_DEV, volNotif, mContext.getMainExecutor(), listener);

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(nextVolume, BT_SCO_DEV);
        } else {
            mADVmgr.setDeviceVolume(nextVolume, BT_SCO_DEV);
        }

        assertNull(listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteVolumeBehavior_matchingStreamType() {
        testSetDeviceAbsoluteVolumeBehavior_matchingStreamType(/* useVolumeForDevice= */ false);
    }

    @RequiresFlagsEnabled({FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT, FLAG_DEVICE_VOLUME_APIS})
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteVolumeBehavior_matchingStreamType_useVolumeForDevice() {
        testSetDeviceAbsoluteVolumeBehavior_matchingStreamType(/* useVolumeForDevice= */ true);
    }

    public void testSetDeviceAbsoluteVolumeBehavior_matchingStreamType(boolean useVolumeForDevice) {
        if (mSkipRingerTests) {
            return;
        }
        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo nextVolume =
                computeNewVolumeWithMute(volMedia, /* mute= */ false, BT_SCO_DEV);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_SCO_DEV, volMedia, mContext.getMainExecutor(), listener);

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(nextVolume, BT_SCO_DEV);
        } else {
            mADVmgr.setDeviceVolume(nextVolume, BT_SCO_DEV);
        }

        checkIndexVolumeInfoEquals(
                nextVolume,
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
    }

    @RequiresFlagsEnabled({FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT, FLAG_DEVICE_VOLUME_APIS})
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteVolumeBehavior_muteOnActiveDevice() {
        if (mSkipRingerTests) {
            return;
        }
        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        // when muting we get a callback for the mute state and then for the index adjustment
        final AudioDeviceVolumeChangedListener listener =
                new AudioDeviceVolumeChangedListener(/* numberOfCallbacks= */ 2);
        final VolumeInfo nextVolume =
                computeNewVolumeWithMute(volMedia, /* mute= */ true, SPEAKER_DEV);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                SPEAKER_DEV, volMedia, mContext.getMainExecutor(), listener);
        mADVmgr.setVolumeForDevice(nextVolume, SPEAKER_DEV);

        checkIsMutedVolumeInfo(
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS),
                /* activeDevice= */ true,
                /* volumeIndexWithoutMute= */ nextVolume.getVolumeIndex(),
                /* useVolumeForDevice= */ true);
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteMultiVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteMultiVolumeBehavior() {
        testSetDeviceAbsoluteMultiVolumeBehavior(/* useVolumeForDevice= */ false);
    }

    @RequiresFlagsEnabled({FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT, FLAG_DEVICE_VOLUME_APIS})
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void setDeviceAbsoluteMultiVolumeBehavior_useVolumeForDevice() {
        testSetDeviceAbsoluteMultiVolumeBehavior(/* useVolumeForDevice= */ true);
    }

    public void testSetDeviceAbsoluteMultiVolumeBehavior(boolean useVolumeForDevice) {
        if (mSkipRingerTests) {
            return;
        }
        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        final VolumeInfo volNotif =
                new VolumeInfo.Builder(AudioManager.STREAM_NOTIFICATION).build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        // set mute to true to check the VolumeInfo callback with the new mute state
        final VolumeInfo nextVolume =
                computeNewVolumeWithMute(volMedia, /* mute= */ true, BT_SCO_DEV);

        mADVmgr.setDeviceAbsoluteMultiVolumeBehavior(
                BT_SCO_DEV,
                ImmutableList.of(volNotif, volMedia),
                mContext.getMainExecutor(),
                listener);

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(nextVolume, BT_SCO_DEV);
        } else {
            mADVmgr.setDeviceVolume(nextVolume, BT_SCO_DEV);
        }

        checkIsMutedVolumeInfo(
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS),
                /* activeDevice= */ false,
                /* volumeIndexWithoutMute= */ nextVolume.getVolumeIndex(),
                useVolumeForDevice);
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#notifyAbsoluteVolumeChanged",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void testNotifyAbsoluteVolumeChanged() {
        if (mSkipRingerTests) {
            return;
        }
        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo nextVolume = computeNewVolumeWithMute(volMedia, /* mute= */ false, BT_DEV);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_DEV, volMedia, mContext.getMainExecutor(), listener);
        mADVmgr.notifyAbsoluteVolumeChanged(nextVolume, BT_DEV);

        // no listener will be triggered since we notify that volume changed
        assertNull(listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
        VolumeInfo actualVolume = mADVmgr.getDeviceVolume(volMedia, BT_DEV);
        assertEquals(nextVolume.getVolumeIndex(), actualVolume.getVolumeIndex());
        assertEquals(nextVolume.getMinVolumeIndex(), actualVolume.getMinVolumeIndex());
        assertEquals(nextVolume.getMaxVolumeIndex(), actualVolume.getMaxVolumeIndex());
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void callbackForSameTypeDifferentVolumeBehaviour() {
        testCallbackForSameTypeDifferentVolumeBehaviour(/* useVolumeForDevice= */ false);
    }

    @RequiresFlagsEnabled({FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT, FLAG_DEVICE_VOLUME_APIS})
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setVolumeForDevice",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    @Test
    public void callbackForSameTypeDifferentVolumeBehaviour_useVolumeForDevice() {
        testCallbackForSameTypeDifferentVolumeBehaviour(/* useVolumeForDevice= */ true);
    }

    public void testCallbackForSameTypeDifferentVolumeBehaviour(boolean useVolumeForDevice) {
        if (mSkipRingerTests) {
            return;
        }
        final int minIndex = mAm.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = mAm.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        // set mute to true to check the VolumeInfo callback with the new mute state
        final VolumeInfo nextVolume = computeNewVolumeWithMute(volMedia, /* mute= */ true, BT_DEV);
        AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_DEV, volMedia, mContext.getMainExecutor(), listener);

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(nextVolume, BT_DEV2);
        } else {
            mADVmgr.setDeviceVolume(nextVolume, BT_DEV2);
        }
        assertNull(listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));

        // reset the mute state
        mAm.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, /* flags= */ 0);

        if (useVolumeForDevice) {
            mADVmgr.setVolumeForDevice(nextVolume, BT_DEV);
        } else {
            mADVmgr.setDeviceVolume(nextVolume, BT_DEV);
        }

        checkIsMutedVolumeInfo(
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS),
                /* activeDevice= */ false,
                /* volumeIndexWithoutMute= */ nextVolume.getVolumeIndex(),
                useVolumeForDevice);
    }

    private void checkIndexVolumeInfoEquals(VolumeInfo expected, VolumeInfo info) {
        assertEquals(expected.getVolumeIndex(), info.getVolumeIndex());
    }

    private void checkIsMutedVolumeInfo(
            VolumeInfo info,
            boolean activeDevice,
            int volumeIndexWithoutMute,
            boolean useVolumeForDevice) {
        if (!useVolumeForDevice || !activeDevice) {
            assertEquals(info.getMinVolumeIndex(), info.getVolumeIndex());
        } else {
            assertTrue(info.isMuted());
            assertEquals(volumeIndexWithoutMute, info.getVolumeIndex());
        }
    }

    /** Return current and new volume info with different volume index and passed mute state. */
    private VolumeInfo computeNewVolumeWithMute(
            VolumeInfo volumeInfo, boolean mute, AudioDeviceAttributes ada) {
        final VolumeInfo curVolume = mADVmgr.getDeviceVolume(volumeInfo, ada);
        final int newVolumeIndex =
                curVolume.getVolumeIndex() < curVolume.getMaxVolumeIndex()
                        ? curVolume.getVolumeIndex() + 1
                        : curVolume.getMinVolumeIndex();
        return new VolumeInfo.Builder(volumeInfo)
                .setVolumeIndex(newVolumeIndex)
                .setMuted(mute)
                .build();
    }
}
