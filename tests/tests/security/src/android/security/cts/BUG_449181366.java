/*
 * Copyright 2025 The Android Open Source Project
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

package android.security.cts;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class BUG_449181366 extends StsExtraBusinessLogicTestCase {

    // The APK contains multiple input method services:
    // * "Method1" service (IME1), which must be loaded successfully, and is used to validate
    //   input method subtype loading behavior.
    // * "Method2" and later services, which are invalid input methods. They references a large
    //   strings. They should be ignored in the framework.
    private static final String LARGE_IME_APK_PATH =
            "/data/local/tmp/cts/security/LargeInputMethodTestApp.apk";
    private static final String LARGE_IME_PKG = "com.android.security.largeime";

    private static final ComponentName IME1 =
            ComponentName.createRelative(LARGE_IME_PKG, ".Method1");
    private static final String ADDED_SUBTYPE_NAME = "Additional Type";

    private static final long HW_TIMEOUT_MULTIPLIER =
            SystemProperties.getInt("ro.hw_timeout_multiplier", 1);
    private static final Duration TIMEOUT = Duration.ofSeconds(15 * HW_TIMEOUT_MULTIPLIER);

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getTargetContext();
    private String mInitiallyEnabledInputMethods;

    @Before
    public void setUp() {
        mInitiallyEnabledInputMethods = getEnabledInputMethods();
        installTestApp();
    }

    @After
    public void tearDown() {
        if (mInitiallyEnabledInputMethods != null) {
            // The test service uses InputMethodManager#setExplicitlyEnabledInputMethodSubtypes.
            // The API eventually updates ENABLED_INPUT_METHODS value. This makes sure the value
            // is reset.
            setEnabledInputMethods(mInitiallyEnabledInputMethods);
        }

        uninstallTestApp();
    }

    @Test
    @AsbSecurityTest(cveBugId = {449181366, 449227003, 449393786, 449416164})
    public void testLoadLargeInputMethod() {
        final var imm = mContext.getSystemService(InputMethodManager.class);

        try {
            // Should be able to successfully loaded, and added to the list within
            // the reasonable timeout.
            PollingCheck.waitFor(
                    TIMEOUT.toMillis(),
                    () ->
                            imm.getInputMethodList().stream()
                                    .map(InputMethodInfo::getComponent)
                                    .toList()
                                    .contains(IME1),
                    "InputMethod must be loaded");

            enableAndSetIme(IME1.flattenToShortString());

            // IME1 adds subtype dynamically onCreate(). The new subtypes should be
            // added to the list within the reasonable timeout.
            PollingCheck.waitFor(
                    TIMEOUT.toMillis(),
                    () -> imm.getCurrentInputMethodInfo().getComponent().equals(IME1),
                    "InputMethod must be enabled");

            // Start an activity with a focused editor to make sure the IME process is started
            // even if config_preventImeStartupUnlessTextEditor is true.
            try (var scenario = ActivityScenario.launch(EditorActivity.class)) {
                PollingCheck.waitFor(
                        TIMEOUT.toMillis(),
                        () -> {
                            AtomicBoolean focused = new AtomicBoolean(false);
                            scenario.onActivity(act -> focused.set(act.hasWindowFocus()));
                            return focused.get();
                        },
                        "EditorActivity must be focused");

                var imi = imm.getCurrentInputMethodInfo();
                PollingCheck.waitFor(
                        TIMEOUT.toMillis(),
                        () -> containsAdditionalSubtype(imm, imi, ADDED_SUBTYPE_NAME),
                        () ->
                                "Valid subtype must be exposed as expected, current: "
                                        + generateSubtypeListString(imm, imi));
            }
        } catch (Exception e) {
            if (e instanceof android.os.DeadSystemRuntimeException) {
                fail("The device is vulnerable to b/449181366.");
            }

            // If the device is vulnerable, the system server crashes or too slow to respond.
            // Catching other exceptions here normally means the test failed with some other reason.
            // Following STS guideline, this should just be an assumption failure.
            assume().that(e).isNull();
        }
    }

    private static boolean containsAdditionalSubtype(
            InputMethodManager imm, InputMethodInfo imi, String expectedSubtypeName) {
        return imm
                .getEnabledInputMethodSubtypeList(imi, /* allowsImplicitlyEnabledSubtypes= */ false)
                .stream()
                .anyMatch(
                        subtype ->
                                TextUtils.equals(expectedSubtypeName, subtype.getNameOverride()));
    }

    @NonNull
    private static String generateSubtypeListString(InputMethodManager imm, InputMethodInfo imi) {
        return imm
                .getEnabledInputMethodSubtypeList(imi, /* allowsImplicitlyEnabledSubtypes= */ false)
                .stream()
                .map(subtype -> subtype.getNameOverride())
                .map(
                        name ->
                                name.length() > 20
                                        ? TextUtils.trimToLengthWithEllipsis(name, 20)
                                        : name)
                .toList()
                .toString();
    }

    private static void installTestApp() {
        SystemUtil.runShellCommand(
                "pm install -r --user " + UserHandle.myUserId() + " " + LARGE_IME_APK_PATH);
    }

    private static void uninstallTestApp() {
        SystemUtil.runShellCommand(
                "pm uninstall --user " + UserHandle.myUserId() + " " + LARGE_IME_PKG);
    }

    private static String getEnabledInputMethods() {
        return SystemUtil.runShellCommand(
                "settings --user "
                        + UserHandle.myUserId()
                        + " get secure "
                        + Settings.Secure.ENABLED_INPUT_METHODS);
    }

    private static void setEnabledInputMethods(String enabledInputMethods) {
        SystemUtil.runShellCommand(
                "settings --user "
                        + UserHandle.myUserId()
                        + " set secure "
                        + Settings.Secure.ENABLED_INPUT_METHODS
                        + " "
                        + enabledInputMethods);
    }

    private static void enableAndSetIme(String imeId) {
        SystemUtil.runShellCommand("ime enable --user " + UserHandle.myUserId() + " " + imeId);
        SystemUtil.runShellCommand("ime set --user " + UserHandle.myUserId() + " " + imeId);
    }

    public static class EditorActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final var editText = new EditText(this);
            setContentView(editText);
            editText.requestFocus();
        }
    }
}
