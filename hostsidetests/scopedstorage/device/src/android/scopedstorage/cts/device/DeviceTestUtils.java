/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.scopedstorage.cts.device;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.FileUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DeviceTestUtils {

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    protected static void createContentFromResource(int resourceId, File file) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                InputStream in = sInstrumentation.getContext()
                        .getResources().openRawResource(resourceId)) {
            // Dump the image we have to external storage
            FileUtils.copy(in, fileOutputStream);
            // Sync file to disk to ensure file is fully written to the lower fs attempting to
            // open for redaction. Otherwise, the FUSE daemon might not accurately parse the
            // EXIF tags and might misleadingly think there are not tags to redact
            fileOutputStream.getFD().sync();
        }

    }

    protected static void checkUISupported() {
        PackageManager pm = sInstrumentation.getContext().getPackageManager();

        // Do not run tests on Watches, TVs, Auto or devices without UI.
        Assume.assumeTrue(!pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }
}