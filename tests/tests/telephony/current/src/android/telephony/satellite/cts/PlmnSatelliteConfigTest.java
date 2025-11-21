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
package android.telephony.satellite.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import android.os.Parcel;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.satellite.PlmnSatelliteConfig;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

public class PlmnSatelliteConfigTest {
    @Test
    public void testForConstructorsAndGetters() {
        Set<Integer> supportedServices = new HashSet<>();
        supportedServices.add(NetworkRegistrationInfo.SERVICE_TYPE_SMS);
        supportedServices.add(NetworkRegistrationInfo.SERVICE_TYPE_DATA);
        PlmnSatelliteConfig config = new PlmnSatelliteConfig(supportedServices);
        assertEquals(supportedServices, config.getSupportedServices());
    }

    @Test
    public void testEquals() {
        Set<Integer> supportedServices1 = new HashSet<>();
        supportedServices1.add(NetworkRegistrationInfo.SERVICE_TYPE_SMS);
        PlmnSatelliteConfig config1 = new PlmnSatelliteConfig(supportedServices1);
        Set<Integer> supportedServices2 = new HashSet<>();
        supportedServices2.add(NetworkRegistrationInfo.SERVICE_TYPE_SMS);
        PlmnSatelliteConfig config2 = new PlmnSatelliteConfig(supportedServices2);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    public void testNotEquals() {
        Set<Integer> supportedServices1 = new HashSet<>();
        supportedServices1.add(NetworkRegistrationInfo.SERVICE_TYPE_SMS);
        PlmnSatelliteConfig config1 = new PlmnSatelliteConfig(supportedServices1);
        Set<Integer> supportedServices2 = new HashSet<>();
        supportedServices2.add(NetworkRegistrationInfo.SERVICE_TYPE_DATA);
        PlmnSatelliteConfig config2 = new PlmnSatelliteConfig(supportedServices2);
        assertNotEquals(config1, config2);
        assertNotEquals(config1, null);
        assertNotEquals(config1, new Object());
    }

    @Test
    public void testParcel() {
        Set<Integer> supportedServices = new HashSet<>();
        supportedServices.add(NetworkRegistrationInfo.SERVICE_TYPE_SMS);
        supportedServices.add(NetworkRegistrationInfo.SERVICE_TYPE_DATA);
        PlmnSatelliteConfig config = new PlmnSatelliteConfig(supportedServices);
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PlmnSatelliteConfig fromParcel = PlmnSatelliteConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        assertEquals(config, fromParcel);
    }
}
