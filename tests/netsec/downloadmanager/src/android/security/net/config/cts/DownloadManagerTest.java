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

import static android.security.net.config.cts.TestUtils.assertDownloadManagerFailsAsPaused;
import static android.security.net.config.cts.TestUtils.assertDownloadManagerSucceeds;
import static android.security.net.config.cts.TestUtils.bindCleartextServer;
import static android.security.net.config.cts.TestUtils.bindTLSServer;
import static android.security.net.config.cts.TestUtils.startMockServer;

import android.security.net.config.cts.CtsNetSecConfigDownloadManagerTestCases.R;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;

@RunWith(AndroidJUnit4.class)
public class DownloadManagerTest extends BaseTestCase {

    private ServerSocket mServerSocket;

    @Test
    public void testConfigTrustedCaAccepted() throws Exception {
        mServerSocket = bindTLSServer(mContext, R.raw.valid_chain, R.raw.test_key);
        startMockServer(mServerSocket);

        assertDownloadManagerSucceeds(
                mContext, "localhost", mServerSocket.getLocalPort(), /* https= */ true);
        mServerSocket.close();
    }

    @Test
    public void testUntrustedCaRejected() throws Exception {
        mServerSocket = bindTLSServer(mContext, R.raw.invalid_chain, R.raw.test_key);
        startMockServer(mServerSocket);

        assertDownloadManagerFailsAsPaused(
                mContext, "localhost", mServerSocket.getLocalPort(), /* https= */ true);
        mServerSocket.close();
    }

    @Test
    public void testPerDomainCleartextAccepted() throws Exception {
        mServerSocket = bindCleartextServer();
        startMockServer(mServerSocket);

        assertDownloadManagerSucceeds(
                mContext, "localhost", mServerSocket.getLocalPort(), /* https= */ false);
        mServerSocket.close();
    }

}
