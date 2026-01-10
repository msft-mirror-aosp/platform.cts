/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.net.cts.legacy.api22.permission;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verify ConnectivityManager related methods without specific network state permissions.
 */
@RunWith(AndroidJUnit4.class)
public class NoNetworkStatePermissionTest {
    private ConnectivityManager mConnectivityManager;
    private static final int TEST_NETWORK_TYPE = ConnectivityManager.TYPE_MOBILE;
    private static final String TEST_FEATURE = "enableHIPRI";
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        assertNotNull(mConnectivityManager);
    }

    /**
     * Verify that ConnectivityManager#startUsingNetworkFeature() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_NETWORK_STATE}.
     */
    @Test
    public void testStartUsingNetworkFeature() {
        try {
            mConnectivityManager.startUsingNetworkFeature(TEST_NETWORK_TYPE, TEST_FEATURE);
            fail("ConnectivityManager.startUsingNetworkFeature didn't throw SecurityException as"
                    + " expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that ConnectivityManager#requestRouteToHost() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_NETWORK_STATE}.
     */
    @Test
    public void testRequestRouteToHost() {
        try {
            mConnectivityManager.requestRouteToHost(TEST_NETWORK_TYPE, 0xffffffff);
            fail("ConnectivityManager.requestRouteToHost didn't throw SecurityException as"
                    + " expected");
        } catch (SecurityException e) {
            // expected
        }
    }
}
