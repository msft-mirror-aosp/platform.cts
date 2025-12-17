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

import static android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST;
import static android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT;
import static android.service.voice.VoiceInteractionSession.SHOW_WITH_SCREENSHOT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.service.voice.flags.Flags;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.service.MainInteractionService;
import android.voiceinteraction.service.MainInteractionSession;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.SettingsStateManager;
import com.android.compatibility.common.util.StateKeeperRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode")
public class VoiceInteractionManifestAttributesTest {

    private static final String TAG = "VoiceInteractionManifestAttributesTest";
    private static final String SERVICE_PKG = "android.voiceinteraction.service";
    private static final String NO_PERMISSION_PKG = "android.voiceinteraction.nopermission";
    private static final long ENABLE_RESTRICT_ASSIST_STRUCTURE = 437416500L;

    private static final String ASSIST_STRUCTURE_ENABLED = "assist_structure_enabled";
    private static final SettingsStateManager sStructureEnabledMgr =
            new SettingsStateManager(
                    getInstrumentation().getTargetContext(), ASSIST_STRUCTURE_ENABLED);

    private static final String EXTRA_HAS_STRUCTURE = "has_structure";
    private static final String EXTRA_HAS_WINDOW_DATA = "has_window_data";
    private static final String EXTRA_HAS_VIEW_DATA = "has_view_data";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final StateKeeperRule<String> mStructureEnabledKeeperRule =
            new StateKeeperRule<>(sStructureEnabledMgr);

    private final Context mContext = getInstrumentation().getTargetContext();
    private String mOriginalService;

    @Before
    public void setup() {
        // Enable the compat change for the service packages
        runShellCommand("am compat enable " + ENABLE_RESTRICT_ASSIST_STRUCTURE + " " + SERVICE_PKG);
        runShellCommand(
                "am compat enable " + ENABLE_RESTRICT_ASSIST_STRUCTURE + " " + NO_PERMISSION_PKG);
        VoiceInteractionTestReceiver.reset();
        mOriginalService =
                Settings.Secure.getString(
                        mContext.getContentResolver(), Settings.Secure.VOICE_INTERACTION_SERVICE);
    }

