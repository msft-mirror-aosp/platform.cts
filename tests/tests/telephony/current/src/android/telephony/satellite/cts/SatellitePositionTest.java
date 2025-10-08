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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.satellite.SatellitePosition;

import org.junit.Test;

public class SatellitePositionTest {
    private static final double LONGITUDE_DEGREE = 90.0;
    private static final double ALTITUDE_KM = 10.0;

    @Test
    public void testConstructorsAndGetters() {
        SatellitePosition satellitePosition = new SatellitePosition(LONGITUDE_DEGREE, ALTITUDE_KM);

        assertThat(satellitePosition.getLongitudeDegrees()).isEqualTo(LONGITUDE_DEGREE);
        assertThat(satellitePosition.getAltitudeKm()).isEqualTo(ALTITUDE_KM);
    }

    @Test
    public void testEquals() {
        SatellitePosition satellitePosition = new SatellitePosition(LONGITUDE_DEGREE, ALTITUDE_KM);
        SatellitePosition equalsSatellitePosition =
                new SatellitePosition(LONGITUDE_DEGREE, ALTITUDE_KM);

        assertThat(satellitePosition).isEqualTo(equalsSatellitePosition);
    }

    @Test
    public void testNotEquals() {
        SatellitePosition satellitePosition = new SatellitePosition(LONGITUDE_DEGREE, ALTITUDE_KM);
        SatellitePosition notEqualsSatellitePosition = new SatellitePosition(45.0, 20.0);
        assertThat(satellitePosition).isNotEqualTo(notEqualsSatellitePosition);
    }

    @Test
    public void testParcel() {
        SatellitePosition satellitePosition = new SatellitePosition(LONGITUDE_DEGREE, ALTITUDE_KM);

        Parcel parcel = Parcel.obtain();
        satellitePosition.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SatellitePosition fromParcel = SatellitePosition.CREATOR.createFromParcel(parcel);
        assertThat(satellitePosition).isEqualTo(fromParcel);
    }
}
