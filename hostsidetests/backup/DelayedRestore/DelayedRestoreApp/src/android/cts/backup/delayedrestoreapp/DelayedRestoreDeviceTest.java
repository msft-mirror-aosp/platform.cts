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

package android.cts.backup.delayedrestoreapp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.BackupManager;
import android.app.backup.DelayedRestoreRequest;
import android.content.Context;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.backup.Flags;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class DelayedRestoreDeviceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String BACKUP_FILE_NAME = "backup_file";
    private static final String DELAYED_RESTORE_FILE_NAME = "delayed_backup_file";
    private static final String FILE_CONTENT = "test_data";

    private File backupFile;
    private File delayedRestoreFile;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        backupFile = new File(context.getFilesDir(), BACKUP_FILE_NAME);
        delayedRestoreFile = new File(context.getFilesDir(), DELAYED_RESTORE_FILE_NAME);
    }

    @Test
    public void assertFilesDontExist() throws Exception {
        assertThat(backupFile.exists()).isFalse();
        assertThat(delayedRestoreFile.exists()).isFalse();
    }

    @Test
    public void writeFilesAndAssertSuccess() throws Exception {
        Files.write(backupFile.toPath(), FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
        assertFileContains(backupFile, FILE_CONTENT);
        Files.write(delayedRestoreFile.toPath(), FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
        assertFileContains(delayedRestoreFile, FILE_CONTENT);
    }

    @Test
    public void clearFilesAndAssertSuccess() throws Exception {
        backupFile.delete();
        delayedRestoreFile.delete();
        assertFilesDontExist();
    }

    @Test
    public void assertSomeFilesRestored() throws Exception {
        assertThat(backupFile.exists()).isTrue();
        assertFileContains(backupFile, FILE_CONTENT);
        assertThat(delayedRestoreFile.exists()).isFalse();
    }

    @Test
    public void assertAllFilesRestored() throws Exception {
        assertThat(backupFile.exists()).isTrue();
        assertFileContains(backupFile, FILE_CONTENT);
        assertThat(delayedRestoreFile.exists()).isTrue();
        assertFileContains(delayedRestoreFile, FILE_CONTENT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testRequest_BuilderAndParcelable() {
        // Test DelayedRestoreRequest class
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName("com.test")
                        .build();
        assertEquals("com.test", request.getPackageName());
        assertEquals(DelayedRestoreRequest.TYPE_APP_INSTALL, request.getType());

        // Test Parcelable implementation (write/read)
        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DelayedRestoreRequest fromParcel = DelayedRestoreRequest.CREATOR.createFromParcel(parcel);
        assertEquals(request.getType(), fromParcel.getType());
        assertEquals(request.getPackageName(), fromParcel.getPackageName());
        parcel.recycle();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testSchedule_ReturnFalse_OutsideRestore() {
        // This app has the permission SCHEDULE_DELAYED_RESTORE.
        // But scheduleDelayedRestore should fail (return false) if not currently restoring.
        BackupManager bm = new BackupManager(context);
        boolean result =
                bm.scheduleDelayedRestore(
                        new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                                .setPackageName("com.test")
                                .build());

        assertFalse("Should return false when not in restore session", result);
    }

    @Test
    public void testOnDelayedRestore_coverage() throws Exception {
        DelayedRestoreBackupAgent agent = new DelayedRestoreBackupAgent();
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName("dummy_package")
                        .build();
        try {
            agent.onDelayedRestore(request, null, 0, null);
        } catch (Exception ignored) {
            // Intentionally ignored as we are only testing for coverage.
        }
    }

    @Test
    public void testBackupHelper_delayedRestoreEntity_coverage() throws Exception {
        BackupHelper helper =
                new BackupHelper() {
                    @Override
                    public void performBackup(
                            ParcelFileDescriptor os, BackupDataOutput d, ParcelFileDescriptor ns) {}

                    @Override
                    public void restoreEntity(BackupDataInputStream d) {}

                    @Override
                    public void writeNewStateDescription(ParcelFileDescriptor ns) {}
                };

        helper.delayedRestoreEntity(null, null);
    }

    private void assertFileContains(File file, String content) throws IOException {
        assertThat(Files.readAllBytes(file.toPath())).isEqualTo(content.getBytes());
    }
}
