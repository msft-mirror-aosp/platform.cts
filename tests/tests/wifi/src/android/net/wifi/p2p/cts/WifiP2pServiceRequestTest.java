/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.p2p.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.wifi.cts.WifiJUnit4TestBase;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiP2pServiceRequestTest extends WifiJUnit4TestBase {

    private final int TEST_UPNP_VERSION = 0x10;
    private final String TEST_UPNP_QUERY = "ssdp:all";

    private String bin2HexStr(byte[] data) {
        StringBuffer sb = new StringBuffer();
        for (byte b: data) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    @Test
    public void validRawRequest() throws IllegalArgumentException {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format(Locale.US, "%02x", TEST_UPNP_VERSION));
        sb.append(bin2HexStr(TEST_UPNP_QUERY.getBytes()));

        WifiP2pServiceRequest rawRequest =
                WifiP2pServiceRequest.newInstance(
                        WifiP2pServiceInfo.SERVICE_TYPE_UPNP,
                        sb.toString());

        WifiP2pUpnpServiceRequest upnpRequest =
                WifiP2pUpnpServiceRequest.newInstance(
                        TEST_UPNP_QUERY);

        assertEquals(rawRequest, upnpRequest);
    }

    @Test
    public void invalidRawRequest() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format(Locale.US, "%02x", TEST_UPNP_VERSION));
        sb.append(bin2HexStr(TEST_UPNP_QUERY.getBytes()));
        sb.append("x");

        assertThrows(IllegalArgumentException.class, () -> WifiP2pServiceRequest.newInstance(
                WifiP2pServiceInfo.SERVICE_TYPE_UPNP, sb.toString()));
    }
}
