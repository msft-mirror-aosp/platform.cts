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

package android.virtualdevice.cts.core;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.content.res.Resources;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/422154396): Move this to ComputerControl CTS suite when we create one.
@RunWith(AndroidJUnit4.class)
public class ComputerControlResourcesTest {

    @Test
    public void computerControlSuperAgents_nonDebuggableBuild_shouldBeEmpty() {
        assumeFalse(Build.isDebuggable());

        final Resources resources = getInstrumentation().getTargetContext().getResources();
        final int resId =
                resources.getIdentifier(
                        "config_computerControlKnownSuperAgents", "array", "android");
        // If the resource is not found, getIdentifier returns 0. Early return to avoid crashing.
        if (resId == 0) {
            return;
        }
        final String[] superAgents = resources.getStringArray(resId);

        assertThat(superAgents).isEmpty();
    }
}
