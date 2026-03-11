/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.assist.cts;

import static android.assist.common.Utils.SHOW_SESSION_FLAGS_TO_SET;
import static android.service.voice.VoiceInteractionSession.KEY_FOREGROUND_ACTIVITIES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.Manifest;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionState;
import android.assist.common.AutoResetLatch;
import android.assist.common.Utils;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ExtraAssistDataTest extends AssistTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "ExtraAssistDataTest";
    private static final String TEST_CASE_TYPE = Utils.EXTRA_ASSIST;

    @Override
    protected void customSetup() throws Exception {
        startTestActivity(TEST_CASE_TYPE);
    }

    @Test
    public void testAssistContentAndAssistData() throws Exception {
        assumeIsNotLowRamDevice();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);
        final AutoResetLatch latch = startSession();
        waitForContext(latch);
        verifyAssistDataNullness(false, false, false, false);

        Log.i(TAG, "assist bundle is: " + Utils.toBundleString(mAssistBundle));

        // first tests that the assist content's structured data is the expected
        assertWithMessage(
                        "AssistContent structured data did not match data in"
                                + " onProvideAssistContent")
                .that(mAssistContent.getStructuredData())
                .isEqualTo(Utils.getStructuredJSON());
        Bundle extraExpectedBundle = Utils.getExtraAssistBundle();
        Bundle extraAssistBundle = mAssistBundle.getBundle(Intent.EXTRA_ASSIST_CONTEXT);
        for (String key : extraExpectedBundle.keySet()) {
            assertWithMessage("Assist bundle does not contain expected extra context key: %s", key)
                    .that(extraAssistBundle.containsKey(key))
                    .isTrue();
            assertWithMessage("Extra assist context bundle values do not match for key: %s", key)
                    .that(extraAssistBundle.get(key))
                    .isEqualTo(extraExpectedBundle.get(key));
        }

        // then test the EXTRA_ASSIST_UID
        int expectedUid = Utils.getExpectedUid(extraAssistBundle);
        int actualUid = mAssistBundle.getInt(Intent.EXTRA_ASSIST_UID);
        assertWithMessage("Wrong value for EXTRA_ASSIST_UID")
                .that(actualUid)
                .isEqualTo(expectedUid);

        // Verify KEY_FOREGROUND_ACTIVITIES was correctly provided in onShow args
        assertThat(mOnShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isTrue();
        ArrayList<ComponentName> foregroundApps =
                mOnShowArgs.getParcelableArrayList(KEY_FOREGROUND_ACTIVITIES);
        Log.i(TAG, "ForegroundActivityComponent:  " + foregroundApps);
        assertWithMessage("Foregrounded apps").that(foregroundApps).isNotEmpty();
        List<String> foregroundAppPackageNames =
                foregroundApps.stream().map(ComponentName::getPackageName).toList();
        assertWithMessage("Foregrounded test assistant app")
                .that(foregroundAppPackageNames)
                .contains("android.assist.testapp");
    }

    @Test
    public void testAssistContentAndDataNullWhenNoFlagsToShowSession() throws Exception {
        assumeIsNotLowRamDevice();
        // TODO(b/299988169): Fix multi/secure displays for automotive
        // Currently automotive uses multi-display and/or secure displays
        // and sending null data is not supported due to the lack of information in main voice
        // interaction service.
        assumeIsNotAutomotive();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);

        Bundle bundle = new Bundle();
        bundle.putInt(SHOW_SESSION_FLAGS_TO_SET, 0);
        final AutoResetLatch latch = startSession(bundle);
        waitForContext(latch);

        verifyActivityIdNullness(/* isActivityIdNull= */ false);
        verifyAssistDataNullness(true, true, true, true);
        assertThat(mOnShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.service.voice.VoiceInteractionSession#getAppFunctionActivityId"})
    public void testAppFunctionActivityIdSameForSameActivity() throws Exception {
        assumeIsNotLowRamDevice();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);

        // First session
        final AutoResetLatch latch1 = startSession();
        waitForContext(latch1);
        AppFunctionActivityId activityId1 = mAppFunctionActivityId;
        // Second session, same activity
        final AutoResetLatch latch2 = startSession();
        waitForContext(latch2);
        AppFunctionActivityId activityId2 = mAppFunctionActivityId;

        assertThat(activityId1).isEqualTo(activityId2);
    }

    @Test
    @ApiTest(apis = {"android.service.voice.VoiceInteractionSession#getAppFunctionActivityId"})
    public void testAppFunctionActivityIdDifferentForDifferentActivities() throws Exception {
        assumeIsNotLowRamDevice();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();

        // First activity
        start3pApp(TEST_CASE_TYPE);
        final AutoResetLatch latch1 = startSession();
        waitForContext(latch1);
        AppFunctionActivityId activityId1 = mAppFunctionActivityId;
        // Second activity
        start3pApp(TEST_CASE_TYPE);
        final AutoResetLatch latch2 = startSession();
        waitForContext(latch2);
        AppFunctionActivityId activityId2 = mAppFunctionActivityId;

        assertThat(activityId1).isNotEqualTo(activityId2);
    }

    @Test
    @ApiTest(apis = {"android.service.voice.VoiceInteractionSession#getAppFunctionActivityId"})
    @RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
    public void testAppFunctionActivityIdMatchesRegisteredFunction() throws Exception {
        assumeIsNotLowRamDevice();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();

        start3pApp(TEST_CASE_TYPE);
        final AutoResetLatch latch = startSession();
        waitForContext(latch);

        AppFunctionActivityId registeredActivityIdByAppFunction =
                getRegisteredAppFunctionActivityId(
                        new AppFunctionName("android.assist.testapp", "stubAppFunction"));
        assertThat(mAppFunctionActivityId).isEqualTo(registeredActivityIdByAppFunction);
        AppFunctionName registeredAppFunctionByActivityId =
                getAppFunctionForActivityId(mAppFunctionActivityId);
        assertThat(registeredAppFunctionByActivityId)
                .isEqualTo(new AppFunctionName("android.assist.testapp", "stubAppFunction"));
    }

    /** Returns the {@link AppFunctionActivityId} registered for the {@link AppFunctionName}. */
    private AppFunctionActivityId getRegisteredAppFunctionActivityId(AppFunctionName functionName)
            throws Exception {
        final AtomicReference<ArraySet<AppFunctionActivityId>> latchedResult =
                new AtomicReference<>();
        final AtomicReference<Exception> latchedError = new AtomicReference<>();
        AutoResetLatch latch = new AutoResetLatch(1);
        AppFunctionManager appFunctionManager = mContext.getSystemService(AppFunctionManager.class);
        assumeNotNull(appFunctionManager);
        appFunctionManager.getAppFunctionStates(
                List.of(functionName),
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(List<AppFunctionState> result) {
                        if (result.size() != 1) {
                            latchedError.set(
                                    new Exception(
                                            "Expected 1 app function state, but got " + result));
                        } else {
                            latchedResult.set(result.get(0).getActivityIds());
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception exception) {
                        latchedError.set(exception);
                        latch.countDown();
                    }
                });
        latch.await();
        assertThat(latchedError.get()).isNull();
        assertThat(latchedResult.get()).hasSize(1);
        return latchedResult.get().valueAt(0);
    }

    /** Returns the {@link AppFunctionName} registered for the {@link AppFunctionActivityId}. */
    private AppFunctionName getAppFunctionForActivityId(AppFunctionActivityId activityId)
            throws Exception {
        final AtomicReference<ArraySet<AppFunctionName>> latchedResult = new AtomicReference<>();
        final AtomicReference<Exception> latchedError = new AtomicReference<>();
        AutoResetLatch latch = new AutoResetLatch(1);
        AppFunctionManager appFunctionManager = mContext.getSystemService(AppFunctionManager.class);
        assumeNotNull(appFunctionManager);
        appFunctionManager.getAppFunctionActivityStates(
                Set.of(activityId),
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(List<AppFunctionActivityState> result) {
                        if (result.size() != 1) {
                            latchedError.set(
                                    new Exception(
                                            "Expected 1 app function activity state, but"
                                                    + " got "
                                                    + result));
                        } else {
                            latchedResult.set(result.get(0).getFunctionNames());
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception exception) {
                        latchedError.set(exception);
                        latch.countDown();
                    }
                });
        latch.await();
        assertThat(latchedError.get()).isNull();
        assertThat(latchedResult.get()).hasSize(1);
        return latchedResult.get().valueAt(0);
    }

    private void assumeIsNotAutomotive() {
        assumeFalse("Test not supported in automotive", Utils.isAutomotive(mContext));
    }

    private void assumeIsNotLowRamDevice() {
        assumeFalse("Test not supported for low-RAM devices", mActivityManager.isLowRamDevice());
    }
}
