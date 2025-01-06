/*
 * Copyright 2024 The Android Open Source Project
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

package android.server.wm.input;

import static android.Manifest.permission.OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW;

import static com.android.hardware.input.Flags.FLAG_OVERRIDE_POWER_KEY_BEHAVIOR_IN_FOCUSED_WINDOW;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowManagerLayoutParamsTest {
    private WindowManager.LayoutParams mLayoutParams;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_OVERRIDE_POWER_KEY_BEHAVIOR_IN_FOCUSED_WINDOW)
    public void testSetReceivePowerKeyDoublePressEnabled_returnsTrue() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mLayoutParams.setReceivePowerKeyDoublePressEnabled(true);
                    assertTrue(mLayoutParams.isReceivePowerKeyDoublePressEnabled());
                },
                OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_OVERRIDE_POWER_KEY_BEHAVIOR_IN_FOCUSED_WINDOW)
    public void testDontSetReceivePowerKeyDoublePressEnabled_returnsFalse() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    assertFalse(mLayoutParams.isReceivePowerKeyDoublePressEnabled());
                },
                OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_OVERRIDE_POWER_KEY_BEHAVIOR_IN_FOCUSED_WINDOW)
    public void testSetReceivePowerKeyDoublePressDisabled_returnsFalse() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mLayoutParams.setReceivePowerKeyDoublePressEnabled(false);
                    assertFalse(mLayoutParams.isReceivePowerKeyDoublePressEnabled());
                },
                OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW);
    }
}
