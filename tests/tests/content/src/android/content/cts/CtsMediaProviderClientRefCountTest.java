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

import static org.junit.Assert.assertNotNull;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.os.RemoteException;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CtsMediaProviderClientRefCountTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testAcquireReleaseUnstableClient() throws RemoteException {
        ContentResolver resolver = mContext.getContentResolver();
        assertNotNull(resolver);

        ContentProviderClient client = null;
        try {
            client = resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client);
        } finally {
            if (client != null) {
                client.release();
            }
        }
    }

    @Test
    public void testAcquireReleaseStableClient() throws RemoteException {
        ContentResolver resolver = mContext.getContentResolver();
        assertNotNull(resolver);

        ContentProviderClient client = null;
        try {
            client = resolver.acquireContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client);
        } finally {
            if (client != null) {
                client.release();
            }
        }
    }

    @Test
    public void testMultipleClientAcquisitionsAndReleases() throws RemoteException {
        ContentResolver resolver = mContext.getContentResolver();
        assertNotNull(resolver);

        ContentProviderClient client1 = null;
        ContentProviderClient client2 = null;
        ContentProviderClient client3 = null;

        try {
            client1 = resolver.acquireContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client1);

            client2 = resolver.acquireContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client2);

            client3 = resolver.acquireContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client3);
        } finally {
            if (client1 != null) {
                client1.release();
            }
            if (client2 != null) {
                client2.release();
            }
            if (client3 != null) {
                client3.release();
            }
        }
    }

    @Test
    public void testMultipleUnstableClientAcquisitionsAndReleases() throws RemoteException {
        ContentResolver resolver = mContext.getContentResolver();
        assertNotNull(resolver);

        ContentProviderClient client1 = null;
        ContentProviderClient client2 = null;
        ContentProviderClient client3 = null;

        try {
            client1 = resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client1);

            client2 = resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client2);

            client3 = resolver.acquireUnstableContentProviderClient(MediaStore.AUTHORITY);
            assertNotNull(client3);
        } finally {
            if (client1 != null) {
                client1.release();
            }
            if (client2 != null) {
                client2.release();
            }
            if (client3 != null) {
                client3.release();
            }
        }
    }
}
