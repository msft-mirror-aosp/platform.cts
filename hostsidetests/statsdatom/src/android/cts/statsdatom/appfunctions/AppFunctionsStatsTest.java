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
package android.cts.statsdatom.appfunctions;

import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER;
import static android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED;
import static android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.StatsLog;
import com.android.os.appfunctions.AppFunctionsExtensionAtoms;
import com.android.os.appfunctions.AppFunctionsRequestReported;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
@RequiresFlagsEnabled(FLAG_ENABLE_APP_FUNCTION_MANAGER)
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppFunctionsStatsTest extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String TEST_PKG = "android.app.appfunctions.cts";
    private static final String TEST_CLASS = TEST_PKG + ".AppFunctionManagerTest";
    private static final String ACCESS_ENABLED_TEST_CLASS =
            TEST_PKG + ".AppFunctionManagerAccessEnabledTest";
    private static final int ERROR_DENIED = 1000;

    private static final int ERROR_INVALID_ARGUMENT = 1001;
    private static final int SUCCESS_ERROR_CODE = -1;
    private static final int THROWS_EXCEPTION_ERROR_CODE = 3000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private IBuildInfo mCtsBuild;
    private ExtensionRegistry mExtensionRegistry;

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), "CtsAppFunctionTestCases.apk", TEST_PKG, mCtsBuild);
        DeviceUtils.installTestApp(
                getDevice(), "CtsAppFunctionsTestHelper.apk", TEST_PKG, mCtsBuild);
        DeviceUtils.installTestApp(
                getDevice(), "CtsAppFunctionsSidecarTestHelper.apk", TEST_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        mExtensionRegistry = ExtensionRegistry.newInstance();
        AppFunctionsExtensionAtoms.registerAllExtensions(mExtensionRegistry);
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                AppFunctionsExtensionAtoms.APP_FUNCTIONS_REQUEST_REPORTED_FIELD_NUMBER);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    @RequiresFlagsDisabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_failed_uncaughtClientException() throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runTestAndGetAtom("executeAppFunction_failed_uncaughtClientException_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_failed_uncaughtClientException_accessEnabled()
            throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runAccessEnabledTestAndGetAtom(
                        "executeAppFunction_failed_uncaughtClientException_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsDisabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_crossUser_success() throws Exception {
        if (!getDevice().isMultiUserSupported()) return;

        AppFunctionsRequestReported afRequestReported =
                runTestAndGetAtom("executeAppFunction_crossUser_success_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(SUCCESS_ERROR_CODE);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_crossUser_fail() throws Exception {
        if (!getDevice().isMultiUserSupported()) return;

        AppFunctionsRequestReported afRequestReported =
                runAccessEnabledTestAndGetAtom(
                        "executeAppFunction"
                                + "_crossUserWithCrossProfileFullPermission_fail_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(ERROR_DENIED);
    }

    @Test
    @RequiresFlagsDisabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_platformManager_platformAppFunctionService_success()
            throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runTestAndGetAtom(
                        "executeAppFunction_platformManager"
                                + "_platformAppFunctionService_success_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(SUCCESS_ERROR_CODE);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void
            testAtom_executeAppFunction_platformManager_platformAppFunctionService_success_accessEnabled()
                    throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runAccessEnabledTestAndGetAtom(
                        "executeAppFunction_platformManager"
                                + "_platformAppFunctionService_success_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(SUCCESS_ERROR_CODE);
    }

    @Test
    @RequiresFlagsDisabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_throwsException() throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runTestAndGetAtom("executeAppFunction_throwsException_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(THROWS_EXCEPTION_ERROR_CODE);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
    })
    public void testAtom_executeAppFunction_throwsException_accessEnabled() throws Exception {
        AppFunctionsRequestReported afRequestReported =
                runAccessEnabledTestAndGetAtom("executeAppFunction_throwsException_nonParam");

        assertThat(afRequestReported.getErrorCode()).isEqualTo(THROWS_EXCEPTION_ERROR_CODE);
    }

    private AppFunctionsRequestReported runAccessEnabledTestAndGetAtom(String testName)
            throws Exception {
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, ACCESS_ENABLED_TEST_CLASS, testName);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), mExtensionRegistry);
        assertThat(data).hasSize(1);

        return data.getFirst()
                .getAtom()
                .getExtension(AppFunctionsExtensionAtoms.appFunctionsRequestReported);
    }

    private AppFunctionsRequestReported runTestAndGetAtom(String testName) throws Exception {
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, testName);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), mExtensionRegistry);
        assertThat(data).hasSize(1);

        return data.getFirst()
                .getAtom()
                .getExtension(AppFunctionsExtensionAtoms.appFunctionsRequestReported);
    }
}
