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

package android.voiceinteraction.cts;

import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.google.common.truth.Truth.assertThat;
import static android.voiceinteraction.cts.testcore.Helper.createFakeAudioFormat;
import static android.voiceinteraction.cts.testcore.Helper.createFakeKeyphraseRecognitionExtraList;

import android.os.PersistableBundle;
import android.os.SystemClock;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.PccHotwordDetectionService;
import android.voiceinteraction.cts.services.VoiceInteractionServiceForPccHotwordDetection;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PCC services are not available for instant apps")
public class PccHotwordDetectionServiceTest {

    private static final String TAG = "PccHotwordDetectionServiceTest";
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.VoiceInteractionServiceForPccHotwordDetection";

    private VoiceInteractionServiceForPccHotwordDetection mService;
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    public String getTestVoiceInteractionService() {
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        mService = (VoiceInteractionServiceForPccHotwordDetection) BaseVoiceInteractionService.getService();
        Objects.requireNonNull(mService);
    }

    @After
    public void tearDown() {
        if (mAlwaysOnHotwordDetector != null) {
            mAlwaysOnHotwordDetector.destroy();
        }
        mService = null;
    }

    @Test
    @RequiresFlagsEnabled({
            android.app.privatecompute.flags.Flags.FLAG_ADOPT_PCC_FRAMEWORK_FOR_HOTWORD_DETECTION,
            android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT})
    public void testHotwordDetectionService_isRunningInPcc() throws Throwable {
        mService.createAlwaysOnHotwordDetector();
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        mAlwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(mAlwaysOnHotwordDetector);

        PersistableBundle options = new PersistableBundle();
        options.putInt(
                PccHotwordDetectionService.KEY_TEST_SCENARIO,
                PccHotwordDetectionService.SCENARIO_VERIFY_PCC_PROPERTIES);

        // TODO(b/456148618): Use bedstead instead
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.RECORD_AUDIO,
                        android.Manifest.permission.CAPTURE_AUDIO_HOTWORD,
                        android.Manifest.permission.MANAGE_HOTWORD_DETECTION);
        try {
            mAlwaysOnHotwordDetector.updateState(options, null);

            // Trigger detection and wait for results
            mService.initDetectRejectLatch();
            mAlwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                    /* status */ 0, // 0 for success
                    /* soundModelHandle */ 1,
                    /* halEventReceivedMillis */ SystemClock.elapsedRealtime(),
                    /* captureAvailable */ true,
                    /* captureSession */ 101, // A non-zero session ID
                    /* captureDelayMs */ 100,
                    /* capturePreambleMs */ 200,
                    /* triggerInData */ true,
                    createFakeAudioFormat(), // A valid AudioFormat object
                    new byte[1024], // Non-empty audio data
                    createFakeKeyphraseRecognitionExtraList()); // Recognition details
            mService.waitOnDetectOrRejectCalled();
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }

        // Verify the results
        AlwaysOnHotwordDetector.EventPayload payload = mService.getHotwordServiceOnDetectedResult();
        assertThat(payload).isNotNull();
        HotwordDetectedResult result = payload.getHotwordDetectedResult();
        assertThat(result).isNotNull();

        Log.d(TAG, "Received score: " + result.getScore());
        Log.d(TAG, "Received personalized score: " + result.getPersonalizedScore());

        assertThat(result.getScore())
                .isEqualTo(PccHotwordDetectionService.RESULT_CODE_PCC_UID_SUCCESS);
        assertThat(result.getPersonalizedScore())
                .isEqualTo(PccHotwordDetectionService.RESULT_CODE_ISOLATED_SUCCESS);
    }
}
