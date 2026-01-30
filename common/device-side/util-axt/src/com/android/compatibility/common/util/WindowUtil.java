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

package com.android.compatibility.common.util;

import android.app.Activity;
import android.view.Window;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowUtil {
    /**
     * The timeout in milliseconds to wait for window focus in
     * {@link #waitForFocus(Window)} and {@link #waitForFocus(Activity)}.
     */
    public static final long WINDOW_FOCUS_TIMEOUT_MILLIS = 10_000L;

    /**
     * Waits until {@code activity}'s main window has focus, timing out after
     * {@link #WINDOW_FOCUS_TIMEOUT_MILLIS}.
     * @param activity The Activity whose main window should gain focus.
     */
    public static void waitForFocus(Activity activity) {
        PollingCheck.waitFor(
                WINDOW_FOCUS_TIMEOUT_MILLIS,
                activity::hasWindowFocus,
                () ->
                        "Timed out waiting for activity "
                                + activity.getComponentName()
                                + " to gain focus; "
                                + getFocusedWindowName(activity.getDisplayId())
                                + " was focused instead");
    }

    /**
     * Waits until {@code window} has focus, timing out after {@link #WINDOW_FOCUS_TIMEOUT_MILLIS}.
     * @param window The Window that should gain focus.
     */
    public static void waitForFocus(Window window) {
        PollingCheck.waitFor(
                WINDOW_FOCUS_TIMEOUT_MILLIS,
                window.getDecorView()::hasWindowFocus,
                () ->
                        "Timed out waiting for window "
                                + window.getAttributes().getTitle()
                                + " to gain focus; "
                                + getFocusedWindowName(window.getContext().getDisplayId())
                                + " was focused instead");
    }

    /**
     * Parses the Input Dispatcher dump to determine what window currently has focus.
     *
     * @param displayId the ID of the display to query
     * @return a window name, or a comma-separated list of window names if there are multiple
     *     displays.
     */
    private static @NonNull String getFocusedWindowName(int displayId) {
        String dumpsysInput = SystemUtil.runShellCommandOrThrow("dumpsys input");
        Pattern startLinePattern = Pattern.compile("\\s*FocusedWindows:");
        Pattern displayLinePattern = Pattern.compile("\\s*displayId=(\\d+), name='([^']*)'");
        boolean inSection = false;
        for (String line : dumpsysInput.split("\\n")) {
            if (inSection) {
                Matcher match = displayLinePattern.matcher(line);
                if (!match.matches()) {
                    // We've reached the end of the section.
                    break;
                }
                if (Integer.parseInt(match.group(1)) == displayId) {
                    String name = match.group(2);
                    return name != null ? name : "<unnamed window>";
                }
            } else if (startLinePattern.matcher(line).matches()) {
                inSection = true;
            }
        }
        return "<no window>";
    }
}
