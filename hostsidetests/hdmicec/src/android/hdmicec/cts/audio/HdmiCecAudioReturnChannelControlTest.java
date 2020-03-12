/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts.audio;

import android.hdmicec.cts.CecDevice;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

/** HDMI CEC test to test audio return channel control (Section 11.2.17) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecAudioReturnChannelControlTest extends BaseHostJUnit4Test {
    private static final CecDevice AUDIO_DEVICE = CecDevice.AUDIO_SYSTEM;

    @Rule
    public HdmiCecClientWrapper hdmiCecClient = new HdmiCecClientWrapper(AUDIO_DEVICE, this);

    /**
     * Test 11.2.17-1
     * Tests that the device sends a directly addressed <Initiate ARC> message
     * when it wants to initiate ARC.
     */
    @Test
    public void cect_11_2_17_1_InitiateArc() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, CecDevice.BROADCAST,
                CecMessage.REPORT_PHYSICAL_ADDRESS,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        getDevice().executeShellCommand("reboot");
        getDevice().waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        hdmiCecClient.checkExpectedOutput(CecDevice.TV, CecMessage.INITIATE_ARC);
    }

    /**
     * Test 11.2.17-3
     * Tests that the device sends a directly addressed <Initiate ARC>
     * message when it is requested to initiate ARC.
     */
    @Test
    public void cect_11_2_17_3_RequestToInitiateArc() throws Exception {
        hdmiCecClient.sendCecMessage(CecDevice.TV, CecDevice.BROADCAST,
                CecMessage.REPORT_PHYSICAL_ADDRESS,
                hdmiCecClient.formatParams(HdmiCecConstants.TV_PHYSICAL_ADDRESS,
                HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
        hdmiCecClient.sendCecMessage(CecDevice.TV, AUDIO_DEVICE,
                CecMessage.REQUEST_ARC_INITIATION);
        hdmiCecClient.checkExpectedOutput(CecDevice.TV, CecMessage.INITIATE_ARC);
    }
}
