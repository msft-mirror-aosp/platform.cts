/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts.playback;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.RequiredPropertyRule;
import android.hdmicec.cts.RequiredFeatureRule;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/** HDMI CEC test to test routing control (Section 11.2.2) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRoutingControlTest extends BaseHdmiCecCtsTest {

    private static final int PHYSICAL_ADDRESS = 0x1000;

    public HdmiCecRoutingControlTest() {
        super(LogicalAddress.PLAYBACK_1);
    }

    @Rule
    public RuleChain ruleChain =
        RuleChain
            .outerRule(CecRules.requiresCec(this))
            .around(CecRules.requiresLeanback(this))
            .around(CecRules.requiresDeviceType(this, LogicalAddress.PLAYBACK_1))
            .around(hdmiCecClient);

    /**
     * Test 11.2.2-1
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <SET_STREAM_PATH>, when
     * the TV has switched to a different input.
     */
    @Test
    public void cect_11_2_2_1_SetStreamPathToDut() throws Exception {
        final long alternateAddress;
        if (dutPhysicalAddress == 0x1000) {
            alternateAddress = 0x2000;
        } else {
            alternateAddress = 0x1000;
        }
        /*
         * Switch to HDMI port whose physical address is alternateAddress. DUT is connected to HDMI
         * port whose physical address is dutPhysicalAddress.
         */
        hdmiCecClient.sendCecMessage(LogicalAddress.PLAYBACK_2, LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE, CecMessage.formatParams(alternateAddress));
        TimeUnit.SECONDS.sleep(3);
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
                CecOperand.SET_STREAM_PATH,
                CecMessage.formatParams(dutPhysicalAddress));
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, dutPhysicalAddress);
    }

    /**
     * Test 11.2.2-2
     * Tests that the device broadcasts a <ACTIVE_SOURCE> in response to a <REQUEST_ACTIVE_SOURCE>.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    @Test
    public void cect_11_2_2_2_RequestActiveSource() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("input keyevent KEYCODE_HOME");
        hdmiCecClient.sendCecMessage(LogicalAddress.TV, LogicalAddress.BROADCAST,
            CecOperand.REQUEST_ACTIVE_SOURCE);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.ACTIVE_SOURCE);
        CecMessage.assertPhysicalAddressValid(message, dutPhysicalAddress);
    }

    /**
     * Test 11.2.2-4
     * Tests that the device sends a <INACTIVE_SOURCE> message when put on standby.
     * This test depends on One Touch Play, and will pass only if One Touch Play passes.
     */
    @Test
    public void cect_11_2_2_4_InactiveSourceOnStandby() throws Exception {
        ITestDevice device = getDevice();
        try {
            device.executeShellCommand("input keyevent KEYCODE_HOME");
            device.executeShellCommand("input keyevent KEYCODE_SLEEP");
            String message = hdmiCecClient.checkExpectedOutput(LogicalAddress.TV,
                    CecOperand.INACTIVE_SOURCE);
            CecMessage.assertPhysicalAddressValid(message, dutPhysicalAddress);
        } finally {
            /* Wake up the device */
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        }
    }
}
