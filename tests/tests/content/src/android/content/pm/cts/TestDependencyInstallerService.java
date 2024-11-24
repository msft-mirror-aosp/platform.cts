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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
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
import android.preference.PreferenceManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.PackageUtils;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import libcore.util.HexEncoding;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
 * A DependencyInstallerService for test.
 *
 * The behavior of the service is controlled by the test by passing specific method names via
 * SharedPreferences.
 *
 */
public class TestDependencyInstallerService extends DependencyInstallerService {

    private static final String TAG = TestDependencyInstallerService.class.getSimpleName();
    private static final String LIB_NAME_SDK_1 = "com.test.sdk1";
    private static final String LIB_NAME_SDK_2 = "com.test.sdk2";
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_SDK1_APK_NAME = "HelloWorldSdk1";
    private static final String TEST_SDK2_APK_NAME = "HelloWorldSdk2";

    static final String METHOD_NAME = "method-name";
    static final String METHOD_INSTALL_SYNC = "install-sync";
    static final String METHOD_INSTALL_ASYNC = "install-async";

    private final Set<Integer> mPendingSessionIds = new ArraySet<>();
    private String mCertDigest;

    @Override
    public void onDependenciesRequired(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) {
        Log.i(TAG, "onDependenciesRequired call received");

        String methodName = getMethodName();

        try {
            if (methodName.equals(METHOD_INSTALL_SYNC)) {
                installDependencies(neededLibraries, callback, /*sync=*/ true);
            } else if (methodName.equals(METHOD_INSTALL_ASYNC)) {
                installDependencies(neededLibraries, callback, /*sync=*/ false);
            } else {
                throw new IllegalStateException("Unknown method name: " + methodName);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            callback.onFailureToResolveAllDependencies();
            return;
        }
    }

    private boolean isSdk1(SharedLibraryInfo info) {
        if (info.getCertDigests().isEmpty() || info.getName() == null) {
            return false;
        }
        return info.getName().equals(LIB_NAME_SDK_1)
            && info.getCertDigests().get(0).equals(mCertDigest);
    }

    private boolean isSdk2(SharedLibraryInfo info) {
        if (info.getCertDigests().isEmpty() || info.getName() == null) {
            return false;
        }
        return info.getName().equals(LIB_NAME_SDK_2)
            && info.getCertDigests().get(0).equals(mCertDigest);
    }

    private void installDependencies(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback, boolean sync) throws Exception {

        mPendingSessionIds.clear();

        // All CTS test artifacts are signed with same cert. So we can assume the SDK apks we
        // have will be same cert as our current package.
        mCertDigest = getPackageCertDigest(getContext().getPackageName());

        for (SharedLibraryInfo info: neededLibraries) {
            if (isSdk1(info)) {
                Log.i(TAG, "SDK1 missing dependency found");
                continue;
            }

            if (isSdk2(info)) {
                Log.i(TAG, "SDK2 missing dependency found");
                continue;
            }
            // For everything else, fail.
            throw new IllegalStateException("Unsupported SDK found: " + info.getName() + " "
                    + info.getCertDigests().get(0));
        }

        PackageInstaller installer = getPackageManager().getPackageInstaller();
        int size = neededLibraries.size();
        List<Integer> sessionIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            SessionParams params = new SessionParams(MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            Log.i(TAG, "Session created: " + sessionId);
            sessionIds.add(sessionId);
        }

        // Return the session ids immediately
        if (!sync) {
            callback.onAllDependenciesResolved(toIntArray(sessionIds));
        }

        SyncBroadcastReceiver sender = new SyncBroadcastReceiver(callback, sessionIds);
        for (int i = 0; i < size; i++) {
            int sessionId = sessionIds.get(i);
            Session session = installer.openSession(sessionId);
            SharedLibraryInfo info = neededLibraries.get(i);
            if (isSdk1(info)) {
                writeApk(session, TEST_SDK1_APK_NAME);
            } else if (isSdk2(info)) {
                writeApk(session, TEST_SDK2_APK_NAME);
            }
            session.commit(sender.getIntentSender(this));
        }
    }

    private String getMethodName() {
        return getDefaultSharedPreferences().getString(METHOD_NAME, "");
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

    private static class SyncBroadcastReceiver extends BroadcastReceiver {
        private final DependencyInstallerCallback mCallback;
        private final int[] mSessionIds;
        private int mPendingSessionCount;

        SyncBroadcastReceiver(DependencyInstallerCallback callback, List<Integer> sessionIds) {
            mCallback = callback;
            mSessionIds = toIntArray(sessionIds);
            mPendingSessionCount = sessionIds.size();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received intent " + prettyPrint(intent));
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                synchronized (this) {
                    mPendingSessionCount--;
                    if (mPendingSessionCount == 0) {
                        mCallback.onAllDependenciesResolved(mSessionIds);
                    }
                }
            } else {
                mCallback.onFailureToResolveAllDependencies();
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

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName + ".apk";
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = getContext().getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private static int[] toIntArray(List<Integer> list) {
        return list.stream().mapToInt(i->i).toArray();
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

}
