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

package android.car.property.cts;

import static android.car.feature.Flags.FLAG_VEHICLE_PROPERTY_ENUMS_REMOVE_SYSTEM_API_TAGS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.VehicleSeatOccupancyState;
import android.car.cts.utils.VehiclePropertyUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class VehicleSeatOccupancyStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(apis = {"android.car.VehicleStateOccupancyState#toString"})
    @Test
    @RequiresFlagsEnabled(FLAG_VEHICLE_PROPERTY_ENUMS_REMOVE_SYSTEM_API_TAGS)
    public void testToString() {
        assertThat(VehicleSeatOccupancyState.toString(VehicleSeatOccupancyState.UNKNOWN))
                .isEqualTo("UNKNOWN");
        assertThat(VehicleSeatOccupancyState.toString(VehicleSeatOccupancyState.VACANT))
                .isEqualTo("VACANT");
        assertThat(VehicleSeatOccupancyState.toString(VehicleSeatOccupancyState.OCCUPIED))
                .isEqualTo("OCCUPIED");
        assertThat(VehicleSeatOccupancyState.toString(3)).isEqualTo("0x3");
        assertThat(VehicleSeatOccupancyState.toString(12)).isEqualTo("0xc");
    }

    @ApiTest(apis = {"android.car.VehicleStateOccupancyState#toString"})
    @Test
    @RequiresFlagsEnabled(FLAG_VEHICLE_PROPERTY_ENUMS_REMOVE_SYSTEM_API_TAGS)
    public void testAllVehicleSeatOccupancyStatesAreMappedInToString() {
        List<Integer> VehicleSeatOccupancyStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(VehicleSeatOccupancyState.class);
        for (Integer VehicleSeatOccupancyState : VehicleSeatOccupancyStates) {
            String VehicleSeatOccupancyStateString =
                    VehicleSeatOccupancyState.toString(VehicleSeatOccupancyState);
            assertWithMessage("%s starts with 0x", VehicleSeatOccupancyStateString)
                    .that(VehicleSeatOccupancyStateString.startsWith("0x"))
                    .isFalse();
        }
    }
}
