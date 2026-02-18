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
package android.car.cal.templates.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** Tests that the TemplatesHost is installed properly */
@RunWith(AndroidJUnit4.class)
public class TemplatesHostTest {

    public static final String TAG = TemplatesHostTest.class.getSimpleName();

    private static final String CAR_TEMPLATE_HOST_DEVICE_FEATURE =
            "android.software.car.templates_host";
    private static final String CAR_MEDIA_TEMPLATE_HOST_DEVICE_FEATURE =
            "android.software.car.templates_host.media";

    private static final String RENDERER_SERVICE_INTENT_ACTION =
            "android.car.template.host.RendererService";

    private static final List<String> REQUIRED_PERMISSIONS =
            Arrays.asList(
                    "android.car.permission.TEMPLATE_RENDERER",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                    "android.car.permission.CAR_DISPLAY_IN_CLUSTER",
                    "android.car.permission.CAR_NAVIGATION_MANAGER",
                    "android.permission.SCHEDULE_EXACT_ALARM",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.GET_PACKAGE_SIZE",
                    "android.permission.INTERNET",
                    "android.permission.WAKE_LOCK",
                    "android.permission.QUERY_ALL_PACKAGES",
                    "android.permission.CONTROL_INCALL_EXPERIENCE");

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
    }

    @Test
    @CddTest(requirements = "3.14/A-0-1")
    public void testTemplateHostFeatureFlag() {
        assertTrue(
                "Device must support android.software.car.templates_host", supportsTemplatesHost());
    }

    @Test
    @CddTest(requirements = "3.14/A-0-3")
    public void testMediaTemplateHostFeatureFlag() {
        assertTrue(
                "Device must support android.software.car.templates_host.media",
                supportsMediaTemplatesHost());
    }

    /** Tests that the Template Host is installed, and all required permission are granted. */
    @Test
    @CddTest(requirements = "3.14/A-0-1")
    public void testTemplatesHostInstalledWithAllRequestedPermissionsGranted() {
        assertTrue(
                "Device must support android.software.car.templates_host", supportsTemplatesHost());
        assertTrue(
                "Device must support android.software.car.templates_host.media",
                supportsMediaTemplatesHost());
        PackageManager pm = mContext.getPackageManager();
        Intent intent = new Intent(RENDERER_SERVICE_INTENT_ACTION);
        List<ResolveInfo> services =
                pm.queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER);

        if (services.isEmpty()) {
            fail(
                    "No Template Host found. The device must implement a service handling "
                            + RENDERER_SERVICE_INTENT_ACTION);
        }

        if (services.size() > 1) {
            fail(
                    "Multiple Template Hosts found. The device must implement only one service"
                            + " handling "
                            + RENDERER_SERVICE_INTENT_ACTION);
        }

        String packageName = services.get(0).serviceInfo.packageName;

        if (!hasPermissions(pm, packageName)) {
            fail("No Template Host found with all required permissions. " + REQUIRED_PERMISSIONS);
        }
    }

    private boolean hasPermissions(PackageManager pm, String packageName) {
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions == null) {
                return false;
            }

            for (String permission : REQUIRED_PERMISSIONS) {
                boolean granted = false;
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    if (permission.equals(pi.requestedPermissions[i])) {
                        if ((pi.requestedPermissionsFlags[i]
                                        & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                                != 0) {
                            granted = true;
                        }
                        break;
                    }
                }
                if (!granted) {
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return true;
    }

    private boolean supportsTemplatesHost() {
        return mContext.getPackageManager().hasSystemFeature(CAR_TEMPLATE_HOST_DEVICE_FEATURE);
    }

    private boolean supportsMediaTemplatesHost() {
        return mContext.getPackageManager()
                .hasSystemFeature(CAR_MEDIA_TEMPLATE_HOST_DEVICE_FEATURE);
    }
}
