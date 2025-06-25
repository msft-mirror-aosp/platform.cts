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
package com.android.cts.appbinding;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.app.supervision.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@Ignore("This test needs to be migrated to Bedstead. See b/415023190.")
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
public class SupervisionAppBindingHostTest extends BaseHostJUnit4Test implements IBuildReceiver {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private static final boolean SKIP_UNINSTALL = false;
    private static final String APK_SUP1 = "CtsAppBindingServiceSupervision1.apk";
    private static final String APK_SUP2 = "CtsAppBindingServiceSupervision2.apk";
    private static final String APK_SUP3 = "CtsAppBindingServiceSupervision3.apk";
    private static final String APK_SUP4 = "CtsAppBindingServiceSupervision4.apk";
    private static final String APK_SUP5 = "CtsAppBindingServiceSupervision5.apk";

    private static final String PACKAGE_SUP = "com.android.cts.appbinding.supervision";
    private static final String PACKAGE_SUP2 = "com.android.cts.appbinding.supervision2";
    private static final String PACKAGE_SUP3 = "com.android.cts.appbinding.supervision3";

    private static final String SERVICE_SUP =
            "com.android.cts.appbinding.supervision.MySupervisionService";
    private static final String SERVICE_SUP2 =
            "com.android.cts.appbinding.supervision2.MySupervisionService2";
    private static final String SERVICE_SUP3 =
            "com.android.cts.appbinding.supervision3.MySupervisionService3";

    private static final String APP_BINDING_SETTING = "app_binding_constants";

    private IBuildInfo mCtsBuild;
    private int mCurrentUserId;

    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int DEFAULT_LONG_TIMEOUT_SEC = 70;

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private void installAppAsUser(String appFileName, boolean grantPermissions, int userId)
            throws Exception {
        CLog.d(String.format("Installing app %s for user %s", appFileName, userId));
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        String result =
                getDevice()
                        .installPackageForUser(
                                buildHelper.getTestFile(appFileName),
                                true, // reinstall app if it is already installed
                                grantPermissions,
                                userId,
                                "-t");
        assertNull(
                String.format("Failed to install %s for user %s: %s", appFileName, userId, result),
                result);
        waitForBroadcastIdle();
    }

    private void waitForBroadcastIdle() throws Exception {
        runCommand("am wait-for-broadcast-idle");
        RunUtil.getDefault()
                .sleep(100); // Just wait a bit to make sure the system isn't too busy...
    }

    private String runCommand(String command) throws Exception {
        return runCommand(command, "", true);
    }

    private String runCommand(String command, String expectedOutputPattern) throws Exception {
        return runCommand(command, expectedOutputPattern, true);
    }

    private String runCommand(String command, String expectedOutputPattern, boolean shouldMatch)
            throws Exception {
        CLog.d(String.format("Executing command: %s", command));
        final String output = getDevice().executeShellCommand(command);

        CLog.d(String.format("Output:\n====================\n%s====================", output));

        final Pattern pat =
                Pattern.compile(expectedOutputPattern, Pattern.MULTILINE | Pattern.COMMENTS);
        if (pat.matcher(output.trim()).find() != shouldMatch) {
            fail(
                    String.format(
                            "Output from \"%s\" %s \"%s\"",
                            command,
                            (shouldMatch ? "didn't match" : "unexpectedly matched"),
                            expectedOutputPattern));
        }
        return output;
    }

    private void updateConstants(String settings) throws Exception {
        runCommand(String.format("settings put global %s '%s'", APP_BINDING_SETTING, settings));
    }

    private void setSupervisionApp(String pkg, int userId, String roleName) throws Throwable {
        runWithRetries(
                300,
                () -> {
                    String output1 =
                            runCommand(
                                    String.format(
                                            "cmd role get-role-holders --user %s %s ",
                                            userId, roleName));
                    if (output1.equals(pkg)) {
                        CLog.d(String.format("%s has been set default supervision app.", pkg));
                    } else {
                        String output2 =
                                runCommand(
                                        String.format(
                                                "cmd role add-role-holder --user %s %s %s",
                                                userId, roleName, pkg));
                        if (output2.contains("TimeoutException")) {
                            RunUtil.getDefault().sleep(10000);
                            throw new RuntimeException("cmd role add-role-holder timeout.");
                        }
                    }
                });
    }

    private void setSystemSupervisionApp(String pkg, int userId) throws Throwable {
        setSupervisionApp(pkg, userId, "android.app.role.SYSTEM_SUPERVISION");
    }

    private void setSupervisionApp(String pkg, int userId) throws Throwable {
        setSupervisionApp(pkg, userId, "android.app.role.SUPERVISION");
    }

    private void uninstallTestApps(boolean always) throws Exception {
        if (SKIP_UNINSTALL && !always) {
            return;
        }
        getDevice().uninstallPackage(PACKAGE_SUP);
        getDevice().uninstallPackage(PACKAGE_SUP2);
        getDevice().uninstallPackage(PACKAGE_SUP3);

        waitForBroadcastIdle();
    }

    private void runWithRetries(int timeoutSeconds, ThrowingRunnable r) throws Throwable {
        final long timeout = System.currentTimeMillis() + timeoutSeconds * 1000;
        Throwable lastThrowable = null;

        int sleep = 200;
        while (System.currentTimeMillis() < timeout) {
            try {
                r.run();
                return;
            } catch (Throwable th) {
                lastThrowable = th;
            }
            RunUtil.getDefault().sleep(sleep);
            sleep = Math.min(1000, sleep * 2);
        }
        throw lastThrowable;
    }

