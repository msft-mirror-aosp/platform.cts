/*
 * Copyright 2021 The Android Open Source Project
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

package android.app.appsearch.cts.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.StorageInfo;
import android.app.appsearch.testutil.AppSearchTestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.appsearch.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class StorageInfoCtsTest {

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    @Test
    public void testBuildStorageInfo() {
        StorageInfo storageInfo =
                new StorageInfo.Builder()
                        .setAliveDocumentsCount(10)
                        .setSizeBytes(1L)
                        .setAliveNamespacesCount(10)
                        .build();

        assertThat(storageInfo.getAliveDocumentsCount()).isEqualTo(10);
        assertThat(storageInfo.getSizeBytes()).isEqualTo(1L);
        assertThat(storageInfo.getAliveNamespacesCount()).isEqualTo(10);
    }

    @Test
    public void testBuildStorageInfo_withDefaults() {
        StorageInfo storageInfo = new StorageInfo.Builder().build();

        assertThat(storageInfo.getAliveDocumentsCount()).isEqualTo(0);
        assertThat(storageInfo.getSizeBytes()).isEqualTo(0L);
        assertThat(storageInfo.getAliveNamespacesCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BLOB_STORE)
    public void testBuildStorageInfo_withBlob() {
        StorageInfo storageInfo =
                new StorageInfo.Builder().setBlobSizeBytes(12L).setBlobCount(20).build();

        assertThat(storageInfo.getBlobCount()).isEqualTo(20);
        assertThat(storageInfo.getBlobSizeBytes()).isEqualTo(12L);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BLOB_STORE)
    public void testBuildStorageInfo_withBlobDefaults() {
        StorageInfo storageInfo = new StorageInfo.Builder().build();

        assertThat(storageInfo.getBlobCount()).isEqualTo(0);
        assertThat(storageInfo.getBlobSizeBytes()).isEqualTo(0L);
    }
}
