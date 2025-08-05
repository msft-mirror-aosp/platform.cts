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

package android.telephony.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

/** Test the fidelity of cellular data connections. */
public class DataConnectionTest {

    static final Set<Integer> CELLULAR_CAPABILITIES;

    static {
        CELLULAR_CAPABILITIES = new ArraySet<>();
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_MMS);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_SUPL);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_DUN);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_FOTA);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_IMS);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_CBS);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_RCS);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_XCAP);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_MCX);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_VSIM);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY);
        CELLULAR_CAPABILITIES.add(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH);
    }

    private ConnectivityManager mCm;
    private PackageManager mPm;
    private final Object mLock = new Object();
    private int mSubId;

    private Set<ConnectivityManager.NetworkCallback> mCallbacks = new ArraySet<>();

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getContext();
        mCm = context.getSystemService(ConnectivityManager.class);
        mPm = context.getPackageManager();
        mSubId = SubscriptionManager.getDefaultDataSubscriptionId();

        assumeTrue(mPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
    }

    @After
    public void tearDown() {
        for (ConnectivityManager.NetworkCallback nc : mCallbacks) {
            mCm.unregisterNetworkCallback(nc);
        }
        mCallbacks.clear();

        // Itty bitty living space
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private NetworkRequest getNetworkRequestForCapability(int cap) {
        return new NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(cap)
                .setSubscriptionIds(Collections.singleton(mSubId))
                .build();
    }

    @Test
    public void testMultipleCellNetworks() throws Exception {
        Set<NetworkRequest> networkRequests = new ArraySet<>();

        // Build a list of individualized network requests, which will ensure that
        // we try to bring up the maximum number of concurrent networks that the current
        // carrier will allow.
        for (Integer cap : CELLULAR_CAPABILITIES) {
            networkRequests.add(getNetworkRequestForCapability(cap));
        }

        // Map of networks to their LinkProperties for evaluation.
        final Map<Network, LinkProperties> networks = new ArrayMap<>();
        // Signals that there are enough networks detected with link properties
        // to permit inspection for multi-network support.
        final CountDownLatch twoNetworkLatch = new CountDownLatch(1);
        // Track the networks that have been reported but are currently awaiting a link properties
        // report. Since we are getting these in parallel with other onAvailable() calls, we
        // opportunistically wait for a LinkProperties for each network that is in the process of
        // being reported. This also simplifies the rest of our code because all pairs in the map
        // are guaranteed to be complete pairs.
        final Semaphore pendingLinkProperties = new Semaphore(0);

        // Phenomenal cosmic power
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS);

        // Phase 1: File network requests for each kind of cellular network and then build
        // a snapshot of each one that is available.
        for (NetworkRequest nr : networkRequests) {
            ConnectivityManager.NetworkCallback nc =
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {
                            synchronized (mLock) {
                                // We've already moved to phase 2; ignore this network
                                if (twoNetworkLatch.getCount() == 0) return;
                                // Don't re-add existing keys.
                                if (networks.containsKey(network)) return;
                                // Add a nascent record to the map.
                                networks.put(network, null);
                                // Indicate that there a new network that needs a link properties.
                                pendingLinkProperties.release();
                            }
                        }

                        @Override
                        public void onLinkPropertiesChanged(Network n, LinkProperties lp) {
                            synchronized (mLock) {
                                if (!networks.containsKey(n)) return;
                                // No LP received for this network yet
                                if (networks.get(n) == null) {
                                    if (!pendingLinkProperties.tryAcquire()) {
                                        fail("Somehow got unexpected link properties");
                                    }
                                }
                                // Update the pending network record with a link properties.
                                networks.replace(n, lp);
                                // If we have met the criteria of having the link properties for at
                                // least two cellular networks, and no more networks are already
                                // pending, move on to phase 2.
                                if (networks.size() > 1
                                        && pendingLinkProperties.availablePermits() == 0) {
                                    twoNetworkLatch.countDown();
                                }
                            }
                        }

                        @Override
                        public void onLost(Network network) {
                            synchronized (mLock) {
                                // We've already moved to phase 2; ignore this network change.
                                if (twoNetworkLatch.getCount() == 0) return;
                                // Just in case there is a network "going down" as the test is
                                // running.
                                networks.remove(network);
                            }
                        }

                        @Override
                        public void onUnavailable() {
                            synchronized (mLock) {
                                if (pendingLinkProperties.availablePermits() > 0) {
                                    fail("Missing link properties");
                                }
                                // After waiting for the maximum time to acquire a network, if
                                // there still are not two cellular networks then we go ahead
                                // and proceed with the test.
                                twoNetworkLatch.countDown();
                            }
                        }
                    };
            mCm.requestNetwork(nr, nc, 20000 /*20 seconds*/);
            mCallbacks.add(nc);
        }
        twoNetworkLatch.await();
        // Phase 2: Go through each of the unique Networks and check their link properties
        // against other networks to ensure that they are indeed brought up as independent
        // networks.
        synchronized (mLock) {
            Set<String> interfaces = new ArraySet<>();
            for (Map.Entry<Network, LinkProperties> network : networks.entrySet()) {
                String iface = network.getValue().getInterfaceName();
                if (TextUtils.isEmpty(iface)) {
                    fail("Found a network with a no interface");
                }
                if (interfaces.contains(iface)) {
                    fail("Found a duplicate interface. Multiple network not supported.");
                }
                interfaces.add(iface);
            }
        }
    }
}
