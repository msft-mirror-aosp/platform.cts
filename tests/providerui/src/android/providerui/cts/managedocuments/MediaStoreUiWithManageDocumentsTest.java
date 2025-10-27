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

package android.providerui.cts.managedocuments;

import static android.provider.cts.media.MediaProviderTestUtils.clearOwner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.cts.media.MediaProviderTestUtils;
import android.providerui.cts.MediaStoreUiTestBase;
import android.system.Os;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MediaStoreUiWithManageDocumentsTest extends MediaStoreUiTestBase {

    @Parameterized.Parameter(0)
    public String mVolumeName;

    @Parameterized.Parameters
    public static Iterable<? extends Object> data() {
        return MediaProviderTestUtils.getSharedVolumeNames();
    }

    @Test
    public void testGetDocumentUri_documentsManager() throws Exception {
        assumeTrue(supportsHardware());

        prepareFile(mVolumeName);
        clearOwner(mMediaStoreUri);
        clearDocumentsUi();
        mDevice.waitForIdle();

        final Uri docUri = MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
        assertNotNull(docUri);
        assertEquals(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, docUri.getAuthority());

        final ContentResolver resolver = mActivity.getContentResolver();

        // Test reading
        final byte[] expected = "TEST".getBytes();
        final byte[] actual = new byte[4];
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(docUri, "r")) {
            Os.read(fd.getFileDescriptor(), actual, 0, actual.length);
            assertArrayEquals(expected, actual);
        }

        // Test writing
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(docUri, "wt")) {
            Os.write(fd.getFileDescriptor(), expected, 0, expected.length);
        }
    }
}
