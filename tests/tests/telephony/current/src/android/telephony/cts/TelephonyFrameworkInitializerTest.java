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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.TelephonyServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

/** CTS test class for verifying the functionality of TelephonyFrameworkInitializer. */
@RunWith(AndroidJUnit4.class)
public class TelephonyFrameworkInitializerTest {

    private Context mContext;
    private TelephonyServiceManager mTelephonyServiceManager;

    private static Field sTelephonyServiceManagerField;

    static {
        try {
            sTelephonyServiceManagerField =
                    TelephonyFrameworkInitializer.class.getDeclaredField(
                            "sTelephonyServiceManager");
            sTelephonyServiceManagerField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("sTelephonyServiceManager field not found", e);
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mTelephonyServiceManager =
                (TelephonyServiceManager) sTelephonyServiceManagerField.get(null);
        resetStaticState();
    }

    @After
    public void tearDown() throws Exception {
        sTelephonyServiceManagerField.set(null, mTelephonyServiceManager);
    }

    private void resetStaticState() throws Exception {
        sTelephonyServiceManagerField.set(null, null);
    }

    /** Tests that setTelephonyServiceManager and getTelephonyServiceManager work correctly. */
    @Test
    public void testSetAndGetTelephonyServiceManager() {
        assertNull(TelephonyFrameworkInitializer.getTelephonyServiceManager());

        final TelephonyServiceManager tsm = new TelephonyServiceManager();
        TelephonyFrameworkInitializer.setTelephonyServiceManager(tsm);

        assertEquals(tsm, TelephonyFrameworkInitializer.getTelephonyServiceManager());
    }

    /**
     * Verifies that core services are correctly retrieved via Context.getSystemService as a result
     * of registerServiceWrappers. This test assumes that registerServiceWrappers has already been
     * called during system boot and verifies its outcome.
     */
    @Test
    public void testServiceWrappers_areServicesRegistered() {
        Object telephonyService = mContext.getSystemService(Context.TELEPHONY_SERVICE);
        assertNotNull("TelephonyManager should be registered", telephonyService);
        assertTrue(telephonyService instanceof TelephonyManager);

        Object subscriptionService =
                mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assertNotNull("SubscriptionManager should be registered", subscriptionService);
        assertTrue(subscriptionService instanceof SubscriptionManager);
    }
}
