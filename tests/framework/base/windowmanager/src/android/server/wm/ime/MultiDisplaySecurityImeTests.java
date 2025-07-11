/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.wm.ime;

import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.MultiDisplayTestBase;

import com.android.cts.mockime.ImeEventStream;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplaySecurityImeTests extends MultiDisplayTestBase {

    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

    @Before
    @Override
    public void setUp() throws Exception {
        assumeRunNotOnVisibleBackgroundNonProfileUser("On visible background users, having the"
                + "keyboard in one display and the app that consumes the key events in another "
                + "virtual display, is not supported");

        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
    }

    @Test
    public void testNoInputConnectionForUntrustedVirtualDisplay() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             ActivityManagerTestBase.TestActivitySession<MultiDisplayImeTests.ImeTestActivity>
                     activitySession = createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setPublicDisplay(true)) {
            // Create a untrusted virtual display and assume the display should not show IME window.
            final var dc = displaySession.createDisplay();

            // Launch IME test activity in virtual display.
            activitySession.launchTestActivityOnDisplay(MultiDisplayImeTests.ImeTestActivity.class,
                    dc.mId);
            final var activity = activitySession.getActivity();
            // Verify that activity which lives in untrusted display should not be focused.
            assertNotEquals("ImeTestActivity should not be focused",
                    mWmState.getFocusedActivity(), activity.getComponentName().toString());

            // Expect onStartInput won't executed in the IME client.
            final ImeEventStream stream = imeSession.openEventStream();
            final var editText = activity.getEditText();
            activitySession.runOnMainSyncAndWait(activity::showSoftInput);
            notExpectEvent(stream, editorMatcher("onStartInput",
                    editText.getPrivateImeOptions()), NOT_EXPECT_TIMEOUT);

            // Expect onStartInput / showSoftInput would be executed when user tapping on the
            // untrusted display intentionally.
            final int[] location = new int[2];
            editText.getLocationOnScreen(location);
            tapOnDisplaySync(location[0], location[1], dc.mId);
            activitySession.runOnMainSyncAndWait(activity::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, DEFAULT_DISPLAY,
                    editorMatcher("onStartInput", editText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Switch focus to top focused display as default display, verify onStartInput won't
            // be called since the untrusted display should no longer get focus.
            tapOnDisplayCenter(DEFAULT_DISPLAY);
            mWmState.computeState();
            assertEquals(DEFAULT_DISPLAY, mWmState.getFocusedDisplayId());
            activity.resetPrivateImeOptionsIdentifier();
            activitySession.runOnMainSyncAndWait(activity::showSoftInput);
            notExpectEvent(stream, editorMatcher("onStartInput",
                    editText.getPrivateImeOptions()), NOT_EXPECT_TIMEOUT);
        }
    }
}
