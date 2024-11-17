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

package android.content.pm.cts;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;

import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.dependencyinstaller.DependencyInstallerCallback;
import android.content.pm.dependencyinstaller.DependencyInstallerService;
import android.os.SystemClock;
import android.util.Log;
import android.util.PackageUtils;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import libcore.util.HexEncoding;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/*
 * A DependencyInstallerService for test.
 *
 * It resolves dependency on SDK_1 synchronously, i.e., waits for installation to complete before
 * notifying.
 *
 * If there is dependency on SDK_2, then it resolves them asynchronously, i.e., returns session id
 * to caller without waiting for installation to complete. It's up to the caller to wait.
 *
 * Anything else can't be resolved by this installer.
 */
public class TestDependencyInstallerService extends DependencyInstallerService {

    private static final String TAG = TestDependencyInstallerService.class.getSimpleName();
    private static final String LIB_NAME_SDK_1 = "com.test.sdk1";
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_SDK1_APK_NAME = "HelloWorldSdk1";

    private String mCertDigest;

    @Override
    public void onDependenciesRequired(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) {
        Log.i(TAG, "onDependenciesRequired call received");

        // All CTS test artifacts are signed with same cert. So we can assume the SDK apks we
        // have will be same cert as our current package.
        try {
            mCertDigest = getPackageCertDigest(getContext().getPackageName());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            callback.onFailureToResolveAllDependencies();
            return;
        }

        for (SharedLibraryInfo info: neededLibraries) {
            // DIS only knows how to handle SDK1
            if (isSdk1(info)) {
                continue;
            }
            // For everything else, fail.
            Log.i(TAG, "Unsupported SDK found: " + info.getName() + " "
                    + info.getCertDigests().get(0));
            callback.onFailureToResolveAllDependencies();
            return;
        }

        installDependenciesBlocked(callback);
    }

    private boolean isSdk1(SharedLibraryInfo info) {
        if (info.getName() == null || !info.getName().equals(LIB_NAME_SDK_1)) {
            return false;
        }
        if (info.getCertDigests().isEmpty()
                || !info.getCertDigests().get(0).equals(mCertDigest)) {
            return false;
        }
        return true;
    }

    private void installDependenciesBlocked(DependencyInstallerCallback callback) {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            SessionParams params = new SessionParams(MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            Log.i(TAG, "Session created: " + sessionId);
            Session session = installer.openSession(sessionId);
            writeApk(session, TEST_SDK1_APK_NAME);
            SyncBroadcastReceiver sender = new SyncBroadcastReceiver(callback, sessionId);
            session.commit(sender.getIntentSender(this));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            callback.onFailureToResolveAllDependencies();
        }

    }

    private void writeApk(@NonNull Session session, @NonNull String name) throws IOException {
        String apkPath = createApkPath(name);
        try (InputStream in = new FileInputStream(apkPath)) {
            try (OutputStream out = session.openWrite(name, 0, -1)) {
                copyStream(in, out);
            }
        }
    }

    private String getPackageCertDigest(String packageName) throws Exception {
        PackageInfo sdkPackageInfo = getPackageManager().getPackageInfo(packageName,
                PackageManager.PackageInfoFlags.of(
                    GET_SIGNING_CERTIFICATES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
        SigningInfo signingInfo = sdkPackageInfo.signingInfo;
        Signature[] signatures =
            signingInfo != null ? signingInfo.getSigningCertificateHistory() : null;
        byte[] digest = PackageUtils.computeSha256DigestBytes(signatures[0].toByteArray());
        return new String(HexEncoding.encode(digest));
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            total += c;
            out.write(buffer, 0, c);
        }
    }

    private static class SyncBroadcastReceiver extends BroadcastReceiver {
        private final DependencyInstallerCallback mCallback;
        private final int mSessionId;

        SyncBroadcastReceiver(DependencyInstallerCallback callback, int sessionId) {
            mCallback = callback;
            mSessionId = sessionId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received intent " + prettyPrint(intent));
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Log.i(TAG, "PENDING USER ACTION!");
                mCallback.onFailureToResolveAllDependencies();
            } else {
                int[] result = {mSessionId};
                mCallback.onAllDependenciesResolved(result);
            }
        }

        public IntentSender getIntentSender(Context context) {
            String action = TestDependencyInstallerService.class.getName()
                    + SystemClock.elapsedRealtime();
            context.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED);
            Intent intent = new Intent(action).setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
            return pending.getIntentSender();
        }

        private static String prettyPrint(Intent intent) {
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            return String.format("%s: {\n"
                    + "sessionId = %d\n"
                    + "status = %d\n"
                    + "message = %s\n"
                    + "}", intent, sessionId, status, message);
        }
    }

    static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName + ".apk";
    }
}
