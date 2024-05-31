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

package android.packageinstaller.cts.cujinstaller;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID;
import static android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "CtsCujInstaller";
    private static final String TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts";
    private static final String TEST_APK_NAME = "CtsEmptyTestApp.apk";
    private static final String TEST_APK_V2_NAME = "CtsEmptyTestAppV2.apk";

    private static final String CONTENT_AUTHORITY =
            "android.packageinstaller.cts.cujinstaller.fileprovider";

    private static final String TEST_PACKAGE_NAME =
            "android.packageinstaller.criticaluserjourney.cts";
    private static final String ACTION_REQUEST_INSTALLER =
            "android.packageinstaller.cts.cujinstaller.action.REQUEST_INSTALLER";
    private static final String ACTION_RESPONSE_INSTALLER =
            "android.packageinstaller.cts.cujinstaller.action.RESPONSE_INSTALLER";
    private static final String EXTRA_EVENT = "extra_event";
    private static final String EXTRA_TEST_APK_URI = "extra_test_apk_uri";
    private static final String EXTRA_TEST_APK_V2_URI = "extra_test_apk_v2_uri";
    private static final String EXTRA_USE_APK_V2 = "extra_use_apk_v2";

    private static final int STATUS_CUJ_INSTALLER_READY = 1000;
    private static final int STATUS_CUJ_INSTALLER_START_ACTIVITY_READY = 1001;
    private static final int EVENT_REQUEST_INSTALLER_CLEAN_UP = -1;
    private static final int EVENT_REQUEST_INSTALLER_SESSION = 0;
    private static final int EVENT_REQUEST_INSTALLER_INTENT = 1;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_FOR_RESULT = 2;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI = 3;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI_FOR_RESULT = 4;
    private static final int REQUEST_CODE = 311;


    private PackageInstaller mPackageInstaller;
    private InstallResultReceiver mInstallResultReceiver;
    private RequestInstallerReceiver mRequestInstallerReceiver;
    private boolean mNotifyReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageInstaller = getPackageManager().getPackageInstaller();
        mRequestInstallerReceiver = new RequestInstallerReceiver();
        getApplicationContext().registerReceiver(mRequestInstallerReceiver,
                new IntentFilter(ACTION_REQUEST_INSTALLER), Context.RECEIVER_EXPORTED);
        copyTestFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNotifyReady) {
            mNotifyReady = false;
            sendInstallerResponseBroadcast(getApplicationContext(), STATUS_CUJ_INSTALLER_READY);
        }
    }

    private void cleanUp() {
        cleanUpSessions();
        if (mInstallResultReceiver != null) {
            getApplicationContext().unregisterReceiver(mInstallResultReceiver);
        }
        getApplicationContext().unregisterReceiver(mRequestInstallerReceiver);
    }

    private void cleanUpSessions() {
        List<PackageInstaller.SessionInfo> sessionInfoList = mPackageInstaller.getMySessions();
        Log.d(TAG, "cleanUpSessions size = " + sessionInfoList.size());
        for (int i = 0; i < sessionInfoList.size(); i++) {
            try {
                mPackageInstaller.abandonSession(sessionInfoList.get(i).getSessionId());
            } catch (Exception ignored) {
                // do nothing
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode: " + requestCode + ", resultCode: " + resultCode
                + ", data: " + data);

        if (requestCode == REQUEST_CODE) {
            sendInstallerResponseBroadcast(getApplicationContext(), resultCode);
        }
    }

    private static void sendInstallerResponseBroadcast(Context context, int status) {
        final Intent intent = new Intent(ACTION_RESPONSE_INSTALLER);
        intent.setPackage(TEST_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(EXTRA_STATUS, status);
        context.sendBroadcast(intent);
    }

    private void copyTestFiles() {
        Uri testApkUri = Uri.parse(getIntent().getStringExtra(EXTRA_TEST_APK_URI));
        Uri testApkV2Uri = Uri.parse(getIntent().getStringExtra(EXTRA_TEST_APK_V2_URI));
        try {
            copyApkFromUri(testApkUri, TEST_APK_NAME);
            copyApkFromUri(testApkV2Uri, TEST_APK_V2_NAME);
        } catch (Exception ex) {
            Log.e(TAG, "Copy test apks from uri failed." , ex);
            mNotifyReady = false;
        }
    }

    private void copyApkFromUri(Uri uri, String apkName) throws Exception {
        File file = new File(getFilesDir(), apkName);
        try (InputStream source = getContentResolver().openInputStream(uri);
                OutputStream target = new FileOutputStream(file)) {

            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }

    private void startInstallationViaPackageInstallerSession(String apkName) throws Exception {
        final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(TEST_APK_PACKAGE_NAME);

        final int sessionId = mPackageInstaller.createSession(params);

        final PackageInstaller.Session session = mPackageInstaller.openSession(sessionId);
        final File apkFile = new File(getFilesDir(), apkName);
        try (OutputStream os = session.openWrite("base.apk", 0, apkFile.length());
                InputStream is = new FileInputStream(apkFile)) {
            writeFullStream(is, os);
        }

        mInstallResultReceiver = new InstallResultReceiver();
        session.commit(mInstallResultReceiver.getIntentSender(getApplicationContext()));
    }

    private void startInstallationViaIntent(boolean getResult, String apkName) {
        final File apkFile = new File(getFilesDir(), apkName);
        final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(FileProvider.getUriForFile(this, CONTENT_AUTHORITY, apkFile));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, getResult);
        if (getResult) {
            startActivityForResult(intent, REQUEST_CODE);
        } else {
            startActivity(intent);
        }
        sendInstallerResponseBroadcast(getApplicationContext(),
                STATUS_CUJ_INSTALLER_START_ACTIVITY_READY);
    }

    private void startInstallationViaIntentWithPackageUri(boolean getResult) {
        final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(Uri.fromParts("package", TEST_APK_PACKAGE_NAME, null));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, getResult);
        if (getResult) {
            startActivityForResult(intent, REQUEST_CODE);
        } else {
            startActivity(intent);
        }
        sendInstallerResponseBroadcast(getApplicationContext(),
                STATUS_CUJ_INSTALLER_START_ACTIVITY_READY);
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
    }

    private static class InstallResultReceiver extends BroadcastReceiver {
        private static final String ACTION = InstallResultReceiver.class.getName()
                + "_" + SystemClock.elapsedRealtime();

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "InstallResultReceiver Received intent " + prettyPrint(intent));
            final int status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID);
            sendInstallerResponseBroadcast(context, status);
            if (status == STATUS_PENDING_USER_ACTION) {
                Intent extraIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                extraIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(extraIntent);
            }
        }

        public IntentSender getIntentSender(Context context) {
            context.registerReceiver(this, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
            Intent intent = new Intent(ACTION).setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                    FLAG_UPDATE_CURRENT | FLAG_MUTABLE);
            return pending.getIntentSender();
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

    private class RequestInstallerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int event = intent.getIntExtra(EXTRA_EVENT, /* defaultValue= */ -1);
            final boolean useTestApkV2 = intent.getBooleanExtra(EXTRA_USE_APK_V2,
                    /* defaultValue= */ false);
            Log.i(TAG, "RequestInstallerReceiver Received intent " + intent
                    + ", event: " + event + ", useTestApkV2:" + useTestApkV2);

            if (event == EVENT_REQUEST_INSTALLER_CLEAN_UP) {
                cleanUp();
            } else if (event == EVENT_REQUEST_INSTALLER_SESSION) {
                try {
                    if (useTestApkV2) {
                        startInstallationViaPackageInstallerSession(TEST_APK_V2_NAME);
                    } else {
                        startInstallationViaPackageInstallerSession(TEST_APK_NAME);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Exception event:" + event, ex);
                }
            } else if (event == EVENT_REQUEST_INSTALLER_INTENT) {
                try {
                    if (useTestApkV2) {
                        startInstallationViaIntent(/* getResult= */ false, TEST_APK_V2_NAME);
                    } else {
                        startInstallationViaIntent(/* getResult= */ false, TEST_APK_NAME);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Exception event:" + event, ex);
                }
            } else if (event == EVENT_REQUEST_INSTALLER_INTENT_FOR_RESULT) {
                try {
                    if (useTestApkV2) {
                        startInstallationViaIntent(/* getResult= */ true, TEST_APK_V2_NAME);
                    } else {
                        startInstallationViaIntent(/* getResult= */ true, TEST_APK_NAME);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Exception event:" + event, ex);
                }
            } else if (event == EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI) {
                try {
                    startInstallationViaIntentWithPackageUri(/* getResult= */ false);
                } catch (Exception ex) {
                    Log.e(TAG, "Exception event:" + event, ex);
                }
            } else if (event == EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI_FOR_RESULT) {
                try {
                    startInstallationViaIntentWithPackageUri(/* getResult= */ true);
                } catch (Exception ex) {
                    Log.e(TAG, "Exception event:" + event, ex);
                }
            }
        }
    }
}
