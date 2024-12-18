/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts.utils;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.feature.Flags;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.LocationCharacterization;
import android.os.Build;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a list of verifiers for vehicle properties.
 */
public class VehiclePropertyVerifiers {

    private VehiclePropertyVerifiers() {
        throw new UnsupportedOperationException("Should only be used as a static class");
    }

    private static final int LOCATION_CHARACTERIZATION_VALID_VALUES_MASK =
            LocationCharacterization.PRIOR_LOCATIONS
            | LocationCharacterization.GYROSCOPE_FUSION
            | LocationCharacterization.ACCELEROMETER_FUSION
            | LocationCharacterization.COMPASS_FUSION
            | LocationCharacterization.WHEEL_SPEED_FUSION
            | LocationCharacterization.STEERING_ANGLE_FUSION
            | LocationCharacterization.CAR_SPEED_FUSION
            | LocationCharacterization.DEAD_RECKONED
            | LocationCharacterization.RAW_GNSS_ONLY;

    private static final ImmutableSet<Integer> HVAC_TEMPERATURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.CELSIUS,
                    VehicleUnit.FAHRENHEIT).build();

    private static final ImmutableSet<Integer> SINGLE_HVAC_FAN_DIRECTIONS =
            ImmutableSet.of(
                            CarHvacFanDirection.UNKNOWN,
                            CarHvacFanDirection.FACE,
                            CarHvacFanDirection.FLOOR,
                            CarHvacFanDirection.DEFROST);

    private static final ImmutableSet<Integer> ALL_POSSIBLE_HVAC_FAN_DIRECTIONS =
            generateAllPossibleHvacFanDirections();

    private static final ImmutableSet<Integer> CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CarHvacFanDirection.UNKNOWN)
                    .build();

    /**
     * Gets the verifier for LOCATION_CHARACTERIZATION.
     */
    public static VehiclePropertyVerifier<Integer> getLocationCharacterizationVerifier(
            CarPropertyManager carPropertyManager) {
        return getLocationCharacterizationVerifier(
            carPropertyManager,
            VehiclePropertyIds.LOCATION_CHARACTERIZATION,
            ACCESS_FINE_LOCATION);
    }

    /**
     * Gets the verifier for backported LOCATION_CHARACTERIZATION.
     *
     * @param carPropertyManager the car property manager instance.
     * @param propertyId the backported property ID.
     * @param readPermission the permission for the backported property.
     */
    public static VehiclePropertyVerifier<Integer> getLocationCharacterizationVerifier(
            CarPropertyManager carPropertyManager,
            int propertyId, String readPermission) {
        var builder = getLocationCharacterizationVerifierBuilder(
                carPropertyManager, propertyId, readPermission);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.requireProperty();
        }
        return builder.build();
    }

    /**
     * Gets the verifier for {@code HVAC_DEFROSTER}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacDefrosterVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DEFROSTER,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_SIDE_MIRROR_HEAT}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacSideMirrorHeatVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_STEERING_WHEEL_HEAT}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacSteeringWheelHeatVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_TEMPERATURE_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacTemperatureDisplayUnitsVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .setAllPossibleEnumValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_TEMPERATURE_VALUE_SUGGESTION}.
     */
    public static VehiclePropertyVerifier<Float[]> getHvacTemperatureValueSuggestionVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float[].class, carPropertyManager)
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            // HVAC_TEMPERATURE_VALUE_SUGGESTION's access must be read+write.
                            assertThat((Flags.areaIdConfigAccess()
                                    ? carPropertyConfig.getAreaIdConfig(0).getAccess()
                                    : carPropertyConfig.getAccess())).isEqualTo(
                                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                temperatureSuggestion) -> {
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_VALUE_SUGGESTION Float[] value"
                                                + " must be size 4.")
                                    .that(temperatureSuggestion.length)
                                    .isEqualTo(4);

                            Float requestedTempUnits = temperatureSuggestion[1];
                            assertWithMessage(
                                            "The value at index 1 must be one of"
                                                + " {VehicleUnit#CELSIUS, VehicleUnit#FAHRENHEIT}"
                                                + " which correspond to values {"
                                                + (float) VehicleUnit.CELSIUS
                                                + ", "
                                                + (float) VehicleUnit.FAHRENHEIT
                                                + "}.")
                                    .that(requestedTempUnits)
                                    .isIn(ImmutableList.of((float) VehicleUnit.CELSIUS,
                                            (float) VehicleUnit.FAHRENHEIT));
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_POWER_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacPowerOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_POWER_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setConfigArrayVerifier(
                        configArray -> {
                            CarPropertyConfig<?> hvacPowerOnCarPropertyConfig =
                                    carPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_POWER_ON);
                            for (int powerDependentProperty : configArray) {
                                CarPropertyConfig<?> powerDependentCarPropertyConfig =
                                        carPropertyManager.getCarPropertyConfig(
                                                powerDependentProperty);
                                if (powerDependentCarPropertyConfig == null) {
                                    continue;
                                }
                                assertWithMessage(
                                                "HVAC_POWER_ON configArray must only contain"
                                                    + " VehicleAreaSeat type properties: "
                                                        + VehiclePropertyIds.toString(
                                                                powerDependentProperty))
                                        .that(powerDependentCarPropertyConfig.getAreaType())
                                        .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_SEAT);

                                for (int powerDependentAreaId :
                                        powerDependentCarPropertyConfig.getAreaIds()) {
                                    boolean powerDependentAreaIdIsContained = false;
                                    for (int hvacPowerOnAreaId :
                                            hvacPowerOnCarPropertyConfig.getAreaIds()) {
                                        if ((powerDependentAreaId & hvacPowerOnAreaId)
                                                == powerDependentAreaId) {
                                            powerDependentAreaIdIsContained = true;
                                            break;
                                        }
                                    }
                                    assertWithMessage(
                                            "HVAC_POWER_ON's area IDs must contain the area IDs"
                                                    + " of power dependent property: "
                                                    + VehiclePropertyIds.toString(
                                                    powerDependentProperty)).that(
                                            powerDependentAreaIdIsContained).isTrue();
                                }
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_FAN_SPEED}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacFanSpeedVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .requireMinMaxValues()
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_FAN_DIRECTION_AVAILABLE}.
     */
    public static VehiclePropertyVerifier<Integer[]> getHvacFanDirectionAvailableVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacFanDirectionCarPropertyConfig =
                                    carPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION must be implemented if "
                                                    + "HVAC_FAN_DIRECTION_AVAILABLE is implemented")
                                    .that(hvacFanDirectionCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area IDs must match the"
                                                + " area IDs of HVAC_FAN_DIRECTION")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            hvacFanDirectionCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                fanDirectionValues) -> {
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " must have at least 1 direction defined")
                                    .that(fanDirectionValues.length)
                                    .isAtLeast(1);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " values all must all be unique: "
                                                    + Arrays.toString(fanDirectionValues))
                                    .that(fanDirectionValues.length)
                                    .isEqualTo(ImmutableSet.copyOf(fanDirectionValues).size());
                            for (Integer fanDirection : fanDirectionValues) {
                                assertWithMessage(
                                                "HVAC_FAN_DIRECTION_AVAILABLE's area ID: "
                                                        + areaId
                                                        + " must be a valid combination of fan"
                                                        + " directions")
                                        .that(fanDirection)
                                        .isIn(ALL_POSSIBLE_HVAC_FAN_DIRECTIONS);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_FAN_DIRECTION}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacFanDirectionVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacFanDirectionAvailableConfig =
                                    carPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE must be implemented if "
                                                    + "HVAC_FAN_DIRECTION is implemented")
                                    .that(hvacFanDirectionAvailableConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION area IDs must match the area IDs of"
                                                + " HVAC_FAN_DIRECTION_AVAILABLE")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            hvacFanDirectionAvailableConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                hvacFanDirection) -> {
                            CarPropertyValue<Integer[]> hvacFanDirectionAvailableCarPropertyValue =
                                    carPropertyManager.getProperty(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                                            areaId);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE value must be available")
                                    .that(hvacFanDirectionAvailableCarPropertyValue)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION area ID "
                                                    + areaId
                                                    + " value must be in list for"
                                                    + " HVAC_FAN_DIRECTION_AVAILABLE")
                                    .that(hvacFanDirection)
                                    .isIn(
                                            Arrays.asList(
                                                    hvacFanDirectionAvailableCarPropertyValue
                                                            .getValue()));
                        })
                .setAllPossibleUnwritableValues(CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_TEMPERATURE_CURRENT}.
     */
    public static VehiclePropertyVerifier<Float> getHvacTemperatureCurrentVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_TEMPERATURE_SET}.
     */
    public static VehiclePropertyVerifier<Float> getHvacTemperatureSetVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            if (configArray.isEmpty()) {
                                return;
                            }
                            assertWithMessage("HVAC_TEMPERATURE_SET config array must be size 6")
                                    .that(configArray.size())
                                    .isEqualTo(6);

                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET lower bound must be less"
                                                    + " than the upper bound for the supported"
                                                    + " temperatures in Celsius")
                                    .that(configArray.get(0))
                                    .isLessThan(configArray.get(1));
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Celsius"
                                                    + " must be greater than 0")
                                    .that(configArray.get(2))
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Celsius must"
                                                    + " be less than the difference between the"
                                                    + " upper and lower bound supported"
                                                    + " temperatures")
                                    .that(configArray.get(2))
                                    .isLessThan(configArray.get(1) - configArray.get(0));
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Celsius must"
                                                    + " evenly space the gap between upper and"
                                                    + " lower bound")
                                    .that(
                                            (configArray.get(1) - configArray.get(0))
                                                    % configArray.get(2))
                                    .isEqualTo(0);
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET lower bound must be less"
                                                    + " than the upper bound for the supported"
                                                    + " temperatures in Fahrenheit")
                                    .that(configArray.get(3))
                                    .isLessThan(configArray.get(4));
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                    + " must be greater than 0")
                                    .that(configArray.get(5))
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                    + " must be less than the difference"
                                                    + " between the upper and lower bound"
                                                    + " supported temperatures")
                                    .that(configArray.get(5))
                                    .isLessThan(configArray.get(4) - configArray.get(3));
                            assertWithMessage(
                                            "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                    + " must evenly space the gap between upper"
                                                    + " and lower bound")
                                    .that(
                                            (configArray.get(4) - configArray.get(3))
                                                    % configArray.get(5))
                                    .isEqualTo(0);
                            assertWithMessage(
                                    "HVAC_TEMPERATURE_SET number of supported values for "
                                            + "Celsius and Fahrenheit must be equal.").that(
                                    (configArray.get(1) - configArray.get(0))
                                            / configArray.get(2)).isEqualTo(
                                    (configArray.get(4) - configArray.get(3))
                                            / configArray.get(5));

                            int[] supportedAreaIds = carPropertyConfig.getAreaIds();
                            int configMinValue = configArray.get(0);
                            int configMaxValue = configArray.get(1);
                            for (int i = 0; i < supportedAreaIds.length; i++) {
                                int areaId = supportedAreaIds[i];
                                Float minValueFloat = (Float) carPropertyConfig.getMinValue(areaId);
                                Integer minValueInt = (int) (minValueFloat * 10);
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET minimum value: " + minValueInt
                                        + " at areaId: " + areaId + " should be equal to minimum"
                                        + " value specified in config"
                                        + " array: " + configMinValue)
                                        .that(minValueInt)
                                        .isEqualTo(configMinValue);

                                Float maxValueFloat = (Float) carPropertyConfig.getMaxValue(areaId);
                                Integer maxValueInt = (int) (maxValueFloat * 10);
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET maximum value: " + maxValueInt
                                        + " at areaId: " + areaId + " should be equal to maximum"
                                        + " value specified in config"
                                        + " array: " + configMaxValue)
                                        .that(maxValueInt)
                                        .isEqualTo(configMaxValue);
                            }
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, tempInCelsius) -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            if (configArray.isEmpty()) {
                                return;
                            }
                            Integer minTempInCelsius = configArray.get(0);
                            Integer maxTempInCelsius = configArray.get(1);
                            Integer incrementInCelsius = configArray.get(2);
                            VehiclePropertyVerifier.verifyHvacTemperatureIsValid(tempInCelsius,
                                    minTempInCelsius, maxTempInCelsius, incrementInCelsius);
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_AC_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacAcOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_ELECTRIC_DEFROSTER_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacMaxAcOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_MAX_DEFROST_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacMaxDefrostOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_RECIRC_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacRecircOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_AUTO_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacAutoOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_SEAT_TEMPERATURE}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacSeatTemperatureVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_ACTUAL_FAN_SPEED_RPM}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacActualFanSpeedRpmVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_AUTO_RECIRC_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacAutoRecircOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_SEAT_VENTILATION}.
     */
    public static VehiclePropertyVerifier<Integer> getHvacSeatVentilationVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_VENTILATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    /**
     * Gets the verifier for {@code HVAC_DUAL_ON}.
     */
    public static VehiclePropertyVerifier<Boolean> getHvacDualOnVerifier(
            CarPropertyManager carPropertyManager) {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DUAL_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, carPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacTempSetCarPropertyConfig =
                                    carPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_TEMPERATURE_SET);
                            if (hvacTempSetCarPropertyConfig == null) {
                                return;
                            }
                            ImmutableSet<Integer> hvacTempSetAreaIds =
                                    ImmutableSet.copyOf(
                                            Arrays.stream(hvacTempSetCarPropertyConfig.getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                            ImmutableSet.Builder<Integer> allPossibleHvacDualOnAreaIdsBuilder =
                                    ImmutableSet.builder();
                            for (int i = 2; i <= hvacTempSetAreaIds.size(); i++) {
                                allPossibleHvacDualOnAreaIdsBuilder.addAll(
                                        Sets.combinations(hvacTempSetAreaIds, i).stream()
                                                .map(
                                                        areaIdCombo -> {
                                                            Integer possibleHvacDualOnAreaId = 0;
                                                            for (Integer areaId : areaIdCombo) {
                                                                possibleHvacDualOnAreaId |= areaId;
                                                            }
                                                            return possibleHvacDualOnAreaId;
                                                        })
                                                .collect(Collectors.toList()));
                            }
                            ImmutableSet<Integer> allPossibleHvacDualOnAreaIds =
                                    allPossibleHvacDualOnAreaIdsBuilder.build();
                            for (int areaId : areaIds) {
                                assertWithMessage(
                                                "HVAC_DUAL_ON area ID: "
                                                        + areaId
                                                        + " must be a combination of"
                                                        + " HVAC_TEMPERATURE_SET area IDs: "
                                                        + Arrays.toString(
                                                                hvacTempSetCarPropertyConfig
                                                                        .getAreaIds()))
                                        .that(areaId)
                                        .isIn(allPossibleHvacDualOnAreaIds);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build();
    }

    private static VehiclePropertyVerifier.Builder<Integer>
            getLocationCharacterizationVerifierBuilder(
                    CarPropertyManager carPropertyManager,
                    int locPropertyId, String readPermission) {
        return VehiclePropertyVerifier.newBuilder(
                        locPropertyId,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, carPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, value) -> {
                            boolean deadReckonedIsSet = (value
                                    & LocationCharacterization.DEAD_RECKONED)
                                    == LocationCharacterization.DEAD_RECKONED;
                            boolean rawGnssOnlyIsSet = (value
                                    & LocationCharacterization.RAW_GNSS_ONLY)
                                    == LocationCharacterization.RAW_GNSS_ONLY;
                            assertWithMessage("LOCATION_CHARACTERIZATION must not be 0 "
                                    + "Found value: " + value)
                                    .that(value)
                                    .isNotEqualTo(0);
                            assertWithMessage("LOCATION_CHARACTERIZATION must not have any bits "
                                    + "set outside of the bit flags defined in "
                                    + "LocationCharacterization. Found value: " + value)
                                    .that(value & LOCATION_CHARACTERIZATION_VALID_VALUES_MASK)
                                    .isEqualTo(value);
                            assertWithMessage("LOCATION_CHARACTERIZATION must have one of "
                                    + "DEAD_RECKONED or RAW_GNSS_ONLY set. They both cannot be set "
                                    + "either. Found value: " + value)
                                    .that(deadReckonedIsSet ^ rawGnssOnlyIsSet)
                                    .isTrue();
                        })
                .addReadPermission(readPermission);
    }

    private static ImmutableSet<Integer> generateAllPossibleHvacFanDirections() {
        ImmutableSet.Builder<Integer> allPossibleFanDirectionsBuilder = ImmutableSet.builder();
        for (int i = 1; i <= SINGLE_HVAC_FAN_DIRECTIONS.size(); i++) {
            allPossibleFanDirectionsBuilder.addAll(Sets.combinations(SINGLE_HVAC_FAN_DIRECTIONS,
                    i).stream().map(hvacFanDirectionCombo -> {
                        Integer possibleHvacFanDirection = 0;
                        for (Integer hvacFanDirection : hvacFanDirectionCombo) {
                            possibleHvacFanDirection |= hvacFanDirection;
                        }
                        return possibleHvacFanDirection;
                    }).collect(Collectors.toList()));
        }
        return allPossibleFanDirectionsBuilder.build();
    }
}
