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
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.FuelType;
import android.car.PortLocationType;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.VehicleSeatOccupancyState;
import android.car.VehicleUnit;
import android.car.feature.Flags;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.EvChargingConnectorType;
import android.car.hardware.property.LocationCharacterization;
import android.car.hardware.property.VehicleAutonomousState;
import android.car.hardware.property.VehicleSizeClass;
import android.car.hardware.property.VehicleTurnSignal;
import android.car.hardware.property.WindshieldWipersState;
import android.util.ArraySet;

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

    /** Used for EV and fuel door port locations. */
    public static final ImmutableSet<Integer> PORT_LOCATION_TYPES =
            ImmutableSet.<Integer>builder()
                    .add(
                            PortLocationType.UNKNOWN,
                            PortLocationType.FRONT_LEFT,
                            PortLocationType.FRONT_RIGHT,
                            PortLocationType.REAR_RIGHT,
                            PortLocationType.REAR_LEFT,
                            PortLocationType.FRONT,
                            PortLocationType.REAR)
                    .build();

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
    private static final ImmutableSet<Integer> VEHICLE_SIZE_CLASSES =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleSizeClass.EPA_TWO_SEATER,
                            VehicleSizeClass.EPA_MINICOMPACT,
                            VehicleSizeClass.EPA_SUBCOMPACT,
                            VehicleSizeClass.EPA_COMPACT,
                            VehicleSizeClass.EPA_MIDSIZE,
                            VehicleSizeClass.EPA_LARGE,
                            VehicleSizeClass.EPA_SMALL_STATION_WAGON,
                            VehicleSizeClass.EPA_MIDSIZE_STATION_WAGON,
                            VehicleSizeClass.EPA_LARGE_STATION_WAGON,
                            VehicleSizeClass.EPA_SMALL_PICKUP_TRUCK,
                            VehicleSizeClass.EPA_STANDARD_PICKUP_TRUCK,
                            VehicleSizeClass.EPA_VAN,
                            VehicleSizeClass.EPA_MINIVAN,
                            VehicleSizeClass.EPA_SMALL_SUV,
                            VehicleSizeClass.EPA_STANDARD_SUV,
                            VehicleSizeClass.EU_A_SEGMENT,
                            VehicleSizeClass.EU_B_SEGMENT,
                            VehicleSizeClass.EU_C_SEGMENT,
                            VehicleSizeClass.EU_D_SEGMENT,
                            VehicleSizeClass.EU_E_SEGMENT,
                            VehicleSizeClass.EU_F_SEGMENT,
                            VehicleSizeClass.EU_J_SEGMENT,
                            VehicleSizeClass.EU_M_SEGMENT,
                            VehicleSizeClass.EU_S_SEGMENT,
                            VehicleSizeClass.JPN_KEI,
                            VehicleSizeClass.JPN_SMALL_SIZE,
                            VehicleSizeClass.JPN_NORMAL_SIZE,
                            VehicleSizeClass.US_GVWR_CLASS_1_CV,
                            VehicleSizeClass.US_GVWR_CLASS_2_CV,
                            VehicleSizeClass.US_GVWR_CLASS_3_CV,
                            VehicleSizeClass.US_GVWR_CLASS_4_CV,
                            VehicleSizeClass.US_GVWR_CLASS_5_CV,
                            VehicleSizeClass.US_GVWR_CLASS_6_CV,
                            VehicleSizeClass.US_GVWR_CLASS_7_CV,
                            VehicleSizeClass.US_GVWR_CLASS_8_CV)
                    .build();
    private static final ImmutableSet<Integer> TURN_SIGNAL_STATES =
            ImmutableSet.<Integer>builder().add(VehicleTurnSignal.STATE_NONE,
                    VehicleTurnSignal.STATE_RIGHT, VehicleTurnSignal.STATE_LEFT).build();
    private static final ImmutableSet<Integer> VEHICLE_AUTONOMOUS_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleAutonomousState.LEVEL_0,
                            VehicleAutonomousState.LEVEL_1,
                            VehicleAutonomousState.LEVEL_2,
                            VehicleAutonomousState.LEVEL_3,
                            VehicleAutonomousState.LEVEL_4,
                            VehicleAutonomousState.LEVEL_5)
                    .build();

    private static final ImmutableSet<Integer> VEHICLE_SEAT_OCCUPANCY_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleSeatOccupancyState.UNKNOWN,
                            VehicleSeatOccupancyState.VACANT,
                            VehicleSeatOccupancyState.OCCUPIED)
                    .build();

    private static final ImmutableSet<Integer> WINDSHIELD_WIPERS_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            WindshieldWipersState.OTHER,
                            WindshieldWipersState.OFF,
                            WindshieldWipersState.ON,
                            WindshieldWipersState.SERVICE)
                    .build();

    /** Gets the verifier builder for {@link VehiclePropertyIds#VEHICLE_CURB_WEIGHT}. */
    public static VehiclePropertyVerifier.Builder<Integer> getVehicleCurbWeightVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Integer> verifierBuilder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.VEHICLE_CURB_WEIGHT)
                        .setConfigArrayVerifier(
                                (verifierContext, configArray) -> {
                                    assertWithMessage(
                                                    "VEHICLE_CURB_WEIGHT configArray must contain"
                                                            + " the gross weight in kilograms")
                                            .that(configArray)
                                            .hasSize(1);
                                    assertWithMessage(
                                                    "VEHICLE_CURB_WEIGHT configArray[0] must"
                                                        + " contain the gross weight in kilograms"
                                                        + " and be greater than zero")
                                            .that(configArray.get(0))
                                            .isGreaterThan(0);
                                })
                        .setCarPropertyValueVerifier(
                                (verifierContext,
                                        carPropertyConfig,
                                        propertyId,
                                        areaId,
                                        timestampNanos,
                                        curbWeightKg) -> {
                                    Integer grossWeightKg =
                                            carPropertyConfig.getConfigArray().get(0);

                                    assertWithMessage(
                                                    "VEHICLE_CURB_WEIGHT must be greater than zero")
                                            .that(curbWeightKg)
                                            .isGreaterThan(0);
                                    assertWithMessage(
                                                    "VEHICLE_CURB_WEIGHT must be less than the"
                                                            + " gross weight")
                                            .that(curbWeightKg)
                                            .isLessThan(grossWeightKg);
                                })
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_PRIVILEGED_CAR_INFO));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_CAR_INFO);
        }
        return verifierBuilder;
    }

    /**
     * Gets the verifier builder for {@link
     * VehiclePropertyIds#VEHICLE_DRIVING_AUTOMATION_CURRENT_LEVEL}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getVehicleDrivingAutomationCurrentLevelVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Integer> verifierBuilder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.VEHICLE_DRIVING_AUTOMATION_CURRENT_LEVEL)
                        .setAllPossibleEnumValues(VEHICLE_AUTONOMOUS_STATES)
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_CAR_DRIVING_STATE));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_CAR_DRIVING_STATE_3P);
        }
        return verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#ENGINE_RPM}. */
    public static VehiclePropertyVerifier.Builder<Float> getEngineRpmVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Float> verifierBuilder =
                VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.ENGINE_RPM)
                        .setCarPropertyValueVerifier(
                                (verifierContext,
                                        carPropertyConfig,
                                        propertyId,
                                        areaId,
                                        timestampNanos,
                                        engineRpm) ->
                                        assertWithMessage(
                                                        "ENGINE_RPM Float value must be greater"
                                                                + " than or equal 0")
                                                .that(engineRpm)
                                                .isAtLeast(0))
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_CAR_ENGINE_DETAILED));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED_3P);
        }
        return verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#WINDSHIELD_WIPERS_STATE}. */
    public static VehiclePropertyVerifier.Builder<Integer>
            getWindshieldWipersStateVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Integer> verifierBuilder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.WINDSHIELD_WIPERS_STATE)
                        .setAllPossibleEnumValues(WINDSHIELD_WIPERS_STATES)
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_READ_WINDSHIELD_WIPERS));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS_3P);
        }
        return verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#PERF_ODOMETER}. */
    public static VehiclePropertyVerifier.Builder<Float> getPerfOdometerVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Float> verifierBuilder =
                VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.PERF_ODOMETER)
                        .setCarPropertyValueVerifier(
                                (verifierContext,
                                        carPropertyConfig,
                                        propertyId,
                                        areaId,
                                        timestampNanos,
                                        perfOdometer) ->
                                        assertWithMessage(
                                                        "PERF_ODOMETER Float value must be greater"
                                                                + " than or equal 0")
                                                .that(perfOdometer)
                                                .isAtLeast(0))
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_MILEAGE));

        return Flags.androidBVehicleProperties()
                ? verifierBuilder.addReadPermission(Car.PERMISSION_MILEAGE_3P)
                : verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#TIRE_PRESSURE}. */
    public static VehiclePropertyVerifier.Builder<Float> getTirePressureVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Float> verifierBuilder =
                VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.TIRE_PRESSURE)
                        .requireMinMaxValues()
                        .setCarPropertyValueVerifier(
                                (verifierContext,
                                        carPropertyConfig,
                                        propertyId,
                                        areaId,
                                        timestampNanos,
                                        tirePressure) ->
                                        assertWithMessage(
                                                        "TIRE_PRESSURE Float value at Area ID"
                                                                + " equals to "
                                                                + areaId
                                                                + " must be greater than or equal"
                                                                + " to 0.")
                                                .that(tirePressure)
                                                .isAtLeast(0))
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_TIRES));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_TIRES_3P);
        }
        return verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#SEAT_OCCUPANCY}. */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatOccupancyVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Integer> verifierBuilder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.SEAT_OCCUPANCY)
                        .setAllPossibleEnumValues(VEHICLE_SEAT_OCCUPANCY_STATES)
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_CONTROL_CAR_SEATS));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_READ_CAR_SEATS);
        }
        return verifierBuilder;
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#PERF_STEERING_ANGLE}. */
    public static VehiclePropertyVerifier.Builder<Float> getPerfSteeringAngleVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Float> verifierBuilder =
                VehiclePropertyVerifier.<Float>newDefaultBuilder(
                                VehiclePropertyIds.PERF_STEERING_ANGLE)
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_READ_STEERING_STATE));

        if (VehiclePropertyVerifier.isAtLeastB() && Flags.vehicleProperty25q23pPermissions()) {
            verifierBuilder.addReadPermission(Car.PERMISSION_READ_STEERING_STATE_3P);
        }
        return verifierBuilder;
    }

    /**
     * Gets the verifier builder for LOCATION_CHARACTERIZATION.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLocationCharacterizationVerifierBuilder() {
        return getLocationCharacterizationVerifierBuilder(
                /* carPropertyManager= */ null,
                VehiclePropertyIds.LOCATION_CHARACTERIZATION,
                ACCESS_FINE_LOCATION);
    }

    /**
     * Gets the verifier builder for LOCATION_CHARACTERIZATION.
     *
     * <p>Works for backported LOCATION_CHARACTERIZATION as well.
     *
     * @param carPropertyManager the car property manager instance.
     * @param propertyId the backported property ID.
     * @param readPermission the permission for the backported property.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLocationCharacterizationVerifierBuilder(
                    CarPropertyManager carPropertyManager,
                    int locPropertyId,
                    String readPermission) {
        return VehiclePropertyVerifier.newBuilder(
                        locPropertyId,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                value) -> {
                            boolean deadReckonedIsSet =
                                    (value & LocationCharacterization.DEAD_RECKONED)
                                            == LocationCharacterization.DEAD_RECKONED;
                            boolean rawGnssOnlyIsSet =
                                    (value & LocationCharacterization.RAW_GNSS_ONLY)
                                            == LocationCharacterization.RAW_GNSS_ONLY;
                            assertWithMessage(
                                            "LOCATION_CHARACTERIZATION must not be 0 "
                                                    + "Found value: "
                                                    + value)
                                    .that(value)
                                    .isNotEqualTo(0);
                            assertWithMessage(
                                            "LOCATION_CHARACTERIZATION must not have any bits "
                                                    + "set outside of the bit flags defined in "
                                                    + "LocationCharacterization. Found value: "
                                                    + value)
                                    .that(value & LOCATION_CHARACTERIZATION_VALID_VALUES_MASK)
                                    .isEqualTo(value);
                            assertWithMessage(
                                            "LOCATION_CHARACTERIZATION must have one of"
                                                + " DEAD_RECKONED or RAW_GNSS_ONLY set. They both"
                                                + " cannot be set either. Found value: "
                                                    + value)
                                    .that(deadReckonedIsSet ^ rawGnssOnlyIsSet)
                                    .isTrue();
                        })
                .setCarPropertyManager(carPropertyManager)
                .addReadPermission(readPermission);
    }

    /**
     * Gets the verifier builder for {@code HVAC_SIDE_MIRROR_HEAT}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHvacSideMirrorHeatVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /**
     * Gets the verifier builder for {@code HVAC_STEERING_WHEEL_HEAT}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHvacSteeringWheelHeatVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets the verifier builder for {@code HVAC_TEMPERATURE_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHvacTemperatureDisplayUnitsVerifierBuilder() {
        VehiclePropertyVerifier.Builder<Integer> builder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS)
                        .setAllPossibleEnumValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                        .setPossibleConfigArrayValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                        .requirePropertyValueTobeInConfigArray()
                        .verifySetterWithConfigArrayValues()
                        .setReadPermission(ImmutableSet.of(Car.PERMISSION_CONTROL_CAR_CLIMATE));

        if (VehiclePropertyVerifier.isAtLeastU()) {
            builder.addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS);
        }
        return builder;
    }

    /**
     * Gets the verifier builder for {@code HVAC_TEMPERATURE_VALUE_SUGGESTION}.
     */
    public static VehiclePropertyVerifier.Builder<Float[]>
            getHvacTemperatureValueSuggestionVerifierBuilder() {
        return VehiclePropertyVerifier.<Float[]>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION)
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            // HVAC_TEMPERATURE_VALUE_SUGGESTION's access must be read+write.
                            assertThat(
                                            (Flags.areaIdConfigAccess()
                                                    ? carPropertyConfig
                                                            .getAreaIdConfig(0)
                                                            .getAccess()
                                                    : carPropertyConfig.getAccess()))
                                    .isEqualTo(
                                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                temperatureSuggestion) ->
                                verifyHvacTemperatureValueSuggestion(
                                        verifierContext, temperatureSuggestion));
    }

    /**
     * Gets the verifier builder for {@code HVAC_POWER_ON}.
     */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacPowerOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_POWER_ON)
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            CarPropertyConfig<?> hvacPowerOnCarPropertyConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
                            for (int powerDependentProperty : configArray) {
                                CarPropertyConfig<?> powerDependentCarPropertyConfig =
                                        verifierContext
                                                .getCarPropertyManager()
                                                .getCarPropertyConfig(powerDependentProperty);
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
                                                    "HVAC_POWER_ON's area IDs must contain the area"
                                                            + " IDs of power dependent property: "
                                                            + VehiclePropertyIds.toString(
                                                                    powerDependentProperty))
                                            .that(powerDependentAreaIdIsContained)
                                            .isTrue();
                                }
                            }
                        });
    }

    /**
     * Gets the verifier builder for {@code HVAC_FAN_SPEED}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHvacFanSpeedVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.HVAC_FAN_SPEED)
                .requireMinMaxValues()
                .setPossiblyDependentOnHvacPowerOn();
    }

    /**
     * Gets the verifier for {@code HVAC_FAN_DIRECTION_AVAILABLE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getHvacFanDirectionAvailableVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        (verifierContext, areaIds) -> {
                            CarPropertyConfig<?> hvacFanDirectionCarPropertyConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
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
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fanDirectionValues) -> {
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " must have at least 1 fan direction defined")
                                    .that(fanDirectionValues.length)
                                    .isAtLeast(1);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " must have only unique fan direction"
                                                    + " values: "
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
                        });
    }

    /**
     * Gets the verifier builder for {@code HVAC_FAN_DIRECTION}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHvacFanDirectionVerifierBuilder() {
        var builder =
                VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                                VehiclePropertyIds.HVAC_FAN_DIRECTION)
                        .setPossiblyDependentOnHvacPowerOn()
                        .setAreaIdsVerifier(
                                (verifierContext, areaIds) -> {
                                    CarPropertyConfig<?> hvacFanDirectionAvailableConfig =
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds
                                                                    .HVAC_FAN_DIRECTION_AVAILABLE);
                                    assertWithMessage(
                                                    "HVAC_FAN_DIRECTION_AVAILABLE must be"
                                                        + " implemented if HVAC_FAN_DIRECTION is"
                                                        + " implemented")
                                            .that(hvacFanDirectionAvailableConfig)
                                            .isNotNull();

                                    assertWithMessage(
                                                    "HVAC_FAN_DIRECTION area IDs must match the"
                                                            + " area IDs of"
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
                                (verifierContext,
                                        carPropertyConfig,
                                        propertyId,
                                        areaId,
                                        timestampNanos,
                                        hvacFanDirection) -> {
                                    CarPropertyValue<Integer[]>
                                            hvacFanDirectionAvailableCarPropertyValue =
                                                    verifierContext
                                                            .getCarPropertyManager()
                                                            .getProperty(
                                                                    VehiclePropertyIds
                                                                            .HVAC_FAN_DIRECTION_AVAILABLE,
                                                                    areaId);
                                    assertWithMessage(
                                                    "HVAC_FAN_DIRECTION_AVAILABLE value must be"
                                                            + " available")
                                            .that(hvacFanDirectionAvailableCarPropertyValue)
                                            .isNotNull();

                                    assertWithMessage(
                                                    "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                            + areaId
                                                            + " must include all possible fan"
                                                            + " direction values")
                                            .that(hvacFanDirection)
                                            .isIn(
                                                    Arrays.asList(
                                                            hvacFanDirectionAvailableCarPropertyValue
                                                                    .getValue()));
                                })
                        .setAllPossibleUnwritableValues(CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES);

        if (VehiclePropertyVerifier.isAtLeastU()) {
            builder.setAllPossibleUnwritableValues(CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES);
        }
        return builder;
    }

    /**
     * Gets the verifier builder for {@code HVAC_TEMPERATURE_CURRENT}.
     */
    public static VehiclePropertyVerifier.Builder<Float>
            getHvacTemperatureCurrentVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /**
     * Gets the verifier builder for {@code HVAC_TEMPERATURE_SET}.
     */
    public static VehiclePropertyVerifier.Builder<Float> getHvacTemperatureSetVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_SET)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
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
                                                    + "Celsius and Fahrenheit must be equal.")
                                    .that(
                                            (configArray.get(1) - configArray.get(0))
                                                    / configArray.get(2))
                                    .isEqualTo(
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
                                                "HVAC_TEMPERATURE_SET minimum value: "
                                                        + minValueInt
                                                        + " at areaId: "
                                                        + areaId
                                                        + " must be equal to minimum"
                                                        + " value specified in config"
                                                        + " array: "
                                                        + configMinValue)
                                        .that(minValueInt)
                                        .isEqualTo(configMinValue);

                                Float maxValueFloat = (Float) carPropertyConfig.getMaxValue(areaId);
                                Integer maxValueInt = (int) (maxValueFloat * 10);
                                assertWithMessage(
                                                "HVAC_TEMPERATURE_SET maximum value: "
                                                        + maxValueInt
                                                        + " at areaId: "
                                                        + areaId
                                                        + " must be equal to maximum"
                                                        + " value specified in config"
                                                        + " array: "
                                                        + configMaxValue)
                                        .that(maxValueInt)
                                        .isEqualTo(configMaxValue);
                            }
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                tempInCelsius) -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            if (configArray.isEmpty()) {
                                return;
                            }
                            Integer minTempInCelsius = configArray.get(0);
                            Integer maxTempInCelsius = configArray.get(1);
                            Integer incrementInCelsius = configArray.get(2);
                            VehiclePropertyVerifier.verifyHvacTemperatureIsValid(
                                    tempInCelsius,
                                    minTempInCelsius,
                                    maxTempInCelsius,
                                    incrementInCelsius);
                        });
    }

    /** Gets the verifier builder for {@code HVAC_AC_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacAcOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_AC_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@code HVAC_MAX_AC_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacMaxAcOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_MAX_AC_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@code HVAC_MAX_DEFROST_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacMaxDefrostOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_MAX_DEFROST_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@code HVAC_RECIRC_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacRecircOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_RECIRC_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#HVAC_AUTO_RECIRC_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacAutoRecircOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_AUTO_RECIRC_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@code HVAC_AUTO_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacAutoOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_AUTO_ON)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@code HVAC_SEAT_TEMPERATURE}. */
    public static VehiclePropertyVerifier.Builder<Integer> getHvacSeatTemperatureVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_SEAT_TEMPERATURE)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#HVAC_SEAT_VENTILATION}. */
    public static VehiclePropertyVerifier.Builder<Integer> getHvacSeatVentilationVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_SEAT_VENTILATION)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#HVAC_ACTUAL_FAN_SPEED_RPM}. */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHvacActualFanSpeedRpmVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM)
                .setPossiblyDependentOnHvacPowerOn();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#HVAC_DUAL_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getHvacDualOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.HVAC_DUAL_ON)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        (verifierContext, areaIds) -> {
                            CarPropertyConfig<?> hvacTempSetCarPropertyConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
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
                        });
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

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_DRIVER_SEAT}. */
    public static VehiclePropertyVerifier.Builder<Integer> getInfoDriverSeatVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.INFO_DRIVER_SEAT)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleAreaSeat.SEAT_ROW_1_LEFT,
                                VehicleAreaSeat.SEAT_ROW_1_CENTER,
                                VehicleAreaSeat.SEAT_ROW_1_RIGHT))
                .setAreaIdsVerifier(
                        (verifierContext, areaIds) ->
                                assertWithMessage(
                                                "Even though INFO_DRIVER_SEAT is"
                                                    + " VEHICLE_AREA_TYPE_SEAT, it is meant to be"
                                                    + " VEHICLE_AREA_TYPE_GLOBAL, so its AreaIds"
                                                    + " must contain a single 0")
                                        .that(areaIds)
                                        .isEqualTo(
                                                new int[] {
                                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                                                }));
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_EV_BATTERY_CAPACITY}. */
    public static VehiclePropertyVerifier.Builder<Float> getInfoEvBatteryCapacityVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evBatteryCapacity) ->
                                assertWithMessage(
                                                "INFO_EV_BATTERY_CAPACITY Float value must"
                                                        + " be greater than or equal to 0")
                                        .that(evBatteryCapacity)
                                        .isAtLeast(0));
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_EV_CONNECTOR_TYPE}. */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getInfoEvConnectorTypeVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evConnectorTypes) -> {
                            assertWithMessage(
                                            "INFO_EV_CONNECTOR_TYPE must specify at least 1"
                                                    + " connection type")
                                    .that(evConnectorTypes.length)
                                    .isGreaterThan(0);
                            for (Integer evConnectorType : evConnectorTypes) {
                                assertWithMessage(
                                                "INFO_EV_CONNECTOR_TYPE must be a defined"
                                                        + " connection type: "
                                                        + evConnectorType)
                                        .that(evConnectorType)
                                        .isIn(
                                                ImmutableSet.builder()
                                                        .add(
                                                                EvChargingConnectorType.UNKNOWN,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_1_AC,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_2_AC,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_3_AC,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_4_DC,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_1_CCS_DC,
                                                                EvChargingConnectorType
                                                                        .IEC_TYPE_2_CCS_DC,
                                                                EvChargingConnectorType
                                                                        .TESLA_ROADSTER,
                                                                EvChargingConnectorType.TESLA_HPWC,
                                                                EvChargingConnectorType
                                                                        .TESLA_SUPERCHARGER,
                                                                EvChargingConnectorType.GBT_AC,
                                                                EvChargingConnectorType.GBT_DC,
                                                                EvChargingConnectorType.OTHER)
                                                        .build());
                            }
                        });
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_EV_PORT_LOCATION}. */
    public static VehiclePropertyVerifier.Builder<Integer> getInfoEvPortLocationVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.INFO_EV_PORT_LOCATION)
                .setAllPossibleEnumValues(PORT_LOCATION_TYPES);
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_VEHICLE_SIZE_CLASS}. */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getInfoVehicleSizeClassVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.INFO_VEHICLE_SIZE_CLASS)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                sizeClasses) -> {
                            ArraySet<Integer> presentStandards = new ArraySet<>();
                            for (int sizeClass : sizeClasses) {
                                assertWithMessage(
                                                "Size class "
                                                        + sizeClass
                                                        + " doesn't exist in "
                                                        + "possible values: "
                                                        + VEHICLE_SIZE_CLASSES)
                                        .that(VEHICLE_SIZE_CLASSES.contains(sizeClass))
                                        .isTrue();
                                int standard = sizeClass & 0xf00;
                                assertWithMessage(
                                                "Multiple values from the standard of size class "
                                                        + sizeClass
                                                        + " are in use.")
                                        .that(presentStandards.contains(standard))
                                        .isFalse();
                                presentStandards.add(standard);
                            }
                        });
    }

    /** Gets the verifier for {@link VehiclePropertyIds#TURN_SIGNAL_LIGHT_STATE}. */
    public static VehiclePropertyVerifier.Builder<Integer>
            getTurnSignalLightStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(TURN_SIGNAL_STATES)
                .add(VehicleTurnSignal.STATE_LEFT | VehicleTurnSignal.STATE_RIGHT)
                .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues);
    }

    /** Gets the verifier for {@link VehiclePropertyIds#TURN_SIGNAL_SWITCH}. */
    public static VehiclePropertyVerifier.Builder<Integer> getTurnSignalSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.TURN_SIGNAL_SWITCH)
                .setAllPossibleEnumValues(TURN_SIGNAL_STATES);
    }

    /**
     * Gets the verifier builder for {@link
     * VehiclePropertyIds#VEHICLE_DRIVING_AUTOMATION_TARGET_LEVEL}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getVehicleDrivingAutomationTargetLevelVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.VEHICLE_DRIVING_AUTOMATION_TARGET_LEVEL)
                .setAllPossibleEnumValues(VEHICLE_AUTONOMOUS_STATES);
    }

    /**
     * Gets the verifier builder for {@link VehiclePropertyIds#VEHICLE_PASSIVE_SUSPENSION_HEIGHT}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getVehiclePassiveSuspensionHeightVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.VEHICLE_PASSIVE_SUSPENSION_HEIGHT)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#RANGE_REMAINING}. */
    public static VehiclePropertyVerifier.Builder<Float> getRangeRemainingVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.RANGE_REMAINING)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                rangeRemaining) ->
                                assertWithMessage(
                                                "RANGE_REMAINING Float value must be greater than"
                                                        + " or equal 0")
                                        .that(rangeRemaining)
                                        .isAtLeast(0));
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#EV_BATTERY_LEVEL}. */
    public static VehiclePropertyVerifier.Builder<Float> getEvBatteryLevelVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.EV_BATTERY_LEVEL)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evBatteryLevel) -> {
                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must be greater than or"
                                                    + " equal 0")
                                    .that(evBatteryLevel)
                                    .isAtLeast(0);

                            if (verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
                                                    VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> infoEvBatteryCapacityValue =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getProperty(
                                                    VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must not exceed "
                                                    + "INFO_EV_BATTERY_CAPACITY Float "
                                                    + "value")
                                    .that(evBatteryLevel)
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        });
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#FUEL_LEVEL}. */
    public static VehiclePropertyVerifier.Builder<Float> getFuelLevelVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(VehiclePropertyIds.FUEL_LEVEL)
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            assertFuelPropertyNotImplementedOnEv(
                                    verifierContext.getCarPropertyManager(),
                                    VehiclePropertyIds.FUEL_LEVEL);
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fuelLevel) -> {
                            assertWithMessage(
                                            "FUEL_LEVEL Float value must be greater than or equal"
                                                    + " 0")
                                    .that(fuelLevel)
                                    .isAtLeast(0);

                            if (verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
                                                    VehiclePropertyIds.INFO_FUEL_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> infoFuelCapacityValue =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getProperty(
                                                    VehiclePropertyIds.INFO_FUEL_CAPACITY,
                                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                            assertWithMessage(
                                            "FUEL_LEVEL Float value must not exceed"
                                                    + " INFO_FUEL_CAPACITY Float value")
                                    .that(fuelLevel)
                                    .isAtMost((Float) infoFuelCapacityValue.getValue());
                        });
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#FUEL_DOOR_OPEN}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getFuelDoorOpenVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.FUEL_DOOR_OPEN)
                .setCarPropertyConfigVerifier(
                        (verifierContext, config) -> {
                            assertFuelPropertyNotImplementedOnEv(
                                    verifierContext.getCarPropertyManager(),
                                    VehiclePropertyIds.FUEL_DOOR_OPEN);
                        });
    }

    /** Assert fuel property is not implement on an EV vehicle. */
    public static void assertFuelPropertyNotImplementedOnEv(
            CarPropertyManager mgr, int propertyId) {
        runWithShellPermissionIdentity(
                () -> {
                    if (mgr.getCarPropertyConfig(VehiclePropertyIds.INFO_FUEL_TYPE) == null) {
                        return;
                    }
                    CarPropertyValue<?> infoFuelTypeValue =
                            mgr.getProperty(VehiclePropertyIds.INFO_FUEL_TYPE, /* areaId */ 0);
                    if (infoFuelTypeValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                        return;
                    }
                    Integer[] fuelTypes = (Integer[]) infoFuelTypeValue.getValue();
                    assertWithMessage(
                                    "If fuelTypes only contains FuelType.ELECTRIC, "
                                            + VehiclePropertyIds.toString(propertyId)
                                            + " property must not be implemented")
                            .that(fuelTypes)
                            .isNotEqualTo(new Integer[] {FuelType.ELECTRIC});
                },
                Car.PERMISSION_CAR_INFO);
    }

    public static VehiclePropertyVerifier.Builder<Float> getEnvOutsideTemperatureVerifierBuilder() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT);
    }

    /**
     * Gets the verifier builder for {@link VehiclePropertyIds#NIGHT_MODE}.
     *
     * <p>This property is required by CDD.
     */
    public static VehiclePropertyVerifier.Builder<Boolean> getNightModeVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(VehiclePropertyIds.NIGHT_MODE)
                .requireProperty();
    }

    private static void verifyHvacTemperatureValueSuggestion(
            VehiclePropertyVerifier.VerifierContext verifierContext,
            Float[] temperatureSuggestion) {
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
                .isIn(
                        ImmutableList.of(
                                (float) VehicleUnit.CELSIUS,
                                (float) VehicleUnit.FAHRENHEIT));

        Float suggestedTempInCelsius = temperatureSuggestion[2];
        Float suggestedTempInFahrenheit = temperatureSuggestion[3];
        CarPropertyConfig<?> hvacTemperatureSetCarPropertyConfig =
                verifierContext
                        .getCarPropertyManager()
                        .getCarPropertyConfig(
                                VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        if (hvacTemperatureSetCarPropertyConfig == null) {
            return;
        }
        List<Integer> hvacTemperatureSetConfigArray =
                hvacTemperatureSetCarPropertyConfig.getConfigArray();
        if (hvacTemperatureSetConfigArray.isEmpty()) {
            return;
        }
        Integer minTempInCelsiusTimesTen = hvacTemperatureSetConfigArray.get(0);
        Integer maxTempInCelsiusTimesTen = hvacTemperatureSetConfigArray.get(1);
        Integer incrementInCelsiusTimesTen =
                hvacTemperatureSetConfigArray.get(2);
        VehiclePropertyVerifier.verifyHvacTemperatureIsValid(
                suggestedTempInCelsius, minTempInCelsiusTimesTen,
                maxTempInCelsiusTimesTen, incrementInCelsiusTimesTen);

        Integer minTempInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(3);
        Integer maxTempInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(4);
        Integer incrementInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(5);
        VehiclePropertyVerifier.verifyHvacTemperatureIsValid(
                suggestedTempInFahrenheit, minTempInFahrenheitTimesTen,
                maxTempInFahrenheitTimesTen, incrementInFahrenheitTimesTen);

        int suggestedTempInCelsiusTimesTen =
                (int) (suggestedTempInCelsius * 10f);
        int suggestedTempInFahrenheitTimesTen =
                (int) (suggestedTempInFahrenheit * 10f);
        int numIncrementsCelsius =
                Math.round(
                        (suggestedTempInCelsiusTimesTen
                                - minTempInCelsiusTimesTen)
                                / incrementInCelsiusTimesTen.floatValue());
        int numIncrementsFahrenheit =
                Math.round(
                        (suggestedTempInFahrenheitTimesTen
                                - minTempInFahrenheitTimesTen)
                                / incrementInFahrenheitTimesTen.floatValue());
        assertWithMessage(
                "The temperature in celsius must map to the same"
                        + " temperature in fahrenheit using the"
                        + " HVAC_TEMPERATURE_SET config array: "
                        + hvacTemperatureSetConfigArray)
                .that(numIncrementsFahrenheit)
                .isEqualTo(numIncrementsCelsius);
    }
}
