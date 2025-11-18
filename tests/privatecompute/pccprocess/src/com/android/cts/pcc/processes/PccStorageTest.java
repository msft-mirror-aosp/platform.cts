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

package com.android.cts.pcc.processes;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.common.StorageTestUtils.deleteThrowException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccStorageTest {

    private static final String PCC_DIR_SUFFIX = "-pcc";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testPccStorage_directoryEndsInPcc() throws IOException {
        assertTrue(mContext.getDataDir().getAbsolutePath().endsWith(PCC_DIR_SUFFIX));
    }

    @Test
    public void testPccStorage_canNotWriteInNonPccDirectory() throws IOException {
        String pccDataDir = mContext.getDataDir().getAbsolutePath();
        File pccFilesDir = new File(pccDataDir, "files");
        String normalFilesDir =
                pccDataDir.substring(0, pccDataDir.lastIndexOf(PCC_DIR_SUFFIX)) + "/files/";
        File pccFile = new File(pccFilesDir, "success.txt");
        File normalFile = new File(normalFilesDir, "fail.txt");

        try {
            // Sanity check to ensure the path construction logic is correct
            assertTrue(pccFile.createNewFile());

            assertThrows(
                    "Expected an exception but nothing thrown when trying to write to : "
                            + normalFilesDir,
                    IOException.class,
                    normalFile::createNewFile);
        } finally {
            deleteThrowException(pccFile);
            deleteIgnoreException(normalFile);
        }
    }

    @Test
    public void testPccStorage_cannotWriteToExternalStorage() {
        File externalDir = Environment.getExternalStorageDirectory();
        File testFile = new File(externalDir, "pcc_fail.txt");
        try {
            assertThrows(
                    "Expected an exception when trying to write to : " + externalDir,
                    IOException.class,
                    testFile::createNewFile);
        } finally {
            deleteIgnoreException(testFile);
        }
    }

    @Test
    public void testPccStorage_canDeleteFile() throws IOException {
        String fileName = "test_to_delete.txt";
        File file = new File(mContext.getFilesDir(), fileName);

        assertTrue("File should be created", file.createNewFile());
        assertTrue("File should exist after creation", file.exists());

        assertTrue("File should be deleted", file.delete());
        assertFalse("File should not exist after deletion", file.exists());
    }

    @Test
    public void testPccStorage_canReadWrite() throws IOException {
        String fileName = "test.txt";
        File file = new File(mContext.getFilesDir(), fileName);
        String content = "hello world";

        try {
            // Write to the file
            try (FileOutputStream fos = new FileOutputStream(file);
                    PrintWriter writer = new PrintWriter(fos)) {
                writer.print(content);
            }

            // Read from the file
            StringBuilder readContent = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                    InputStreamReader isr = new InputStreamReader(fis);
                    BufferedReader reader = new BufferedReader(isr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    readContent.append(line);
                }
            }

            // Verify the content
            assertEquals("File content should match", content, readContent.toString());
        } finally {
            deleteThrowException(file);
        }
    }
}
