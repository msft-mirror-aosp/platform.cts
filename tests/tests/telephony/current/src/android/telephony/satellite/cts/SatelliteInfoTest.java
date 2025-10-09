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
import android.telephony.satellite.EarfcnRange;
import android.telephony.satellite.SatelliteInfo;
import android.telephony.satellite.SatellitePosition;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SatelliteInfoTest {
    private static final UUID SATELLITE_ID =
            UUID.fromString("568b9842-554a-4efb-a4f3-1ce79832cbfe");
    private static final SatellitePosition SATELLITE_POSITION = new SatellitePosition(90.0, 10.0);
    private static final List<Integer> BAND_LIST = new ArrayList<>(List.of(259, 260));
    private static final List<EarfcnRange> EARFCN_RANGE_LIST =
            new ArrayList<>(List.of(new EarfcnRange(3000, 4300)));

    @Test
    public void testConstructorsAndGetters() {
        SatelliteInfo info =
                new SatelliteInfo(SATELLITE_ID, SATELLITE_POSITION, BAND_LIST, EARFCN_RANGE_LIST);

        assertThat(info.getSatelliteId()).isEqualTo(SATELLITE_ID);
        assertThat(info.getSatellitePosition()).isEqualTo(SATELLITE_POSITION);
        assertThat(info.getBands()).isEqualTo(BAND_LIST);
        assertThat(info.getEarfcnRanges()).isEqualTo(EARFCN_RANGE_LIST);
    }

    @Test
    public void testEquals() {
        SatelliteInfo info =
                new SatelliteInfo(SATELLITE_ID, SATELLITE_POSITION, BAND_LIST, EARFCN_RANGE_LIST);

        SatelliteInfo equalsInfo =
                new SatelliteInfo(SATELLITE_ID, SATELLITE_POSITION, BAND_LIST, EARFCN_RANGE_LIST);

        assertThat(info).isEqualTo(equalsInfo);
    }

    @Test
    public void testNotEquals() {
        SatelliteInfo info =
                new SatelliteInfo(SATELLITE_ID, SATELLITE_POSITION, BAND_LIST, EARFCN_RANGE_LIST);

        SatelliteInfo notEqualsInfo =
                new SatelliteInfo(
                        UUID.fromString("75284889-9798-4f4c-ac18-df4c1fbc3696"),
                        SATELLITE_POSITION,
                        BAND_LIST,
                        EARFCN_RANGE_LIST);

        assertThat(info).isNotEqualTo(notEqualsInfo);
    }

    @Test
    public void testParcel() {
        SatelliteInfo info =
                new SatelliteInfo(SATELLITE_ID, SATELLITE_POSITION, BAND_LIST, EARFCN_RANGE_LIST);

        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SatelliteInfo fromParcel = SatelliteInfo.CREATOR.createFromParcel(parcel);
        assertThat(info).isEqualTo(fromParcel);
    }
}
