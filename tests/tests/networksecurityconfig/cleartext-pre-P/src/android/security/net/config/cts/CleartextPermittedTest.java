/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.security.net.config.cts.TestUtils.assertCleartextConnectionFails;
import static android.security.net.config.cts.TestUtils.assertCleartextConnectionSucceeds;
import static android.security.net.config.cts.TestUtils.assertTlsConnectionSucceeds;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CleartextPermittedTest extends BaseTestCase {

    @Test
    public void testDefaultAllowed() throws Exception {
        assertCleartextConnectionSucceeds("google.com", 80);
        assertTlsConnectionSucceeds("google.com", 443);
    }

    @Test
    public void testCleartextBlocked() throws Exception {
        assertCleartextConnectionFails("android.com", 80);
        assertTlsConnectionSucceeds("android.com", 443);
        // subdomains of android.com are also disallowed.
        assertCleartextConnectionFails("www.android.com", 80);
        assertTlsConnectionSucceeds("www.android.com", 443);
    }

    @Test
    public void testNestedCleartextPermitted() throws Exception {
        // developer.android.com is explicitly permitted.
        assertCleartextConnectionSucceeds("developer.android.com", 80);
        assertTlsConnectionSucceeds("developer.android.com", 443);
    }
}
