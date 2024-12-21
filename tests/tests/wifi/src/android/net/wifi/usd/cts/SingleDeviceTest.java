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

package android.net.wifi.usd.cts;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.cts.WifiJUnit3TestBase;
import android.net.wifi.flags.Flags;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.DiscoveryResult;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.net.wifi.usd.UsdManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SdkSuppress;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * USD CTS test suite: single device testing. Perform tests on a single device to validate USD.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
@RequiresFlagsEnabled(Flags.FLAG_USD)
public class SingleDeviceTest extends WifiJUnit3TestBase {
    private static final String USD_SERVICE_NAME = "USD_CTS_TEST";
    private static final int WAIT_FOR_USD_CALLBACK_SECS = 15;
    private UsdManager mUsdManager;
    private WifiManager mWifiManager;
    private final Object mLock = new Object();
    private AtomicBoolean mAvailability = new AtomicBoolean();
    private AtomicBoolean mIsCallbackStatus = new AtomicBoolean();
    private Consumer<Boolean> mListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        assertNotNull("Wi-Fi Manager", mWifiManager);

        // Enable Wi-Fi
        if (!mWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.setWifiEnabled(true));
        }

        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            // Check whether Usd is supported or not
            if (mWifiManager.isUsdPublisherSupported() || mWifiManager.isUsdSubscriberSupported()) {
                mUsdManager = (UsdManager) getContext().getSystemService(Context.WIFI_USD_SERVICE);
                assertNotNull("Usd Manager", mUsdManager);
            }
        }
        mAvailability.set(false);
        mIsCallbackStatus.set(false);
        mListener =
                new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean value) {
                        synchronized (mLock) {
                            mAvailability.set(value);
                            mIsCallbackStatus.set(true);
                            mLock.notify();
                        }
                    }
                };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test USD characteristics that are available.
     */
    @ApiTest(apis = {"android.net.wifi.usd.UsdManager#isUsdSupported",
            "android.net.wifi.usd.UsdManager#getCharacteristics",
            "android.net.wifi.usd.Characteristics#getMaxServiceNameLength",
            "android.net.wifi.usd.Characteristics#getMaxMatchFilterLength",
            "android.net.wifi.usd.Characteristics#getMaxNumberOfPublishSessions",
            "android.net.wifi.usd.Characteristics#getMaxNumberOfSubscribeSessions",
            "android.net.wifi.usd.Characteristics#getMaxServiceSpecificInfoLength"})
    public void testCharacteristics() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!(mWifiManager.isUsdPublisherSupported()
                    || mWifiManager.isUsdSubscriberSupported())) {
                return;
            }
            assertNotNull(mUsdManager);
            Characteristics characteristics = mUsdManager.getCharacteristics();
            assertNotNull(characteristics);
            assertEquals("Service Name Length", characteristics.getMaxServiceNameLength(), 255);
            assertEquals("Match Filter Length", characteristics.getMaxMatchFilterLength(), 255);
            assertTrue("Maximum number of Publish sessions",
                    characteristics.getMaxNumberOfPublishSessions() > 0);
            assertTrue("Maximum number of Subscribe sessions",
                    characteristics.getMaxNumberOfSubscribeSessions() > 0);
            assertTrue("Maximum Service Specific Info Length",
                    characteristics.getMaxServiceSpecificInfoLength() >= 255);
        }
    }

    private static class PublishSessionCallbackTest extends PublishSessionCallback {
        static final int ERROR = 0;
        static final int FAILED = 1;
        static final int STARTED = 2;
        static final int REPLIED = 3;
        static final int TERMINATED = 4;
        static final int RECEIVED = 5;
        static final int UNKNOWN = 255;
        private PublishSession mPublishSession;
        private int mCallbackCalled = UNKNOWN;

        private int mReasonCode = PublishSessionCallback.TERMINATION_REASON_UNKNOWN;
        private CountDownLatch mBlocker = new CountDownLatch(1);

        @Override
        public void onPublishFailed(int reason) {
            mCallbackCalled = FAILED;
            mBlocker.countDown();
        }

        @Override
        public void onPublishStarted(@NonNull PublishSession session) {
            mCallbackCalled = STARTED;
            mPublishSession = session;
            mBlocker.countDown();
        }

        @Override
        public void onPublishReplied(@NonNull DiscoveryResult discoveryResult) {
            mCallbackCalled = REPLIED;
            mBlocker.countDown();
        }

        @Override
        public void onSessionTerminated(int reason) {
            mCallbackCalled = TERMINATED;
            mReasonCode = reason;
            mBlocker.countDown();
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            mCallbackCalled = RECEIVED;
            mBlocker.countDown();
        }

        /**
         * Waits for any of the callbacks to be called - or an error (timeout, interruption).
         */
        int waitForAnyCallback() {
            try {

                boolean noTimeout = mBlocker.await(WAIT_FOR_USD_CALLBACK_SECS, TimeUnit.SECONDS);
                mBlocker = new CountDownLatch(1);
                if (noTimeout) {
                    return mCallbackCalled;
                } else {
                    return ERROR;
                }
            } catch (InterruptedException e) {
                return ERROR;
            }
        }

        public PublishSession getPublishSession() {
            return mPublishSession;
        }

        public int getReasonCode() {
            return mReasonCode;
        }

    }

    private boolean isCallbackCalled() {
        return mIsCallbackStatus.get();
    }

    private boolean isUsdAvailable() throws Exception {
        long now, deadline;
        synchronized (mLock) {
            now = System.currentTimeMillis();
            deadline = now + WAIT_FOR_USD_CALLBACK_SECS * 1000;
            while (!mAvailability.get() && now < deadline) {
                mLock.wait(deadline - now);
                now = System.currentTimeMillis();
            }
        }
        return mAvailability.get();
    }

    /** Test USD publish */
    @ApiTest(
            apis = {
                "android.net.wifi.WifiManager#isUsdPublisherSupported",
                "android.net.wifi.usd.UsdManager#registerPublisherStatusListener",
                "android.net.wifi.usd.UsdManager#unregisterPublisherStatusListener",
                "android.net.wifi.usd.UsdManager#publish",
                "android.net.wifi.usd.PublishSession#cancel",
                "android.net.wifi.usd.PublishSessionCallback#onPublishStarted",
                "android.net.wifi.usd.PublishSessionCallback#onSessionTerminated"
            })
    public void testPublish() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerPublisherStatusListener(executor, mListener);
            if (!isUsdAvailable()) {
                assertTrue("Publisher status never updated", isCallbackCalled());
                return;
            }
            // Publish
            PublishConfig publishConfig = new PublishConfig.Builder(USD_SERVICE_NAME)
                    .build();
            PublishSessionCallbackTest publishSessionCallbackTest =
                    new PublishSessionCallbackTest();
            mUsdManager.publish(publishConfig, executor, publishSessionCallbackTest);
            // Check whether publish is started or not
            assertEquals(PublishSessionCallbackTest.STARTED,
                    publishSessionCallbackTest.waitForAnyCallback());
            assertNotNull(publishSessionCallbackTest.getPublishSession());
            // Cancel
            publishSessionCallbackTest.getPublishSession().cancel();
            // Check whether publish is terminated or not
            assertEquals(PublishSessionCallbackTest.TERMINATED,
                    publishSessionCallbackTest.waitForAnyCallback());
            assertEquals(PublishSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    publishSessionCallbackTest.getReasonCode());
            mUsdManager.unregisterPublisherStatusListener(mListener);
        }
    }

    /** Test USD publish with operating frequencies */
    @ApiTest(
            apis = {
                "android.net.wifi.WifiManager#isUsdPublisherSupported",
                "android.net.wifi.usd.UsdManager#registerPublisherStatusListener",
                "android.net.wifi.usd.UsdManager#unregisterPublisherStatusListener",
                "android.net.wifi.usd.UsdManager.PublishConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager.PublishConfig.Builder#getOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager#publish",
                "android.net.wifi.usd.PublishSession#cancel",
                "android.net.wifi.usd.PublishSessionCallback#onPublishStarted",
                "android.net.wifi.usd.PublishSessionCallback#onSessionTerminated"
            })
    public void testPublishWithOperatingFrequencies() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerPublisherStatusListener(executor, mListener);
            if (!isUsdAvailable()) {
                assertTrue("Publisher status never updated", isCallbackCalled());
                return;
            }
            // Publish only on channels 1, 6 and 11
            int[] operatingFrequencies = new int[] {2412, 2437, 2462};
            PublishConfig publishConfig = new PublishConfig.Builder(USD_SERVICE_NAME)
                    .setOperatingFrequenciesMhz(operatingFrequencies)
                    .build();
            assertEquals(operatingFrequencies, publishConfig.getOperatingFrequenciesMhz());
            PublishSessionCallbackTest publishSessionCallbackTest =
                    new PublishSessionCallbackTest();
            mUsdManager.publish(publishConfig, executor, publishSessionCallbackTest);
            // Check whether publish is started or not
            assertEquals(PublishSessionCallbackTest.STARTED,
                    publishSessionCallbackTest.waitForAnyCallback());
            assertNotNull(publishSessionCallbackTest.getPublishSession());
            // Cancel
            publishSessionCallbackTest.getPublishSession().cancel();
            // Check whether publish is terminated or not
            assertEquals(PublishSessionCallbackTest.TERMINATED,
                    publishSessionCallbackTest.waitForAnyCallback());
            assertEquals(PublishSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    publishSessionCallbackTest.getReasonCode());
            mUsdManager.unregisterPublisherStatusListener(mListener);
        }
    }

    private static class SubscribeSessionCallbackTest extends SubscribeSessionCallback {
        static final int ERROR = 0;
        static final int FAILED = 1;
        static final int STARTED = 2;
        static final int DISCOVERED = 3;
        static final int TERMINATED = 4;
        static final int RECEIVED = 5;
        static final int UNKNOWN = 255;
        private SubscribeSession mSubscribeSession;
        private int mCallbackCalled = UNKNOWN;
        private CountDownLatch mBlocker = new CountDownLatch(1);

        private int mReasonCode = SubscribeSessionCallback.TERMINATION_REASON_UNKNOWN;

        @Override
        public void onSessionTerminated(int reason) {
            mCallbackCalled = TERMINATED;
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            mCallbackCalled = RECEIVED;
        }

        @Override
        public void onSubscribeFailed(int reason) {
            mCallbackCalled = FAILED;
        }

        @Override
        public void onSubscribeStarted(@NonNull SubscribeSession session) {
            mCallbackCalled = STARTED;
            mSubscribeSession = session;
        }

        @Override
        public void onServiceDiscovered(@NonNull DiscoveryResult discoveryResult) {
            mCallbackCalled = DISCOVERED;
        }

        int waitForAnyCallback() {
            try {

                boolean noTimeout = mBlocker.await(WAIT_FOR_USD_CALLBACK_SECS, TimeUnit.SECONDS);
                mBlocker = new CountDownLatch(1);
                if (noTimeout) {
                    return mCallbackCalled;
                } else {
                    return ERROR;
                }
            } catch (InterruptedException e) {
                return ERROR;
            }
        }

        public SubscribeSession getSubscribeSession() {
            return mSubscribeSession;
        }

        public int getReasonCode() {
            return mReasonCode;
        }

    }

    /** Test USD subscribe */
    @ApiTest(
            apis = {
                "android.net.wifi.WifiManager#isUsdSubscriberSupported",
                "android.net.wifi.usd.UsdManager#registerSubscriberStatusListener",
                "android.net.wifi.usd.UsdManager#unregisterSubscriberStatusListener",
                "android.net.wifi.usd.UsdManager#subscribe",
                "android.net.wifi.usd.SubscribeSession#cancel",
                "android.net.wifi.usd.SubscribeSessionCallback#onSubscribeStarted",
                "android.net.wifi.usd.SubscribeSessionCallback#onSessionTerminated"
            })
    public void testSubscribe() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerSubscriberStatusListener(executor, mListener);
            if (!isUsdAvailable()) {
                assertTrue("Subscriber status never updated", isCallbackCalled());
                return;
            }
            // Subscribe
            SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_SERVICE_NAME).build();
            SubscribeSessionCallbackTest subscribeSessionCallbackTest =
                    new SubscribeSessionCallbackTest();
            mUsdManager.subscribe(subscribeConfig, executor, subscribeSessionCallbackTest);
            // Check whether subscribe operation is started or not
            assertEquals(SubscribeSessionCallbackTest.STARTED,
                    subscribeSessionCallbackTest.waitForAnyCallback());
            assertNotNull(subscribeSessionCallbackTest.getSubscribeSession());
            // Cancel Subscribe
            subscribeSessionCallbackTest.getSubscribeSession().cancel();
            // Make sure terminate notification is generated
            assertEquals(SubscribeSessionCallbackTest.TERMINATED,
                    subscribeSessionCallbackTest.waitForAnyCallback());
            assertEquals(SubscribeSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    subscribeSessionCallbackTest.getReasonCode());
            mUsdManager.unregisterSubscriberStatusListener(mListener);
        }
    }

    /** Test USD subscribe with operating frequencies */
    @ApiTest(
            apis = {
                "android.net.wifi.WifiManager#isUsdSubscriberSupported",
                "android.net.wifi.usd.UsdManager#registerSubscriberStatusListener",
                "android.net.wifi.usd.UsdManager#unregisterSubscriberStatusListener",
                "android.net.wifi.usd.UsdManager.SubscribeConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager.SubscribeConfig.Builder#getOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager#subscribe",
                "android.net.wifi.usd.SubscribeSession#cancel",
                "android.net.wifi.usd.SubscribeSessionCallback#onSubscribeStarted",
                "android.net.wifi.usd.SubscribeSessionCallback#onSessionTerminated"
            })
    public void testSubscribeWithOperatingFrequencies() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerSubscriberStatusListener(executor, mListener);
            if (!isUsdAvailable()) {
                assertTrue("Subscriber status never updated", isCallbackCalled());
                return;
            }
            // Subscribe on channel 1, 6 or 11
            int[] operatingFrequencies = new int[] {2412, 2437, 2462};
            SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_SERVICE_NAME)
                    .setOperatingFrequenciesMhz(operatingFrequencies)
                    .build();
            assertEquals(operatingFrequencies, subscribeConfig.getOperatingFrequenciesMhz());
            SubscribeSessionCallbackTest subscribeSessionCallbackTest =
                    new SubscribeSessionCallbackTest();
            mUsdManager.subscribe(subscribeConfig, executor, subscribeSessionCallbackTest);
            // Check whether subscribe operation is started or not
            assertEquals(SubscribeSessionCallbackTest.STARTED,
                    subscribeSessionCallbackTest.waitForAnyCallback());
            assertNotNull(subscribeSessionCallbackTest.getSubscribeSession());
            // Cancel Subscribe
            subscribeSessionCallbackTest.getSubscribeSession().cancel();
            // Make sure terminate notification is generated
            assertEquals(SubscribeSessionCallbackTest.TERMINATED,
                    subscribeSessionCallbackTest.waitForAnyCallback());
            assertEquals(SubscribeSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    subscribeSessionCallbackTest.getReasonCode());
            mUsdManager.unregisterSubscriberStatusListener(mListener);
        }
    }
}
