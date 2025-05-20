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

package com.android.tests.protectedpackages;

import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;
import static android.Manifest.permission.DELETE_PACKAGES;
import static android.Manifest.permission.INSTALL_PACKAGES;
import static android.Manifest.permission.MANAGE_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PROTECT_SUPERVISION_PACKAGES)
public class ProtectedPackagesTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static PackageManager sPackageManager;

    @BeforeClass
    public static void initialize() {
        sPackageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
    }

    @Before
    public void setUp() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(INSTALL_PACKAGES)) {
            Install.single(TestApp.A1).commit();
        }
    }

    @After
    public void tearDown() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(DELETE_PACKAGES)) {
            Uninstall.packages(TestApp.A);
        }
    }

    /**
     * The permission MANAGE_USERS and QUERY_USERS are needed to call SupervisionManager. The test
     * should pass because ProtectedPackages calls SupervisionManager as the SystemServer.
     */
    @Test
    public void testSetApplicationHiddenApi() throws Exception {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(CHANGE_COMPONENT_ENABLED_STATE)
                        .withoutPermission(MANAGE_USERS)
                        .withoutPermission(QUERY_USERS)) {
            sPackageManager.setApplicationEnabledSetting(
                    TestApp.A, COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
    }
}
