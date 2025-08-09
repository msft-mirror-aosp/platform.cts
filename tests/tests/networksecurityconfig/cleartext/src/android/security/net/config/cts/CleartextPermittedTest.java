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

import static android.security.net.config.cts.TestUtils.assertCleartextConnectionFails;
import static android.security.net.config.cts.TestUtils.assertCleartextConnectionSucceeds;
import static android.security.net.config.cts.TestUtils.assertTlsConnectionSucceeds;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CleartextPermittedTest extends BaseTestCase {

    @Test
    public void testDefaultDenied() throws Exception {
        assertCleartextConnectionFails("google.com");
        assertTlsConnectionSucceeds("google.com");
    }

    @Test
    public void testCleartextAllowed() throws Exception {
        assertCleartextConnectionSucceeds("android.com");
        assertTlsConnectionSucceeds("android.com");
        // subdomains of android.com are also disallowed.
        assertCleartextConnectionSucceeds("www.android.com");
        assertTlsConnectionSucceeds("www.android.com");
    }

    @Test
    public void testNestedCleartextDenied() throws Exception {
        // developer.android.com is explicitly denied.
        assertCleartextConnectionFails("developer.android.com");
        assertTlsConnectionSucceeds("developer.android.com");
    }
}
