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

import static android.security.net.config.cts.TestUtils.assertCleartextConnectionSucceeds;
import static android.security.net.config.cts.TestUtils.assertDownloadManagerSucceeds;
import static android.security.net.config.cts.TestUtils.assertTlsConnectionSucceeds;

import static org.junit.Assert.assertEquals;

import android.security.NetworkSecurityPolicy;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetworkSecurityPolicyCleartextTrueTest extends BaseTestCase {
    @Test
    public void testNetworkSecurityPolicy() {
        assertEquals(true, NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
    }

    @Test
    public void testCleartextAllowed() throws Exception {
        assertCleartextConnectionSucceeds("android.com", 80);
    }

    @Test
    public void testTlsAllowed() throws Exception {
        assertTlsConnectionSucceeds("android.com", 443);
    }

    @Test
    public void testCleartextDownloadManagerAllowed() throws Exception {
        assertDownloadManagerSucceeds(mContext, "android.com", 80, /* https= */ false);
    }

    @Test
    public void testTlsDownloadManagerAllowed() throws Exception {
        assertDownloadManagerSucceeds(mContext, "android.com", 443, /* https= */ true);
    }
}
