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

package com.google.cts.interactive.usb.typec;

import static android.Manifest.permission.MANAGE_USB;
import static android.hardware.usb.UsbManager.ACTION_USB_PORT_CHANGED;

import static com.android.bedstead.harrier.components.BroadcastReceiversComponentKt.registerBroadcastReceiver;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.ParcelableUsbPort;
import android.hardware.usb.PowerProfileInfo;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbManager.Bc12TypeListener;
import android.hardware.usb.UsbManager.PowerProfileInfoListener;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.annotations.UntetheredTest;

import com.google.cts.interactive.usb.typec.steps.Bc12CdpTestInstructionsStep;
import com.google.cts.interactive.usb.typec.steps.Bc12DcpTestInstructionsStep;
import com.google.cts.interactive.usb.typec.steps.Bc12SdpTestInstructionsStep;
import com.google.cts.interactive.usb.typec.steps.DisconnectCableStep;
import com.google.cts.interactive.usb.typec.steps.IdentifyPortStep;
import com.google.cts.interactive.usb.typec.steps.PowerProfileDetectFixedInstructionStep;
import com.google.cts.interactive.usb.typec.steps.StartTypecTestListenerStep;
import com.google.cts.interactive.usb.typec.steps.TestCompleteStep;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(BedsteadJUnit4.class)
@EnsureHasPermission(MANAGE_USB)
public class CtsUsbTypecTestCases {
    private static final String TAG = CtsUsbTypecTestCases.class.getSimpleName();
    private static final int LISTENER_TIMEOUT_MS = 15000;
    private static final int LOCATEPORT_TIMEOUT_MS = 15000;

    private Context mContext;
    private Executor mExecutor;

    private UsbManager mUsbManager;
    private UiAutomation mUiAutomation;

    private static final DeviceState sDeviceState = new DeviceState();

    private static String sPortName = "";

    private static boolean sTestPassed;
    private static boolean sTestFinished;
    private static boolean sPortIdentified;

    public static boolean getTestBool() {
        return sTestPassed;
    }

    public static boolean getTestFinished() {
        return sTestFinished;
    }

    public static String getPortName() {
        return sPortName;
    }

    public static boolean getPortIdentified() {
        return sPortIdentified;
    }

    private static class TestBc12TypeListener implements Bc12TypeListener {
        private final int mTargetBc12Type;
        private final CountDownLatch mLatch;

        TestBc12TypeListener(int targetBc12Type, CountDownLatch latch) {
            mTargetBc12Type = targetBc12Type;
            mLatch = latch;
        }

        @Override
        public void onPartnerBc12TypeChanged(UsbPort port, int partnerBc12Type) {
            if (port.getId().equals(sPortName) && partnerBc12Type == mTargetBc12Type) {
                mLatch.countDown();
            }
        }
    }

    private static class TestPowerProfileDetectionListener implements PowerProfileInfoListener {
        private final int mTargetType;
        private final CountDownLatch mLatch;
        private final boolean mTestSource;

        TestPowerProfileDetectionListener(
                int targetType, CountDownLatch latch, boolean testSource) {
            mTargetType = targetType;
            mLatch = latch;
            mTestSource = testSource;
        }

        @Override
        public void onPowerProfileInfoChanged(UsbPort port, UsbPortStatus status) {
            List<PowerProfileInfo> partnerPowerProfiles =
                    mTestSource
                            ? status.getPartnerSourcePowerProfiles()
                            : status.getPartnerSinkPowerProfiles();

            for (PowerProfileInfo profile : partnerPowerProfiles) {
                if (port.getId().equals(sPortName)
                        && profile.getPowerProfileType() == mTargetType) {
                    mLatch.countDown();
                }
            }
        }
    }

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = mContext.getMainExecutor();
        mUiAutomation =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation();
        mUsbManager = InstrumentationRegistry.getContext().getSystemService(UsbManager.class);
        sTestPassed = false;
        sTestFinished = false;

