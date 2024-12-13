/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    Flags.FLAG_CERTIFICATE_TRANSPARENCY_CONFIGURATION,
    com.android.org.conscrypt.flags.Flags.FLAG_CERTIFICATE_TRANSPARENCY_PLATFORM
})
// TODO(b/383539782): replace reused test helpers & constants with common utils
public class SctValidationNoLogListPresentTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String NO_SCT_PROVIDED_DOMAIN = "https://no-sct.badssl.com/";
    private static final int HTTP_OK_RESPONSE_CODE = 200;

    // Path copied from com.android.server.net.ct.Config
    // Note: we do this to avoid a dependency on the service, which may result in
    // testing the code in CTS instead of the device itself
    private static final String CT_ROOT_DIRECTORY_PATH = "/data/misc/keychain/ct/";

    @Test
    public void testCTVerification_whenLogListAbsent_noSctDomain_failsOpen() throws IOException {
        assertFalse(isLogListFilePresent());
        URL url = new URL(NO_SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
    }

    /**
     * Returns whether the CT root directory is empty or not. For simplicity, we do not check
     * whether the correct log list file version is present.
     */
    private static boolean isLogListFilePresent() {
        // TODO(b/378427150): replace with Conscrypt API once implemented
        try {
            Path ctRootDir = Paths.get(CT_ROOT_DIRECTORY_PATH);

            try (Stream<Path> stream = Files.list(ctRootDir)) {
                boolean hasFiles = stream.findAny().isPresent();
                return Files.exists(ctRootDir) && Files.isDirectory(ctRootDir) && hasFiles;
            }
        } catch (IOException e) {
            // NoSuchFileException is a subclass of IOException, which is why we do not
            // specify it here in the catch statement.
            return false;
        }
    }
}
