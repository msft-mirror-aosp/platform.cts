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

package android.app.appsearch.cts.service;

import static org.junit.Assert.assertTrue;

import android.app.appsearch.testutil.AppSearchTestUtils;
import android.app.appsearch.testutil.SystemUtil;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.appsearch.flags.Flags;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobSchedulerTest {

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();
    private static final String APPSEARCH_MAINTENANCE_JOB_REGEX =
            "JOB #\\d+/\\d+:\\s+[a-f0-9]+\\s+android/com\\.android\\.server\\.appsearch\\"
                    + ".AppSearchMaintenanceService\\s*.*"
                    + "Source: uid=1000 user=0 pkg=android\\s*.*"
                    + "Service: android/com\\.android\\.server\\.appsearch\\."
                    + "AppSearchMaintenanceService\\s*.*"
                    + "PERIODIC:\\s+interval=\\+1d0h0m0s0ms\\s+flex=\\+1d0h0m0s0ms\\s*.*"
                    + "\\s*PERSISTED\\s*.*";

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SCHEDULE_MAINTENANCE_JOB)
    @Ignore("Temporarily disable this test due to AppSearch maintenance service SDK issue")
    public void testMaintenanceServiceJob() {
        // This test is disabled temporarily due to:
        // - FLAG_ENABLE_SCHEDULE_MAINTENANCE_JOB is released in AppSearch mainline (M-2026-02).
        // - But the registry of AppSearch maintenance service is not released together with
        //   AppSearch mainline.
        // - This CTS test only checks the maintenance service via dumpsys, so it fails if running
        //   on a device with M-2026-02 AppSearch mainline but without AppSearch maintenance service
        //   registry.
        //
        // TODO(b/408269409): add SDK check to this cts test and enable it once figuring out the SDK
        //   version for AppSearch maintenance service registry.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    String commandOutput =
                            SystemUtil.runShellCommandRuntime("dumpsys jobscheduler");
                    Pattern regexPattern =
                            Pattern.compile(APPSEARCH_MAINTENANCE_JOB_REGEX, Pattern.DOTALL);
                    Matcher matcher = regexPattern.matcher(commandOutput);
                    assertTrue(matcher.find());
                },
                android.Manifest.permission.DUMP,
                android.Manifest.permission.PACKAGE_USAGE_STATS);
    }
}
