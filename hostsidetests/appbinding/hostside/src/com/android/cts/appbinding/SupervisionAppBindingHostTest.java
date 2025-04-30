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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
public class SupervisionAppBindingHostTest extends BaseHostJUnit4Test implements IBuildReceiver {

    private static final boolean SKIP_UNINSTALL = false;
    private static final String APK_SUP1 = "CtsAppBindingServiceSupervision1.apk";
    private static final String APK_SUP2 = "CtsAppBindingServiceSupervision2.apk";
    private static final String APK_SUP3 = "CtsAppBindingServiceSupervision3.apk";

    private static final String PACKAGE_SUP = "com.android.cts.appbinding.supervision";

    private static final String APP_BINDING_SETTING = "app_binding_constants";

    private static final String SERVICE_SUP =
            "com.android.cts.appbinding.supervision.MySupervisionService";

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

    private void setSupervisionApp(String pkg, int userId) throws Throwable {
        runWithRetries(
                300,
                () -> {
                    String output1 =
                            runCommand(
                                    String.format(
                                            "cmd role get-role-holders --user %s"
                                                    + " android.app.role.SYSTEM_SUPERVISION ",
                                            userId));
                    if (output1.equals(pkg)) {
                        CLog.d(String.format("%s has been set default supervision app.", pkg));
                    } else {
                        String output2 =
                                runCommand(
                                        String.format(
                                                "cmd role add-role-holder --user %s"
                                                        + " android.app.role.SYSTEM_SUPERVISION %s",
                                                userId, pkg));
                        if (output2.contains("TimeoutException")) {
                            RunUtil.getDefault().sleep(10000);
                            throw new RuntimeException("cmd role add-role-holder timeout.");
                        }
                    }
                });
    }

    private void uninstallTestApps(boolean always) throws Exception {
        if (SKIP_UNINSTALL && !always) {
            return;
        }
        getDevice().uninstallPackage(PACKAGE_SUP);

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

    private void installAndCheckBound(
            String apk, String packageName, String serviceClass, int userId) throws Throwable {
        // Install
        installAppAsUser(apk, true, userId);

        // Set as the default app
        setSupervisionApp(packageName, userId);

        checkBound(packageName, serviceClass, userId, false);
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

    private void installAndCheckNotBound(
            String apk, String packageName, int userId, String expectedErrorPattern)
            throws Throwable {
        // Install
        installAppAsUser(apk, true, userId);

        // Set as the default app
        setSupervisionApp(packageName, userId);

        checkNotBoundWithError(packageName, userId, expectedErrorPattern);
    }

    private void checkNotBoundWithError(String packageName, int userId, String expectedErrorPattern)
            throws Throwable {
        // This should contain:
        // "finder,0,[Supervision app],0,PACKAGE,null,ERROR-MESSAGE"
        runWithRetries(
                DEFAULT_TIMEOUT_SEC,
                () -> {
                    runCommand(
                            "dumpsys app_binding -s",
                            String.format(
                                    "^%s(%s|null)%s.*%s.*$",
                                    Pattern.quote("finder,[Supervision app],"),
                                    userId,
                                    Pattern.quote(String.format(",%s,null,", packageName)),
                                    Pattern.quote(expectedErrorPattern)));
                });
    }

    /** Install APK 1 and make and make sure the service gets bound. */
    @Test
    public void testSimpleBind1() throws Throwable {
        installAndCheckBound(APK_SUP1, PACKAGE_SUP, SERVICE_SUP, mCurrentUserId);
    }

    /** APK 2 doesn't have a valid service to be bound. */
    @Test
    public void testSimpleNotBound2() throws Throwable {
        installAndCheckNotBound(
                APK_SUP2,
                PACKAGE_SUP,
                mCurrentUserId,
                "must be protected with android.permission.BIND_SUPERVISION_APP_SERVICE");
    }

    /** APK 3 doesn't have a valid service to be bound. */
    @Test
    public void testSimpleNotBound3() throws Throwable {
        installAndCheckNotBound(
                APK_SUP3,
                PACKAGE_SUP,
                mCurrentUserId,
                "Service with android.app.action.SUPERVISION_APP_SERVICE not found");
    }

    /** Test calling onEnabled(). */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testOnEnabled() throws Throwable {
        // We need to make sure it is bound before enabling/disabling supervision
        installAndCheckBound(APK_SUP1, PACKAGE_SUP, SERVICE_SUP, mCurrentUserId);

        waitForBroadcastIdle();
        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));

        // Check that supervisionAppService is bound and supervision is disabled
        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, true);
    }

    /** Test calling onDisabled(). */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
    public void testOnDisabled() throws Throwable {
        // We need to make sure it is bound before enabling/disabling supervision
        installAndCheckBound(APK_SUP1, PACKAGE_SUP, SERVICE_SUP, mCurrentUserId);

        waitForBroadcastIdle();
        runCommand(String.format("cmd supervision enable %s", mCurrentUserId));

        waitForBroadcastIdle();
        runCommand(String.format("cmd supervision disable %s", mCurrentUserId));

        // Check that supervisionAppService is bound and supervision is disabled
        checkBound(PACKAGE_SUP, SERVICE_SUP, mCurrentUserId, false);
    }
}