    @Before
    public void setUp() throws Exception {
        // Reset to the default setting.
        updateConstants(",");

        uninstallTestApps(true);

        mCurrentUserId = getDevice().getCurrentUser();
    }

    @After
    public void tearDown() throws Exception {
        uninstallTestApps(false);

        // Reset to the default setting.
        updateConstants(",");
    }

    private void checkBound(
            String packageName, String serviceClass, int userId, boolean supervisionEnabled)
            throws Throwable {
        runWithRetries(
                DEFAULT_LONG_TIMEOUT_SEC,
                () -> {
                    runCommand(
                            String.format(
                                    "dumpsys activity service %s/%s", packageName, serviceClass),
                            String.format(
                                    "%s .* %s.*%s",
                                    Pattern.quote(String.format("[%s]", packageName)),
                                    Pattern.quote(String.format("[%s]", serviceClass)),
                                    Pattern.quote(
                                            String.format("Enabled=[%s]", supervisionEnabled))));
                });

        // This should contain:
        // "conn,[Supervision app],0,PACKAGE,CLASS,bound,connected"

        // The binding information is propagated asynchronously, so we need a retry here too.
        // (Even though the activity manager said it's already bound.)
        runWithRetries(
                DEFAULT_TIMEOUT_SEC,
                () -> {
                    runCommand(
                            "dumpsys app_binding -s",
                            String.format(
                                    "^%s",
                                    Pattern.quote(
                                            String.format(
                                                    "conn,[Supervision"
                                                            + " app],%s,%s,%s,bound,connected,",
                                                    userId, packageName, serviceClass))));
                });
    }

    private void checkNotBoundWithError(String packageName, int userId,
            String expectedErrorPattern) throws Throwable {
        // This should contain:
        // "finder,[Supervision app],0,PACKAGE,null,ERROR-MESSAGE"
        runWithRetries(DEFAULT_TIMEOUT_SEC, () -> {
            runCommand("dumpsys app_binding -s",
                    "^" + Pattern.quote("finder,[Supervision app]," + userId + ","
                            + packageName + ",null,") + ".*"
                            + Pattern.quote(expectedErrorPattern) + ".*$");
        });
    }

    /** Test binding to one supervision app with SYSTEM_SUPERVISION role. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testSimpleBound_systemSupervisionRole() throws Throwable {
        installAppAsUser(APK_SUP1, true, mCurrentUserId);
        setSystemSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));

    }

    /** Test binding to one supervision app with SUPERVISION role. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testSimpleBound_supervisionRole() throws Throwable {
        installAppAsUser(APK_SUP1, true, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** Test binding to one app with SYSTEM_SUPERVISION and one app with SUPERVISION role. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testSimpleBound_oneSystemSupervisionOneSupervision() throws Throwable {
        installAppAsUser(APK_SUP1, true, mCurrentUserId);
        installAppAsUser(APK_SUP2, true, mCurrentUserId);
        setSystemSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP2, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
        checkBound(PACKAGE_SUP2, SERVICE_SUP2, mCurrentUserId, true);
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** Test binding to one supervision app with two supervision apps with SUPERVISION role. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testSimpleBound_twoAppsNoSystemSupervision() throws Throwable {
        installAppAsUser(APK_SUP1, true, mCurrentUserId);
        installAppAsUser(APK_SUP2, true, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP2, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
        checkBound(PACKAGE_SUP2, SERVICE_SUP2, mCurrentUserId, true);
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** Test binding to one supervision app with three apps. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testSimpleBound_threeSupervisionApps() throws Throwable {
        installAppAsUser(APK_SUP1, true, mCurrentUserId);
        installAppAsUser(APK_SUP2, true, mCurrentUserId);
        installAppAsUser(APK_SUP3, true, mCurrentUserId);
        setSystemSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP2, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP3, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
        checkBound(PACKAGE_SUP2, SERVICE_SUP2, mCurrentUserId, true);
        checkBound(PACKAGE_SUP3, SERVICE_SUP3, mCurrentUserId, true);
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** APK 4 doesn't have a valid service to be bound. */
    @Test
    public void testSimpleNotBound1() throws Throwable {
        installAppAsUser(APK_SUP4, true, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkNotBoundWithError(PACKAGE_SUP, mCurrentUserId,
                "must be protected with android.permission.BIND_SUPERVISION_APP_SERVICE");
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** APK 5 doesn't have a valid service to be bound. */
    @Test
    public void testSimpleNotBound2() throws Throwable {
        installAppAsUser(APK_SUP5, true, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkNotBoundWithError(PACKAGE_SUP, mCurrentUserId,
                "Service with android.app.action.SUPERVISION_APP_SERVICE not found");
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }

    /** Three supervision apps, but one doesn't have a valid service. */
    @Test
    public void testTwoBoundOneNotBound() throws Throwable {
        installAppAsUser(APK_SUP5, true, mCurrentUserId);
        installAppAsUser(APK_SUP2, true, mCurrentUserId);
        installAppAsUser(APK_SUP3, true, mCurrentUserId);
        setSystemSupervisionApp(PACKAGE_SUP, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP2, mCurrentUserId);
        setSupervisionApp(PACKAGE_SUP3, mCurrentUserId);
        waitForBroadcastIdle();

        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));
        waitForBroadcastIdle();

        checkBound(PACKAGE_SUP2, SERVICE_SUP2, mCurrentUserId, true);
        checkBound(PACKAGE_SUP3, SERVICE_SUP3, mCurrentUserId, true);
        checkNotBoundWithError(PACKAGE_SUP, mCurrentUserId,
                "Service with android.app.action.SUPERVISION_APP_SERVICE not found");
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));
    }
}
