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

package android.server.wm.display;

import static android.server.wm.allowvirtualgamepadoverrideoptin.Components.ALLOW_VIRTUAL_GAMEPAD_OVERRIDE_OPT_IN_ACTIVITY;
import static android.server.wm.allowvirtualgamepadoverrideoptout.Components.ALLOW_VIRTUAL_GAMEPAD_OVERRIDE_OPT_OUT_ACTIVITY;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.WindowManagerTestBase;

import com.android.compatibility.common.util.ApiTest;
import com.android.window.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the {@link android.view.WindowManager#PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE}
 * property.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceDisplay:VirtualGamepadPropertyTest
 */
@Presubmit
@ApiTest(apis = "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE")
public class VirtualGamepadPropertyTest extends WindowManagerTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_GAMEPAD_DEVELOPER_OPT_OUT)
    public void testPropertyAllowVirtualGamepadOverride_OptIn()
            throws PackageManager.NameNotFoundException {
        assertTrue(getProperty(ALLOW_VIRTUAL_GAMEPAD_OVERRIDE_OPT_IN_ACTIVITY.getPackageName()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_GAMEPAD_DEVELOPER_OPT_OUT)
    public void testPropertyAllowVirtualGamepadOverride_OptOut()
            throws PackageManager.NameNotFoundException {
        assertFalse(getProperty(ALLOW_VIRTUAL_GAMEPAD_OVERRIDE_OPT_OUT_ACTIVITY.getPackageName()));
    }

    private boolean getProperty(String packageName) throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        final PackageManager.Property property =
                pm.getProperty(PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE, packageName);
        assertNotNull(property);
        assertTrue(property.isBoolean());
        assertEquals(PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE, property.getName());
        assertEquals(packageName, property.getPackageName());
        return property.getBoolean();
    }
}
