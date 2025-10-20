/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.managedprofile;

import static android.app.admin.flags.Flags.FLAG_MULTI_USER_MANAGEMENT_DEVICE_PROVISIONING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.graphics.Color;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

public class OrganizationInfoTest extends BaseManagedProfileTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // needs to match DevicePolicyManagerService.ActiveAdmin.DEF_ORGANIZATION_COLOR
    private static final int DEFAULT_ORGANIZATION_COLOR = Color.parseColor("#00796B");

    @Test
    public void testDefaultOrganizationColor() {
        int defaultColor = mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT);
        assertEquals("Default color returned: " + Integer.toHexString(defaultColor),
                DEFAULT_ORGANIZATION_COLOR, defaultColor);
    }

    @Test
    public void testSetOrganizationColor() {
        int previousColor = mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT);

        try {
            final int[] colors = {
                Color.TRANSPARENT,
                Color.WHITE,
                Color.RED,
                Color.GREEN,
                Color.BLUE,
                0x7FFE5B35 /* HTML name: "Sunset orange". Opacity: 50%. */
            };

            for (int color : colors) {
                mDevicePolicyManager.setOrganizationColor(ADMIN_RECEIVER_COMPONENT, color);
                assertEquals(
                        color | 0xFF000000 /* opacity always enforced to 100% */,
                        mDevicePolicyManager.getOrganizationColor(ADMIN_RECEIVER_COMPONENT));
            }
        } finally {
            // Put the organization color back how it was.
            mDevicePolicyManager.setOrganizationColor(ADMIN_RECEIVER_COMPONENT, previousColor);
        }
    }

    @Test
    public void testSetOrGetOrganizationColorWithNullAdminFails() {
        try {
            mDevicePolicyManager.setOrganizationColor(null, Color.GRAY);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }

        try {
            int color = mDevicePolicyManager.getOrganizationColor(null);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testDefaultOrganizationNameIsNull() {
        CharSequence organizationName = mDevicePolicyManager.getOrganizationName(
                ADMIN_RECEIVER_COMPONENT);
        assertNull(organizationName);
    }

    @Test
    public void testSetOrganizationName() {
        CharSequence previousOrganizationName = mDevicePolicyManager.getOrganizationName(
                ADMIN_RECEIVER_COMPONENT);

        try {
            final CharSequence name = "test-set-name";
            mDevicePolicyManager.setOrganizationName(ADMIN_RECEIVER_COMPONENT, name);
            CharSequence organizationName =
                    mDevicePolicyManager.getOrganizationName(ADMIN_RECEIVER_COMPONENT);
            assertEquals(name, organizationName);
        } finally {
            mDevicePolicyManager.setOrganizationName(ADMIN_RECEIVER_COMPONENT,
                    previousOrganizationName);
        }
    }

    // TODO(450898476): This test needs to be deleted when the flag is ready for clean-up.
    @RequiresFlagsDisabled({FLAG_MULTI_USER_MANAGEMENT_DEVICE_PROVISIONING})
    @Test
    public void testSetOrGetOrganizationNameWithNullAdminFails() {
        try {
            mDevicePolicyManager.setOrganizationName(null, "null-admin-fails");
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }

        try {
            mDevicePolicyManager.getOrganizationName(null);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (Exception expected) {
        }
    }

    @RequiresFlagsEnabled({FLAG_MULTI_USER_MANAGEMENT_DEVICE_PROVISIONING})
    @Test
    public void testSetOrGetOrganizationNameWithNullAdmin() {
        CharSequence previousOrganizationName =
                mDevicePolicyManager.getOrganizationName(/* admin= */ null);

        try {
            final CharSequence name = "test-set-name";
            mDevicePolicyManager.setOrganizationName(/* admin= */ null, name);
            CharSequence organizationName =
                    mDevicePolicyManager.getOrganizationName(/* admin= */ null);
            assertNotNull(organizationName);
            assertEquals(name.toString(), organizationName.toString());
        } finally {
            mDevicePolicyManager.setOrganizationName(/* admin= */ null, previousOrganizationName);
        }
    }
}
