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

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_NETWORK_SECURITY_CONFIG_LOCALHOST)
public class LocalhostCleartextWithNscTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ServerSocket mServerSocket;

    @Before
    public void setUp() throws Exception {
        mServerSocket = TestUtils.bindCleartextServer();
        TestUtils.startMockServer(mServerSocket);
    }

    @After
    public void tearDown() throws Exception {
        mServerSocket.close();
    }

    @Test
    public void connectInCleartextOnIpV4Localhost_connectionSucceeds() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("localhost", mServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV6Localhost_connectionSucceeds() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("ip6-localhost", mServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV4127_0_0_1_connectionSucceeds() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("127.0.0.1", mServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV4127_0_0_42_connectionSucceeds() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("127.0.0.42", mServerSocket.getLocalPort());
    }

    @Test
    public void connectInCleartextOnIpV6LoopbackAddress_connectionSucceeds() throws Exception {
        TestUtils.assertCleartextConnectionSucceeds("[::1]", mServerSocket.getLocalPort());
    }
}
