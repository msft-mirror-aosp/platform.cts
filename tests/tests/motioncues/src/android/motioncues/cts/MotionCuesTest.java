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

package android.motioncues.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.systemui.dump.nano.SystemUIProtoDump;
import com.android.systemui.motioncues.nano.MotionCueState;
import com.android.systemui.motioncues.nano.MotionBubble;

import android.Manifest;
import android.app.Flags;
import android.app.StatusBarManager;
import android.app.UiAutomation;
import android.app.motioncues.MotionCuesVisualStyle;
import android.app.motioncues.MotionCuesSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class MotionCuesTest {
    private static final String TAG = "MotionCuesTest";
    private static final int TIMEOUT_MS = 15000;
    private static final MotionCuesSettings DEFAULT_MOTION_CUES_SETTINGS = new MotionCuesSettings.Builder()
            .setHorizontalSpacingDp(60)
            .setVerticalSpacingDp(140)
            .setMarginSizeDp(20)
            .setRadiusDp(15)
            .build();
    private Context mContext;
    private StatusBarManager mStatusBarManager;
    private UiAutomation mUiAutomation;
    private ComponentName mClientServiceComponent;

    @Rule
    public final CheckFlagsRule mFlags = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private boolean isAutomotive() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean isWatch() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private boolean isTv() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // Motion cues is not supported on automotive, watch, or TV.
        Assume.assumeFalse(isAutomotive() || isWatch() || isTv());

        mStatusBarManager = mContext.getSystemService(StatusBarManager.class);
        assertThat(mStatusBarManager).isNotNull();

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        mClientServiceComponent = new ComponentName(mContext, TestMotionCuesService.class);
        TestMotionCuesService.reset();

        // Ensure we're in the primary user.
        TestApis.users().primary().switchTo();
    }

    @After
    public void tearDown() {
        try {
            if (TestMotionCuesService.isConnected()) {
                endSessionAndAwait();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to end motion cues session during test cleanup", e);
        }
    }

   @Test
   @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession", "android.app.StatusBarManager#endMotionCuesSession"})
   @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
   @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
   public void testStartAndEndMotionCuesSession() throws Exception {
       startSessionAndAwait();
       assertThat(TestMotionCuesService.isConnected()).isTrue();

       endSessionAndAwait();
       assertThat(TestMotionCuesService.isConnected()).isFalse();
   }

   @Test
   @ApiTest(apis = {"android.app.StatusBarManager#endMotionCuesSession"})
   @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
   @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testEndSessionWithoutStart() throws Exception {
        endSessionAndAwait();

        assertThat(TestMotionCuesService.isConnected()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testSessionStart_initialStateIsCorrect() throws Exception {
        startSessionAndAwait();

        MotionCueState state = getMotionCuesState();
        assertThat(state.isStarted).isTrue();
        assertThat(state.clientPackageName).isEqualTo(mContext.getPackageName());
        assertThat(state.horizontalSpacingDp).isEqualTo(
                DEFAULT_MOTION_CUES_SETTINGS.getHorizontalSpacingDp());
        assertThat(state.verticalSpacingDp).isEqualTo(
                DEFAULT_MOTION_CUES_SETTINGS.getVerticalSpacingDp());
        assertThat(state.marginSizeDp).isEqualTo(DEFAULT_MOTION_CUES_SETTINGS.getMarginSizeDp());
        assertThat(state.radiusDp).isEqualTo(DEFAULT_MOTION_CUES_SETTINGS.getRadiusDp());
        assertThat(state.motionBubbles).isNotEmpty();
    }

    @Test(expected = SecurityException.class)
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureDoesNotHavePermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testStartSession_withoutPermission_throwsSecurityException() {
        mStatusBarManager.startMotionCuesSession(mClientServiceComponent,
                DEFAULT_MOTION_CUES_SETTINGS);
    }

    @Test(expected = SecurityException.class)
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testStartSession_withNonExistentService_cleansUpState() {
        ComponentName nonExistentComponent = new ComponentName(
                "com.android.nonexistent", "com.android.nonexistent.NonExistentService");

        mStatusBarManager.startMotionCuesSession(nonExistentComponent,
                DEFAULT_MOTION_CUES_SETTINGS);

        assertThat(getMotionCuesState().isStarted).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesClient#updateMotionCuesVisualStyle"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testDataUpdate_updatesColorAndShape() throws Exception {
        TestMotionCuesService service = startSessionAndAwait();

        MotionCuesVisualStyle data = new MotionCuesVisualStyle(Color.BLUE, R.drawable.ic_test);
        service.updateMotionCuesVisualStyle(data);

        MotionCueState state = getMotionCuesState();
        assertThat(state.paintColor).isEqualTo(String.format("#%08X", Color.BLUE));
        assertThat(state.bubbleShapeResId).isEqualTo(R.drawable.ic_test);
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesClient#updateMotionCuesVisualStyle"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testDataUpdate_withInvalidShape_fallsBackToDefault() throws Exception {
        TestMotionCuesService service = startSessionAndAwait();
        assertThat(getMotionCuesState().bubbleShapeResId).isEqualTo(0);

        MotionCuesVisualStyle invalidDrawableData = new MotionCuesVisualStyle(Color.RED, -1);
        service.updateMotionCuesVisualStyle(invalidDrawableData);

        assertThat(getMotionCuesState().bubbleShapeResId).isEqualTo(0);
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesClient#updateBubblePixelPos"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testPositionUpdate_movesBubblesCorrectly() throws Exception {
        TestMotionCuesService service = startSessionAndAwait();
        MotionCueState initialState = getMotionCuesState();

        float dx = 15.0f;
        float dy = 25.0f;
        service.updateBubblePixelPos(dx, dy);
        MotionCueState updatedState = getMotionCuesState();

        for (int i = 0; i < initialState.motionBubbles.length; i++) {
            MotionBubble initialBubble = initialState.motionBubbles[i];
            MotionBubble updatedBubble = updatedState.motionBubbles[i];
            assertThat(updatedBubble.x).isWithin(0.01f).of(initialBubble.x + dx);
            assertThat(updatedBubble.y).isWithin(0.01f).of(initialBubble.y + dy);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#endMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testSessionEnd_clearsState() throws Exception {
        startSessionAndAwait();
        endSessionAndAwait();

        MotionCueState state = getMotionCuesState();
        assertThat(state.isStarted).isFalse();
        assertThat(state.clientPackageName).isEmpty();
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testStartSession_whenAlreadyActive_isIgnored() throws Exception {
        // Start a session and confirm it's active with the default settings.
        startSessionAndAwait();
        MotionCueState initialState = getMotionCuesState();
        assertThat(initialState.isStarted).isTrue();
        assertThat(initialState.radiusDp).isEqualTo(DEFAULT_MOTION_CUES_SETTINGS.getRadiusDp());

        // Attempt to start another session with different settings.
        MotionCuesSettings differentSettings = new MotionCuesSettings.Builder()
                .setRadiusDp(99)
                .build();
        mStatusBarManager.startMotionCuesSession(mClientServiceComponent, differentSettings);

        // Verify that the session is still active and that the settings have NOT changed,
        // proving that the second start call was ignored.
        MotionCueState stateAfterSecondCall = getMotionCuesState();
        assertThat(stateAfterSecondCall.isStarted).isTrue();
        assertThat(stateAfterSecondCall.radiusDp).isEqualTo(initialState.radiusDp);
        assertThat(stateAfterSecondCall.radiusDp).isNotEqualTo(differentSettings.getRadiusDp());
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesClient#updateBubblePixelPos"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testSession_withContinuousPositionUpdates() throws Exception {
        TestMotionCuesService service = startSessionAndAwait();
        service.updateMotionCuesVisualStyle(new MotionCuesVisualStyle(Color.GREEN, R.drawable.ic_test));

        long startTime = System.currentTimeMillis();
        long duration = 10000;
        Random random = new Random();
        float dx = 0f;
        float dy = 0f;

        while (System.currentTimeMillis() - startTime < duration) {
            float jumpDx = random.nextFloat() * 0.5f;
            float jumpDy = random.nextFloat() * 0.5f;

            dx = Math.max(-1f, Math.min(1f, dx + jumpDx));
            dy = Math.max(-1f, Math.min(1f, dy + jumpDy));

            MotionCueState initialState = getMotionCuesState();
            service.updateBubblePixelPos(dx, dy);
            MotionCueState updatedState = getMotionCuesState();

            for (int i = 0; i < initialState.motionBubbles.length; i++) {
                MotionBubble initialBubble = initialState.motionBubbles[i];
                MotionBubble updatedBubble = updatedState.motionBubbles[i];
                assertThat(updatedBubble.x).isWithin(0.01f).of(initialBubble.x + dx);
                assertThat(updatedBubble.y).isWithin(0.01f).of(initialBubble.y + dy);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted");
            }
        }
        endSessionAndAwait();
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testServiceDisabled_cleansUpState() throws Exception {
        startSessionAndAwait();
        PollingCheck.waitFor(TIMEOUT_MS, () -> getMotionCuesState().isStarted);
        assertThat(getMotionCuesState().isStarted).isTrue();

        try {
            // Trigger the onBindingDied callback.
            setServiceEnabled(false);

            PollingCheck.waitFor(TIMEOUT_MS, () -> !getMotionCuesState().isStarted);
            MotionCueState finalState = getMotionCuesState();
            assertThat(finalState.isStarted).isFalse();
            assertThat(finalState.clientPackageName).isEmpty();
        } finally {
            // Re-enable the service so other tests are not affected.
            setServiceEnabled(true);
        }
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testSessionEnds_onUserSwitchFromPrimary() throws Exception {
        // Start a session as the primary user
        startSessionAndAwait();
        PollingCheck.waitFor(TIMEOUT_MS, () -> getMotionCuesState().isStarted);
        assertThat(getMotionCuesState().isStarted).isTrue();

        // Create and switch to a secondary user
        UserReference secondaryUser = TestApis.users().createUser().createAndStart();
        try {
            secondaryUser.switchTo();

            // Verify the session has ended
            PollingCheck.waitFor(TIMEOUT_MS, () -> !getMotionCuesState().isStarted);
            MotionCueState finalState = getMotionCuesState();
            assertThat(finalState.isStarted).isFalse();
            assertThat(finalState.clientPackageName).isEmpty();
        } finally {
            TestApis.users().initial().switchTo();
            PollingCheck.waitFor(TIMEOUT_MS,
                    () -> TestApis.users().current().id() == TestApis.users().initial().id());
            secondaryUser.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.app.StatusBarManager#startMotionCuesSession"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MOTION_CUES)
    @EnsureHasPermission(Manifest.permission.DRAW_MOTION_CUES)
    public void testSessionEnds_onUserSwitchToPrimary() throws Exception {
        // Create and switch to a secondary user
        try (UserReference secondaryUser = TestApis.users().createUser().createAndStart()) {
            secondaryUser.switchTo();

            // Start a session as the secondary user
            startSessionAndAwait();
            PollingCheck.waitFor(TIMEOUT_MS, () -> getMotionCuesState().isStarted);
            assertThat(getMotionCuesState().isStarted).isTrue();

            // Switch back to the primary user
            TestApis.users().initial().switchTo();

            // Verify the session has ended
            PollingCheck.waitFor(TIMEOUT_MS, () -> !getMotionCuesState().isStarted);
            MotionCueState finalState = getMotionCuesState();
            assertThat(finalState.isStarted).isFalse();
            assertThat(finalState.clientPackageName).isEmpty();
        }
    }

    private TestMotionCuesService startSessionAndAwait() throws Exception {
        mStatusBarManager.startMotionCuesSession(mClientServiceComponent,
                DEFAULT_MOTION_CUES_SETTINGS);
        TestMotionCuesService service = TestMotionCuesService.awaitInstance();
        assertThat(service).isNotNull();
        return service;
    }

    private void endSessionAndAwait() throws Exception {
        mStatusBarManager.endMotionCuesSession();
        TestMotionCuesService.awaitDisconnected();
    }

    private MotionCueState getMotionCuesState() {
        try {
            ParcelFileDescriptor pfd = mUiAutomation.executeShellCommand(
                    "dumpsys statusbar MotionCuesManager --proto");
            byte[] buf = new byte[4096];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return SystemUIProtoDump.parseFrom(stdout.toByteArray()).motionCueState;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setServiceEnabled(boolean enabled) {
        int state = enabled
                ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        mContext.getPackageManager().setComponentEnabledSetting(
                mClientServiceComponent, state, android.content.pm.PackageManager.DONT_KILL_APP);
    }
}

