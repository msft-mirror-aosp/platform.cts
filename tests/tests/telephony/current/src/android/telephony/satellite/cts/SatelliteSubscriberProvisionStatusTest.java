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
import android.telephony.satellite.SatelliteSubscriberInfo;
import android.telephony.satellite.SatelliteSubscriberProvisionStatus;

import org.junit.Test;

public class SatelliteSubscriberProvisionStatusTest {
    private static final SatelliteSubscriberInfo SATELLITE_SUBSCRIBER_INFO =
            new SatelliteSubscriberInfo.Builder()
                    .setSubscriberId("09876543")
                    .setCarrierId(12345)
                    .setNiddApn("")
                    .setSubscriptionId(1)
                    .setSubscriberIdType(SatelliteSubscriberInfo.SUBSCRIBER_ID_TYPE_ICCID)
                    .build();

    private static final boolean IS_PROVISIONED = true;

    @Test
    public void testBuilderAndGetters() {
        SatelliteSubscriberProvisionStatus provisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(IS_PROVISIONED)
                        .build();

        assertThat(provisionStatus.getSatelliteSubscriberInfo())
                .isEqualTo(SATELLITE_SUBSCRIBER_INFO);
        assertThat(provisionStatus.isProvisioned()).isEqualTo(IS_PROVISIONED);
    }

    @Test
    public void testEquals() {
        SatelliteSubscriberProvisionStatus provisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(IS_PROVISIONED)
                        .build();

        SatelliteSubscriberProvisionStatus equalsProvisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(IS_PROVISIONED)
                        .build();

        assertThat(provisionStatus).isEqualTo(equalsProvisionStatus);
    }

    @Test
    public void testNotEquals() {
        SatelliteSubscriberProvisionStatus provisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(IS_PROVISIONED)
                        .build();

        SatelliteSubscriberProvisionStatus notEqualsProvisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(false)
                        .build();

        assertThat(provisionStatus).isNotEqualTo(notEqualsProvisionStatus);
    }

    @Test
    public void testParcel() {
        SatelliteSubscriberProvisionStatus provisionStatus =
                new SatelliteSubscriberProvisionStatus.Builder()
                        .setSatelliteSubscriberInfo(SATELLITE_SUBSCRIBER_INFO)
                        .setProvisioned(IS_PROVISIONED)
                        .build();

        Parcel parcel = Parcel.obtain();
        provisionStatus.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SatelliteSubscriberProvisionStatus fromParcel =
                SatelliteSubscriberProvisionStatus.CREATOR.createFromParcel(parcel);
        assertThat(provisionStatus).isEqualTo(fromParcel);
    }
}
