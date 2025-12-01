/*
 * Copyright (C) 2021 The Android Open Source Project
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


import static com.google.common.truth.Truth.assertThat;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.text.TextUtils;
import android.voiceinteraction.cts.testcore.Helper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Tests for successfully changing ROLE_ASSISTANT. The test focuses on changing ROLE_ASSISTANT role,
 * the target voice interaction services do nothing during the test.
 */
@AppModeFull(reason = "No need for testing role for instant app")
@RunWith(AndroidJUnit4.class)
public class VoiceInteractionRoleTest {
    private static final String VOICE_INTERACTION_HAS_RECOGNITION_SERVICE =
            Helper.CTS_SERVICE_PACKAGE;
    private static final String VOICE_INTERACTION_NO_RECOGNITION_SERVICE =
            "android.voiceinteraction.norecognition";

    private static Context sContext;
    private static RoleManager sRoleManager;

    List<String> mOriginalRoleHolders;

    @BeforeClass
    public static void oneTimeSetup() {
        sContext = ApplicationProvider.getApplicationContext();
        sRoleManager = sContext.getSystemService(RoleManager.class);
    }

    @Before
    public void setup() throws Exception {
        mOriginalRoleHolders = Helper.getAssistRoleHolders(sRoleManager);
    }

    @After
    public void cleanup() throws Exception {
        if (mOriginalRoleHolders != null && mOriginalRoleHolders.size() > 0) {
            // Restore to original, assistant is singleton role
            Helper.addAssistRoleHolder(mOriginalRoleHolders.get(0), sContext, sRoleManager);
        }
    }

    @Test
    public void testAssistRole_hasRecognitionService() throws Exception {
        roleTestingForPackage(VOICE_INTERACTION_HAS_RECOGNITION_SERVICE, /* hasRecognition= */
                true);
    }

    @Test
    public void testAssistRole_noRecognitionService() throws Exception {
        roleTestingForPackage(VOICE_INTERACTION_NO_RECOGNITION_SERVICE, /* hasRecognition= */
                false);
    }

    // TODO: Use helpers and move the assertion in Test instead of move together
    private void roleTestingForPackage(String packageName, boolean hasRecognition)
            throws Exception {
        assertThat(Helper.getAssistRoleHolders(sRoleManager)).doesNotContain(packageName);

        Helper.addAssistRoleHolder(packageName, sContext, sRoleManager);
        if (mOriginalRoleHolders != null && mOriginalRoleHolders.size() > 0) {
            String originalHolder = mOriginalRoleHolders.get(0);
            Helper.removeAssistRoleHolder(originalHolder, sContext, sRoleManager);
            assertThat(Helper.getAssistRoleHolders(sRoleManager)).doesNotContain(originalHolder);
        }
        assertThat(Helper.getAssistRoleHolders(sRoleManager)).containsExactly(packageName);

        Callable<Boolean> condition = hasRecognition
                ? () -> !TextUtils.isEmpty(Settings.Secure.getString(sContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE))
                : () -> "".equals(Settings.Secure.getString(sContext.getContentResolver(),
                        Settings.Secure.VOICE_INTERACTION_SERVICE));
        PollingCheck.check("Make sure that Settings VOICE_INTERACTION_SERVICE "
                + "becomes available.", 500, condition);
        final String curVoiceInteractionComponentName = Settings.Secure.getString(
                sContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE);
        String curVoiceInteractionPackageName = "";
        if (!TextUtils.isEmpty(curVoiceInteractionComponentName)) {
            curVoiceInteractionPackageName =
                    ComponentName.unflattenFromString(
                            curVoiceInteractionComponentName).getPackageName();
        }
        assertThat(curVoiceInteractionPackageName).isEqualTo(hasRecognition ? packageName : "");

        Helper.removeAssistRoleHolder(packageName, sContext, sRoleManager);
        assertThat(Helper.getAssistRoleHolders(sRoleManager)).doesNotContain(packageName);
    }
}
