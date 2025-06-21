/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.net.config.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.security.net.config.cts.CtsNetSecConfigDownloadManagerTestCases.R;
import android.text.format.DateUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLServerSocket;

@RunWith(AndroidJUnit4.class)
public class DownloadManagerTest extends BaseTestCase {

    private static final String HTTP_RESPONSE =
            "HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nContent-length: 5\r\n\r\nhello";
    private static final long TIMEOUT = 3 * DateUtils.SECOND_IN_MILLIS;

    @Test
    public void testConfigTrustedCaAccepted() throws Exception {
        SSLServerSocket serverSocket =
                TestUtils.bindTLSServer(mContext, R.raw.valid_chain, R.raw.test_key);
        runDownloadManagerTest(serverSocket, true);
    }

    @Test
    public void testUntrustedCaRejected() throws Exception {
        try {
            SSLServerSocket serverSocket =
                    TestUtils.bindTLSServer(mContext, R.raw.invalid_chain, R.raw.test_key);
            runDownloadManagerTest(serverSocket, true);
            fail("Invalid CA should be rejected");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testPerDomainCleartextAccepted() throws Exception {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(null);
        runDownloadManagerTest(serverSocket, false);
    }

    private void runDownloadManagerTest(ServerSocket serverSocket, boolean https) throws Exception {
        DownloadManager dm =  mContext.getSystemService(DownloadManager.class);
        DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        FutureTask<Void> serverFuture = new FutureTask<Void>(new Callable() {
            @Override
            public Void call() throws Exception {
                runServer(serverSocket);
                return null;
            }
        });
        try {
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            new Thread(serverFuture).start();
            String host = (https ? "https" : "http") + "://localhost";
            Uri destination = Uri.parse(host + ":" + serverSocket.getLocalPort());
            long id = dm.enqueue(new DownloadManager.Request(destination));
            try {
                serverFuture.get();
                // Check that the download was successful.
                receiver.waitForDownloadComplete(TIMEOUT, id);
                assertSuccessfulDownload(id);
            } catch (InterruptedException e) {
                // Wrap InterruptedException since otherwise it gets eaten by AndroidTest
                throw new RuntimeException(e);
            } finally {
                dm.remove(id);
            }
        } finally {
            mContext.unregisterReceiver(receiver);
            serverFuture.cancel(true);
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
        }
    }

    private void runServer(ServerSocket server) throws Exception {
        Socket s = server.accept();
        s.getOutputStream().write(HTTP_RESPONSE.getBytes());
        s.getOutputStream().flush();
        s.close();
    }

    private void assertSuccessfulDownload(long id) throws Exception {
        Cursor cursor = null;
        DownloadManager dm = mContext.getSystemService(DownloadManager.class);
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterById(id));
            assertTrue(cursor.moveToNext());
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static final class DownloadCompleteReceiver extends BroadcastReceiver {
        private HashSet<Long> mCompletedDownloads = new HashSet<>();

        public DownloadCompleteReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized(mCompletedDownloads) {
                mCompletedDownloads.add(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                mCompletedDownloads.notifyAll();
            }
        }

        public void waitForDownloadComplete(long timeout, long id)
                throws TimeoutException, InterruptedException  {
            long deadline = SystemClock.elapsedRealtime() + timeout;
            do {
                synchronized (mCompletedDownloads) {
                    long millisTillTimeout = deadline - SystemClock.elapsedRealtime();
                    if (millisTillTimeout > 0) {
                        mCompletedDownloads.wait(millisTillTimeout);
                    }
                    if (mCompletedDownloads.contains(id)) {
                        return;
                    }
                }
            } while (SystemClock.elapsedRealtime() < deadline);

            throw new TimeoutException("Timed out waiting for download complete");
        }
    }


}
