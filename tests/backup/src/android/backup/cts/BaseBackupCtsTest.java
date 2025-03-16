/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.backup.cts;

import static junit.framework.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.BackupUtils;
import com.android.compatibility.common.util.LogcatInspector;

import java.io.InputStream;

/**
 * Base class for backup instrumentation tests.
 *
 * Ensures that backup is enabled and local transport selected, and provides some utility methods.
 */
public class BaseBackupCtsTest {
    private static final String APP_LOG_TAG = "BackupCTSApp";

    public final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private boolean mIsBackupSupported;
    private LogcatInspector mLogcatInspector =
            new LogcatInspector() {
                @Override
                protected InputStream executeShellCommand(String command) {
                    return executeInstrumentationShellCommand(mInstrumentation, command);
                }
            };
    private BackupUtils mBackupUtils =
            new BackupUtils() {
                @Override
                protected InputStream executeShellCommand(String command) {
                    return executeInstrumentationShellCommand(mInstrumentation, command);
                }
            };

    /** Inheritors need to call this on their @Before methods. */
    public void setUp() throws Exception {
        PackageManager packageManager = mInstrumentation.getContext().getPackageManager();
        mIsBackupSupported =
                packageManager != null
                        && packageManager.hasSystemFeature(PackageManager.FEATURE_BACKUP);

        if (mIsBackupSupported) {
            assertTrue("Backup not enabled", mBackupUtils.isBackupEnabled());
            assertTrue("LocalTransport not selected", mBackupUtils.isLocalTransportSelected());
            getBackupUtils()
                    .executeShellCommandSync("setprop log.tag." + APP_LOG_TAG + " VERBOSE");
        }
    }

    protected BackupUtils getBackupUtils() {
        return mBackupUtils;
    }

    protected boolean isBackupSupported() {
        return mIsBackupSupported;
    }

    /** See {@link LogcatInspector#mark(String)}. */
    protected String markLogcat() throws Exception {
        return mLogcatInspector.mark(APP_LOG_TAG);
    }

    /** See {@link LogcatInspector#assertLogcatContainsInOrder(String, int, String...)}. */
    protected void waitForLogcat(int maxTimeoutInSeconds, String... logcatStrings)
            throws Exception {
        mLogcatInspector.assertLogcatContainsInOrder(
                APP_LOG_TAG + ":* *:S", maxTimeoutInSeconds, logcatStrings);
    }

    protected void createTestFileOfSize(String packageName, int size) throws Exception {
        getBackupUtils().executeShellCommandSync(
                "am start -a android.intent.action.MAIN "
                        + "-c android.intent.category.LAUNCHER "
                        + "-n "
                        + packageName
                        + "/android.backup.app.MainActivity "
                        + "-e file_size " + size);
        waitForLogcat(30, "File created!");
    }

    protected void setLocalTransportParameters(String parameters) throws Exception {
        getBackupUtils().executeShellCommandSync(
                "settings put secure backup_local_transport_parameters " + parameters);
    }

    protected void clearBackupDataInLocalTransport(String packageName) throws Exception {
        getBackupUtils().executeShellCommandSync(
                String.format("bmgr wipe %s %s", "com.android.localtransport/.LocalTransport",
                        packageName));
    }

    protected void forceStopPackage(String packageName) throws Exception {
        getBackupUtils().executeShellCommandSync(String.format("am force-stop %s", packageName));
    }

    private static InputStream executeInstrumentationShellCommand(
            Instrumentation instrumentation, String command) {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
    }
}
