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

package android.security.net.config.cts;

import static com.android.org.conscrypt.net.flags.Flags.FLAG_NETWORK_SECURITY_CONFIG_LOCALHOST;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.net.config.cts.CtsNetSecConfigLocalhostTlsTestCases.R;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;

import javax.net.ssl.SSLServerSocket;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_NETWORK_SECURITY_CONFIG_LOCALHOST)
public class LocalhostTlsTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ServerSocket mCleartextServerSocket;
    private SSLServerSocket mTlsServerSocket;

    @Before
    public void setUp() throws Exception {
        mTlsServerSocket = TestUtils.bindTLSServer(mContext, R.raw.valid_chain, R.raw.test_key);
        TestUtils.startMockServer(mTlsServerSocket);
        mCleartextServerSocket = TestUtils.bindCleartextServer();
        TestUtils.startMockServer(mCleartextServerSocket);
    }

    @After
    public void tearDown() throws Exception {
        mTlsServerSocket.close();
        mCleartextServerSocket.close();
    }

    @Test
    public void connectWithTlsOnIpV4Localhost_connectionSucceeds() throws Exception {
        TestUtils.assertTlsConnectionSucceeds("localhost", mTlsServerSocket.getLocalPort());
    }

    @Test
    public void connectWithTlsOnIpV6Localhost_connectionSucceeds() throws Exception {
        TestUtils.assertTlsConnectionSucceeds("ip6-localhost", mTlsServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV4Localhost_connectionFails() throws Exception {
        TestUtils.assertCleartextConnectionFails(
                "localhost", mCleartextServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV6Localhost_connectionFails() throws Exception {
        TestUtils.assertCleartextConnectionFails(
                "ip6-localhost", mCleartextServerSocket.getLocalPort());
    }
}
