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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public final class CtsMediaProviderClientRefCountTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testAcquireReleaseUnstableClient() {
        ContentResolver resolver = mContext.getContentResolver();
        assertThat(resolver).isNotNull();

        try (ContentProviderClient client =
                resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    public void testAcquireReleaseStableClient() {
        ContentResolver resolver = mContext.getContentResolver();
        assertThat(resolver).isNotNull();

        try (ContentProviderClient client =
                resolver.acquireContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    public void testMultipleClientAcquisitionsAndReleases() {
        ContentResolver resolver = mContext.getContentResolver();
        assertThat(resolver).isNotNull();

        try (ContentProviderClient client1 =
                resolver.acquireContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client1).isNotNull();
        }
        try (ContentProviderClient client2 =
                resolver.acquireContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client2).isNotNull();
        }
        try (ContentProviderClient client3 =
                resolver.acquireContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client3).isNotNull();
        }
    }

    @Test
    public void testMultipleUnstableClientAcquisitionsAndReleases() {
        ContentResolver resolver = mContext.getContentResolver();
        assertThat(resolver).isNotNull();

        try (ContentProviderClient client1 =
                resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client1).isNotNull();
        }
        try (ContentProviderClient client2 =
                resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client2).isNotNull();
        }
        try (ContentProviderClient client3 =
                resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            assertThat(client3).isNotNull();
        }
    }
}
