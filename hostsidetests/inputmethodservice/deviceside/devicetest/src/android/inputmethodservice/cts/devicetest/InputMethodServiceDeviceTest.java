/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.inputmethodservice.cts.devicetest;

import static android.inputmethodservice.cts.DeviceEvent.isFrom;
import static android.inputmethodservice.cts.DeviceEvent.isNewerThan;
import static android.inputmethodservice.cts.DeviceEvent.isType;
import static android.inputmethodservice.cts.common.BusyWaitUtils.pollingCheck;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_BIND_INPUT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_CREATE;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_START_INPUT;
import static android.inputmethodservice.cts.common.ImeCommandConstants.ACTION_IME_COMMAND;
import static android.inputmethodservice.cts.common.ImeCommandConstants.COMMAND_SWITCH_INPUT_METHOD;
import static android.inputmethodservice.cts.common.ImeCommandConstants.COMMAND_SWITCH_TO_NEXT_INPUT;
import static android.inputmethodservice.cts.common.ImeCommandConstants.COMMAND_SWITCH_TO_PREVIOUS_INPUT;
import static android.inputmethodservice.cts.common.ImeCommandConstants.EXTRA_ARG_STRING1;
import static android.inputmethodservice.cts.common.ImeCommandConstants.EXTRA_COMMAND;
import static android.inputmethodservice.cts.devicetest.MoreCollectors.startingFrom;
import static android.provider.Settings.Secure.STYLUS_HANDWRITING_DEFAULT_VALUE;
import static android.provider.Settings.Secure.STYLUS_HANDWRITING_ENABLED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.inputmethodservice.cts.DeviceEvent;
import android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType;
import android.inputmethodservice.cts.common.EditTextAppConstants;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.inputmethodservice.cts.common.Ime2Constants;
import android.inputmethodservice.cts.common.test.ShellCommandUtils;
import android.inputmethodservice.cts.devicetest.SequenceMatcher.MatchResult;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * Test general lifecycle events around InputMethodService.
 */
@RunWith(AndroidJUnit4.class)
public class InputMethodServiceDeviceTest {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    private static final int SETTING_VALUE_ON = 1;
    private static final int SETTING_VALUE_OFF = 0;

    /** Test to check CtsInputMethod1 receives onCreate and onStartInput. */
    @Test
    public void testCreateIme1() throws Throwable {
        final TestHelper helper = new TestHelper();

        final long startActivityTime = SystemClock.uptimeMillis();
        helper.launchActivity(EditTextAppConstants.PACKAGE, EditTextAppConstants.CLASS,
                EditTextAppConstants.URI);

        pollingCheck(() -> helper.queryAllEvents()
                        .collect(startingFrom(helper.isStartOfTest()))
                        .anyMatch(isFrom(Ime1Constants.CLASS).and(isType(ON_CREATE))),
                TIMEOUT, "CtsInputMethod1.onCreate is called");
        pollingCheck(() -> helper.queryAllEvents()
                        .filter(isNewerThan(startActivityTime))
                        .anyMatch(isFrom(Ime1Constants.CLASS).and(isType(ON_START_INPUT))),
                TIMEOUT, "CtsInputMethod1.onStartInput is called");
    }

    /**
     * Test {@link android.inputmethodservice.InputMethodService#switchToNextInputMethod(boolean)}.
     */
    @Test
    public void testSwitchToNextInputMethod() throws Throwable {
        final TestHelper helper = new TestHelper();
        final long startActivityTime = SystemClock.uptimeMillis();
        final int testUserId = UserHandle.myUserId();
        helper.launchActivity(EditTextAppConstants.PACKAGE, EditTextAppConstants.CLASS,
                EditTextAppConstants.URI);
        pollingCheck(() -> helper.queryAllEvents()
                        .filter(isNewerThan(startActivityTime))
                        .anyMatch(isFrom(Ime1Constants.CLASS).and(isType(ON_START_INPUT))),
                TIMEOUT, "CtsInputMethod1.onStartInput is called");
        helper.findUiObject(EditTextAppConstants.EDIT_TEXT_RES_NAME).click();

        pollingCheck(() -> helper.shell(ShellCommandUtils.getCurrentIme(testUserId))
                        .equals(Ime1Constants.IME_ID),
                TIMEOUT, "CtsInputMethod1 is current IME");
        helper.shell(ShellCommandUtils.broadcastIntent(
                ACTION_IME_COMMAND, Ime1Constants.PACKAGE,
                "-e", EXTRA_COMMAND, COMMAND_SWITCH_TO_NEXT_INPUT));
        pollingCheck(() -> !helper.shell(ShellCommandUtils.getCurrentIme(testUserId))
                        .equals(Ime1Constants.IME_ID),
                TIMEOUT, "CtsInputMethod1 shouldn't be current IME");
    }

