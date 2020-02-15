/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.writeexternalstorageapp;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_NONE;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_READ;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificObbGiftPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.readInt;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.writeInt;

import android.os.Environment;
import android.test.AndroidTestCase;

import java.io.File;
import java.util.List;

public class WriteGiftTest extends AndroidTestCase {
    /**
     * Verify we can't read other obb dirs.
     */
    public void testCantAccessOtherObbDirs() throws Exception {
        final List<File> noneList = getAllPackageSpecificObbGiftPaths(getContext(), PACKAGE_NONE);
        for (File none : noneList) {
            assertFileNoAccess(none);
        }
    }

    /**
     * Leave gifts for other packages in their obb directories.
     */
    public void testObbGifts() throws Exception {
        final List<File> noneList = getAllPackageSpecificObbGiftPaths(getContext(), PACKAGE_NONE);
        for (File none : noneList) {

            none.getParentFile().mkdirs();
            none.createNewFile();
            assertFileReadWriteAccess(none);

            writeInt(none, 100);
            assertEquals(100, readInt(none));
        }
    }

    /**
     * Verify we can't access gifts in obb dirs.
     */
    public void testAccessObbGifts() throws Exception {
        final List<File> noneList = getAllPackageSpecificObbGiftPaths(getContext(), PACKAGE_NONE);
        for (File none : noneList) {
            assertFileReadWriteAccess(none);
            assertEquals(100, readInt(none));
        }
    }

    /**
     * Verify we can't access gifts in obb dirs.
     */
    public void testCantAccessObbGifts() throws Exception {
        final List<File> noneList = getAllPackageSpecificObbGiftPaths(getContext(), PACKAGE_NONE);
        for (File none : noneList) {
            assertFileNoAccess(none);
            assertDirNoAccess(none.getParentFile());
        }
    }

    public void testClearingWrite() throws Exception {
        for (File dir : getAllPackageSpecificPaths(getContext())) {
            dir.mkdirs();
            new File(dir, "probe").createNewFile();
            assertTrue(new File(dir, "probe").exists());
        }
    }

    public void testClearingRead() throws Exception {
        for (File dir : getAllPackageSpecificPaths(getContext())) {
            assertFalse(new File(dir, "probe").exists());
        }
    }

    public void testIsExternalStorageLegacy() {
        assertTrue(Environment.isExternalStorageLegacy());
    }

    public void testNotIsExternalStorageLegacy() {
        assertFalse(Environment.isExternalStorageLegacy());
    }
}
