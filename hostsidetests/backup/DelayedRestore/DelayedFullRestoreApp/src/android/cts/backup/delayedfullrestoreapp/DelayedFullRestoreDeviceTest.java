/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.cts.backup.delayedfullrestoreapp;

import static com.google.common.truth.Truth.assertThat;

import android.app.backup.DelayedRestoreRequest;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class DelayedFullRestoreDeviceTest {
    private static final String FILE_NAME = "backup_file";
    private static final String FILE_CONTENT = "test data";

    private File backupFile;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        backupFile = new File(context.getFilesDir(), FILE_NAME);
    }

    @Test
    public void assertFilesDontExist() throws Exception {
        assertThat(backupFile.exists()).isFalse();
    }

    @Test
    public void writeFilesAndAssertSuccess() throws Exception {
        Files.write(backupFile.toPath(), FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readAllBytes(backupFile.toPath()))
                .isEqualTo(FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void clearFilesAndAssertSuccess() throws Exception {
        backupFile.delete();
        assertThat(backupFile.exists()).isFalse();
    }

    @Test
    public void assertFilesRestored() throws Exception {
        assertThat(backupFile.exists()).isTrue();
        assertThat(Files.readAllBytes(backupFile.toPath()))
                .isEqualTo(FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testOnDelayedFullRestore_coverage() throws Exception {
        DelayedFullBackupAgent agent = new DelayedFullBackupAgent();
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName("dummy_package")
                        .build();
        agent.onDelayedFullRestore(request);
    }
}
