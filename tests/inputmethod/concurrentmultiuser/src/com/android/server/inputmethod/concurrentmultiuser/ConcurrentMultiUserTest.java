/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.inputmethod.concurrentmultiuser;

import static android.Manifest.permission.ACCESS_SURFACE_FLINGER;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.TEST_INPUT_METHOD;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.getResponderUserId;
import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.launchActivityAsUserSync;
import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.sendBundleAndWaitForReply;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.KEY_DISPLAY_ID;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.KEY_EDITTEXT_CENTER;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.KEY_REQUEST_CODE;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.REQUEST_DISPLAY_ID;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.REQUEST_EDITTEXT_POSITION;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.REQUEST_HIDE_IME;
import static com.android.server.inputmethod.concurrentmultiuser.TestRequestConstants.REQUEST_SHOW_IME;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.server.wm.BuildUtils;
import android.server.wm.CtsWindowInfoUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireAutomotive;
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsers;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.WindowUtil;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
@RequireVisibleBackgroundUsers(
        reason =
                "This test requires a background visible user in addition to the current visible"
                        + " user to test concurrent multi-user IME scenarios")
@RequireAutomotive(reason = "Visible background users are currently only supported on automotive")
public final class ConcurrentMultiUserTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName TEST_ACTIVITY =
            new ComponentName(
                    getInstrumentation().getTargetContext().getPackageName(),
                    ConcurrentMultiUserTestActivity.class.getName());
    private static final long TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(5) * BuildUtils.HW_TIMEOUT_MULTIPLIER;
    private static final long TIMEOUT_NOT_EXPECT = TimeUnit.SECONDS.toMillis(1);
    private static final String MOCKIME_ID = "com.android.cts.mockime/.MockIme";
    private final Context mContext = getInstrumentation().getTargetContext();

    private ImeSettings.Builder mDriverImeSettings;
    private ImeSettings.Builder mPassengerImeSettings;
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);
    private final UiAutomation mUiAutomation = getInstrumentation().getUiAutomation();

    private ActivityScenario<ConcurrentMultiUserTestActivity> mActivityScenario;
    private ConcurrentMultiUserTestActivity mActivity;
    private UserHandle mDriverUser;
    private UserHandle mPassengerUser;
    private int mPeerUserId;
    private Context mPassengerContext;

    @Before
    public void setUp() {
        // Launch passenger activity.
        mPeerUserId = getResponderUserId();
        launchActivityAsUserSync(TEST_ACTIVITY, mPeerUserId);

        // Launch driver activity.
        mActivityScenario = ActivityScenario.launch(ConcurrentMultiUserTestActivity.class);
        mActivityScenario.onActivity(activity -> mActivity = activity);
        WindowUtil.waitForFocus(mActivity);
        mUiAutomation.adoptShellPermissionIdentity(
                INTERACT_ACROSS_USERS_FULL,
                ACCESS_SURFACE_FLINGER,
                TEST_INPUT_METHOD,
                WRITE_SECURE_SETTINGS);

        // Set up user handles and builders
        mDriverUser = UserHandle.of(mContext.getUserId());
        mPassengerUser = UserHandle.of(mPeerUserId);
        mDriverImeSettings = new ImeSettings.Builder().setTargetUser(mDriverUser);
        mPassengerImeSettings = new ImeSettings.Builder().setTargetUser(mPassengerUser);
        mPassengerContext = mContext.createContextAsUser(mPassengerUser, /* flags */ 0);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    /**
     * Verifies that showing the IME on the driver's display does not affect the IME visibility on
     * the passenger's display.
     */
    @Test
    public void driverShowImeNotAffectPassenger() throws Exception {
        try (MockImeSession passengerImeSession =
                MockImeSession.create(mPassengerContext, mUiAutomation, mPassengerImeSettings)) {
            try (MockImeSession driverImeSession =
                    MockImeSession.create(mContext, mUiAutomation, mDriverImeSettings)) {
                final ImeEventStream passengerStream = passengerImeSession.openEventStream();
                final ImeEventStream driverStream = driverImeSession.openEventStream();

                passengerStream.skipAll();

                showDriverImeAndAssert(driverStream);

                // Assertion needed to make sure passenger MockIme not affected
                notExpectEvent(
                        passengerStream, eventMatcher("onStartInputView"), TIMEOUT_NOT_EXPECT);
            }
        }
    }

    /**
     * Verifies that showing the IME on the passenger's display does not affect the IME visibility
     * on the driver's display.
     */
    @Test
    public void passengerShowImeNotAffectDriver() throws Exception {
        try (MockImeSession passengerImeSession =
                MockImeSession.create(mPassengerContext, mUiAutomation, mPassengerImeSettings)) {
            try (MockImeSession driverImeSession =
                    MockImeSession.create(mContext, mUiAutomation, mDriverImeSettings)) {
                final ImeEventStream driverStream = driverImeSession.openEventStream();
                final ImeEventStream passengerStream = passengerImeSession.openEventStream();

                driverStream.skipAll();

                showPassengerImeAndAssert(passengerStream);

                // Assertion needed to make sure driver MockIme not affected
                notExpectEvent(driverStream, eventMatcher("onStartInputView"), TIMEOUT_NOT_EXPECT);
            }
        }
    }

    /**
     * Verifies that hiding the IME on the driver's display does not affect the IME visibility on
     * the passenger's display.
     */
    @Test
    public void driverHideImeNotAffectPassenger() throws Exception {
        try (MockImeSession passengerImeSession =
                MockImeSession.create(mPassengerContext, mUiAutomation, mPassengerImeSettings)) {
            try (MockImeSession driverImeSession =
                    MockImeSession.create(mContext, mUiAutomation, mDriverImeSettings)) {
                final ImeEventStream driverStream = driverImeSession.openEventStream();
                final ImeEventStream passengerStream = passengerImeSession.openEventStream();

                showPassengerImeAndAssert(passengerStream);
                showDriverImeAndAssert(driverStream);

                passengerStream.skipAll();

                hideDriverImeAndAssert(driverStream);

                // Assertion needed to make sure passenger MockIme not affected
                notExpectEvent(
                        passengerStream, eventMatcher("onFinishInputView"), TIMEOUT_NOT_EXPECT);
            }
        }
    }

    /**
     * Verifies that hiding the IME on the passenger's display does not affect the IME visibility on
     * the driver's display.
     */
    @Test
    public void passengerHideImeNotAffectDriver() throws Exception {
        try (MockImeSession driverImeSession =
                MockImeSession.create(mContext, mUiAutomation, mDriverImeSettings)) {
            try (MockImeSession passengerImeSession =
                    MockImeSession.create(
                            mPassengerContext, mUiAutomation, mPassengerImeSettings)) {
                final ImeEventStream driverStream = driverImeSession.openEventStream();
                final ImeEventStream passengerStream = passengerImeSession.openEventStream();

                showPassengerImeAndAssert(passengerStream);
                showDriverImeAndAssert(driverStream);

                driverStream.skipAll();

                hidePassengerImeAndAssert(passengerStream);

                // Assertion needed to make sure driver MockIme not affected
                notExpectEvent(driverStream, eventMatcher("onFinishInputView"), TIMEOUT_NOT_EXPECT);
            }
        }
    }

    /** Verifies that both the driver and the passenger user have at least one IME installed. */
    @Test
    public void imeListNotEmpty() {
        List<InputMethodInfo> driverImeList = mInputMethodManager.getInputMethodList();
        assertWithMessage("Driver IME list should contain MockIme")
                .that(
                        driverImeList.stream()
                                .map(InputMethodInfo::getId)
                                .collect(Collectors.toList()))
                .contains(MOCKIME_ID);

        List<InputMethodInfo> passengerImeList =
                mInputMethodManager.getInputMethodListAsUser(mPeerUserId);
        assertWithMessage("Passenger IME list should contain MockIme")
                .that(
                        passengerImeList.stream()
                                .map(InputMethodInfo::getId)
                                .collect(Collectors.toList()))
                .contains(MOCKIME_ID);
    }

    /** Verifies that both the driver and the passenger user have at least one enabled IME. */
    @Test
    public void enabledImeListNotEmpty() {
        List<InputMethodInfo> driverEnabledImeList =
                mInputMethodManager.getEnabledInputMethodList();
        assertWithMessage("Driver enabled IME list shouldn't be empty")
                .that(driverEnabledImeList.isEmpty())
                .isFalse();

        List<InputMethodInfo> passengerEnabledImeList =
                mInputMethodManager.getEnabledInputMethodListAsUser(mPassengerUser);
        assertWithMessage("Passenger enabled IME list shouldn't be empty")
                .that(passengerEnabledImeList.isEmpty())
                .isFalse();
    }

    /**
     * Verifies that both the driver and the passenger user have a non-null current IME selected.
     */
    @Test
    public void currentImeNotNull() {
        InputMethodInfo driverIme = mInputMethodManager.getCurrentInputMethodInfo();
        assertWithMessage("Driver IME shouldn't be null").that(driverIme).isNotNull();

        InputMethodInfo passengerIme =
                mInputMethodManager.getCurrentInputMethodInfoAsUser(UserHandle.of(mPeerUserId));
        assertWithMessage("Passenger IME shouldn't be null").that(passengerIme).isNotNull();
    }

    /**
     * Verifies that enabling or disabling an IME for one user does not affect the IME settings of
     * another user.
     */
    @Test
    public void enableDisableImePerUser() {
        enableDisableImeForUser(mDriverUser, mPassengerUser);
        enableDisableImeForUser(mPassengerUser, mDriverUser);
    }

    /**
     * Verifies that setting the current IME for one user does not affect the current IME of another
     * user.
     */
    @Test
    public void setImePerUser() {
        setImeForUser(mDriverUser, mPassengerUser);
        setImeForUser(mPassengerUser, mDriverUser);
    }

    private void showDriverImeAndAssert(ImeEventStream driverStream) throws Exception {
        showDriverIme();
        assertImeShown(driverStream);
    }

    private void showPassengerImeAndAssert(ImeEventStream passengerStream) throws Exception {
        showPassengerIme();
        assertImeShown(passengerStream);
    }

    private void hideDriverImeAndAssert(ImeEventStream driverStream) throws Exception {
        hideDriverIme();
        expectEvent(driverStream, eventMatcher("onFinishInputView"), TIMEOUT_MILLIS);
    }

    private void hidePassengerImeAndAssert(ImeEventStream passengerStream) throws Exception {
        hidePassengerIme();
        expectEvent(passengerStream, eventMatcher("onFinishInputView"), TIMEOUT_MILLIS);
    }

    private void assertImeShown(ImeEventStream userStream) throws Exception {
        expectEvent(userStream, eventMatcher("onCreate"), TIMEOUT_MILLIS);
        expectEvent(userStream, eventMatcher("onStartInput"), TIMEOUT_MILLIS);
        expectEvent(userStream, eventMatcher("onCreateInputView"), TIMEOUT_MILLIS);
        expectEvent(userStream, eventMatcher("onStartInputView"), TIMEOUT_MILLIS);

        // Assertion needed to make sure MockIme didn't flicker
        notExpectEvent(userStream, eventMatcher("onFinishInputView"), TIMEOUT_NOT_EXPECT);
    }

    private void showDriverIme() throws Exception {
        //  WindowManagerInternal only allows the top focused display to show IME, so this method
        //  taps the driver display in case it is not the top focused display.
        moveDriverDisplayToTop();
        mActivity.showMyImeAndWait();
    }

    private void hideDriverIme() {
        mActivity.hideMyImeAndWait();
    }

    private void showPassengerIme() throws Exception {
        // WindowManagerInternal only allows the top focused display to show IME, so this method
        // taps the passenger display in case it is not the top focused display.
        movePassengerDisplayToTop();

        Bundle bundleToSend = new Bundle();
        bundleToSend.putInt(KEY_REQUEST_CODE, REQUEST_SHOW_IME);
        sendBundleAndWaitForReply(TEST_ACTIVITY.getPackageName(), mPeerUserId, bundleToSend);
    }

    private void hidePassengerIme() {
        Bundle bundleToSend = new Bundle();
        bundleToSend.putInt(KEY_REQUEST_CODE, REQUEST_HIDE_IME);
        sendBundleAndWaitForReply(TEST_ACTIVITY.getPackageName(), mPeerUserId, bundleToSend);
    }

    private void moveDriverDisplayToTop() throws Exception {
        float[] driverEditTextCenter = mActivity.getEditTextCenter();
        SystemUtil.runShellCommandOrThrow(
                String.format("input tap %f %f", driverEditTextCenter[0], driverEditTextCenter[1]));
        mUiAutomation.syncInputTransactions();
        CtsWindowInfoUtils.waitForStableWindowGeometry(Duration.ofMillis(TIMEOUT_MILLIS));
    }

    private void movePassengerDisplayToTop() throws Exception {
        final Bundle bundleToSend = new Bundle();
        bundleToSend.putInt(KEY_REQUEST_CODE, REQUEST_EDITTEXT_POSITION);
        Bundle receivedBundle =
                sendBundleAndWaitForReply(
                        TEST_ACTIVITY.getPackageName(), mPeerUserId, bundleToSend);
        final float[] passengerEditTextCenter = receivedBundle.getFloatArray(KEY_EDITTEXT_CENTER);

        bundleToSend.putInt(KEY_REQUEST_CODE, REQUEST_DISPLAY_ID);
        receivedBundle =
                sendBundleAndWaitForReply(
                        TEST_ACTIVITY.getPackageName(), mPeerUserId, bundleToSend);
        final int passengerDisplayId = receivedBundle.getInt(KEY_DISPLAY_ID);
        SystemUtil.runShellCommandOrThrow(
                String.format(
                        "input -d %d tap %f %f",
                        passengerDisplayId,
                        passengerEditTextCenter[0],
                        passengerEditTextCenter[1]));
        mUiAutomation.syncInputTransactions();
        CtsWindowInfoUtils.waitForStableWindowGeometry(Duration.ofMillis(TIMEOUT_MILLIS));
    }

    /**
     * Disables/enables IME for {@code user1}, then verifies that the IME settings for {@code user1}
     * has changed as expected and {@code user2} stays the same.
     */
    private void enableDisableImeForUser(@NonNull UserHandle user1, @NonNull UserHandle user2) {
        List<InputMethodInfo> user2EnabledImeList =
                mInputMethodManager.getEnabledInputMethodListAsUser(user2);
        mInputMethodManager.enableInputMethodForTesting(MOCKIME_ID, user1.getIdentifier());
        List<InputMethodInfo> user1EnabledImeList =
                mInputMethodManager.getEnabledInputMethodListAsUser(user1);
        PollingCheck.waitFor(
                TIMEOUT_MILLIS,
                () ->
                        mInputMethodManager.getEnabledInputMethodListAsUser(user1).stream()
                                .map(InputMethodInfo::getId)
                                .toList()
                                .contains(MOCKIME_ID),
                "enable IME test API failed.");
        // Disable an IME for user1.
        mInputMethodManager.disableInputMethodForTesting(MOCKIME_ID, user1.getIdentifier());
        List<InputMethodInfo> user1EnabledImeList2 =
                mInputMethodManager.getEnabledInputMethodListAsUser(user1);
        List<InputMethodInfo> user2EnabledImeList2 =
                mInputMethodManager.getEnabledInputMethodListAsUser(user2);
        PollingCheck.waitFor(
                TIMEOUT_MILLIS,
                () ->
                        !mInputMethodManager.getEnabledInputMethodListAsUser(user1).stream()
                                .map(InputMethodInfo::getId)
                                .toList()
                                .contains(MOCKIME_ID),
                "disable IME test API failed.");

        assertWithMessage("User " + user1.getIdentifier() + "'s MockIme should be disabled")
                .that(
                        user1EnabledImeList2.stream()
                                .map(InputMethodInfo::getId)
                                .toList()
                                .contains(MOCKIME_ID))
                .isFalse();
        assertWithMessage(
                        "Disabling user "
                                + user1.getIdentifier()
                                + "'s MockIme shouldn't affect user "
                                + user2.getIdentifier())
                .that(
                        user2EnabledImeList2.containsAll(user2EnabledImeList)
                                && user2EnabledImeList.containsAll(user2EnabledImeList2))
                .isTrue();

        // Enable the IME.
        mInputMethodManager.enableInputMethodForTesting(MOCKIME_ID, user1.getIdentifier());
        PollingCheck.waitFor(
                TIMEOUT_MILLIS,
                () ->
                        mInputMethodManager.getEnabledInputMethodListAsUser(user1).stream()
                                .map(InputMethodInfo::getId)
                                .toList()
                                .contains(MOCKIME_ID),
                "enable IME test API failed.");
        List<InputMethodInfo> user1EnabledImeList3 =
                mInputMethodManager.getEnabledInputMethodListAsUser(user1);
        List<InputMethodInfo> user2EnabledImeList3 =
                mInputMethodManager.getEnabledInputMethodListAsUser(user2);
        assertWithMessage("User " + user1.getIdentifier() + "'s MockIme should be enabled")
                .that(
                        user1EnabledImeList3.stream()
                                .map(InputMethodInfo::getId)
                                .toList()
                                .contains(MOCKIME_ID))
                .isTrue();
        assertWithMessage(
                        "Enabling user "
                                + user1.getIdentifier()
                                + "'s MockIme shouldn't affect user "
                                + user2.getIdentifier())
                .that(
                        user2EnabledImeList2.containsAll(user2EnabledImeList3)
                                && user2EnabledImeList3.containsAll(user2EnabledImeList2))
                .isTrue();
    }

    /**
     * Sets/resets IME for {@code user1}, then verifies that the IME settings for {@code user1} has
     * changed as expected and {@code user2} stays the same.
     */
    private void setImeForUser(@NonNull UserHandle user1, @NonNull UserHandle user2) {
        // Reset IME for user1.
        mInputMethodManager.resetInputMethodsForTesting(user1.getIdentifier());
        List<InputMethodInfo> user1EnabledImeList =
                mInputMethodManager.getEnabledInputMethodListAsUser(user1);
        assumeTrue("There must be at least two IME to test", user1EnabledImeList.size() >= 2);
        InputMethodInfo user1Ime = mInputMethodManager.getCurrentInputMethodInfoAsUser(user1);
        InputMethodInfo user2Ime = mInputMethodManager.getCurrentInputMethodInfoAsUser(user2);

        // Set to another IME for user1.
        InputMethodInfo anotherIme = null;
        for (InputMethodInfo info : user1EnabledImeList) {
            if (!info.equals(user1Ime)) {
                anotherIme = info;
            }
        }
        mInputMethodManager.setInputMethodForTesting(anotherIme.getId(), user1.getIdentifier());
        final String anotherImeId = anotherIme.getId();
        PollingCheck.waitFor(
                TIMEOUT_MILLIS,
                () ->
                        mInputMethodManager
                                .getCurrentInputMethodInfoAsUser(user1)
                                .getId()
                                .equals(anotherImeId),
                "set IME test API failed.");
        InputMethodInfo user1Ime2 = mInputMethodManager.getCurrentInputMethodInfoAsUser(user1);
        InputMethodInfo user2Ime2 = mInputMethodManager.getCurrentInputMethodInfoAsUser(user2);
        assertWithMessage("The current IME for user " + user1.getIdentifier() + " is wrong")
                .that(user1Ime2)
                .isEqualTo(anotherIme);
        assertWithMessage("The current IME for user " + user2.getIdentifier() + " shouldn't change")
                .that(user2Ime2)
                .isEqualTo(user2Ime);

        // Reset IME for user1.
        mInputMethodManager.resetInputMethodsForTesting(user1.getIdentifier());
        PollingCheck.waitFor(
                TIMEOUT_MILLIS,
                () ->
                        mInputMethodManager
                                .getCurrentInputMethodInfoAsUser(user1)
                                .getId()
                                .equals(user1Ime.getId()),
                "reset IME test API failed.");
        InputMethodInfo user1Ime3 = mInputMethodManager.getCurrentInputMethodInfoAsUser(user1);
        InputMethodInfo user2Ime3 = mInputMethodManager.getCurrentInputMethodInfoAsUser(user2);
        assertWithMessage("The current IME for user " + user1.getIdentifier() + " is wrong")
                .that(user1Ime3)
                .isEqualTo(user1Ime);
        assertWithMessage("The current IME for user " + user2.getIdentifier() + " shouldn't change")
                .that(user2Ime3)
                .isEqualTo(user2Ime);
    }
}
