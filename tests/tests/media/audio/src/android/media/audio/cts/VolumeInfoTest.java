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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.audio.Flags;
import android.media.audiopolicy.AudioVolumeGroup;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@FrameworkSpecificTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class VolumeInfoTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "VolumeInfoTest";
    private static final int MIN_VOL = 0;
    private static final int MAX_VOL = 100;
    private static final int SET_VOL = 77;

    /**
     * Verify marshalled VolumeInfo has the same information as the original when using stream
     * types.
     *
     * @throws Exception
     */
    @ApiTest(
            apis = {
                "android.media.VolumeInfo#getStreamType",
                "android.media.VolumeInfo#hasStreamType",
                "android.media.VolumeInfo#getStreamType",
                "android.media.VolumeInfo#hasVolumeGroup",
                "android.media.VolumeInfo#getVolumeGroup",
                "android.media.VolumeInfo#getMinVolumeIndex",
                "android.media.VolumeInfo#getMaxVolumeIndex",
                "android.media.VolumeInfo#getVolumeIndex",
                "android.media.VolumeInfo#hasMuteState",
                "android.media.VolumeInfo#hasMuteCommand",
                "android.media.VolumeInfo#isMuted",
                "android.media.VolumeInfo.Builder#build"
            })
    @Test
    public void testStreamTypeParcelableWriteToParcelCreate() throws Exception {
        // test VolumeInfo with stream type, no mute command
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                false /*has mute*/, true /*ignored*/);
        // test VolumeInfo with stream type, mute command set to mute
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                true /*has mute*/, true /*mute*/);
        // test VolumeInfo with stream type, mute command set to unmute
        exerciseParcelableWriteToParcelCreate(AudioManager.STREAM_MUSIC, null /*AudioVolumeGroup*/,
                true /*has mute*/, false /*mute*/);
    }

    /**
     * Verify marshalled VolumeInfo has the same information as the original when using
     * AudioVolumeGroup.
     *
     * @throws Exception
     */
    @ApiTest(
            apis = {
                "android.media.VolumeInfo#getStreamType",
                "android.media.VolumeInfo#hasStreamType",
                "android.media.VolumeInfo#getStreamType",
                "android.media.VolumeInfo#hasVolumeGroup",
                "android.media.VolumeInfo#getVolumeGroup",
                "android.media.VolumeInfo#getMinVolumeIndex",
                "android.media.VolumeInfo#getMaxVolumeIndex",
                "android.media.VolumeInfo#getVolumeIndex",
                "android.media.VolumeInfo#hasMuteState",
                "android.media.VolumeInfo#hasMuteCommand",
                "android.media.VolumeInfo#isMuted",
                "android.media.VolumeInfo.Builder#build"
            })
    @Test
    public void testVolGroupParcelableWriteToParcelCreate() throws Exception {
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
            List<AudioVolumeGroup> groups = AudioManager.getAudioVolumeGroups();
            if (groups.isEmpty()) {
                Log.i(TAG, "no AudioVolumeGroup to use for testing VolumeInfo");
                return;
            }
            final AudioVolumeGroup group = groups.get(0);
            // test VolumeInfo with volume group, no mute command
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    false /*has mute*/, true /*ignored*/);
            // test VolumeInfo with volume group, mute command set to mute
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    true /*has mute*/, true /*mute*/);
            // test VolumeInfo with volume group, mute command set to unmute
            exerciseParcelableWriteToParcelCreate(0, group /*AudioVolumeGroup*/,
                    true /*has mute*/, false /*mute*/);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void exerciseParcelableWriteToParcelCreate(int streamType, AudioVolumeGroup group,
            boolean hasMute, boolean mute) throws Exception {
        final VolumeInfo.Builder srcVIB;
        if (group == null) {
            srcVIB = new VolumeInfo.Builder(streamType);
        } else {
            srcVIB = new VolumeInfo.Builder(group);
        }
        srcVIB.setMinVolumeIndex(MIN_VOL)
                .setMaxVolumeIndex(MAX_VOL)
                .setVolumeIndex(SET_VOL);
        if (hasMute) {
            srcVIB.setMuted(mute);
        }
        final VolumeInfo srcVI = srcVIB.build();
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcVI.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final VolumeInfo targetVI = VolumeInfo.CREATOR.createFromParcel(dstParcel);

        // test getters
        assertEquals(group == null, srcVI.hasStreamType());
        assertEquals(group != null, srcVI.hasVolumeGroup());
        assertEquals("Marshalled/restored hasStreamType doesn't match for " + srcVI,
                srcVI.hasStreamType(), targetVI.hasStreamType());
        if (group == null) { // using stream type
            assertEquals("Marshalled/restored stream type doesn't match for " + srcVI,
                    srcVI.getStreamType(), targetVI.getStreamType());
            assertFalse(srcVI.hasVolumeGroup());
        } else { // using AudioVolumeGroup
            assertEquals("Marshalled/restored volume group doesn't match for " + srcVI,
                    srcVI.getVolumeGroup(), targetVI.getVolumeGroup());
            assertFalse(srcVI.hasStreamType());
        }
        assertEquals("Marshalled/restored min volume index doesn't match for " + srcVI,
                srcVI.getMinVolumeIndex(), targetVI.getMinVolumeIndex());
        assertEquals("Marshalled/restored max volume index doesn't match for " + srcVI,
                srcVI.getMaxVolumeIndex(), targetVI.getMaxVolumeIndex());
        assertEquals("Marshalled/restored volume index doesn't match for " + srcVI,
                srcVI.getVolumeIndex(), targetVI.getVolumeIndex());
        if (Flags.deviceVolumeApis()) {
            assertEquals(
                    "Marshalled/restored has mute command doesn't match for " + srcVI,
                    srcVI.hasMuteState(),
                    targetVI.hasMuteState());
        } else {
            assertEquals(
                    "Marshalled/restored has mute command doesn't match for " + srcVI,
                    srcVI.hasMuteCommand(),
                    targetVI.hasMuteCommand());
        }
        if (hasMute) {
            assertEquals("set source mute command not as retrieved for " + srcVI,
                    mute, srcVI.isMuted());
            assertEquals("Marshalled/restored mute command doesn't match for " + srcVI,
                    srcVI.isMuted(), targetVI.isMuted());
        }

        // test equality
        assertEquals(srcVI, targetVI);
    }

    @ApiTest(
            apis = {
                "android.media.VolumeInfo#getDefaultVolumeInfo",
                "android.media.VolumeInfo#hasStreamType",
                "android.media.VolumeInfo#hasVolumeGroup"
            })
    @Test
    public void testDefaultVolInfo() throws Exception {
        final VolumeInfo defaultVI = VolumeInfo.getDefaultVolumeInfo();
        assertNotNull(defaultVI);
        boolean hasStream = defaultVI.hasStreamType();
        assertEquals(!hasStream, defaultVI.hasVolumeGroup());
    }
}
