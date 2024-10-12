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

package android.security.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class ReadOnlyDynamicCodeLoadingTest {

    private native int functionA();

    @Test
    public void testLoadReadOnly() {
        System.load(getNativeLibrary().getAbsolutePath());
        assertThat(functionA()).isEqualTo(1);
    }

    @Test
    public void testLoadWritable() throws Exception {
        copyLibraryToWritableFileAndLoad();
        assertThat(functionA()).isEqualTo(1);
    }

    @Test
    public void testLoadWritable_expectException() throws Exception {
        UnsatisfiedLinkError unsatisfiedLinkError = assertThrows(UnsatisfiedLinkError.class,
                this::copyLibraryToWritableFileAndLoad);
        assertThat(unsatisfiedLinkError).hasMessageThat().contains("writable");
    }

    private void copyLibraryToWritableFileAndLoad() throws Exception {
        File writableLib = File.createTempFile("test", ".so");
        try (FileInputStream in = new FileInputStream(getNativeLibrary());
                FileOutputStream out = new FileOutputStream(writableLib)) {
            byte[] buffer = new byte[8192];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
        }

        System.load(writableLib.getAbsolutePath());
    }

    private File getNativeLibrary() {
        return new File(InstrumentationRegistry.getContext().getApplicationInfo().nativeLibraryDir,
                "read_only_native_lib.so");
    }
}
