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

package com.android.cts.declarepermissions.withspaces;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VerifyPermissionNameTrimmingTest {
    private static final String PERM = " android.permission.POST_NOTIFICATIONS";
    private static final String PERM_GROUP = " com.android.cts.test.group";

    private static final String sPkgName =
            InstrumentationRegistry.getTargetContext().getPackageName();
    private static final PackageManager sPm =
            InstrumentationRegistry.getTargetContext().getPackageManager();

    @Test
    public void verifyPermissionNamesTrimmed() throws Exception {
        assertThrows(
                PackageManager.NameNotFoundException.class, () -> sPm.getPermissionInfo(PERM, 0));
        PermissionInfo pI = sPm.getPermissionInfo(PERM.trim(), 0);
        assertNotNull(pI);
        assertNotEquals(sPkgName, pI.packageName);
        assertNotEquals(Manifest.permission_group.PHONE, pI.group);
    }

    @Test
    public void verifyPermissionGroupNamesTrimmed() throws Exception {
        assertThrows(
                PackageManager.NameNotFoundException.class,
                () -> sPm.getPermissionGroupInfo(PERM_GROUP, 0));
        assertNotNull(sPm.getPermissionGroupInfo(PERM_GROUP.trim(), 0));
    }
}
