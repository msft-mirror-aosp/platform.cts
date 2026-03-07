/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.cts.multisensory;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.cts.multisensory.util.MultisensoryVibratorListener;
import android.os.multisensory.Flags;
import android.os.multisensory.MultisensoryManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_MULTISENSORY_FEEDBACK)
@RunWith(AndroidJUnit4.class)
public class MultisensoryManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE);

    private final MultisensoryVibratorListener mVibratorStateListener =
            new MultisensoryVibratorListener();

    private Vibrator mVibrator;
    private MultisensoryManager mUnderTest;

    @Before
    public void setUp() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mUnderTest = context.getSystemService(MultisensoryManager.class);
        VibratorManager vibratorManager = context.getSystemService(VibratorManager.class);
        mVibrator = vibratorManager.getDefaultVibrator();
        mVibrator.cancel();
        mVibrator.addVibratorStateListener(mVibratorStateListener);
    }

    @After
    public void cleanUp() throws InterruptedException {
        mVibrator.removeVibratorStateListener(mVibratorStateListener);
        mVibrator.cancel();
    }

    @Test
    public void playToken_forAllTokens_hapticsPlay() throws InterruptedException {
        int[] allTokens = MultisensoryManager.getMultisensoryTokens();
        List<Integer> failingTokens = new ArrayList<>();

        for (int tokenConstant : allTokens) {
            if (!verifyTokenDelivered(tokenConstant)) {
                failingTokens.add(tokenConstant);
            }
        }

        assertThat(failingTokens).isEmpty();
    }

    private boolean verifyTokenDelivered(int tokenConstant) throws InterruptedException {
        // Ensure the vibrator is idle before starting each token
        mVibratorStateListener.waitForIdle();
        mVibratorStateListener.reset();

        mUnderTest.playToken(tokenConstant);

        // In a device without haptics, playing a token is a no-op
        if (!mVibrator.hasVibrator()) return true;

        return mVibratorStateListener.awaitAnyVibration();
    }
}
