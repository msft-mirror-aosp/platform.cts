/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.media.session.cts;

import android.media.router.cts.BaseMediaRouter2HostSideTest;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.ddmlib.NullOutputReceiver;
import com.android.media.flags.Flags;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class OutputSwitcherHostTest extends BaseMediaRouter2HostSideTest {
    private static final String CREATOR_APK = "CtsMediaSessionCreatorApp.apk";
    private static final String CREATOR_PKG = "android.media.router.cts.output.switcher.creator";
    private static final String OWNER_APK = "CtsMediaSessionOwnerApp.apk";
    private static final String OWNER_PKG = "android.media.router.cts.output.switcher.owner";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private BackgroundDeviceAction mSessionCreatorApp;

    @Before
    public void setUp() throws Exception {
        mSessionCreatorApp = null;
        installPackage(CREATOR_APK);
        installPackage(OWNER_APK);
    }

    @After
    public void tearDown() throws Exception {
        getDevice()
                .executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
        getDevice().uninstallPackage(CREATOR_PKG);
        getDevice().uninstallPackage(OWNER_PKG);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @RequiresDevice
    @Test
    public void testShowSystemOutputSwitcherWithOverriddenMediaSessionOwner() throws Exception {
        int userId = getDevice().getCurrentUser();
        String instrumentCreatorAppCommand =
                "am instrument -w --user "
                        + userId
                        + " "
                        + CREATOR_PKG
                        + "/"
                        + ".StayAliveInstrumentation";
        mSessionCreatorApp =
                new BackgroundDeviceAction(
                        instrumentCreatorAppCommand,
                        "Instrumentation that keeps the app alive",
                        getDevice(),
                        NullOutputReceiver.getReceiver(),
                        /* startDelay= */ 0);
        mSessionCreatorApp.start();
        runDeviceTests(
                OWNER_PKG,
                OWNER_PKG + ".MediaSessionOwnerTest",
                "testShowSystemOutputSwitcherWithOverriddenOwner");
    }
}
