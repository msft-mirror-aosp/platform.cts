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

package android.packageinstaller.criticaluserjourney.cts;

import static android.Manifest.permission.DELETE_PACKAGES;
import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED;
import static android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED;
import static android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID;
import static android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION;
import static android.content.pm.PackageInstaller.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;

import org.junit.After;
import org.junit.Before;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The test base to test PackageInstaller uninstallation.
 */
public class UninstallationTestBase extends PackageInstallerCujTestBase {

    private static final String ACTION_UNINSTALL_RESULT =
            "android.packageinstaller.criticaluserjourney.cts.action.UNINSTALL_RESULT";

    private static CompletableFuture<Intent> sUninstallResult;
    private static UninstallResultReceiver sUninstallResultReceiver;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        resetUninstallResult();
        if (shouldInstallTestAppInTestBaseSetup()) {
            installTestPackage();
        }
        sUninstallResultReceiver = new UninstallResultReceiver();
        getContext().registerReceiver(sUninstallResultReceiver,
                new IntentFilter(ACTION_UNINSTALL_RESULT), Context.RECEIVER_EXPORTED);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        if (sUninstallResultReceiver != null) {
            getContext().unregisterReceiver(sUninstallResultReceiver);
            sUninstallResultReceiver = null;
        }
        super.tearDown();
    }

    /*
     * It the test case extends the test base and it doesn't need to install the test app in setup
     * method, it can override this method and return false.
     */
    protected boolean shouldInstallTestAppInTestBaseSetup() {
        return true;
    }

    private static Intent getUninstallResult() throws Exception {
        return sUninstallResult.get(10, TimeUnit.SECONDS);
    }

    private static int getUninstallStatus() throws Exception {
        return getUninstallResult().getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID);
    }

    private static void resetUninstallResult() {
        sUninstallResult = new CompletableFuture<>();
    }

    private static IntentSender getIntentSender() {
        Intent intent = new Intent(ACTION_UNINSTALL_RESULT).setPackage(
                getContext().getPackageName()).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        PendingIntent pending = PendingIntent.getBroadcast(getContext(), 0, intent,
                FLAG_UPDATE_CURRENT | FLAG_MUTABLE);
        return pending.getIntentSender();
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} via
     * PackageInstaller#uninstall api with granting DELETE_PACKAGES permission
     */
    public static void startUninstallationViaPackageInstallerApiWithDeletePackages(
            boolean isSameInstaller) throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackages(isSameInstaller,
                TEST_APP_PACKAGE_NAME);
    }

    /**
     * Start the uninstallation for {@code packageName} via PackageInstaller#uninstall api
     * with granting DELETE_PACKAGES permission
     */
    public static void startUninstallationViaPackageInstallerApiWithDeletePackages(
            boolean isSameInstaller, String packageName) throws Exception {
        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(DELETE_PACKAGES);
            getPackageManager().getPackageInstaller().uninstall(packageName, getIntentSender());
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }

        if (isSameInstaller) {
            // Grant DELETE_PACKAGES permission, the test app will be uninstalled silently.
            assertThat(getUninstallStatus()).isNotEqualTo(STATUS_PENDING_USER_ACTION);
        } else {
            assertThat(getUninstallStatus()).isEqualTo(STATUS_PENDING_USER_ACTION);
            getUninstallResultAndStartConfirmedActivity();
        }
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} via
     * PackageInstaller#uninstall api
     */
    public static void startUninstallationViaPackageInstallerApi() throws Exception {
        startUninstallationViaPackageInstallerApi(TEST_APP_PACKAGE_NAME);
    }

    /**
     * Start the uninstallation for {@code packageName} via PackageInstaller#uninstall api
     */
    public static void startUninstallationViaPackageInstallerApi(String packageName)
            throws Exception {
        getPackageManager().getPackageInstaller().uninstall(packageName, getIntentSender());

        assertThat(getUninstallStatus()).isEqualTo(STATUS_PENDING_USER_ACTION);

        getUninstallResultAndStartConfirmedActivity();
    }

    private static void getUninstallResultAndStartConfirmedActivity() throws Exception {
        final Intent result = getUninstallResult();
        Intent extraIntent = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertThat(extraIntent).isNotNull();
        extraIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(extraIntent);
        resetUninstallResult();
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} via startActivity with
     * ACTION_DELETE.
     */
    public static void startUninstallationViaIntentActionDelete() throws Exception {
        startUninstallationViaIntentActionDelete(TEST_APP_PACKAGE_NAME);
    }

    /**
     * Start the uninstallation for {@code packageName} via startActivity with ACTION_DELETE.
     */
    public static void startUninstallationViaIntentActionDelete(String packageName)
            throws Exception {
        startUninstallationViaIntent(Intent.ACTION_DELETE, packageName);
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} via startActivity with
     * ACTION_UNINSTALL_PACKAGE.
     */
    public static void startUninstallationViaIntentActionUninstallPackage() throws Exception {
        startUninstallationViaIntentActionUninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    /**
     * Start the uninstallation for {@code packageName} via startActivity with
     * ACTION_UNINSTALL_PACKAGE.
     */
    public static void startUninstallationViaIntentActionUninstallPackage(String packageName)
            throws Exception {
        startUninstallationViaIntent(Intent.ACTION_UNINSTALL_PACKAGE, packageName);
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} on {@code context} via
     * startActivity with ACTION_DELETE.
     */
    public static void startUninstallationViaIntentActionDeleteForUser(Context context)
            throws Exception {
        startUninstallationViaIntentForUser(context, Intent.ACTION_DELETE);
    }

    /**
     * Start the uninstallation for {@link #TEST_APP_PACKAGE_NAME} on {@code context} via
     * startActivity with ACTION_UNINSTALL_PACKAGE.
     */
    public static void startUninstallationViaIntentActionUninstallPackageForUser(Context context)
            throws Exception {
        startUninstallationViaIntentForUser(context, Intent.ACTION_UNINSTALL_PACKAGE);
    }

    private static void startUninstallationViaIntentForUser(Context context, String action)
            throws Exception {
        final Intent intent = getIntentAndGrantPermission(action, TEST_APP_PACKAGE_NAME);
        intent.putExtra(Intent.EXTRA_USER, context.getUser());
        getContext().startActivity(intent);
    }

    private static void startUninstallationViaIntent(String action, String packageName)
            throws Exception {
        final Intent intent = getIntentAndGrantPermission(action, packageName);
        getContext().startActivity(intent);
    }

    private static Intent getIntentAndGrantPermission(String action, String packageName) {
        final Intent intent = new Intent(action);
        intent.setData(Uri.fromParts("package", packageName, null));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    /**
     * Click the OK button and wait for the uninstallation dialog to disappear.
     */
    public static void clickUninstallOkButton() throws Exception {
        assertUninstallDialog();
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_OK_LABEL));
    }

    /**
     * Click the OK button for uninstalling the clone app and wait for the uninstallation dialog
     * to disappear.
     */
    public static void clickUninstallCloneOkButton() throws Exception {
        findPackageInstallerObject(TEST_APP_LABEL + " " + CLONE_LABEL);
        findPackageInstallerObject(By.textContains(DELETE_LABEL), /* checkNull= */ true);
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_OK_LABEL));
    }

    /**
     * Click the OK button for uninstalling the test app from the work profile and
     * wait for the uninstallation dialog to disappear.
     */
    public static void clickUninstallAppFromWorkProfileOkButton() throws Exception {
        assertUninstallDialog();
        findPackageInstallerObject(By.textContains(WORK_PROFILE_LABEL), /* checkNull= */ true);
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_OK_LABEL));
    }

    /**
     * Assert the title is {@link #TEST_APP_LABEL} and the content includes
     * {@link #UNINSTALL_LABEL}.
     */
    public static void assertUninstallDialog() throws Exception {
        assertTitleIsTestAppLabel();
        findPackageInstallerObject(By.textContains(UNINSTALL_LABEL), /* checkNull= */ true);
    }

    /**
     * Assert the uninstall status is PackageInstaller#STATUS_SUCCESS.
     */
    public static void assertUninstallSuccess() throws Exception {
        assertThat(getUninstallStatus()).isEqualTo(STATUS_SUCCESS);
        resetUninstallResult();
    }

    /**
     * Assert the uninstall status is PackageInstaller#STATUS_FAILURE_BLOCKED.
     */
    public static void assertUninstallFailureBlocked() throws Exception {
        assertThat(getUninstallStatus()).isEqualTo(STATUS_FAILURE_BLOCKED);
        resetUninstallResult();
    }

    /**
     * Assert the uninstall status is PackageInstaller#STATUS_FAILURE_ABORTED.
     */
    public static void assertUninstallFailureAborted() throws Exception {
        assertThat(getUninstallStatus()).isEqualTo(STATUS_FAILURE_ABORTED);
        resetUninstallResult();
    }


    /**
     * Assert the test package is installed on the {@code userContext}
     */
    public static void assertTestPackageInstalledOnUser(@NonNull Context userContext) {
        assertThat(isTestPackageInstalledOnUser(userContext)).isTrue();
    }

    /**
     * Assert the test package is NOT installed on the {@code userContext}
     */
    public static void assertTestPackageNotInstalledOnUser(@NonNull Context userContext) {
        assertThat(isTestPackageInstalledOnUser(userContext)).isFalse();
    }

    public static class UninstallResultReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "UninstallResultReceiver Received intent " + prettyPrint(intent));
            sUninstallResult.complete(intent);
        }

        private static String prettyPrint(Intent intent) {
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            int status = intent.getIntExtra(EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            return String.format("%s: {\n"
                    + "sessionId = %d\n"
                    + "status = %d\n"
                    + "message = %s\n"
                    + "}", intent, sessionId, status, message);
        }
    }
}
