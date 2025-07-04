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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.os.Binder;
import android.os.IBinder;
import android.os.TelephonyServiceManager;
import android.os.TelephonyServiceManager.ServiceRegisterer;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** CTS test class for verifying the functionality of TelephonyServiceManager. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHONE_NUMBER_PARSING_API)
public class TelephonyServiceManagerTest {

    private static final String NAMESPACE_TELEPHONY = "telephony";
    private static final String FLAG_ENABLE_PHONE_NUMBER_PARSING_API =
            "enable_phone_number_parsing_api";

    /** Tests that we can get an instance of TelephonyServiceManager and call its methods. */
    private TelephonyServiceManager mTelephonyServiceManager;

    @Before
    public void setUp() {
        mTelephonyServiceManager = new TelephonyServiceManager();
    }

    /** Verify getPhoneNumberServiceRegisterer method call. */
    @Test
    public void testGetPhoneNumberServiceRegisterer() {
        ServiceRegisterer registerer = mTelephonyServiceManager.getPhoneNumberServiceRegisterer();
        assertNotNull(
                "getPhoneNumberServiceRegisterer should return a non-null object", registerer);

        final IBinder mockBinder = new Binder();
        assertThrows(
                SecurityException.class,
                () -> {
                    registerer.publishBinderService(mockBinder);
                });

        IBinder binder = registerer.get();
        assertNotNull("Retrieved binder should not be null when feature is enabled.", binder);
    }
}