    /**
     * Test {@link android.inputmethodservice.InputMethodService#switchToPreviousInputMethod()}.
     */
    @Test
    public void switchToPreviousInputMethod() throws Throwable {
        final TestHelper helper = new TestHelper();
        final long startActivityTime = SystemClock.uptimeMillis();
        final int testUserId = UserHandle.myUserId();
        helper.launchActivity(EditTextAppConstants.PACKAGE, EditTextAppConstants.CLASS,
                EditTextAppConstants.URI);
        helper.findUiObject(EditTextAppConstants.EDIT_TEXT_RES_NAME).click();

        final String initialIme = helper.shell(ShellCommandUtils.getCurrentIme(testUserId));
        helper.shell(ShellCommandUtils.setCurrentImeSync(Ime2Constants.IME_ID, testUserId));
        pollingCheck(() -> helper.queryAllEvents()
                        .filter(isNewerThan(startActivityTime))
                        .anyMatch(isFrom(Ime2Constants.CLASS).and(isType(ON_START_INPUT))),
                TIMEOUT, "CtsInputMethod2.onStartInput is called");
        helper.shell(ShellCommandUtils.broadcastIntent(
                ACTION_IME_COMMAND, Ime2Constants.PACKAGE,
                "-e", EXTRA_COMMAND, COMMAND_SWITCH_TO_PREVIOUS_INPUT));
        pollingCheck(() -> helper.shell(ShellCommandUtils.getCurrentIme(testUserId))
                        .equals(initialIme),
                TIMEOUT, initialIme + " is current IME");
    }

    /**
     * Test switching to IME capable of {@link InputMethodInfo#supportsStylusHandwriting()} is
     * reported in {@link InputMethodManager#isStylusHandwritingAvailable()} immediately after
     * switching.
     * @throws Throwable
     */
    @Test
    public void testSwitchToHandwritingInputMethod() throws Throwable {
        final TestHelper helper = new TestHelper();
        final long startActivityTime = SystemClock.uptimeMillis();
        helper.launchActivity(EditTextAppConstants.PACKAGE, EditTextAppConstants.CLASS,
                EditTextAppConstants.URI);
        pollingCheck(() -> helper.queryAllEvents()
                        .filter(isNewerThan(startActivityTime))
                        .anyMatch(isFrom(Ime1Constants.CLASS).and(isType(ON_START_INPUT))),
                TIMEOUT, "CtsInputMethod1.onStartInput is called");
        helper.findUiObject(EditTextAppConstants.EDIT_TEXT_RES_NAME).click();

        // determine stylus handwriting setting, enable it if not already.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        boolean mShouldRestoreInitialHwState = false;
        int initialHwState = Settings.Secure.getInt(context.getContentResolver(),
                STYLUS_HANDWRITING_ENABLED, STYLUS_HANDWRITING_DEFAULT_VALUE);
        if (initialHwState != SETTING_VALUE_ON) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.Secure.putInt(context.getContentResolver(),
                        STYLUS_HANDWRITING_ENABLED, SETTING_VALUE_ON);
            }, Manifest.permission.WRITE_SECURE_SETTINGS);
            mShouldRestoreInitialHwState = true;
        }

        try {
            final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
            assertFalse("CtsInputMethod1 shouldn't support handwriting",
                    imm.isStylusHandwritingAvailable());
            // Switch IME from CtsInputMethod1 to CtsInputMethod2.
            final long switchImeTime = SystemClock.uptimeMillis();
            helper.shell(ShellCommandUtils.broadcastIntent(
                    ACTION_IME_COMMAND, Ime1Constants.PACKAGE,
                    "-e", EXTRA_COMMAND, COMMAND_SWITCH_INPUT_METHOD,
                    "-e", EXTRA_ARG_STRING1, Ime2Constants.IME_ID));
            final int testUserId = UserHandle.myUserId();
            pollingCheck(() -> helper.shell(ShellCommandUtils.getCurrentIme(testUserId))
                            .equals(Ime2Constants.IME_ID),
                    TIMEOUT, "CtsInputMethod2 is current IME");


            pollingCheck(() -> helper.queryAllEvents()
                            .filter(isNewerThan(switchImeTime))
                            .filter(isFrom(Ime2Constants.CLASS))
                            .collect(sequenceOfTypes(ON_CREATE, ON_BIND_INPUT))
                            .matched(),
                    TIMEOUT,
                    "CtsInputMethod2.onCreate, onBindInput are called after switching");
            assertTrue("CtsInputMethod2 should support handwriting after onBindInput",
                    imm.isStylusHandwritingAvailable());

            pollingCheck(() -> helper.queryAllEvents()
                            .filter(isNewerThan(switchImeTime))
                            .filter(isFrom(Ime2Constants.CLASS))
                            .collect(sequenceOfTypes(ON_START_INPUT))
                            .matched(),
                    TIMEOUT,
                    "CtsInputMethod2.onStartInput is called");
            assertTrue("CtsInputMethod2 should support handwriting after StartInput",
                    imm.isStylusHandwritingAvailable());
        } finally {
            if (mShouldRestoreInitialHwState) {
                SystemUtil.runWithShellPermissionIdentity(() -> {
                    Settings.Secure.putInt(context.getContentResolver(),
                            STYLUS_HANDWRITING_ENABLED, initialHwState);
                }, Manifest.permission.WRITE_SECURE_SETTINGS);
            }
        }
    }

    /**
     * Build stream collector of {@link DeviceEvent} collecting sequence that elements have
     * specified types.
     *
     * @param types {@link DeviceEventType}s that elements of sequence should have.
     * @return {@link java.util.stream.Collector} that corrects the sequence.
     */
    private static Collector<DeviceEvent, ?, MatchResult<DeviceEvent>> sequenceOfTypes(
            final DeviceEventType... types) {
        final IntFunction<Predicate<DeviceEvent>[]> arraySupplier = Predicate[]::new;
        return SequenceMatcher.of(Arrays.stream(types)
                .map(DeviceEvent::isType)
                .toArray(arraySupplier));
    }
}
