/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.privatecompute.flags.Flags;
import android.os.PersistableBundle;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;

@RunWith(AndroidJUnit4.class)
public class PersistableBundleTest extends StsExtraBusinessLogicTestCase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @AsbSecurityTest(cveBugId = 247513680)
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testReadFromStream_invalidType() throws Exception {
        String input = "<bundle><string name=\"key\">value</string>"
                + "<byte-array name=\"invalid\" num=\"2\">ffff</byte-array></bundle>";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());

        // Reading from the stream with invalid type should not throw an exception
        PersistableBundle restoredBundle = PersistableBundle.readFromStream(inputStream);

        // verify invalid type is ignored
        assertFalse(restoredBundle.containsKey("invalid"));
        // verify valid type exists
        assertEquals("value", restoredBundle.getString("key"));
    }

    // 20/11/2025 update: byte array is now a valid type. Instead of being filtered out while
    // parsing, it should exist in the PersistableBundle. It should not throw an exception
    // when accessed.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testReadFromStream_invalidTypeIsNowValid() throws Exception {
        String input = "<bundle><string name=\"key\">value</string>"
                + "<byte-array name=\"valid\" num=\"2\">abcd</byte-array></bundle>";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());

        // Reading from the stream with invalid type should not throw an exception
        PersistableBundle restoredBundle = PersistableBundle.readFromStream(inputStream);

        assertTrue(restoredBundle.containsKey("valid"));
        byte[] validByteArray = restoredBundle.getByteArray("valid");
        assertEquals(2, validByteArray.length);
        // We don't want to tie this test to the internal XML representation of the byte array,
        // so we won't check the contents.
        // verify valid type exists
        assertEquals("value", restoredBundle.getString("key"));
    }
}
