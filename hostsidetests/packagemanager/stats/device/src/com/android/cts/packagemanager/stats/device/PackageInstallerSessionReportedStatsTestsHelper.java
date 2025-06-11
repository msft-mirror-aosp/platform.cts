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

package com.android.cts.packagemanager.stats.device;

import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.os.Process.myUid;

import android.app.Instrumentation;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.IOException;

public class PackageInstallerSessionReportedStatsTestsHelper {
    // Instrumentation status code used to write resolution to metrics
    private static final int INST_STATUS_IN_PROGRESS = 2;

    @Test
    public void createSessionAndAbandon() throws IOException {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = inst.getTargetContext().getPackageManager();
        PackageInstaller pi = pm.getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(MODE_FULL_INSTALL);
        int sessionId = pi.createSession(params);
        pi.abandonSession(sessionId);
        // Pass data to the host-side test
        Bundle bundle = new Bundle();
        bundle.putInt("sessionId", sessionId);
        bundle.putInt("installerUid", myUid());
        inst.sendStatus(INST_STATUS_IN_PROGRESS, bundle);
    }
}