        mUiAutomation.adoptShellPermissionIdentity("android.permission.MANAGE_USB");
    }

    @After
    public void tearDown() throws Exception {
        mUiAutomation.dropShellPermissionIdentity();
    }

    private void startListenerTimer(CountDownLatch latch) {
        new Thread(
                        new Runnable() {
                            public void run() {
                                try {
                                    sTestPassed =
                                            latch.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                                } catch (Exception e) {
                                    Log.e(TAG, "startListenerTimer: exception: " + e.getMessage());
                                }
                                sTestFinished = true;
                            }
                        })
                .start();
    }

    private Runnable mLocatePortRunnable =
            new Runnable() {
                public void run() {
                    while (!sPortIdentified) {
                        try (BlockingBroadcastReceiver usbPortChangedReceiver =
                                registerBroadcastReceiver(sDeviceState, ACTION_USB_PORT_CHANGED)) {
                            Intent usbPortChangedIntent =
                                    usbPortChangedReceiver.awaitForBroadcast();
                            ParcelableUsbPort parcelablePort =
                                    usbPortChangedIntent.getParcelableExtra(
                                            UsbManager.EXTRA_PORT, ParcelableUsbPort.class);
                            UsbPort port = parcelablePort.getUsbPort(mUsbManager);
                            UsbPortStatus portStatus =
                                    usbPortChangedIntent.getParcelableExtra(
                                            UsbManager.EXTRA_PORT_STATUS, UsbPortStatus.class);

                            if (port.getId().equals(sPortName) && portStatus.isConnected()) {
                                sPortIdentified = true;
                                Log.i(TAG, "locatePortRunnable: port identified");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "locatePortRunnable: exception: " + e.getMessage());
                        }
                    }
                }
            };

    private Runnable mLocatePortStepRunnable =
            new Runnable() {
                public void run() {
                    try {
                        Step.execute(IdentifyPortStep.class);
                    } catch (Exception e) {
                        Log.e(TAG, "locatePortStepRunnable: exception: " + e.getMessage());
                    }
                }
            };

    private void locatePort() {
        sPortIdentified = false;
        try {
            Step.execute(DisconnectCableStep.class);
        } catch (Exception e) {
            Log.e(TAG, "locatePort: exception: " + e.getMessage());
        }

        ExecutorService executorBroadcast = Executors.newSingleThreadExecutor();
        ExecutorService executorStep = Executors.newSingleThreadExecutor();
        Future<?> futureBroadcast = executorBroadcast.submit(mLocatePortRunnable);
        Future<?> futureStep = executorStep.submit(mLocatePortStepRunnable);

        try {
            futureStep.get(LOCATEPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.e(TAG, "locatePort: TimeoutException");
        } catch (Exception e) {
            Log.e(TAG, "locatePort: exception: " + e.getMessage());
        } finally {
            futureBroadcast.cancel(true);
            futureStep.cancel(true);
            executorBroadcast.shutdown();
            executorStep.shutdown();
        }
    }

    private void testBc12Type(int bc12Type) {
        CountDownLatch latch = new CountDownLatch(1);
        TestBc12TypeListener listener = new TestBc12TypeListener(bc12Type, latch);
        List<UsbPort> ports = mUsbManager.getPorts();
        boolean supportsBc12Reporting = false;

        for (UsbPort port : ports) {
            if (port.supportsPartnerBc12Type()) {
                supportsBc12Reporting = true;
                sPortName = port.getId();
                break;
            }
        }

        if (!supportsBc12Reporting) {
            Log.i(TAG, "No port on the device supports reporting BC 1.2");
            return;
        }

        locatePort();

        Assert.assertTrue(
                "Specified BC12 Type is invalid, val %d".formatted(bc12Type),
                (bc12Type == UsbPortStatus.BC12_TYPE_SDP)
                        || (bc12Type == UsbPortStatus.BC12_TYPE_CDP)
                        || (bc12Type == UsbPortStatus.BC12_TYPE_DCP));

        try {
            switch (bc12Type) {
                case UsbPortStatus.BC12_TYPE_SDP -> {
                    Step.execute(Bc12SdpTestInstructionsStep.class);
                }
                case UsbPortStatus.BC12_TYPE_CDP -> {
                    Step.execute(Bc12CdpTestInstructionsStep.class);
                }
                case UsbPortStatus.BC12_TYPE_DCP -> {
                    Step.execute(Bc12DcpTestInstructionsStep.class);
                }
                default -> {
                    return;
                }
            }

            Step.execute(DisconnectCableStep.class);
            mUsbManager.registerBc12TypeListener(mExecutor, listener);

            startListenerTimer(latch);

            Step.execute(StartTypecTestListenerStep.class);

            mUsbManager.unregisterBc12TypeListener(listener);

            Step.execute(TestCompleteStep.class);

            Assert.assertTrue("Listener did not identify the required device", sTestPassed);
        } catch (Exception e) {
            Log.e(TAG, "exception caught");
        }
    }

    @Test
    @Interactive
    @UntetheredTest
    @NotFullyAutomated(reason = "Requires plug and remove of a physical USB device")
    public void testBc12Sdp() throws Exception {
        testBc12Type(UsbPortStatus.BC12_TYPE_SDP);
    }

    @Test
    @Interactive
    @UntetheredTest
    @NotFullyAutomated(reason = "Requires plug and remove of a physical USB device")
    public void testBc12Cdp() throws Exception {
        testBc12Type(UsbPortStatus.BC12_TYPE_CDP);
    }

    @Test
    @Interactive
    @UntetheredTest
    @NotFullyAutomated(reason = "Requires plug and remove of a physical USB device")
    public void testBc12Dcp() throws Exception {
        testBc12Type(UsbPortStatus.BC12_TYPE_DCP);
    }

    private void testPowerProfileDetection(int powerProfileType, boolean testSource) {
        CountDownLatch latch = new CountDownLatch(1);
        TestPowerProfileDetectionListener listener =
                new TestPowerProfileDetectionListener(powerProfileType, latch, testSource);
        List<UsbPort> ports = mUsbManager.getPorts();
        boolean supportsPowerProfiles = false;

        for (UsbPort port : ports) {
            if (port.supportsPowerProfiles()) {
                supportsPowerProfiles = true;
                sPortName = port.getId();
                break;
            }
        }

        if (!supportsPowerProfiles) {
            Log.i(TAG, "No port on the device supports reporting PowerProfileInfo");
            return;
        }

        locatePort();

        try {
            switch (powerProfileType) {
                case PowerProfileInfo.POWER_PROFILE_TYPE_FIXED -> {
                    Step.execute(PowerProfileDetectFixedInstructionStep.class);
                }
                default -> {
                    return;
                }
            }

            Step.execute(DisconnectCableStep.class);
            mUsbManager.registerPowerProfileInfoListener(mExecutor, listener);

            startListenerTimer(latch);

            Step.execute(StartTypecTestListenerStep.class);

            mUsbManager.unregisterPowerProfileInfoListener(listener);

            Step.execute(TestCompleteStep.class);

            Assert.assertTrue("Listener did not identify the required device", sTestPassed);
        } catch (Exception e) {
            Log.e(TAG, "exception caught");
        }
    }

    @Test
    @Interactive
    @UntetheredTest
    @NotFullyAutomated(reason = "Requires plug and remove of a physical USB device")
    public void testPowerProfileSourceFixed() throws Exception {
        testPowerProfileDetection(PowerProfileInfo.POWER_PROFILE_TYPE_FIXED, true);
    }
}
