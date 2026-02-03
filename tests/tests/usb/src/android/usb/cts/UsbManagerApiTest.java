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

package android.usb.cts;

import static android.Manifest.permission.MANAGE_USB;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.DisplayPortAltModeInfo;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbManager.Bc12TypeListener;
import android.hardware.usb.UsbManager.DisplayPortAltModeInfoListener;
import android.hardware.usb.UsbManager.PowerProfileInfoListener;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.hardware.usb.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link android.hardware.usb.UsbManager}. Note: MUST claimed MANAGE_USB permission
 * in Manifest
 */
@RunWith(BedsteadJUnit4.class)
public class UsbManagerApiTest {
    private static final String TAG = UsbManagerApiTest.class.getSimpleName();

    private UsbManager mUsbManagerSys =
        InstrumentationRegistry.getContext().getSystemService(UsbManager.class);

    // Update latest HAL version here
    private int USB_HAL_LATEST_VERSION = UsbManager.USB_HAL_V1_3;

    private UiAutomation mUiAutomation =
        InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Context mContext;
    private Executor mExecutor;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = mContext.getMainExecutor();
        PackageManager pm = mContext.getPackageManager();

        boolean hasUsbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        boolean hasUsbAccessory =
            pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
        Assume.assumeTrue(hasUsbHost || hasUsbAccessory);
        Assert.assertNotNull(mUsbManagerSys);
    }

    /**
     * Verify NO SecurityException.
     * Go through System Server.
     */
    @Test
    public void test_UsbApiSetGetCurrentFunctionsSys() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        mUsbManagerSys.setCurrentFunctions(UsbManager.FUNCTION_NONE);
        Assert.assertEquals("CurrentFunctions mismatched: ", UsbManager.FUNCTION_NONE,
                mUsbManagerSys.getCurrentFunctions());

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getCurrentFunctions();
            Assert.fail("Expecting SecurityException on getCurrentFunctions.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getCurrentFunctions");
        }

        try {
            mUsbManagerSys.setCurrentFunctions(UsbManager.FUNCTION_NONE);
            Assert.fail("Expecting SecurityException on setCurrentFunctions.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on setCurrentFunctions");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForUsbGadgetHal() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        int version = mUsbManagerSys.getGadgetHalVersion();
        int usbBandwidth = mUsbManagerSys.getUsbBandwidthMbps();
        if (version > UsbManager.GADGET_HAL_V1_2) {
            Assert.assertTrue(usbBandwidth >= UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        } else if (version > UsbManager.GADGET_HAL_V1_1) {
            Assert.assertTrue(usbBandwidth > UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        } else {
            Assert.assertEquals(usbBandwidth, UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getGadgetHalVersion();
            Assert.fail("Expecting SecurityException on getGadgetHalVersion.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getGadgetHalVersion.");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForUsbHal() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        int version = mUsbManagerSys.getUsbHalVersion();
        if (version == USB_HAL_LATEST_VERSION) {
            Log.d(TAG, "Running with the latest HAL version");
        } else if (version == UsbManager.USB_HAL_NOT_SUPPORTED) {
            Log.d(TAG, "Not supported HAL version");
        }
        else {
            Log.d(TAG, "Not the latest HAL version");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getUsbHalVersion();
            Assert.fail("Expecting SecurityException on getUsbHalVersion.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getUsbHalVersion.");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        final DisplayPortAltModeInfoListener displayPortListener =
                new DisplayPortAltModeInfoListener() {
            public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
                Log.d(TAG, "test_UsbApiForDisplayPortAltModeRegisterSecurity listener called");
            };
        };

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);
        mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        assertThrows(SecurityException.class, () ->
                mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor,
                displayPortListener));
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeUnregisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        final DisplayPortAltModeInfoListener displayPortListener =
                new DisplayPortAltModeInfoListener() {
            public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
                Log.d(TAG, "test_UsbApiForDisplayPortAltModeUnregisterSecurity listener called");
            };
        };

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        assertThrows(SecurityException.class, () ->
                mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener));
    }

    /**
     * Verify DisplayPortAltModeInfo changes properly invoke consumers from
     * registerDisplayPortAltModeInfoListener.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterFunctionality() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portIdTestString = "ctstest-singlelistener";
        final CountDownLatch notifiedForCtsPort = new CountDownLatch(1);

        // Should pass with permission.
        final LatchedDisplayPortAltModeInfoListener displayPortListener =
                new LatchedDisplayPortAltModeInfoListener(notifiedForCtsPort);

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);

        SystemUtil.runShellCommand("dumpsys usb add-port " + portIdTestString
                + " dual --displayport");
        SystemUtil.runShellCommand("dumpsys usb set-displayport-status "
                + portIdTestString + " 2 2 2 false 0");

        assertTrue(notifiedForCtsPort.await(1000, TimeUnit.MILLISECONDS));
        mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener);

        SystemUtil.runShellCommand("dumpsys usb remove-port " + portIdTestString);

        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify DisplayPortAltModeInfo changes properly invoke consumers from
     * registerDisplayPortAltModeInfoListener.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterMultiListenerFunctionality()
            throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portIdTestString = "ctstest-multilistener";
        final int numListeners = 2;
        final CountDownLatch notifiedForCtsPort = new CountDownLatch(numListeners);

        // Should pass with permission.
        final ArrayList<LatchedDisplayPortAltModeInfoListener> listeners =
                new ArrayList<LatchedDisplayPortAltModeInfoListener>();
        for (int i = 0; i < numListeners; i++) {
            final LatchedDisplayPortAltModeInfoListener listener =
                    new LatchedDisplayPortAltModeInfoListener(notifiedForCtsPort);
            mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, listener);
            listeners.add(listener);
        }

        SystemUtil.runShellCommand("dumpsys usb add-port " + portIdTestString
                + " dual --displayport");
        SystemUtil.runShellCommand("dumpsys usb set-displayport-status "
                + portIdTestString + " 2 2 2 false 0");

        assertTrue(notifiedForCtsPort.await(1000, TimeUnit.MILLISECONDS));

        for (int i = 0; i < numListeners; i++) {
            mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(listeners.get(i));
        }

        SystemUtil.runShellCommand("dumpsys usb remove-port " + portIdTestString);

        mUiAutomation.dropShellPermissionIdentity();
    }

    private static class LatchedDisplayPortAltModeInfoListener implements
            DisplayPortAltModeInfoListener {
        private final CountDownLatch mLatch;

        public LatchedDisplayPortAltModeInfoListener(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
            mLatch.countDown();
        }
    }

    /**
     * Verify NO SecurityException when the MANAGE_USB is held, and verify that SecurityException is
     * thrown when MANAGE_USB is not held.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_POWER_PROFILE_REPORTING)
    public void test_UsbApiForBc12TypeRegisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_USB)) {
            // Should pass with permission
            final Bc12TypeListener bc12TypeListener =
                    new Bc12TypeListener() {
                        public void onPartnerBc12TypeChanged(UsbPort port, int partnerBc12Type) {
                            Log.d(TAG, "test_UsbApiForBc12TypeRegisterSecurity listener called");
                        }
                    };

            try {
                mUsbManagerSys.registerBc12TypeListener(mExecutor, bc12TypeListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "registerBc12TypeListener failed with SecurityException when MANAGE_USB is"
                                + " held.");
            }

            try {
                mUsbManagerSys.unregisterBc12TypeListener(bc12TypeListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "unregisterBc12TypeListener failed with SecurityException when MANAGE_USB"
                                + " is held.");
            }

            // Drop MANAGE_USB permission.
            try (PermissionContext p2 = TestApis.permissions().withoutPermission(MANAGE_USB)) {
                assertThrows(
                        SecurityException.class,
                        () -> mUsbManagerSys.registerBc12TypeListener(mExecutor, bc12TypeListener));
            }
        }
    }

    /**
     * Verify NO SecurityException when the MANAGE_USB is held, and verify that SecurityException is
     * thrown when MANAGE_USB is not held.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_POWER_PROFILE_REPORTING)
    public void test_UsbApiForBc12TypeUnregisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_USB)) {
            // Should pass with permission
            final Bc12TypeListener bc12TypeListener =
                    new Bc12TypeListener() {
                        public void onPartnerBc12TypeChanged(UsbPort port, int partnerBc12Type) {
                            Log.d(TAG, "test_UsbApiForBc12TypeUnregisterSecurity listener called");
                        }
                    };

            try {
                mUsbManagerSys.registerBc12TypeListener(mExecutor, bc12TypeListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "registerBc12TypeListener failed with SecurityException when MANAGE_USB is"
                                + " held.");
            }

            // Drop MANAGE_USB permission.
            try (PermissionContext p2 = TestApis.permissions().withoutPermission(MANAGE_USB)) {
                assertThrows(
                        SecurityException.class,
                        () -> mUsbManagerSys.unregisterBc12TypeListener(bc12TypeListener));
            }
        }
    }

    /**
     * Verify NO SecurityException when the MANAGE_USB is held, and verify that SecurityException is
     * thrown when MANAGE_USB is not held.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_POWER_PROFILE_REPORTING)
    public void test_UsbApiForPowerProfileInfoRegisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_USB)) {
            // Should pass with permission
            final PowerProfileInfoListener powerProfileListener =
                    new PowerProfileInfoListener() {
                        public void onPowerProfileInfoChanged(
                                UsbPort port, UsbPortStatus portStatus) {
                            Log.d(
                                    TAG,
                                    "test_UsbApiForPowerProfileInfoRegisterSecurity listener"
                                            + " called");
                        }
                    };

            try {
                mUsbManagerSys.registerPowerProfileInfoListener(mExecutor, powerProfileListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "registerPowerProfileInfoListener failed with SecurityException when"
                                + " MANAGE_USB is held.");
            }

            try {
                mUsbManagerSys.unregisterPowerProfileInfoListener(powerProfileListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "unregisterPowerProfileInfoListener failed with SecurityException when"
                                + " MANAGE_USB is held.");
            }

            try (PermissionContext p2 = TestApis.permissions().withoutPermission(MANAGE_USB)) {
                assertThrows(
                        SecurityException.class,
                        () ->
                                mUsbManagerSys.registerPowerProfileInfoListener(
                                        mExecutor, powerProfileListener));
            }
        }
    }

    /**
     * Verify NO SecurityException when the MANAGE_USB is held, and verify that SecurityException is
     * thrown when MANAGE_USB is not held.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_POWER_PROFILE_REPORTING)
    public void test_UsbApiForPowerProfileInfoUnregisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_USB)) {
            // Should pass with permission
            final PowerProfileInfoListener powerProfileListener =
                    new PowerProfileInfoListener() {
                        public void onPowerProfileInfoChanged(
                                UsbPort port, UsbPortStatus portStatus) {
                            Log.d(
                                    TAG,
                                    "test_UsbApiForPowerProfileInfoUnregisterSecurity listener"
                                            + " called");
                        }
                    };

            try {
                mUsbManagerSys.registerPowerProfileInfoListener(mExecutor, powerProfileListener);
            } catch (SecurityException e) {
                Assert.fail(
                        "registerPowerProfileInfoListener failed with SecurityException when"
                                + " MANAGE_USB is held.");
            }

            try (PermissionContext p2 = TestApis.permissions().withoutPermission(MANAGE_USB)) {
                assertThrows(
                        SecurityException.class,
                        () ->
                                mUsbManagerSys.unregisterPowerProfileInfoListener(
                                        powerProfileListener));
            }
        }
    }
}
