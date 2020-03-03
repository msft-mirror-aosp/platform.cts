/*
 * Copyright 2019 The Android Open Source Project
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
package com.android.cts.blob;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.app.blob.BlobHandle;
import android.app.blob.BlobStoreManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import com.android.cts.blob.R;
import com.android.cts.blob.ICommandReceiver;
import com.android.utils.blob.DummyBlobData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.io.BaseEncoding;

@RunWith(AndroidJUnit4.class)
public class BlobStoreManagerTest {
    private static final String TAG = "BlobStoreTest";

    private static final long TIMEOUT_COMMIT_CALLBACK_SEC = 5;

    private static final long TIMEOUT_BIND_SERVICE_SEC = 2;

    private static final String HELPER_PKG = "com.android.cts.blob.helper";
    private static final String HELPER_PKG2 = "com.android.cts.blob.helper2";
    private static final String HELPER_PKG3 = "com.android.cts.blob.helper3";

    private static final String HELPER_SERVICE = HELPER_PKG + ".BlobStoreTestService";

    private static final byte[] HELPER_PKG2_CERT_SHA256 = BaseEncoding.base16().decode(
            "187E3D3172F2177D6FEC2EA53785BF1E25DFF7B2E5F6E59807E365A7A837E6C3");
    private static final byte[] HELPER_PKG3_CERT_SHA256 = BaseEncoding.base16().decode(
            "D760873D812FE1CFC02C15ED416AB774B2D4C2E936DF6D8B6707277479D4812F");

    private Context mContext;
    private BlobStoreManager mBlobStoreManager;

    private final ArrayList<Long> mCreatedSessionIds = new ArrayList<>();

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBlobStoreManager = (BlobStoreManager) mContext.getSystemService(
                Context.BLOB_STORE_SERVICE);
        clearAllBlobsData();
    }

    @After
    public void tearDown() {
        clearAllBlobsData();
    }

    private void clearAllBlobsData() {
        executeCmd("cmd blob_store clear-all-sessions");
        executeCmd("cmd blob_store clear-all-blobs");
    }

    @Test
    public void testGetCreateSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            assertThat(mBlobStoreManager.openSession(sessionId)).isNotNull();
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testCreateBlobHandle_invalidArguments() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        final BlobHandle handle = blobData.getBlobHandle();
        try {
            assertThrows(IllegalArgumentException.class, () -> BlobHandle.createWithSha256(
                    handle.getSha256Digest(), null, handle.getExpiryTimeMillis(), handle.getTag()));
            assertThrows(IllegalArgumentException.class, () -> BlobHandle.createWithSha256(
                    handle.getSha256Digest(), handle.getLabel(), handle.getExpiryTimeMillis(),
                    null));
            assertThrows(IllegalArgumentException.class, () -> BlobHandle.createWithSha256(
                    handle.getSha256Digest(), handle.getLabel(), -1, handle.getTag()));
            assertThrows(IllegalArgumentException.class, () -> BlobHandle.createWithSha256(
                    EMPTY_BYTE_ARRAY, handle.getLabel(), handle.getExpiryTimeMillis(),
                    handle.getTag()));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testGetCreateSession_invalidArguments() throws Exception {
        assertThrows(NullPointerException.class, () -> mBlobStoreManager.createSession(null));
    }

    @Test
    public void testOpenSession_invalidArguments() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.openSession(-1));
    }

    @Test
    public void testDeleteSession_invalidArguments() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.openSession(-1));
    }

    @Test
    public void testDeleteSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            // Verify that session can be opened.
            assertThat(mBlobStoreManager.openSession(sessionId)).isNotNull();

            mBlobStoreManager.deleteSession(sessionId);
            // Verify that trying to open session after it is deleted will throw.
            assertThrows(SecurityException.class, () -> mBlobStoreManager.openSession(sessionId));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenReadWriteSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session, 0, blobData.getFileSize());
                blobData.readFromSessionAndVerifyDigest(session);
                blobData.readFromSessionAndVerifyBytes(session,
                        101 /* offset */, 1001 /* length */);

                blobData.writeToSession(session, 202 /* offset */, 2002 /* length */);
                blobData.readFromSessionAndVerifyBytes(session,
                        202 /* offset */, 2002 /* length */);
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenSession_fromAnotherPkg() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                assertThat(session).isNotNull();
            }
            assertThrows(SecurityException.class, () -> openSessionFromPkg(sessionId, HELPER_PKG));
            assertThrows(SecurityException.class, () -> openSessionFromPkg(sessionId, HELPER_PKG2));

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session, 0, blobData.getFileSize());
                blobData.readFromSessionAndVerifyDigest(session);
            }
            assertThrows(SecurityException.class, () -> openSessionFromPkg(sessionId, HELPER_PKG));
            assertThrows(SecurityException.class, () -> openSessionFromPkg(sessionId, HELPER_PKG2));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAbandonSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                // Verify session can be opened for read/write.
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();

                // Verify that trying to read/write to the session after it is abandoned will throw.
                session.abandon();
                assertThrows(IllegalStateException.class, () -> session.openWrite(0, 0));
            }

            // Verify that trying to open the session after it is abandoned will throw.
            assertThrows(SecurityException.class, () -> mBlobStoreManager.openSession(sessionId));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testCloseSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            // Verify session can be opened for read/write.
            BlobStoreManager.Session session = null;
            try {
                session = mBlobStoreManager.openSession(sessionId);
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();
            } finally {
                session.close();
            }

            // Verify trying to read/write to session after it is closed will throw.
            // an exception.
            final BlobStoreManager.Session closedSession = session;
            assertThrows(IllegalStateException.class, () -> closedSession.openWrite(0, 0));

            // Verify that the session can be opened again for read/write.
            try {
                session = mBlobStoreManager.openSession(sessionId);
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();
            } finally {
                session.close();
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPublicAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);
                session.allowPublicAccess();

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertPkgCanAccess(blobData, HELPER_PKG);
            assertPkgCanAccess(blobData, HELPER_PKG2);
            assertPkgCanAccess(blobData, HELPER_PKG3);
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPublicAccess_abandonedSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPublicAccess();
                assertThat(session.isPublicAccessAllowed()).isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowPublicAccess());
                assertThrows(IllegalStateException.class,
                        () -> session.isPublicAccessAllowed());
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowSameSignatureAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);
                session.allowSameSignatureAccess();

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertPkgCanAccess(blobData, HELPER_PKG);
            assertPkgCannotAccess(blobData, HELPER_PKG2);
            assertPkgCannotAccess(blobData, HELPER_PKG3);
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowSameSignatureAccess_abandonedSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowSameSignatureAccess();
                assertThat(session.isSameSignatureAccessAllowed()).isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowSameSignatureAccess());
                assertThrows(IllegalStateException.class,
                        () -> session.isSameSignatureAccessAllowed());
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPackageAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);
                session.allowPackageAccess(HELPER_PKG2, HELPER_PKG2_CERT_SHA256);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertPkgCannotAccess(blobData, HELPER_PKG);
            assertPkgCanAccess(blobData, HELPER_PKG2);
            assertPkgCannotAccess(blobData, HELPER_PKG3);
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPackageAccess_allowMultiple() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);
                session.allowPackageAccess(HELPER_PKG2, HELPER_PKG2_CERT_SHA256);
                session.allowPackageAccess(HELPER_PKG3, HELPER_PKG3_CERT_SHA256);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertPkgCannotAccess(blobData, HELPER_PKG);
            assertPkgCanAccess(blobData, HELPER_PKG2);
            assertPkgCanAccess(blobData, HELPER_PKG3);
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPackageAccess_abandonedSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPackageAccess("com.example", "test_bytes".getBytes());
                assertThat(session.isPackageAccessAllowed("com.example", "test_bytes".getBytes()))
                        .isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowPackageAccess(
                                "com.example2", "test_bytes2".getBytes()));
                assertThrows(IllegalStateException.class,
                        () -> session.isPackageAccessAllowed(
                                "com.example", "test_bytes".getBytes()));
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testPrivateAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        final TestServiceConnection connection3 = bindToHelperService(HELPER_PKG3);
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertPkgCannotAccess(blobData, connection1);
            assertPkgCannotAccess(blobData, connection2);
            assertPkgCannotAccess(blobData, connection3);

            commitBlobFromPkg(blobData, connection1);
            assertPkgCanAccess(blobData, connection1);
            assertPkgCannotAccess(blobData, connection2);
            assertPkgCannotAccess(blobData, connection3);

            commitBlobFromPkg(blobData, connection2);
            assertPkgCanAccess(blobData, connection1);
            assertPkgCanAccess(blobData, connection2);
            assertPkgCannotAccess(blobData, connection3);
        } finally {
            blobData.delete();
            connection1.unbind();
            connection2.unbind();
            connection3.unbind();
        }
    }

    @Test
    public void testMixedAccessType() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowSameSignatureAccess();
                session.allowPackageAccess("com.example", "test_bytes".getBytes());

                assertThat(session.isSameSignatureAccessAllowed()).isTrue();
                assertThat(session.isPackageAccessAllowed("com.example", "test_bytes".getBytes()))
                        .isTrue();
                assertThat(session.isPublicAccessAllowed()).isFalse();

                session.allowPublicAccess();
                assertThat(session.isPublicAccessAllowed()).isTrue();
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testSessionCommit() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                final ParcelFileDescriptor pfd = session.openWrite(
                        0L /* offset */, 0L /* length */);
                assertThat(pfd).isNotNull();
                blobData.writeToFd(pfd.getFileDescriptor(), 0 /* offset */, 100 /* length */);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);

                // Verify that writing to the session after commit will throw.
                assertThrows(IOException.class, () -> blobData.writeToFd(
                        pfd.getFileDescriptor() /* length */, 0 /* offset */, 100 /* length */));
                assertThrows(IllegalStateException.class, () -> session.openWrite(0L, 0L));
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenBlob() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                // Verify that trying to access the blob before commit throws
                assertThrows(SecurityException.class,
                        () -> mBlobStoreManager.openBlob(blobData.getBlobHandle()));

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            // Verify that blob can be access after committing.
            try (ParcelFileDescriptor pfd = mBlobStoreManager.openBlob(blobData.getBlobHandle())) {
                assertThat(pfd).isNotNull();

                blobData.verifyBlob(pfd);
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenBlob_invalidArguments() throws Exception {
        assertThrows(NullPointerException.class, () -> mBlobStoreManager.openBlob(null));
    }

    @Test
    public void testAcquireReleaseLease() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertThrows(IllegalArgumentException.class, () ->
                    mBlobStoreManager.acquireLease(blobData.getBlobHandle(),
                            R.string.test_desc, blobData.getExpiryTimeMillis() + 1000));

            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), R.string.test_desc,
                    blobData.getExpiryTimeMillis() - 1000);
            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), R.string.test_desc);
            // TODO: verify acquiring lease took effect.
            mBlobStoreManager.releaseLease(blobData.getBlobHandle());

            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), "Test description",
                    blobData.getExpiryTimeMillis() - 20000);
            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), "Test description two");
            mBlobStoreManager.releaseLease(blobData.getBlobHandle());
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAcquireReleaseLease_invalidArguments() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        try {
            assertThrows(NullPointerException.class, () -> mBlobStoreManager.acquireLease(
                    null, R.string.test_desc, blobData.getExpiryTimeMillis()));
            assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.acquireLease(
                    blobData.getBlobHandle(), R.string.test_desc, -1));
            assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.acquireLease(
                    blobData.getBlobHandle(), -1));
            assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.acquireLease(
                    blobData.getBlobHandle(), null));
            assertThrows(IllegalArgumentException.class, () -> mBlobStoreManager.acquireLease(
                    blobData.getBlobHandle(), null, blobData.getExpiryTimeMillis()));
        } finally {
            blobData.delete();
        }

        assertThrows(NullPointerException.class, () -> mBlobStoreManager.releaseLease(null));
    }

    @Test
    public void testStorageAttributedToSelf() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();
        final long partialFileSize = 3373L;

        final StorageStatsManager storageStatsManager = mContext.getSystemService(
                StorageStatsManager.class);
        StorageStats beforeStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        StorageStats beforeStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // Create a session and write some data.
        final long sessionId = createSession(blobData.getBlobHandle());
        assertThat(sessionId).isGreaterThan(0L);
        try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
            blobData.writeToSession(session, 0, partialFileSize);
        }

        StorageStats afterStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        StorageStats afterStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // 'partialFileSize' bytes were written, verify the size increase.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(partialFileSize);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(partialFileSize);

        // Complete writing data.
        final long totalFileSize = blobData.getFileSize();
        try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
            blobData.writeToSession(session, partialFileSize, totalFileSize - partialFileSize);
        }

        afterStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        afterStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // 'totalFileSize' bytes were written so far, verify the size increase.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(totalFileSize);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(totalFileSize);

        // Commit the session.
        try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
            blobData.writeToSession(session, partialFileSize, session.getSize() - partialFileSize);
            final CompletableFuture<Integer> callback = new CompletableFuture<>();
            session.commit(mContext.getMainExecutor(), callback::complete);
            assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                    .isEqualTo(0);
        }

        mBlobStoreManager.acquireLease(blobData.getBlobHandle(), R.string.test_desc);

        afterStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        afterStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // Session was committed but no one else is using it, verify the size increase stays
        // the same as earlier.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(totalFileSize);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(totalFileSize);

        mBlobStoreManager.releaseLease(blobData.getBlobHandle());

        afterStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        afterStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // No leases on the blob, so it should not be attributed.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(0L);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(0L);
    }

    @Test
    public void testStorageAttribution_acquireLease() throws Exception {
        final DummyBlobData blobData = new DummyBlobData(mContext);
        blobData.prepare();

        final StorageStatsManager storageStatsManager = mContext.getSystemService(
                StorageStatsManager.class);
        StorageStats beforeStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        StorageStats beforeStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        final long sessionId = mBlobStoreManager.createSession(blobData.getBlobHandle());
        try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
            blobData.writeToSession(session);
            session.allowPublicAccess();

            final CompletableFuture<Integer> callback = new CompletableFuture<>();
            session.commit(mContext.getMainExecutor(), callback::complete);
            assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                    .isEqualTo(0);
        }

        StorageStats afterStatsForPkg = storageStatsManager
                .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(), mContext.getUser());
        StorageStats afterStatsForUid = storageStatsManager
                .queryStatsForUid(UUID_DEFAULT, Process.myUid());

        // No leases on the blob, so it should not be attributed.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(0L);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(0L);

        final TestServiceConnection serviceConnection = bindToHelperService(HELPER_PKG);
        final ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
        try {
            StorageStats beforeStatsForHelperPkg = commandReceiver.queryStatsForPackage();
            StorageStats beforeStatsForHelperUid = commandReceiver.queryStatsForUid();

            commandReceiver.acquireLease(blobData.getBlobHandle());

            StorageStats afterStatsForHelperPkg = commandReceiver.queryStatsForPackage();
            StorageStats afterStatsForHelperUid = commandReceiver.queryStatsForUid();

            assertThat(
                    afterStatsForHelperPkg.getDataBytes() - beforeStatsForHelperPkg.getDataBytes())
                    .isEqualTo(blobData.getFileSize());
            assertThat(
                    afterStatsForHelperUid.getDataBytes() - beforeStatsForHelperUid.getDataBytes())
                    .isEqualTo(blobData.getFileSize());

            afterStatsForPkg = storageStatsManager
                    .queryStatsForPackage(UUID_DEFAULT, mContext.getPackageName(),
                            mContext.getUser());
            afterStatsForUid = storageStatsManager
                    .queryStatsForUid(UUID_DEFAULT, Process.myUid());

            // There shouldn't be no change in stats for this package
            assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                    .isEqualTo(0L);
            assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                    .isEqualTo(0L);

            commandReceiver.releaseLease(blobData.getBlobHandle());

            afterStatsForHelperPkg = commandReceiver.queryStatsForPackage();
            afterStatsForHelperUid = commandReceiver.queryStatsForUid();

            // Lease is released, so it should not be attributed anymore.
            assertThat(
                    afterStatsForHelperPkg.getDataBytes() - beforeStatsForHelperPkg.getDataBytes())
                    .isEqualTo(0L);
            assertThat(
                    afterStatsForHelperUid.getDataBytes() - beforeStatsForHelperUid.getDataBytes())
                    .isEqualTo(0L);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void commitBlobFromPkg(DummyBlobData blobData, TestServiceConnection serviceConnection)
            throws Exception {
        final ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
        try (ParcelFileDescriptor pfd = blobData.openForRead()) {
            assertThat(commandReceiver.commit(blobData.getBlobHandle(),
                    pfd, TIMEOUT_COMMIT_CALLBACK_SEC, blobData.getFileSize()))
                            .isEqualTo(0);
        }
    }

    private void openSessionFromPkg(long sessionId, String pkg) throws Exception {
        final TestServiceConnection serviceConnection = bindToHelperService(pkg);
        try {
            final ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            commandReceiver.openSession(sessionId);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPkgCanAccess(DummyBlobData blobData, String pkg) throws Exception {
        final TestServiceConnection serviceConnection = bindToHelperService(pkg);
        try {
            assertPkgCanAccess(blobData, serviceConnection);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPkgCanAccess(DummyBlobData blobData,
            TestServiceConnection serviceConnection) throws Exception {
        final ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
        commandReceiver.acquireLease(blobData.getBlobHandle());
        try (ParcelFileDescriptor pfd = commandReceiver.openBlob(blobData.getBlobHandle())) {
            assertThat(pfd).isNotNull();
            blobData.verifyBlob(pfd);
        }
    }

    private void assertPkgCannotAccess(DummyBlobData blobData, String pkg) throws Exception {
        final TestServiceConnection serviceConnection = bindToHelperService(pkg);
        try {
            assertPkgCannotAccess(blobData, serviceConnection);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPkgCannotAccess(DummyBlobData blobData,
        TestServiceConnection serviceConnection) throws Exception {
        final ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
        assertThrows(SecurityException.class,
                () -> commandReceiver.acquireLease(blobData.getBlobHandle()));
        assertThrows(SecurityException.class,
                () -> commandReceiver.openBlob(blobData.getBlobHandle()));
    }

    private TestServiceConnection bindToHelperService(String pkg) throws Exception {
        final TestServiceConnection serviceConnection = new TestServiceConnection(mContext);
        final Intent intent = new Intent()
                .setComponent(new ComponentName(pkg, HELPER_SERVICE));
        mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return serviceConnection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private ICommandReceiver mCommandReceiver;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service got connected: " + componentName);
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Service got disconnected: " + componentName);
        }

        private IBinder getService() throws Exception {
            final IBinder service = mBlockingQueue.poll(TIMEOUT_BIND_SERVICE_SEC,
                    TimeUnit.SECONDS);
            return service;
        }

        public ICommandReceiver getCommandReceiver() throws Exception {
            if (mCommandReceiver == null) {
                mCommandReceiver = ICommandReceiver.Stub.asInterface(getService());
            }
            return mCommandReceiver;
        }

        public void unbind() {
            mCommandReceiver = null;
            mContext.unbindService(this);
        }
    }

    private long createSession(BlobHandle blobHandle) throws Exception {
        final long sessionId = mBlobStoreManager.createSession(blobHandle);
        mCreatedSessionIds.add(sessionId);
        return sessionId;
    }

    private String executeCmd(String cmd) {
        final String result = runShellCommand(cmd).trim();
        Log.d(TAG, "Output of <" + cmd + ">: <" + result + ">");
        return result;
    }
}