    @After
    public void tearDown() {
        // Reset compat change
        runShellCommand("am compat reset " + ENABLE_RESTRICT_ASSIST_STRUCTURE + " " + SERVICE_PKG);
        runShellCommand(
                "am compat reset " + ENABLE_RESTRICT_ASSIST_STRUCTURE + " " + NO_PERMISSION_PKG);
        // Restore service
        if (mOriginalService != null) {
            runShellCommand(
                    "settings put secure "
                            + Settings.Secure.VOICE_INTERACTION_SERVICE
                            + " "
                            + mOriginalService);
        } else {
            runShellCommand("settings delete secure " + Settings.Secure.VOICE_INTERACTION_SERVICE);
        }
        // Clean up activities and services
        runShellCommand("am force-stop " + SERVICE_PKG);
        runShellCommand("am force-stop " + NO_PERMISSION_PKG);
        runShellCommand("am force-stop android.voiceinteraction.testapp");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES)
    public void testAssistDataOnly() throws Exception {
        String serviceClass = "android.voiceinteraction.service.MainInteractionServiceAssistData";
        String serviceComponent = SERVICE_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);
        startSession(
                serviceClass,
                SHOW_WITH_ASSIST
                        | SHOW_WITH_SCREENSHOT
                        | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
        // Expect ASSIST only. Structure is dependent on ASSIST, but
        // usesAssistStructureScreenContent is false.
        verifyFlags(SHOW_WITH_ASSIST);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES)
    public void testScreenshotOnly() throws Exception {
        String serviceClass =
                "android.voiceinteraction.service.MainInteractionServiceAssistScreenshot";
        String serviceComponent = SERVICE_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);
        startSession(
                serviceClass,
                SHOW_WITH_ASSIST
                        | SHOW_WITH_SCREENSHOT
                        | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
        verifyFlags(SHOW_WITH_SCREENSHOT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES)
    public void testAllDisabled() throws Exception {
        String serviceClass = "android.voiceinteraction.service.MainInteractionServiceAllDisabled";
        String serviceComponent = SERVICE_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);
        startSession(
                serviceClass,
                SHOW_WITH_ASSIST
                        | SHOW_WITH_SCREENSHOT
                        | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
        verifyFlags(0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES)
    public void testAllEnabled() throws Exception {
        String serviceClass = "android.voiceinteraction.service.MainInteractionServiceAllEnabled";
        String serviceComponent = SERVICE_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);
        startSession(
                serviceClass,
                SHOW_WITH_ASSIST
                        | SHOW_WITH_SCREENSHOT
                        | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
        verifyFlags(
                SHOW_WITH_ASSIST
                        | SHOW_WITH_SCREENSHOT
                        | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES,
        android.permission.flags.Flags.FLAG_RESTRICT_ASSIST_STRUCTURE_SCREEN_CONTENT_ENABLED
    })
    public void testAssistStructureOnly_hasPermission() throws Exception {
        String serviceClass =
                "android.voiceinteraction.service.MainInteractionServiceAssistStructure";
        String serviceComponent = SERVICE_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);
        startSession(
                SERVICE_PKG,
                "android.voiceinteraction.service.VoiceInteractionMain",
                serviceClass,
                SHOW_WITH_ASSIST | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);
        verifyFlags(SHOW_WITH_ASSIST | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        Bundle extras =
                VoiceInteractionTestReceiver.waitAssistDataReceivedBundle(5, TimeUnit.SECONDS);
        assertThat(extras).isNotNull();
        assertThat(extras.getBoolean(EXTRA_HAS_STRUCTURE)).isTrue();
        assertThat(extras.getBoolean(EXTRA_HAS_WINDOW_DATA)).isTrue();
        assertThat(extras.getBoolean(EXTRA_HAS_VIEW_DATA)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES,
        android.permission.flags.Flags.FLAG_RESTRICT_ASSIST_STRUCTURE_SCREEN_CONTENT_ENABLED
    })
    public void testAssistStructureOnly_noPermission() throws Exception {
        String serviceClass =
                "android.voiceinteraction.nopermission.NoPermissionInteractionService";
        String serviceComponent = NO_PERMISSION_PKG + "/" + serviceClass;

        startServiceSession(serviceComponent);

        // Use the trampoline in the norecognition package
        startSession(
                NO_PERMISSION_PKG,
                "android.voiceinteraction.nopermission.NoPermissionTrampolineActivity",
                serviceClass,
                SHOW_WITH_ASSIST | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        // Expect flags to be passed (permission check doesn't strip flags).
        verifyFlags(SHOW_WITH_ASSIST | SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT);

        // With NoPermissionTrampolineActivity visible, we should receive an assist structure,
        // but it should be sanitized (no view data) due to missing permission.
        Bundle extras =
                VoiceInteractionTestReceiver.waitAssistDataReceivedBundle(5, TimeUnit.SECONDS);
        assertThat(extras).isNotNull();
        assertThat(extras.getBoolean(EXTRA_HAS_STRUCTURE)).isTrue();
        assertThat(extras.getBoolean(EXTRA_HAS_WINDOW_DATA)).isTrue();
        assertThat(extras.getBoolean(EXTRA_HAS_VIEW_DATA)).isFalse();
    }

    private void startServiceSession(String component) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        latch.countDown();
                    }
                };
        mContext.registerReceiver(
                receiver,
                new IntentFilter(MainInteractionService.ACTION_READY),
                Context.RECEIVER_EXPORTED);

        try {
            runShellCommand(
                    "settings put secure "
                            + Settings.Secure.VOICE_INTERACTION_SERVICE
                            + " "
                            + component);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Service ready broadcast not received for " + component);
            }

            // Enable screen context as it is switched off by the system after after changing
            sStructureEnabledMgr.set("1");
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private void startSession(String serviceClass, int flags) {
        startSession(
                SERVICE_PKG,
                "android.voiceinteraction.service.VoiceInteractionMain",
                serviceClass,
                flags);
    }

    private void startSession(String pkg, String activityName, String serviceClass, int flags) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, activityName));
        intent.putExtra("target_service", serviceClass);
        intent.putExtra(Utils.KEY_TEST_EVENT, Utils.VIS_NORMAL_TEST);
        intent.putExtra("showFlags", flags);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestApis.activities().startActivity(intent);
    }

    private void verifyFlags(int expectedFlags) throws Exception {
        Bundle onShowArgs = VoiceInteractionTestReceiver.waitOnShowReceived(5, TimeUnit.SECONDS);
        assertThat(onShowArgs).isNotNull();
        int receivedFlags = onShowArgs.getInt(MainInteractionSession.EXTRA_SHOW_FLAGS);
        assertThat(receivedFlags).isEqualTo(expectedFlags);
    }
}
