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

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.service.voice.VoiceInteractionSession.KEY_FOREGROUND_ACTIVITIES;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.createFakeAudioFormat;
import static android.voiceinteraction.cts.testcore.Helper.createFakeKeyphraseRecognitionExtraList;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VoiceInteractionSession;
import android.support.test.uiautomator.UiDevice;
import android.voiceinteraction.cts.activities.EmptyActivity;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.compatibility.common.util.SettingsStateKeeperRule;
import com.android.compatibility.common.util.SettingsStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Tests for self-triggered sessions in {@link android.service.voice.VoiceInteractionService}. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode")
public class VoiceInteractionServiceSelfTriggerTest {

    private static final String TAG = "VoiceInteractionServiceSelfTriggerTest";
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final SettingsStateKeeperRule mPublicServiceSettingsKeeper =
            new SettingsStateKeeperRule(
                    getInstrumentation().getTargetContext(), "assist_screenshot_enabled");

    private static final SettingsStateManager sScreenshotEnabledManager =
            new SettingsStateManager(
                    getInstrumentation().getTargetContext(), "assist_screenshot_enabled");

    protected final Context mContext = getInstrumentation().getTargetContext();
    private final UiDevice mUiDevice = UiDevice.getInstance(getInstrumentation());

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(mContext, getTestVoiceInteractionService());

    private CtsBasicVoiceInteractionService mService;

    public String getTestVoiceInteractionService() {
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        Objects.requireNonNull(mService);
        VoiceInteractionTestReceiver.reset();
        sScreenshotEnabledManager.set("1");
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            // Clean up detector if created
            AlwaysOnHotwordDetector detector = mService.getAlwaysOnHotwordDetector();
            if (detector != null) {
                detector.destroy();
            }
        }
        mService = null;
        // Ensure device is in a known state (e.g. home screen or test app)
        mUiDevice.pressHome();
    }

    @Test
    @RequiresFlagsEnabled(
            com.android.server.voiceinteraction.flags.Flags.FLAG_ENABLE_RESTRICT_VIS_SELF_TRIGGER)
    public void testSelfTrigger_background_noRecentHotword_assistStripped() throws Exception {
        // 1. background the test app
        mUiDevice.pressHome();
        mUiDevice.waitForIdle();

        // 2. Trigger session
        mService.showSession(
                new Bundle(),
                VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                        | VoiceInteractionSession.SHOW_WITH_ASSIST
                        | VoiceInteractionSession.SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        // 3. Verify NO assist data/screenshot
        assertHasNoAssistDataAndScreenshot();
    }

    @Test
    @RequiresFlagsEnabled(
            com.android.server.voiceinteraction.flags.Flags.FLAG_ENABLE_RESTRICT_VIS_SELF_TRIGGER)
    public void testSelfTrigger_background_recentHotword_assistAllowed() throws Exception {
        // 1. Create detector
        mService.createAlwaysOnHotwordDetector();
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        AlwaysOnHotwordDetector detector = mService.getAlwaysOnHotwordDetector();

        // 2. background the test app
        mUiDevice.pressHome();
        mUiDevice.waitForIdle();

        // 3. Trigger hotword

        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_HOTWORD_DETECTION)) {

            mService.initDetectRejectLatch();

            detector.triggerHardwareRecognitionEventForTest(

                    /* status= */ 0,
                    /* soundModelHandle= */ 100, /* halEventReceivedMillis */
                    12345,

                    /* captureAvailable= */ true,

                    /* captureSession= */ 101,

                    /* captureDelayMs= */ 1000,

                    /* capturePreambleMs= */ 1001,

                    /* triggerInData= */ true,
                    createFakeAudioFormat(),
                    new byte[1024],
                    createFakeKeyphraseRecognitionExtraList());

            mService.waitOnDetectOrRejectCalled();
        }

        // 4. Trigger session immediately
        mService.showSession(
                new Bundle(),
                VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                        | VoiceInteractionSession.SHOW_WITH_ASSIST
                        | VoiceInteractionSession.SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        // 5. Verify assist data/screenshot present
        assertHasAssistDataAndScreenshot();
    }

    @Test
    @RequiresFlagsEnabled(
            com.android.server.voiceinteraction.flags.Flags.FLAG_ENABLE_RESTRICT_VIS_SELF_TRIGGER)
    public void testSelfTrigger_foreground_noRecentHotword_assistAllowed() throws Exception {
        // 1. Bring test app to foreground
        Intent intent = new Intent(mContext, EmptyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestApis.activities().startActivity(intent);

        // 2. Trigger session
        mService.showSession(
                new Bundle(),
                VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                        | VoiceInteractionSession.SHOW_WITH_ASSIST
                        | VoiceInteractionSession.SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        // 3. Verify assist data/screenshot present
        assertHasAssistDataAndScreenshot();
    }

    private void assertHasNoAssistDataAndScreenshot() throws InterruptedException {
        assertAssistDataAvailability(false);
    }

    private void assertHasAssistDataAndScreenshot() throws InterruptedException {
        assertAssistDataAvailability(true);
    }

    private void assertAssistDataAvailability(boolean isAvailable) throws InterruptedException {
        boolean obtainedScreenshot =
                VoiceInteractionTestReceiver.waitScreenshotReceived(5, TimeUnit.SECONDS);
        boolean obtainedAssistData =
                VoiceInteractionTestReceiver.waitAssistDataReceived(5, TimeUnit.SECONDS);
        Bundle onShowArgs = VoiceInteractionTestReceiver.waitOnShowReceived(5, TimeUnit.SECONDS);

        // If we expect available, we assert true. If we expect unavailable, we assert false.
        assertThat(obtainedScreenshot).isEqualTo(isAvailable);
        assertThat(obtainedAssistData).isEqualTo(isAvailable);

        if (isAvailable) {
            assertThat(onShowArgs).isNotNull();
            assertThat(onShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isTrue();
        } else {
            // If not available, we might still get onShowArgs, but it shouldn't have the key,
            // OR we might not get onShow if the session fails to start?
            // The restriction strips flags, it doesn't prevent session start.
            // So onShow should happen.
            if (onShowArgs != null) {
                assertThat(onShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isFalse();
            }
        }
    }
}
