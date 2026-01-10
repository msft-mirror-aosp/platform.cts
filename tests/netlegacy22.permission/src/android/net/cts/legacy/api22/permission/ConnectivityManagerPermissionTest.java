/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that protected android.net.ConnectivityManager methods cannot be called without
 * permissions
 */
@RunWith(AndroidJUnit4.class)
public class ConnectivityManagerPermissionTest {

    private ConnectivityManager mConnectivityManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mConnectivityManager =
               (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        assertNotNull(mConnectivityManager);
    }

    /**
     * Verify that calling {@link ConnectivityManager#requestRouteToHost(int, int)}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#CHANGE_NETWORK_STATE}.
     */
    @Test
    public void testRequestRouteToHost() {
        try {
            mConnectivityManager.requestRouteToHost(ConnectivityManager.TYPE_MOBILE, 1);
            fail("Was able to call requestRouteToHost");
        } catch (SecurityException e) {
            // expected
        }
    }
}
