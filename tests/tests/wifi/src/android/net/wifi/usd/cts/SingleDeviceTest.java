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

import static android.net.wifi.usd.Config.PUBLISH_TYPE_SOLICITED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.flags.Flags;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.DiscoveryResult;
import android.net.wifi.usd.ProximityRangingInfo;
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
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * USD CTS test suite: single device testing. Perform tests on a single device to validate USD.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
@RequiresFlagsEnabled(Flags.FLAG_USD)
@RunWith(AndroidJUnit4.class)
public class SingleDeviceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String USD_SERVICE_NAME = "USD_CTS_TEST";
    private static final int WAIT_FOR_USD_CALLBACK_SECS = 15;
    private static final int TEST_TIMEOUT_SECS = 1;
    private static final int USD_SERVICE_NAME_MAX_LENGTH = 255;
    private static final int USD_MIN_SSI_LENGTH = 255;
    private UsdManager mUsdManager;
    private WifiManager mWifiManager;
    private final Object mLock = new Object();
    private static final byte[] TEST_SSI = new byte[] {1, 2, 3, 4};
    private static final int TEST_TTL_SECONDS = 3000;
    private static final int[] TEST_FREQUENCIES = new int[] {2412, 2437, 2462};
    private List<byte[]> mFilter;
    private Consumer<Boolean> mCallback;
    private static final int CALLBACK_STATUS_NOT_CALLED = 0;
    private static final int CALLBACK_STATUS_SUCCESS = 1;
    private static final int CALLBACK_STATUS_FAILURE = 2;
    private final AtomicInteger mCallbackStatus = new AtomicInteger();
    private static final int TEST_PEER_ID = 200;
    private static final String TEST_PR_DEVICE_NAME = "TEST_PR_DEVICE_NAME";
    private static final byte[] TEST_DEVICE_IDENTITY_KEY = {
        11, 22, 33, 44, 55, 66, 77, 88, 11, 22, 33, 44, 55, 66, 77, 88
    };
    private static final byte[] TEST_PEER_DEVICE_IDENTITY_KEY_1 = {
        11, 21, 31, 44, 55, 66, 77, 88, 11, 22, 33, 44, 55, 66, 77, 88
    };
    private static final byte[] TEST_PEER_DEVICE_IDENTITY_KEY_2 = {
        12, 22, 32, 44, 55, 66, 77, 88, 11, 22, 33, 44, 55, 66, 77, 88
    };

    private Consumer<Boolean> getCallbackHandler() {
        return mCallback;
    }

    private void initCallback() {
        resetCallback();
        mCallback =
                accepted -> {
                    synchronized (mLock) {
                        mCallbackStatus.set(
                                (accepted ? CALLBACK_STATUS_SUCCESS : CALLBACK_STATUS_FAILURE));
                        mLock.notify();
                    }
                };
    }

    private void resetCallback() {
        mCallbackStatus.set(CALLBACK_STATUS_NOT_CALLED);
    }

    private boolean isCallbackResultSuccess() throws Exception {
        long now, deadline;
        synchronized (mLock) {
            now = System.currentTimeMillis();
            deadline = now + WAIT_FOR_USD_CALLBACK_SECS * 1000;
            while (mCallbackStatus.get() == CALLBACK_STATUS_NOT_CALLED && now < deadline) {
                mLock.wait(deadline - now);
                now = System.currentTimeMillis();
            }
            return mCallbackStatus.get() == CALLBACK_STATUS_SUCCESS;
        }
    }

    private boolean isCallbackCalled() {
        return mCallbackStatus.get() != CALLBACK_STATUS_NOT_CALLED;
    }

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!WifiFeature.isWifiSupported(context)) {
            // skip the test if WiFi is not supported
            return;
        }
        mWifiManager = context.getSystemService(WifiManager.class);
        assertNotNull("Wi-Fi Manager", mWifiManager);

        // Enable Wi-Fi
        if (!mWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.setWifiEnabled(true));
        }

        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            // Check whether Usd is supported or not
            if (mWifiManager.isUsdPublisherSupported() || mWifiManager.isUsdSubscriberSupported()) {
                mUsdManager = context.getSystemService(UsdManager.class);
                assertNotNull("Usd Manager", mUsdManager);
            }
        }
        initCallback();
        mFilter = new ArrayList<>();
        mFilter.add(new byte[] {10, 11});
        mFilter.add(new byte[] {12, 13, 14});
    }

    @After
    public void tearDown() throws Exception {
        // No-op
    }

    /** Test USD characteristics that are available. */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.UsdManager#isUsdSupported",
                "android.net.wifi.usd.UsdManager#getCharacteristics",
                "android.net.wifi.usd.Characteristics#getMaxServiceNameLength",
                "android.net.wifi.usd.Characteristics#getMaxMatchFilterLength",
                "android.net.wifi.usd.Characteristics#getMaxNumberOfPublishSessions",
                "android.net.wifi.usd.Characteristics#getMaxNumberOfSubscribeSessions",
                "android.net.wifi.usd.Characteristics#getMaxServiceSpecificInfoLength"
            })
    @Test
    public void characteristics() {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()
                    && !mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            Characteristics characteristics = mUsdManager.getCharacteristics();
            assertNotNull(characteristics);
            assertEquals(
                    "Service Name Length",
                    USD_SERVICE_NAME_MAX_LENGTH,
                    characteristics.getMaxServiceNameLength());
            assertTrue("Match Filter Length", characteristics.getMaxMatchFilterLength() >= 0);
            if (mWifiManager.isUsdPublisherSupported()) {
                assertTrue(
                        "Maximum number of Publish sessions",
                        characteristics.getMaxNumberOfPublishSessions() > 0);
            }
            if (mWifiManager.isUsdSubscriberSupported()) {
                assertTrue(
                        "Maximum number of Subscribe sessions",
                        characteristics.getMaxNumberOfSubscribeSessions() > 0);
            }
            assertTrue(
                    "Maximum Service Specific Info Length",
                    characteristics.getMaxServiceSpecificInfoLength() >= USD_MIN_SSI_LENGTH);
        }
    }

    /** Test set and get for PublishConfig */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.PublishConfig#Builder",
                "android.net.wifi.usd.PublishConfig.Builder#setAnnouncementPeriodMillis",
                "android.net.wifi.usd.PublishConfig.Builder#setServiceProtoType",
                "android.net.wifi.usd.PublishConfig.Builder#setSolicitedTransmissionType",
                "android.net.wifi.usd.PublishConfig.Builder#setEventsEnabled",
                "android.net.wifi.usd.PublishConfig.Builder#setTtlSeconds",
                "android.net.wifi.usd.PublishConfig.Builder#setServiceSpecificInfo",
                "android.net.wifi.usd.PublishConfig.Builder#setTxMatchFilter",
                "android.net.wifi.usd.PublishConfig.Builder#setRxMatchFilter",
                "android.net.wifi.usd.PublishConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.PublishConfig.Builder#build",
                "android.net.wifi.usd.Config#getServiceName",
                "android.net.wifi.usd.PublishConfig#getAnnouncementPeriodMillis",
                "android.net.wifi.usd.Config#getServiceProtoType",
                "android.net.wifi.usd.PublishConfig#getSolicitedTransmissionType",
                "android.net.wifi.usd.PublishConfig#isEventsEnabled",
                "android.net.wifi.usd.Config#getTtlSeconds",
                "android.net.wifi.usd.Config#getServiceSpecificInfo",
                "android.net.wifi.usd.Config#getRxMatchFilter",
                "android.net.wifi.usd.Config#getTxMatchFilter",
                "android.net.wifi.usd.Config#getOperatingFrequenciesMhz"
            })
    @Test
    public void publishConfig() {
        PublishConfig publishConfig =
                new PublishConfig.Builder(USD_SERVICE_NAME)
                        .setAnnouncementPeriodMillis(200)
                        .setServiceProtoType(PublishConfig.SERVICE_PROTO_TYPE_GENERIC)
                        .setSolicitedTransmissionType(PublishConfig.TRANSMISSION_TYPE_UNICAST)
                        .setEventsEnabled(true)
                        .setTtlSeconds(TEST_TTL_SECONDS)
                        .setServiceSpecificInfo(TEST_SSI)
                        .setTxMatchFilter(mFilter)
                        .setRxMatchFilter(mFilter)
                        .setOperatingFrequenciesMhz(TEST_FREQUENCIES)
                        .build();
        assertArrayEquals(USD_SERVICE_NAME.getBytes(), publishConfig.getServiceName());
        assertEquals(200, publishConfig.getAnnouncementPeriodMillis());
        assertEquals(PublishConfig.SERVICE_PROTO_TYPE_GENERIC, publishConfig.getServiceProtoType());
        assertEquals(
                PublishConfig.TRANSMISSION_TYPE_UNICAST,
                publishConfig.getSolicitedTransmissionType());
        assertTrue(publishConfig.isEventsEnabled());
        assertEquals(TEST_TTL_SECONDS, publishConfig.getTtlSeconds());
        assertArrayEquals(TEST_SSI, publishConfig.getServiceSpecificInfo());
        assertEquals(mFilter.size(), publishConfig.getRxMatchFilter().size());
        assertEquals(mFilter.size(), publishConfig.getTxMatchFilter().size());
        for (int i = 0; i < mFilter.size(); i++) {
            assertArrayEquals(mFilter.get(i), publishConfig.getRxMatchFilter().get(i));
            assertArrayEquals(mFilter.get(i), publishConfig.getTxMatchFilter().get(i));
        }
        assertArrayEquals(TEST_FREQUENCIES, publishConfig.getOperatingFrequenciesMhz());
    }

    private static class PublishSessionCallbackTest extends PublishSessionCallback {
        static final int ERROR = 0;
        static final int ON_PUBLISH_FAILED = 1;
        static final int ON_PUBLISH_STARTED = 2;
        static final int ON_PUBLISH_REPLIED = 3;
        static final int ON_PUBLISH_TERMINATED = 4;
        static final int ON_PUBLISH_RECEIVED = 5;
        static final int UNKNOWN = 255;
        private PublishSession mPublishSession;
        private int mReasonCode = PublishSessionCallback.TERMINATION_REASON_UNKNOWN;
        private final Object mLocalLock = new Object();
        private final ArrayDeque<Integer> mCallbackQueue = new ArrayDeque<>();
        private CountDownLatch mBlocker;
        private int mCurrentWaitForCallback;

        private void processCallback(int callback) {
            synchronized (mLocalLock) {
                if (mBlocker != null && mCurrentWaitForCallback == callback) {
                    mBlocker.countDown();
                } else {
                    mCallbackQueue.addLast(callback);
                }
            }
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true on success
         * (specified callback triggered) or false on failure (timed-out or interrupted while
         * waiting for the requested callback).
         *
         * <p>Note: other callbacks happening while waiting for the specified callback will be
         * queued.
         */
        boolean waitForCallback(int callback) {
            return waitForCallback(callback, WAIT_FOR_USD_CALLBACK_SECS);
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true on success
         * (specified callback triggered) or false on failure (timed-out or interrupted while
         * waiting for the requested callback).
         *
         * <p>Same as waitForCallback(int callback) execpt that allows specifying a custom timeout.
         * The default timeout is a short value expected to be sufficient for all behaviors which
         * should happen relatively quickly. Specifying a custom timeout should only be done for
         * those cases which are known to take a specific longer period of time.
         *
         * <p>Note: other callbacks happening while waiting for the specified callback will be
         * queued.
         */
        boolean waitForCallback(int callback, int timeoutSec) {
            synchronized (mLocalLock) {
                boolean found = mCallbackQueue.remove(callback);
                if (found) {
                    return true;
                }

                mCurrentWaitForCallback = callback;
                mBlocker = new CountDownLatch(1);
            }

            try {
                return mBlocker.await(timeoutSec, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Indicates whether the specified callback (any of the ON_* constants) has already happened
         * and in the queue. Useful when the order of events is important.
         */
        boolean hasCallbackAlreadyHappened(int callback) {
            synchronized (mLocalLock) {
                return mCallbackQueue.contains(callback);
            }
        }

        @Override
        public void onPublishFailed(int reason) {
            processCallback(ON_PUBLISH_FAILED);
        }

        @Override
        public void onPublishStarted(@NonNull PublishSession session) {
            mPublishSession = session;
            processCallback(ON_PUBLISH_STARTED);
        }

        @Override
        public void onPublishReplied(@NonNull DiscoveryResult discoveryResult) {
            processCallback(ON_PUBLISH_REPLIED);
        }

        @Override
        public void onSessionTerminated(int reason) {
            mReasonCode = reason;
            processCallback(ON_PUBLISH_TERMINATED);
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            processCallback(ON_PUBLISH_RECEIVED);
        }

        public PublishSession getPublishSession() {
            return mPublishSession;
        }

        public int getReasonCode() {
            return mReasonCode;
        }
    }

    /** Test USD publish */
    @ApiTest(
            apis = {
                "android.net.wifi.WifiManager#isUsdPublisherSupported",
                "android.net.wifi.usd.UsdManager#registerPublisherStatusListener",
                "android.net.wifi.usd.UsdManager#unregisterPublisherStatusListener",
                "android.net.wifi.usd.PublisherConfig#Builder",
                "android.net.wifi.usd.PublisherConfig.Builder#setAnnouncementPeriodMillis",
                "android.net.wifi.usd.PublisherConfig.Builder#setEventsEnabled",
                "android.net.wifi.usd.PublisherConfig.Builder#setTtlSeconds",
                "android.net.wifi.usd.PublisherConfig.Builder#setServiceProtoType",
                "android.net.wifi.usd.PublisherConfig.Builder#setSolicitedTransmissionType",
                "android.net.wifi.usd.PublisherConfig.Builder#setServiceSpecificInfo",
                "android.net.wifi.usd.PublisherConfig.Builder#setTxMatchFilter",
                "android.net.wifi.usd.PublisherConfig.Builder#setRxMatchFilter",
                "android.net.wifi.usd.UsdManager#publish",
                "android.net.wifi.usd.PublishSession#cancel",
                "android.net.wifi.usd.PublishSessionCallback#onPublishStarted",
                "android.net.wifi.usd.PublishSessionCallback#onPublishReplied",
                "android.net.wifi.usd.SessionCallback#onSessionTerminated",
                "android.net.wifi.usd.SessionCallback#onMessageReceived",
                "android.net.wifi.usd.PublishSession#sendMessage",
                "android.net.wifi.usd.PublishSession#updatePublish",
            })
    @Test
    public void publish() throws Exception {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerPublisherStatusListener(executor, getCallbackHandler());
            // Make sure USD publisher is available.
            assertTrue("Publisher is not available", isCallbackResultSuccess());
            mUsdManager.unregisterPublisherStatusListener(getCallbackHandler());
            Characteristics characteristics = mUsdManager.getCharacteristics();
            assertNotNull(characteristics);
            assertTrue(USD_SERVICE_NAME.length() <= characteristics.getMaxServiceNameLength());
            // Set publish configuration.
            PublishConfig.Builder builder =
                    new PublishConfig.Builder(USD_SERVICE_NAME)
                            .setAnnouncementPeriodMillis(200)
                            .setEventsEnabled(true)
                            .setTtlSeconds(TEST_TTL_SECONDS)
                            .setServiceProtoType(PublishConfig.SERVICE_PROTO_TYPE_CSA_MATTER)
                            .setSolicitedTransmissionType(PublishConfig.TRANSMISSION_TYPE_UNICAST);
            if (TEST_SSI.length <= characteristics.getMaxServiceSpecificInfoLength()) {
                builder.setServiceSpecificInfo(TEST_SSI);
            }
            if (mFilter.size() <= characteristics.getMaxMatchFilterLength()) {
                builder.setTxMatchFilter(mFilter);
                builder.setRxMatchFilter(mFilter);
            }
            PublishConfig publishConfig = builder.build();
            PublishSessionCallbackTest publishSessionCallbackTest =
                    new PublishSessionCallbackTest();
            // Publish.
            mUsdManager.publish(publishConfig, executor, publishSessionCallbackTest);
            // Check whether publish is started or not.
            assertTrue(
                    "Publish started",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_STARTED));
            assertNotNull(publishSessionCallbackTest.getPublishSession());
            // Make sure no more callbacks are called for failure, replied or message received.
            assertFalse(
                    "Publish failed",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_FAILED, TEST_TIMEOUT_SECS));
            assertFalse(
                    "Publish replied",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_REPLIED, TEST_TIMEOUT_SECS));
            assertFalse(
                    "Message received",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_RECEIVED, TEST_TIMEOUT_SECS));
            // Make sure session is not terminated.
            assertFalse(
                    "Publish terminated",
                    publishSessionCallbackTest.hasCallbackAlreadyHappened(
                            SubscribeSessionCallbackTest.ON_SESSION_TERMINATED));
            PublishSession session = publishSessionCallbackTest.getPublishSession();
            assertNotNull(session);
            // Send a message; this is expected to fail.
            resetCallback();
            session.sendMessage(1, "some message".getBytes(), executor, getCallbackHandler());
            assertFalse("SendMessage cannot succeed", isCallbackResultSuccess());
            assertTrue("Callback was never called", isCallbackCalled());
            // Update publish.
            session.updatePublish(new byte[] {1, 2, 3});
            // Cancel Publish.
            publishSessionCallbackTest.getPublishSession().cancel();
            // Check whether publish is terminated or not.
            assertTrue(
                    "Publish terminated",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_TERMINATED));
            assertEquals(
                    PublishSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    publishSessionCallbackTest.getReasonCode());
        }
    }

    /** Test USD publish with operating frequencies */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.UsdManager.PublishConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager.PublishConfig.Builder#getOperatingFrequenciesMhz",
            })
    @Test
    public void publishWithOperatingFrequencies() throws Exception {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerPublisherStatusListener(executor, getCallbackHandler());
            // Make sure publisher is available.
            assertTrue("Publisher is not available", isCallbackResultSuccess());
            mUsdManager.unregisterPublisherStatusListener(getCallbackHandler());
            // Publish only on channels 1, 6 and 11.
            int[] operatingFrequencies = new int[] {2412, 2437, 2462};
            PublishConfig publishConfig =
                    new PublishConfig.Builder(USD_SERVICE_NAME)
                            .setOperatingFrequenciesMhz(operatingFrequencies)
                            .build();
            assertArrayEquals(operatingFrequencies, publishConfig.getOperatingFrequenciesMhz());
            PublishSessionCallbackTest publishSessionCallbackTest =
                    new PublishSessionCallbackTest();
            mUsdManager.publish(publishConfig, executor, publishSessionCallbackTest);
            // Check whether publish is started or not.
            assertTrue(
                    "Publish started",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_STARTED));
            assertNotNull(publishSessionCallbackTest.getPublishSession());
            // Cancel.
            publishSessionCallbackTest.getPublishSession().cancel();
            // Check whether publish is terminated or not.
            assertTrue(
                    "Publish terminated",
                    publishSessionCallbackTest.waitForCallback(
                            PublishSessionCallbackTest.ON_PUBLISH_TERMINATED));
            assertEquals(
                    PublishSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    publishSessionCallbackTest.getReasonCode());
        }
    }

    /** Test set and get for SubscribeConfig */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.SubscriberConfig#Builder",
                "android.net.wifi.usd.SubscriberConfig.Builder#setQueryPeriodMillis",
                "android.net.wifi.usd.SubscriberConfig.Builder#setSubscribeType",
                "android.net.wifi.usd.SubscriberConfig.Builder#setServiceProtoType",
                "android.net.wifi.usd.SubscriberConfig.Builder#setTtlSeconds",
                "android.net.wifi.usd.SubscriberConfig.Builder#setRecommendedOperatingFrequenciesMhz",
                "android.net.wifi.usd.SubscriberConfig.Builder#setServiceSpecificInfo",
                "android.net.wifi.usd.SubscriberConfig.Builder#setTxMatchFilter",
                "android.net.wifi.usd.SubscriberConfig.Builder#setRxMatchFilter",
                "android.net.wifi.usd.SubscriberConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.SubscriberConfig.Builder#build",
                "android.net.wifi.usd.SubscriberConfig#getServiceName",
                "android.net.wifi.usd.SubscriberConfig#getQueryPeriodMillis",
                "android.net.wifi.usd.SubscriberConfig#getSubscribeType",
                "android.net.wifi.usd.SubscriberConfig#getServiceProtoType",
                "android.net.wifi.usd.SubscriberConfig#getTtlSeconds",
                "android.net.wifi.usd.SubscriberConfig#getRecommendedOperatingFrequenciesMhz",
                "android.net.wifi.usd.SubscriberConfig#getServiceSpecificInfo",
                "android.net.wifi.usd.SubscriberConfig#getRxMatchFilter",
                "android.net.wifi.usd.SubscriberConfig#getTxMatchFilter",
                "android.net.wifi.usd.SubscriberConfig#getOperatingFrequenciesMhz"
            })
    @Test
    public void subscribeConfig() {
        SubscribeConfig subscribeConfig =
                new SubscribeConfig.Builder(USD_SERVICE_NAME)
                        .setQueryPeriodMillis(200)
                        .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                        .setServiceProtoType(SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC)
                        .setTtlSeconds(TEST_TTL_SECONDS)
                        .setRecommendedOperatingFrequenciesMhz(TEST_FREQUENCIES)
                        .setServiceSpecificInfo(TEST_SSI)
                        .setTxMatchFilter(mFilter)
                        .setRxMatchFilter(mFilter)
                        .setOperatingFrequenciesMhz(TEST_FREQUENCIES)
                        .build();
        assertArrayEquals(USD_SERVICE_NAME.getBytes(), subscribeConfig.getServiceName());
        assertEquals(200, subscribeConfig.getQueryPeriodMillis());
        assertEquals(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE, subscribeConfig.getSubscribeType());
        assertEquals(
                SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC, subscribeConfig.getServiceProtoType());
        assertEquals(TEST_TTL_SECONDS, subscribeConfig.getTtlSeconds());
        assertArrayEquals(
                TEST_FREQUENCIES, subscribeConfig.getRecommendedOperatingFrequenciesMhz());
        assertArrayEquals(TEST_SSI, subscribeConfig.getServiceSpecificInfo());
        assertEquals(mFilter.size(), subscribeConfig.getRxMatchFilter().size());
        assertEquals(mFilter.size(), subscribeConfig.getTxMatchFilter().size());
        for (int i = 0; i < mFilter.size(); i++) {
            assertArrayEquals(mFilter.get(i), subscribeConfig.getRxMatchFilter().get(i));
            assertArrayEquals(mFilter.get(i), subscribeConfig.getTxMatchFilter().get(i));
        }
        assertArrayEquals(TEST_FREQUENCIES, subscribeConfig.getOperatingFrequenciesMhz());
    }

    private static class SubscribeSessionCallbackTest extends SubscribeSessionCallback {
        static final int ERROR = 0;
        static final int ON_SUBSCRIBE_FAILED = 1;
        static final int ON_SUBSCRIBE_STARTED = 2;
        static final int ON_SERVICE_DISCOVERED = 3;
        static final int ON_SESSION_TERMINATED = 4;
        static final int ON_MESSAGE_RECEIVED = 5;
        static final int UNKNOWN = 255;
        private SubscribeSession mSubscribeSession;
        private int mReasonCode = SubscribeSessionCallback.TERMINATION_REASON_UNKNOWN;
        private final Object mLocalLock = new Object();
        private final ArrayDeque<Integer> mCallbackQueue = new ArrayDeque<>();
        private CountDownLatch mBlocker;
        private int mCurrentWaitForCallback;

        private void processCallback(int callback) {
            synchronized (mLocalLock) {
                if (mBlocker != null && mCurrentWaitForCallback == callback) {
                    mBlocker.countDown();
                } else {
                    mCallbackQueue.addLast(callback);
                }
            }
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true on success
         * (specified callback triggered) or false on failure (timed-out or interrupted while
         * waiting for the requested callback).
         *
         * <p>Note: other callbacks happening while waiting for the specified callback will be
         * queued.
         */
        boolean waitForCallback(int callback) {
            return waitForCallback(callback, WAIT_FOR_USD_CALLBACK_SECS);
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true on success
         * (specified callback triggered) or false on failure (timed-out or interrupted while
         * waiting for the requested callback).
         *
         * <p>Same as waitForCallback(int callback) execpt that allows specifying a custom timeout.
         * The default timeout is a short value expected to be sufficient for all behaviors which
         * should happen relatively quickly. Specifying a custom timeout should only be done for
         * those cases which are known to take a specific longer period of time.
         *
         * <p>Note: other callbacks happening while waiting for the specified callback will be
         * queued.
         */
        boolean waitForCallback(int callback, int timeoutSec) {
            synchronized (mLocalLock) {
                boolean found = mCallbackQueue.remove(callback);
                if (found) {
                    return true;
                }

                mCurrentWaitForCallback = callback;
                mBlocker = new CountDownLatch(1);
            }

            try {
                return mBlocker.await(timeoutSec, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Indicates whether the specified callback (any of the ON_* constants) has already happened
         * and in the queue. Useful when the order of events is important.
         */
        boolean hasCallbackAlreadyHappened(int callback) {
            synchronized (mLocalLock) {
                return mCallbackQueue.contains(callback);
            }
        }

        @Override
        public void onSessionTerminated(int reason) {
            mReasonCode = reason;
            processCallback(ON_SESSION_TERMINATED);
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            processCallback(ON_MESSAGE_RECEIVED);
        }

        @Override
        public void onSubscribeFailed(int reason) {
            processCallback(ON_SUBSCRIBE_FAILED);
        }

        @Override
        public void onSubscribeStarted(@NonNull SubscribeSession session) {
            mSubscribeSession = session;
            processCallback(ON_SUBSCRIBE_STARTED);
        }

        @Override
        public void onServiceDiscovered(@NonNull DiscoveryResult discoveryResult) {
            processCallback(ON_SERVICE_DISCOVERED);
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
                "android.net.wifi.usd.SubscriberConfig#Builder",
                "android.net.wifi.usd.SubscriberConfig.Builder#setQueryPeriodMillis",
                "android.net.wifi.usd.SubscriberConfig.Builder#setSubscribeType",
                "android.net.wifi.usd.SubscriberConfig.Builder#setServiceProtoType",
                "android.net.wifi.usd.SubscriberConfig.Builder#setTtlSeconds",
                "android.net.wifi.usd.SubscriberConfig.Builder#setRecommendedOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager#subscribe",
                "android.net.wifi.usd.SubscribeSession#cancel",
                "android.net.wifi.usd.SubscribeSessionCallback#onSubscribeStarted",
                "android.net.wifi.usd.SubscribeSessionCallback#onSubscribeFailed",
                "android.net.wifi.usd.SubscribeSessionCallback#onServiceDiscovered",
                "android.net.wifi.usd.SessionCallback#onMessageReceived",
                "android.net.wifi.usd.SessionCallback#onSessionTerminated"
            })
    @Test
    public void subscribe() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerSubscriberStatusListener(executor, getCallbackHandler());
            // Make sure USD subscriber is available.
            assertTrue("Subscriber is not available", isCallbackResultSuccess());
            mUsdManager.unregisterSubscriberStatusListener(getCallbackHandler());
            Characteristics characteristics = mUsdManager.getCharacteristics();
            assertNotNull(characteristics);
            assertTrue(USD_SERVICE_NAME.length() <= characteristics.getMaxServiceNameLength());
            // Set subscribe configuration.
            SubscribeConfig.Builder builder =
                    new SubscribeConfig.Builder(USD_SERVICE_NAME)
                            .setQueryPeriodMillis(200)
                            .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                            .setServiceProtoType(SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC)
                            .setTtlSeconds(TEST_TTL_SECONDS)
                            .setRecommendedOperatingFrequenciesMhz(TEST_FREQUENCIES);
            if (TEST_SSI.length <= characteristics.getMaxServiceSpecificInfoLength()) {
                builder.setServiceSpecificInfo(TEST_SSI);
            }
            if (mFilter.size() <= characteristics.getMaxMatchFilterLength()) {
                builder.setTxMatchFilter(mFilter);
                builder.setRxMatchFilter(mFilter);
            }
            // Subscribe.
            SubscribeConfig subscribeConfig = builder.build();
            SubscribeSessionCallbackTest subscribeSessionCallbackTest =
                    new SubscribeSessionCallbackTest();
            mUsdManager.subscribe(subscribeConfig, executor, subscribeSessionCallbackTest);
            // Check whether subscribe operation is started or not.
            assertTrue(
                    "Subscribe started",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SUBSCRIBE_STARTED));
            assertNotNull(subscribeSessionCallbackTest.getSubscribeSession());
            // Make sure no more callbacks are called for failure, discovery or message received.
            assertFalse(
                    "Subscribe failed",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SUBSCRIBE_FAILED, TEST_TIMEOUT_SECS));
            assertFalse(
                    "Service Discovered",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SERVICE_DISCOVERED, TEST_TIMEOUT_SECS));
            assertFalse(
                    "Message received",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_MESSAGE_RECEIVED, TEST_TIMEOUT_SECS));
            // Make sure session is not terminated.
            assertFalse(
                    "Subscribe terminated",
                    subscribeSessionCallbackTest.hasCallbackAlreadyHappened(
                            SubscribeSessionCallbackTest.ON_SESSION_TERMINATED));
            SubscribeSession session = subscribeSessionCallbackTest.getSubscribeSession();
            assertNotNull("Session is null", session);
            // Send a message; this is expected to fail in case of single device test.
            resetCallback();
            session.sendMessage(1, "some message".getBytes(), executor, getCallbackHandler());
            assertFalse("SendMessage cannot succeed", isCallbackResultSuccess());
            assertTrue("Callback was never called", isCallbackCalled());
            // Cancel Subscribe.
            session.cancel();
            // Make sure terminate notification is generated.
            assertTrue(
                    "Subscribe session terminated",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SESSION_TERMINATED));
            assertEquals(SubscribeSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    subscribeSessionCallbackTest.getReasonCode());
        }
    }

    /** Test USD subscribe with operating frequencies */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.UsdManager.SubscribeConfig.Builder#setOperatingFrequenciesMhz",
                "android.net.wifi.usd.UsdManager.SubscribeConfig.Builder#getOperatingFrequenciesMhz",
            })
    @Test
    public void subscribeWithOperatingFrequencies() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            mUsdManager.registerSubscriberStatusListener(executor, getCallbackHandler());
            // Make sure subscriber is available.
            assertTrue("Subscriber is not available", isCallbackResultSuccess());
            mUsdManager.unregisterSubscriberStatusListener(getCallbackHandler());
            SubscribeConfig subscribeConfig =
                    new SubscribeConfig.Builder(USD_SERVICE_NAME)
                            .setOperatingFrequenciesMhz(TEST_FREQUENCIES)
                            .build();
            assertArrayEquals(TEST_FREQUENCIES, subscribeConfig.getOperatingFrequenciesMhz());
            SubscribeSessionCallbackTest subscribeSessionCallbackTest =
                    new SubscribeSessionCallbackTest();
            mUsdManager.subscribe(subscribeConfig, executor, subscribeSessionCallbackTest);
            // Check whether subscribe operation is started or not.
            assertTrue(
                    "Subscribe started",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SUBSCRIBE_STARTED));
            assertNotNull(subscribeSessionCallbackTest.getSubscribeSession());
            // Cancel Subscribe.
            subscribeSessionCallbackTest.getSubscribeSession().cancel();
            // Make sure terminate notification is generated.
            assertTrue(
                    "Subscribe session terminated",
                    subscribeSessionCallbackTest.waitForCallback(
                            SubscribeSessionCallbackTest.ON_SESSION_TERMINATED));
            assertEquals(SubscribeSessionCallback.TERMINATION_REASON_USER_INITIATED,
                    subscribeSessionCallbackTest.getReasonCode());
        }
    }

    /** Test set and get of DiscoveryResult */
    @ApiTest(
            apis = {
                "android.net.wifi.usd.DiscoveryResult#Builder",
                "android.net.wifi.usd.DiscoveryResult.Builder#setFsdEnabled",
                "android.net.wifi.usd.DiscoveryResult.Builder#setServiceProtoType",
                "android.net.wifi.usd.DiscoveryResult.Builder#setServiceSpecificInfo",
                "android.net.wifi.usd.DiscoveryResult.Builder#build",
                "android.net.wifi.usd.DiscoveryResult#getPeerId",
                "android.net.wifi.usd.DiscoveryResult#isFsdEnabled",
                "android.net.wifi.usd.DiscoveryResult#getServiceProtoType",
                "android.net.wifi.usd.DiscoveryResult#getServiceSpecificInfo",
            })
    @Test
    public void discoveryResult() {
        DiscoveryResult discoveryResult =
                new DiscoveryResult.Builder(TEST_PEER_ID)
                        .setFsdEnabled(true)
                        .setServiceProtoType(SubscribeConfig.SERVICE_PROTO_TYPE_CSA_MATTER)
                        .setServiceSpecificInfo(TEST_SSI)
                        .build();
        assertEquals(TEST_PEER_ID, discoveryResult.getPeerId());
        assertTrue(discoveryResult.isFsdEnabled());
        assertEquals(
                SubscribeConfig.SERVICE_PROTO_TYPE_CSA_MATTER,
                discoveryResult.getServiceProtoType());
        assertArrayEquals(TEST_SSI, discoveryResult.getServiceSpecificInfo());
    }

    /** Test USD characteristics for Proximity Detection support */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.wifi.flags.Flags.FLAG_PROXIMITY_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.usd"
                        + ".Characteristics#isFindingProximityDetectionDevicesSupported"
            })
    @Test
    public void testIsFindingProximityDetectionDevicesSupported() {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.MANAGE_WIFI_NETWORK_SELECTION)) {
            if (!mWifiManager.isUsdPublisherSupported()
                    && !mWifiManager.isUsdSubscriberSupported()) {
                return;
            }
            assertNotNull(mUsdManager);
            Characteristics characteristics = mUsdManager.getCharacteristics();
            assertNotNull(characteristics);
            characteristics.isFindingProximityDetectionDevicesSupported();
        }
    }

    /** Test set and get for Subscribe config with proximity ranging enabled */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.wifi.flags.Flags.FLAG_PROXIMITY_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.usd.SubscribeConfig.Builder#setProximityRangingEnabled",
                "android.net.wifi.usd.SubscribeConfig.Builder#setSelfDeviceIdentityKey",
                "android.net.wifi.usd.SubscribeConfig.Builder#setPeerDeviceIdentityKeys",
                "android.net.wifi.usd.Config#isProximityRangingEnabled",
                "android.net.wifi.usd.Config#getSelfDeviceIdentityKey",
                "android.net.wifi.usd.Config#getPeerDeviceIdentityKeyList",
            })
    @Test
    public void testSubscribeConfigWithProximityRangingEnabled() {
        SubscribeConfig subscribeConfig =
                new SubscribeConfig.Builder(USD_SERVICE_NAME)
                        .setProximityRangingEnabled(true)
                        .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                        .setSelfDeviceIdentityKey(TEST_DEVICE_IDENTITY_KEY)
                        .setPeerDeviceIdentityKeys(
                                List.of(
                                        TEST_PEER_DEVICE_IDENTITY_KEY_1,
                                        TEST_PEER_DEVICE_IDENTITY_KEY_2))
                        .build();
        assertArrayEquals(USD_SERVICE_NAME.getBytes(), subscribeConfig.getServiceName());
        assertTrue(subscribeConfig.isProximityRangingEnabled());
        assertArrayEquals(TEST_DEVICE_IDENTITY_KEY, subscribeConfig.getSelfDeviceIdentityKey());
        assertEquals(2, subscribeConfig.getPeerDeviceIdentityKeys().size());
        assertArrayEquals(
                TEST_PEER_DEVICE_IDENTITY_KEY_1,
                subscribeConfig.getPeerDeviceIdentityKeys().get(0));
        assertArrayEquals(
                TEST_PEER_DEVICE_IDENTITY_KEY_2,
                subscribeConfig.getPeerDeviceIdentityKeys().get(1));
    }

    /** Test set and get for Publish config with proximity ranging enabled */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.wifi.flags.Flags.FLAG_PROXIMITY_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.usd.PublishConfig.Builder#setProximityRangingEnabled",
                "android.net.wifi.usd.PublishConfig.Builder#setSelfDeviceIdentityKey",
                "android.net.wifi.usd.PublishConfig.Builder#setPeerDeviceIdentityKeys",
                "android.net.wifi.usd.PublishConfig.Builder#setPublishType",
                "android.net.wifi.usd.Config#isProximityRangingEnabled",
                "android.net.wifi.usd.Config#getPublishType",
                "android.net.wifi.usd.Config#getSelfDeviceIdentityKey",
                "android.net.wifi.usd.Config#getPeerDeviceIdentityKeyList",
            })
    @Test
    public void testPublishConfigWithProximityRangingEnabled() {
        PublishConfig publishConfig =
                new PublishConfig.Builder(USD_SERVICE_NAME)
                        .setProximityRangingEnabled(true)
                        .setPublishType(PUBLISH_TYPE_SOLICITED)
                        .setSelfDeviceIdentityKey(TEST_DEVICE_IDENTITY_KEY)
                        .setPeerDeviceIdentityKeys(
                                List.of(
                                        TEST_PEER_DEVICE_IDENTITY_KEY_1,
                                        TEST_PEER_DEVICE_IDENTITY_KEY_2))
                        .build();
        assertArrayEquals(USD_SERVICE_NAME.getBytes(), publishConfig.getServiceName());
        assertTrue(publishConfig.isProximityRangingEnabled());
        assertEquals(PUBLISH_TYPE_SOLICITED, publishConfig.getPublishType());
        assertArrayEquals(TEST_DEVICE_IDENTITY_KEY, publishConfig.getSelfDeviceIdentityKey());
        assertEquals(2, publishConfig.getPeerDeviceIdentityKeys().size());
        assertArrayEquals(
                TEST_PEER_DEVICE_IDENTITY_KEY_1, publishConfig.getPeerDeviceIdentityKeys().get(0));
        assertArrayEquals(
                TEST_PEER_DEVICE_IDENTITY_KEY_2, publishConfig.getPeerDeviceIdentityKeys().get(1));
    }

    /** Test discovery result with proximity ranging information */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.wifi.flags.Flags.FLAG_PROXIMITY_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.usd.DiscoveryResult.Builder#setProximityRangingInfo",
                "android.net.wifi.usd.DiscoveryResult.Builder#setDeviceIdentityKey",
                "android.net.wifi.usd.DiscoveryResult#getProximityRangingInfo",
                "android.net.wifi.usd.DiscoveryResult#getDeviceIdentityKey",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setDeviceName",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#set80211mcBasedRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#setNtbNonSecureLtfRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setNtbSecureLtfRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#setUnauthenticatedPasnModeSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#setAuthenticatedPasnModeSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#set80211mcBasedIstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#set80211mcBasedRstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setNtbIstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setNtbRstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#setMaxSupportedPacketWidth80211mcBased",
                "android.net.wifi.usd.ProximityRangingInfo.Builder"
                        + "#setMaxSupportedPreamble80211mcBased",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setMaxSupportedPacketWidthNtb",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#setMaxSupportedPreambleNtb",
                "android.net.wifi.usd.ProximityRangingInfo.Builder#set6GHzSupported",
                "android.net.wifi.usd.ProximityRangingInfo#getDeviceName",
                "android.net.wifi.usd.ProximityRangingInfo#is80211mcBasedRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isNtbNonSecureLtfRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isNtbSecureLtfRangingSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isUnauthenticatedPasnModeSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isAuthenticatedPasnModeSupported",
                "android.net.wifi.usd.ProximityRangingInfo#is80211mcBasedIstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo#is80211mcBasedRstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isNtbIstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo#isNtbRstaRoleSupported",
                "android.net.wifi.usd.ProximityRangingInfo#getMaxSupportedPacketWidth80211mcBased",
                "android.net.wifi.usd.ProximityRangingInfo#getMaxSupportedPreamble80211mcBased",
                "android.net.wifi.usd.ProximityRangingInfo#getMaxSupportedPacketWidthNtb",
                "android.net.wifi.usd.ProximityRangingInfo#getMaxSupportedPreambleNtb",
                "android.net.wifi.usd.ProximityRangingInfo#is6GHzSupported"
            })
    @Test
    public void testDiscoveryResultWithProximityRangingInfo() {
        ProximityRangingInfo rangingInfo =
                new ProximityRangingInfo.Builder()
                        .setDeviceName(TEST_PR_DEVICE_NAME)
                        .set80211mcBasedRangingSupported(true)
                        .setNtbNonSecureLtfRangingSupported(true)
                        .setNtbSecureLtfRangingSupported(false)
                        .setUnauthenticatedPasnModeSupported(true)
                        .setAuthenticatedPasnModeSupported(true)
                        .set80211mcBasedIstaRoleSupported(true)
                        .set80211mcBasedRstaRoleSupported(false)
                        .setNtbIstaRoleSupported(true)
                        .setNtbRstaRoleSupported(false)
                        .setMaxSupportedPacketWidth80211mcBased(ScanResult.CHANNEL_WIDTH_80MHZ)
                        .setMaxSupportedPreamble80211mcBased(ScanResult.PREAMBLE_VHT)
                        .setMaxSupportedPacketWidthNtb(ScanResult.CHANNEL_WIDTH_80MHZ)
                        .setMaxSupportedPreambleNtb(ScanResult.PREAMBLE_HE)
                        .set6GHzSupported(false)
                        .build();
        DiscoveryResult discoveryResult =
                new DiscoveryResult.Builder(TEST_PEER_ID)
                        .setFsdEnabled(true)
                        .setServiceProtoType(SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC)
                        .setServiceSpecificInfo(TEST_SSI)
                        .setProximityRangingInfo(rangingInfo)
                        .setDeviceIdentityKey(TEST_PEER_DEVICE_IDENTITY_KEY_1)
                        .build();
        assertEquals(TEST_PEER_ID, discoveryResult.getPeerId());
        assertTrue(discoveryResult.isFsdEnabled());
        assertEquals(
                SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC, discoveryResult.getServiceProtoType());
        assertArrayEquals(TEST_SSI, discoveryResult.getServiceSpecificInfo());
        assertArrayEquals(TEST_PEER_DEVICE_IDENTITY_KEY_1, discoveryResult.getDeviceIdentityKey());
        ProximityRangingInfo retrievedRangingInfo = discoveryResult.getProximityRangingInfo();
        assertNotNull(retrievedRangingInfo);
        assertEquals(TEST_PR_DEVICE_NAME, retrievedRangingInfo.getDeviceName());
        assertTrue(retrievedRangingInfo.is80211mcBasedRangingSupported());
        assertTrue(retrievedRangingInfo.isNtbNonSecureLtfRangingSupported());
        assertFalse(retrievedRangingInfo.isNtbSecureLtfRangingSupported());
        assertTrue(retrievedRangingInfo.isUnauthenticatedPasnModeSupported());
        assertTrue(retrievedRangingInfo.isAuthenticatedPasnModeSupported());
        assertTrue(retrievedRangingInfo.is80211mcBasedIstaRoleSupported());
        assertFalse(retrievedRangingInfo.is80211mcBasedRstaRoleSupported());
        assertTrue(retrievedRangingInfo.isNtbIstaRoleSupported());
        assertFalse(retrievedRangingInfo.isNtbRstaRoleSupported());
        assertEquals(
                ScanResult.CHANNEL_WIDTH_80MHZ,
                retrievedRangingInfo.getMaxSupportedPacketWidth80211mcBased());
        assertEquals(
                ScanResult.PREAMBLE_VHT,
                retrievedRangingInfo.getMaxSupportedPreamble80211mcBased());
        assertEquals(
                ScanResult.CHANNEL_WIDTH_80MHZ,
                retrievedRangingInfo.getMaxSupportedPacketWidthNtb());
        assertEquals(ScanResult.PREAMBLE_HE, retrievedRangingInfo.getMaxSupportedPreambleNtb());
        assertFalse(retrievedRangingInfo.is6GHzSupported());
    }
}
