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
import android.car.GsrComplianceType;
import android.car.PortLocationType;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleGear;
import android.car.VehicleIgnitionState;
import android.car.VehiclePropertyIds;
import android.car.VehicleSeatOccupancyState;
import android.car.VehicleUnit;
import android.car.feature.Flags;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.AutomaticEmergencyBrakingState;
import android.car.hardware.property.BlindSpotWarningState;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CrossTrafficMonitoringWarningState;
import android.car.hardware.property.CruiseControlCommand;
import android.car.hardware.property.CruiseControlState;
import android.car.hardware.property.CruiseControlType;
import android.car.hardware.property.DriverDistractionState;
import android.car.hardware.property.DriverDistractionWarning;
import android.car.hardware.property.DriverDrowsinessAttentionState;
import android.car.hardware.property.DriverDrowsinessAttentionWarning;
import android.car.hardware.property.ElectronicStabilityControlState;
import android.car.hardware.property.EmergencyLaneKeepAssistState;
import android.car.hardware.property.ErrorState;
import android.car.hardware.property.EvChargeState;
import android.car.hardware.property.EvChargingConnectorType;
import android.car.hardware.property.EvRegenerativeBrakingState;
import android.car.hardware.property.EvStoppingMode;
import android.car.hardware.property.ForwardCollisionWarningState;
import android.car.hardware.property.HandsOnDetectionDriverState;
import android.car.hardware.property.HandsOnDetectionWarning;
import android.car.hardware.property.ImpactSensorLocation;
import android.car.hardware.property.LaneCenteringAssistCommand;
import android.car.hardware.property.LaneCenteringAssistState;
import android.car.hardware.property.LaneDepartureWarningState;
import android.car.hardware.property.LaneKeepAssistState;
import android.car.hardware.property.LocationCharacterization;
import android.car.hardware.property.LowSpeedAutomaticEmergencyBrakingState;
import android.car.hardware.property.LowSpeedCollisionWarningState;
import android.car.hardware.property.TrailerState;
import android.car.hardware.property.VehicleAirbagLocation;
import android.car.hardware.property.VehicleAutonomousState;
import android.car.hardware.property.VehicleElectronicTollCollectionCardStatus;
import android.car.hardware.property.VehicleElectronicTollCollectionCardType;
import android.car.hardware.property.VehicleLightState;
import android.car.hardware.property.VehicleLightSwitch;
import android.car.hardware.property.VehicleOilLevel;
import android.car.hardware.property.VehicleSizeClass;
import android.car.hardware.property.VehicleTurnSignal;
import android.car.hardware.property.WindshieldWipersState;
import android.car.hardware.property.WindshieldWipersSwitch;
import android.util.ArraySet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

import java.time.Year;
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

    private static final ImmutableSet<Integer> VEHICLE_GEARS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleGear.GEAR_UNKNOWN,
                            VehicleGear.GEAR_NEUTRAL,
                            VehicleGear.GEAR_REVERSE,
                            VehicleGear.GEAR_PARK,
                            VehicleGear.GEAR_DRIVE,
                            VehicleGear.GEAR_FIRST,
                            VehicleGear.GEAR_SECOND,
                            VehicleGear.GEAR_THIRD,
                            VehicleGear.GEAR_FOURTH,
                            VehicleGear.GEAR_FIFTH,
                            VehicleGear.GEAR_SIXTH,
                            VehicleGear.GEAR_SEVENTH,
                            VehicleGear.GEAR_EIGHTH,
                            VehicleGear.GEAR_NINTH)
                    .build();

    public static final ImmutableSet<Integer> TRAILER_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            TrailerState.STATE_UNKNOWN,
                            TrailerState.STATE_NOT_PRESENT,
                            TrailerState.STATE_PRESENT,
                            TrailerState.STATE_ERROR)
                    .build();
    public static final ImmutableSet<Integer> DISTANCE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleUnit.MILLIMETER,
                            VehicleUnit.METER,
                            VehicleUnit.KILOMETER,
                            VehicleUnit.MILE)
                    .build();
    public static final ImmutableSet<Integer> VOLUME_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleUnit.MILLILITER,
                            VehicleUnit.LITER,
                            VehicleUnit.US_GALLON,
                            VehicleUnit.IMPERIAL_GALLON)
                    .build();
    public static final ImmutableSet<Integer> PRESSURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder()
                    .add(VehicleUnit.KILOPASCAL, VehicleUnit.PSI, VehicleUnit.BAR)
                    .build();
    public static final ImmutableSet<Integer> BATTERY_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder()
                    .add(VehicleUnit.WATT_HOUR, VehicleUnit.AMPERE_HOURS, VehicleUnit.KILOWATT_HOUR)
                    .build();
    public static final ImmutableSet<Integer> SPEED_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleUnit.METER_PER_SEC,
                            VehicleUnit.MILES_PER_HOUR,
                            VehicleUnit.KILOMETERS_PER_HOUR)
                    .build();
    public static final ImmutableSet<Integer> VEHICLE_LIGHT_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleLightState.STATE_OFF,
                            VehicleLightState.STATE_ON,
                            VehicleLightState.STATE_DAYTIME_RUNNING)
                    .build();
    public static final ImmutableSet<Integer> VEHICLE_LIGHT_SWITCHES =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleLightSwitch.STATE_OFF,
                            VehicleLightSwitch.STATE_ON,
                            VehicleLightSwitch.STATE_DAYTIME_RUNNING,
                            VehicleLightSwitch.STATE_AUTOMATIC)
                    .build();
    public static final ImmutableSet<Integer> VEHICLE_OIL_LEVELS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleOilLevel.LEVEL_CRITICALLY_LOW,
                            VehicleOilLevel.LEVEL_LOW,
                            VehicleOilLevel.LEVEL_NORMAL,
                            VehicleOilLevel.LEVEL_HIGH,
                            VehicleOilLevel.LEVEL_ERROR)
                    .build();
    public static final ImmutableSet<Integer> WINDSHIELD_WIPERS_SWITCHES =
            ImmutableSet.<Integer>builder()
                    .add(
                            WindshieldWipersSwitch.OTHER,
                            WindshieldWipersSwitch.OFF,
                            WindshieldWipersSwitch.MIST,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_1,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_2,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_3,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_4,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_5,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_1,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_2,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_3,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_4,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_5,
                            WindshieldWipersSwitch.AUTO,
                            WindshieldWipersSwitch.SERVICE)
                    .build();
    public static final ImmutableSet<Integer> EV_STOPPING_MODES =
            ImmutableSet.<Integer>builder()
                    .add(
                            EvStoppingMode.STATE_OTHER,
                            EvStoppingMode.STATE_CREEP,
                            EvStoppingMode.STATE_ROLL,
                            EvStoppingMode.STATE_HOLD)
                    .build();
    public static final ImmutableSet<Integer> VEHICLE_AIRBAG_LOCATIONS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleAirbagLocation.FRONT,
                            VehicleAirbagLocation.KNEE,
                            VehicleAirbagLocation.LEFT_SIDE,
                            VehicleAirbagLocation.RIGHT_SIDE,
                            VehicleAirbagLocation.CURTAIN)
                    .build();
    public static final ImmutableSet<Integer> IMPACT_SENSOR_LOCATIONS =
            ImmutableSet.<Integer>builder()
                    .add(
                            ImpactSensorLocation.FRONT,
                            ImpactSensorLocation.FRONT_LEFT_DOOR_SIDE,
                            ImpactSensorLocation.FRONT_RIGHT_DOOR_SIDE,
                            ImpactSensorLocation.REAR_LEFT_DOOR_SIDE,
                            ImpactSensorLocation.REAR_RIGHT_DOOR_SIDE,
                            ImpactSensorLocation.REAR)
                    .build();
    public static final ImmutableSet<Integer> EMERGENCY_LANE_KEEP_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            EmergencyLaneKeepAssistState.OTHER,
                            EmergencyLaneKeepAssistState.ENABLED,
                            EmergencyLaneKeepAssistState.WARNING_LEFT,
                            EmergencyLaneKeepAssistState.WARNING_RIGHT,
                            EmergencyLaneKeepAssistState.ACTIVATED_STEER_LEFT,
                            EmergencyLaneKeepAssistState.ACTIVATED_STEER_RIGHT,
                            EmergencyLaneKeepAssistState.USER_OVERRIDE)
                    .build();
    public static final ImmutableSet<Integer> CRUISE_CONTROL_TYPES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlType.OTHER,
                            CruiseControlType.STANDARD,
                            CruiseControlType.ADAPTIVE,
                            CruiseControlType.PREDICTIVE)
                    .build();
    public static final ImmutableSet<Integer> CRUISE_CONTROL_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlState.OTHER,
                            CruiseControlState.ENABLED,
                            CruiseControlState.ACTIVATED,
                            CruiseControlState.USER_OVERRIDE,
                            CruiseControlState.SUSPENDED,
                            CruiseControlState.FORCED_DEACTIVATION_WARNING)
                    .build();
    public static final ImmutableSet<Integer> CRUISE_CONTROL_COMMANDS =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlCommand.ACTIVATE,
                            CruiseControlCommand.SUSPEND,
                            CruiseControlCommand.INCREASE_TARGET_SPEED,
                            CruiseControlCommand.DECREASE_TARGET_SPEED,
                            CruiseControlCommand.INCREASE_TARGET_TIME_GAP,
                            CruiseControlCommand.DECREASE_TARGET_TIME_GAP)
                    .build();
    public static final ImmutableSet<Integer>
            CRUISE_CONTROL_COMMANDS_UNAVAILABLE_STATES_ON_STANDARD_CRUISE_CONTROL =
                    ImmutableSet.<Integer>builder()
                            .add(
                                    CruiseControlCommand.INCREASE_TARGET_TIME_GAP,
                                    CruiseControlCommand.DECREASE_TARGET_TIME_GAP)
                            .build();
    public static final ImmutableSet<Integer> HANDS_ON_DETECTION_DRIVER_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            HandsOnDetectionDriverState.OTHER,
                            HandsOnDetectionDriverState.HANDS_ON,
                            HandsOnDetectionDriverState.HANDS_OFF)
                    .build();
    public static final ImmutableSet<Integer> HANDS_ON_DETECTION_WARNINGS =
            ImmutableSet.<Integer>builder()
                    .add(
                            HandsOnDetectionWarning.OTHER,
                            HandsOnDetectionWarning.NO_WARNING,
                            HandsOnDetectionWarning.WARNING)
                    .build();
    public static final ImmutableSet<Integer> DRIVER_DROWSINESS_ATTENTION_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            DriverDrowsinessAttentionState.OTHER,
                            DriverDrowsinessAttentionState.KSS_RATING_1_EXTREMELY_ALERT,
                            DriverDrowsinessAttentionState.KSS_RATING_2_VERY_ALERT,
                            DriverDrowsinessAttentionState.KSS_RATING_3_ALERT,
                            DriverDrowsinessAttentionState.KSS_RATING_4_RATHER_ALERT,
                            DriverDrowsinessAttentionState.KSS_RATING_5_NEITHER_ALERT_NOR_SLEEPY,
                            DriverDrowsinessAttentionState.KSS_RATING_6_SOME_SLEEPINESS,
                            DriverDrowsinessAttentionState.KSS_RATING_7_SLEEPY_NO_EFFORT,
                            DriverDrowsinessAttentionState.KSS_RATING_8_SLEEPY_SOME_EFFORT,
                            DriverDrowsinessAttentionState.KSS_RATING_9_VERY_SLEEPY)
                    .build();
    public static final ImmutableSet<Integer> DRIVER_DROWSINESS_ATTENTION_WARNINGS =
            ImmutableSet.<Integer>builder()
                    .add(
                            DriverDrowsinessAttentionWarning.OTHER,
                            DriverDrowsinessAttentionWarning.NO_WARNING,
                            DriverDrowsinessAttentionWarning.WARNING)
                    .build();
    public static final ImmutableSet<Integer> DRIVER_DISTRACTION_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            DriverDistractionState.OTHER,
                            DriverDistractionState.NOT_DISTRACTED,
                            DriverDistractionState.DISTRACTED)
                    .build();
    public static final ImmutableSet<Integer> DRIVER_DISTRACTION_WARNINGS =
            ImmutableSet.<Integer>builder()
                    .add(
                            DriverDistractionWarning.OTHER,
                            DriverDistractionWarning.NO_WARNING,
                            DriverDistractionWarning.WARNING)
                    .build();

    public static final ImmutableSet<Integer> ERROR_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            ErrorState.OTHER_ERROR_STATE,
                            ErrorState.NOT_AVAILABLE_DISABLED,
                            ErrorState.NOT_AVAILABLE_SPEED_LOW,
                            ErrorState.NOT_AVAILABLE_SPEED_HIGH,
                            ErrorState.NOT_AVAILABLE_POOR_VISIBILITY,
                            ErrorState.NOT_AVAILABLE_SAFETY)
                    .build();
    public static final ImmutableSet<Integer> AUTOMATIC_EMERGENCY_BRAKING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            AutomaticEmergencyBrakingState.OTHER,
                            AutomaticEmergencyBrakingState.ENABLED,
                            AutomaticEmergencyBrakingState.ACTIVATED,
                            AutomaticEmergencyBrakingState.USER_OVERRIDE)
                    .build();
    public static final ImmutableSet<Integer> FORWARD_COLLISION_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            ForwardCollisionWarningState.OTHER,
                            ForwardCollisionWarningState.NO_WARNING,
                            ForwardCollisionWarningState.WARNING)
                    .build();
    public static final ImmutableSet<Integer> BLIND_SPOT_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            BlindSpotWarningState.OTHER,
                            BlindSpotWarningState.NO_WARNING,
                            BlindSpotWarningState.WARNING)
                    .build();
    public static final ImmutableSet<Integer> LANE_DEPARTURE_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneDepartureWarningState.OTHER,
                            LaneDepartureWarningState.NO_WARNING,
                            LaneDepartureWarningState.WARNING_LEFT,
                            LaneDepartureWarningState.WARNING_RIGHT)
                    .build();
    public static final ImmutableSet<Integer> LANE_KEEP_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneKeepAssistState.OTHER,
                            LaneKeepAssistState.ENABLED,
                            LaneKeepAssistState.ACTIVATED_STEER_LEFT,
                            LaneKeepAssistState.ACTIVATED_STEER_RIGHT,
                            LaneKeepAssistState.USER_OVERRIDE)
                    .build();
    public static final ImmutableSet<Integer> LANE_CENTERING_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneCenteringAssistState.OTHER,
                            LaneCenteringAssistState.ENABLED,
                            LaneCenteringAssistState.ACTIVATION_REQUESTED,
                            LaneCenteringAssistState.ACTIVATED,
                            LaneCenteringAssistState.USER_OVERRIDE,
                            LaneCenteringAssistState.FORCED_DEACTIVATION_WARNING)
                    .build();
    public static final ImmutableSet<Integer> LANE_CENTERING_ASSIST_COMMANDS =
            ImmutableSet.<Integer>builder()
                    .add(LaneCenteringAssistCommand.ACTIVATE, LaneCenteringAssistCommand.DEACTIVATE)
                    .build();
    public static final ImmutableSet<Integer> LOW_SPEED_COLLISION_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LowSpeedCollisionWarningState.OTHER,
                            LowSpeedCollisionWarningState.NO_WARNING,
                            LowSpeedCollisionWarningState.WARNING)
                    .build();
    public static final ImmutableSet<Integer> ELECTRONIC_STABILITY_CONTROL_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            ElectronicStabilityControlState.OTHER,
                            ElectronicStabilityControlState.ENABLED,
                            ElectronicStabilityControlState.ACTIVATED)
                    .build();
    public static final ImmutableSet<Integer> CROSS_TRAFFIC_MONITORING_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CrossTrafficMonitoringWarningState.OTHER,
                            CrossTrafficMonitoringWarningState.NO_WARNING,
                            CrossTrafficMonitoringWarningState.WARNING_FRONT_LEFT,
                            CrossTrafficMonitoringWarningState.WARNING_FRONT_RIGHT,
                            CrossTrafficMonitoringWarningState.WARNING_FRONT_BOTH,
                            CrossTrafficMonitoringWarningState.WARNING_REAR_LEFT,
                            CrossTrafficMonitoringWarningState.WARNING_REAR_RIGHT,
                            CrossTrafficMonitoringWarningState.WARNING_REAR_BOTH)
                    .build();
    public static final ImmutableSet<Integer> LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LowSpeedAutomaticEmergencyBrakingState.OTHER,
                            LowSpeedAutomaticEmergencyBrakingState.ENABLED,
                            LowSpeedAutomaticEmergencyBrakingState.ACTIVATED,
                            LowSpeedAutomaticEmergencyBrakingState.USER_OVERRIDE)
                    .build();
    public static final ImmutableSet<Integer> CRUISE_CONTROL_TYPE_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .addAll(ERROR_STATES)
                    .add(CruiseControlType.OTHER)
                    .build();
    public static final ImmutableSet<Integer> EV_STOPPING_MODE_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder().add(EvStoppingMode.STATE_OTHER).build();
    public static final ImmutableSet<Integer> WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder().add(WindshieldWipersSwitch.OTHER).build();

    private static final int REASONABLE_FUTURE_MODEL_YEAR_OFFSET = 5;
    private static final int REASONABLE_PAST_MODEL_YEAR_OFFSET = -10;

    private static void verifyWheelTickConfigArray(
            int supportedWheels, int wheelToVerify, int configArrayIndex, int wheelTicksToUm) {
        if ((supportedWheels & wheelToVerify) != 0) {
            assertWithMessage(
                            "WHEEL_TICK configArray["
                                    + configArrayIndex
                                    + "] must specify the ticks to micrometers for "
                                    + wheelToString(wheelToVerify))
                    .that(wheelTicksToUm)
                    .isGreaterThan(0);
        } else {
            assertWithMessage(
                            "WHEEL_TICK configArray["
                                    + configArrayIndex
                                    + "] should be zero since "
                                    + wheelToString(wheelToVerify)
                                    + " is not supported")
                    .that(wheelTicksToUm)
                    .isEqualTo(0);
        }
    }

    private static void verifyWheelTickValue(
            int supportedWheels, int wheelToVerify, int valueIndex, Long ticks) {
        if ((supportedWheels & wheelToVerify) == 0) {
            assertWithMessage(
                            "WHEEL_TICK value["
                                    + valueIndex
                                    + "] should be zero since "
                                    + wheelToString(wheelToVerify)
                                    + " is not supported")
                    .that(ticks)
                    .isEqualTo(0);
        }
    }

    private static String wheelToString(int wheel) {
        switch (wheel) {
            case VehicleAreaWheel.WHEEL_LEFT_FRONT:
                return "WHEEL_LEFT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_FRONT:
                return "WHEEL_RIGHT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_REAR:
                return "WHEEL_RIGHT_REAR";
            case VehicleAreaWheel.WHEEL_LEFT_REAR:
                return "WHEEL_LEFT_REAR";
            default:
                return Integer.toString(wheel);
        }
    }

    private static void verifyWindshieldWipersSwitchLevelsAreConsecutive(
            List<Integer> supportedEnumValues, ImmutableList<Integer> levels, int areaId) {
        for (int i = 0; i < levels.size(); i++) {
            Integer level = levels.get(i);
            if (supportedEnumValues.contains(level)) {
                for (int j = i + 1; j < levels.size(); j++) {
                    assertWithMessage(
                                    "For VehicleAreaWindow area ID "
                                            + areaId
                                            + ", "
                                            + WindshieldWipersSwitch.toString(levels.get(j))
                                            + " must be supported if "
                                            + WindshieldWipersSwitch.toString(level)
                                            + " is supported.")
                            .that(levels.get(j))
                            .isIn(supportedEnumValues);
                }
                break;
            }
        }
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EMERGENCY_LANE_KEEP_ASSIST_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getEmergencyLaneKeepAssistStateVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(EMERGENCY_LANE_KEEP_ASSIST_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_STATE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRUISE_CONTROL_TYPE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getCruiseControlTypeVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(CRUISE_CONTROL_TYPES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_TYPE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setAllPossibleUnwritableValues(CRUISE_CONTROL_TYPE_UNWRITABLE_STATES)
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRUISE_CONTROL_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getCruiseControlStateVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(CRUISE_CONTROL_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_STATE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRUISE_CONTROL_COMMAND} on adaptive cruise control.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getCruiseControlCommandVerifierBuilder_OnAdaptiveCruiseControl() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_COMMAND)
                .setAllPossibleEnumValues(CRUISE_CONTROL_COMMANDS)
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRUISE_CONTROL_COMMAND} on standard cruise control.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getCruiseControlCommandVerifierBuilder_OnStandardCruiseControl() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_COMMAND)
                .setAllPossibleEnumValues(CRUISE_CONTROL_COMMANDS)
                .setAllPossibleUnavailableValues(
                        CRUISE_CONTROL_COMMANDS_UNAVAILABLE_STATES_ON_STANDARD_CRUISE_CONTROL)
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRUISE_CONTROL_TARGET_SPEED}.
     */
    public static VehiclePropertyVerifier.Builder<Float>
            getCruiseControlTargetSpeedVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_TARGET_SPEED)
                .requireMinMaxValues()
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            List<? extends AreaIdConfig<?>> areaIdConfigs =
                                    carPropertyConfig.getAreaIdConfigs();
                            for (AreaIdConfig<?> areaIdConfig : areaIdConfigs) {
                                assertWithMessage("Min/Max values must be non-negative")
                                        .that((Float) areaIdConfig.getMinValue())
                                        .isAtLeast(0F);
                            }
                        })
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getAdaptiveCruiseControlTargetTimeGapVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP)
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();

                            for (Integer configArrayValue : configArray) {
                                assertWithMessage(
                                                "configArray values of"
                                                        + " ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP"
                                                        + " must be positive. Detected value "
                                                        + configArrayValue
                                                        + " in configArray "
                                                        + configArray)
                                        .that(configArrayValue)
                                        .isGreaterThan(0);
                            }

                            for (int i = 0; i < configArray.size() - 1; i++) {
                                assertWithMessage(
                                                "configArray values of"
                                                    + " ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP"
                                                    + " must be in ascending order. Detected value "
                                                        + configArray.get(i)
                                                        + " is greater than or equal to "
                                                        + configArray.get(i + 1)
                                                        + " in configArray "
                                                        + configArray)
                                        .that(configArray.get(i))
                                        .isLessThan(configArray.get(i + 1));
                            }
                        })
                .verifySetterWithConfigArrayValues()
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getAdaptiveCruiseControlLeadVehicleMeasuredDistanceVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .setDependentOnProperty(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HANDS_ON_DETECTION_DRIVER_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHandsOnDetectionDriverStateVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(HANDS_ON_DETECTION_DRIVER_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_DRIVER_STATE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HANDS_ON_DETECTION_WARNING}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHandsOnDetectionWarningVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(HANDS_ON_DETECTION_WARNINGS)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_WARNING)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getDriverDrowsinessAttentionStateVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(DRIVER_DROWSINESS_ATTENTION_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_STATE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_SYSTEM_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_WARNING}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getDriverDrowsinessAttentionWarningVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(DRIVER_DROWSINESS_ATTENTION_WARNINGS)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_WARNING)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#DRIVER_DISTRACTION_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getDriverDistractionStateVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(DRIVER_DISTRACTION_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.DRIVER_DISTRACTION_STATE)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.DRIVER_DISTRACTION_SYSTEM_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#DRIVER_DISTRACTION_WARNING}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getDriverDistractionWarningVerifierBuilder() {
        ImmutableSet<Integer> possibleEnumValues =
                ImmutableSet.<Integer>builder()
                        .addAll(DRIVER_DISTRACTION_WARNINGS)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.DRIVER_DISTRACTION_WARNING)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.DRIVER_DISTRACTION_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS,
                                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS))
                .verifyErrorStates();
    }

    /** Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#WHEEL_TICK}. */
    public static VehiclePropertyVerifier.Builder<Long[]> getWheelTickVerifierBuilder() {
        return VehiclePropertyVerifier.<Long[]>newDefaultBuilder(VehiclePropertyIds.WHEEL_TICK)
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            assertWithMessage("WHEEL_TICK config array must be size 5")
                                    .that(configArray.size())
                                    .isEqualTo(5);

                            int supportedWheels = configArray.get(0);
                            assertWithMessage(
                                            "WHEEL_TICK config array first element specifies which"
                                                    + " wheels are supported")
                                    .that(supportedWheels)
                                    .isGreaterThan(VehicleAreaWheel.WHEEL_UNKNOWN);
                            assertWithMessage(
                                            "WHEEL_TICK config array first element specifies which"
                                                    + " wheels are supported")
                                    .that(supportedWheels)
                                    .isAtMost(
                                            VehicleAreaWheel.WHEEL_LEFT_FRONT
                                                    | VehicleAreaWheel.WHEEL_RIGHT_FRONT
                                                    | VehicleAreaWheel.WHEEL_LEFT_REAR
                                                    | VehicleAreaWheel.WHEEL_RIGHT_REAR);

                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_FRONT,
                                    1,
                                    configArray.get(1));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                                    2,
                                    configArray.get(2));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_REAR,
                                    3,
                                    configArray.get(3));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_REAR,
                                    4,
                                    configArray.get(4));
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                wheelTicks) -> {
                            List<Integer> wheelTickConfigArray = carPropertyConfig.getConfigArray();
                            int supportedWheels = wheelTickConfigArray.get(0);

                            assertWithMessage("WHEEL_TICK Long[] value must be size 5")
                                    .that(wheelTicks.length)
                                    .isEqualTo(5);

                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_FRONT,
                                    1,
                                    wheelTicks[1]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                                    2,
                                    wheelTicks[2]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_REAR,
                                    3,
                                    wheelTicks[3]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_REAR,
                                    4,
                                    wheelTicks[4]);
                        });
    }

    /** Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#INFO_VIN}. */
    public static VehiclePropertyVerifier.Builder<String> getInfoVinVerifierBuilder() {
        return VehiclePropertyVerifier.<String>newDefaultBuilder(VehiclePropertyIds.INFO_VIN)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                vin) ->
                                assertWithMessage("INFO_VIN must be 17 characters")
                                        .that(vin)
                                        .hasLength(17));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#INFO_FUEL_CAPACITY}.
     */
    public static VehiclePropertyVerifier.Builder<Float> getInfoFuelCapacityVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.INFO_FUEL_CAPACITY)
                .setCarPropertyConfigVerifier(
                        (verifierContext, config) -> {
                            assertFuelPropertyNotImplementedOnEv(
                                    verifierContext.getCarPropertyManager(),
                                    VehiclePropertyIds.INFO_FUEL_CAPACITY);
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fuelCapacity) ->
                                assertWithMessage(
                                                "INFO_FUEL_CAPACITY Float value must be greater"
                                                        + " than or equal to 0")
                                        .that(fuelCapacity)
                                        .isAtLeast(0));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#INFO_FUEL_TYPE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]> getInfoFuelTypeVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.INFO_FUEL_TYPE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fuelTypes) -> {
                            assertWithMessage("INFO_FUEL_TYPE must specify at least 1 fuel type")
                                    .that(fuelTypes.length)
                                    .isGreaterThan(0);
                            for (Integer fuelType : fuelTypes) {
                                assertWithMessage(
                                                "INFO_FUEL_TYPE must be a defined fuel type: "
                                                        + fuelType)
                                        .that(fuelType)
                                        .isIn(
                                                ImmutableSet.builder()
                                                        .add(
                                                                FuelType.UNKNOWN,
                                                                FuelType.UNLEADED,
                                                                FuelType.LEADED,
                                                                FuelType.DIESEL_1,
                                                                FuelType.DIESEL_2,
                                                                FuelType.BIODIESEL,
                                                                FuelType.E85,
                                                                FuelType.LPG,
                                                                FuelType.CNG,
                                                                FuelType.LNG,
                                                                FuelType.ELECTRIC,
                                                                FuelType.HYDROGEN,
                                                                FuelType.OTHER)
                                                        .build());
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#INFO_FUEL_DOOR_LOCATION}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getInfoFuelDoorLocationVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION)
                .setCarPropertyConfigVerifier(
                        (verifierContext, config) -> {
                            assertFuelPropertyNotImplementedOnEv(
                                    verifierContext.getCarPropertyManager(),
                                    VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION);
                        })
                .setAllPossibleEnumValues(PORT_LOCATION_TYPES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#INFO_MULTI_EV_PORT_LOCATIONS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getInfoMultiEvPortLocationsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evPortLocations) -> {
                            assertWithMessage(
                                            "INFO_MULTI_EV_PORT_LOCATIONS must specify at least 1"
                                                    + " port location")
                                    .that(evPortLocations.length)
                                    .isGreaterThan(0);
                            for (Integer evPortLocation : evPortLocations) {
                                assertWithMessage(
                                                "INFO_MULTI_EV_PORT_LOCATIONS must be a defined"
                                                        + " port location: "
                                                        + evPortLocation)
                                        .that(evPortLocation)
                                        .isIn(PORT_LOCATION_TYPES);
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#INFO_EXTERIOR_DIMENSIONS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getInfoExteriorDimensionsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                exteriorDimensions) -> {
                            assertWithMessage(
                                            "INFO_EXTERIOR_DIMENSIONS must specify all 8 dimension"
                                                    + " measurements")
                                    .that(exteriorDimensions.length)
                                    .isEqualTo(8);
                            for (Integer exteriorDimension : exteriorDimensions) {
                                assertWithMessage(
                                                "INFO_EXTERIOR_DIMENSIONS measurement must be"
                                                        + " greater than 0")
                                        .that(exteriorDimension)
                                        .isGreaterThan(0);
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_POSITION}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getUltrasonicsSensorPositionVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_POSITION)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                positions) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_POSITION must specify 3 values, "
                                                    + "areaId: "
                                                    + areaId)
                                    .that(positions.length)
                                    .isEqualTo(3);
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_ORIENTATION}.
     */
    public static VehiclePropertyVerifier.Builder<Float[]>
            getUltrasonicsSensorOrientationVerifierBuilder() {
        return VehiclePropertyVerifier.<Float[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_ORIENTATION)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                orientations) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_ORIENTATION must specify 4 "
                                                    + "values, areaId: "
                                                    + areaId)
                                    .that(orientations.length)
                                    .isEqualTo(4);
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_FIELD_OF_VIEW}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getUltrasonicsSensorFieldOfViewVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_FIELD_OF_VIEW)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fieldOfViews) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_FIELD_OF_VIEW must specify 2 "
                                                    + "values, areaId: "
                                                    + areaId)
                                    .that(fieldOfViews.length)
                                    .isEqualTo(2);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_FIELD_OF_VIEW horizontal fov "
                                                    + "must be greater than zero, areaId: "
                                                    + areaId)
                                    .that(fieldOfViews[0])
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_FIELD_OF_VIEW vertical fov "
                                                    + "must be greater than zero, areaId: "
                                                    + areaId)
                                    .that(fieldOfViews[1])
                                    .isGreaterThan(0);
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_DETECTION_RANGE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getUltrasonicsSensorDetectionRangeVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_DETECTION_RANGE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                detectionRanges) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_DETECTION_RANGE must "
                                                    + "specify 2 values, areaId: "
                                                    + areaId)
                                    .that(detectionRanges.length)
                                    .isEqualTo(2);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_DETECTION_RANGE min value must "
                                                    + "be at least zero, areaId: "
                                                    + areaId)
                                    .that(detectionRanges[0])
                                    .isAtLeast(0);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_DETECTION_RANGE max value must "
                                                    + "be greater than min, areaId: "
                                                    + areaId)
                                    .that(detectionRanges[1])
                                    .isGreaterThan(detectionRanges[0]);
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_SUPPORTED_RANGES}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getUltrasonicsSensorSupportedRangesVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_SUPPORTED_RANGES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                supportedRanges) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_SUPPORTED_RANGES "
                                                    + "must have at least 1 range, areaId: "
                                                    + areaId)
                                    .that(supportedRanges.length)
                                    .isAtLeast(2);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_SUPPORTED_RANGES must "
                                                    + "specify an even number of values, areaId: "
                                                    + areaId)
                                    .that(supportedRanges.length % 2)
                                    .isEqualTo(0);
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_SUPPORTED_RANGES values "
                                                    + "must be greater than zero, areaId: "
                                                    + areaId)
                                    .that(supportedRanges[0])
                                    .isAtLeast(0);
                            for (int i = 1; i < supportedRanges.length; i++) {
                                assertWithMessage(
                                                "ULTRASONICS_SENSOR_SUPPORTED_RANGES values "
                                                        + "must be in ascending order, areaId: "
                                                        + areaId)
                                        .that(supportedRanges[i])
                                        .isGreaterThan(supportedRanges[i - 1]);
                            }
                            verifyUltrasonicsSupportedRangesWithinDetectionRange(
                                    verifierContext.getCarPropertyManager(),
                                    areaId,
                                    supportedRanges);
                        });
    }

    private static void verifyUltrasonicsSupportedRangesWithinDetectionRange(
            CarPropertyManager carPropertyManager, int areaId, Integer[] supportedRanges) {
        if (carPropertyManager.getCarPropertyConfig(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_DETECTION_RANGE)
                == null) {
            return;
        }

        Integer[] detectionRange =
                (Integer[])
                        carPropertyManager
                                .getProperty(
                                        VehiclePropertyIds.ULTRASONICS_SENSOR_DETECTION_RANGE,
                                        areaId)
                                .getValue();

        for (int i = 0; i < supportedRanges.length; i++) {
            assertWithMessage(
                            "ULTRASONICS_SENSOR_SUPPORTED_RANGES values must "
                                    + "be within the ULTRASONICS_SENSOR_DETECTION_RANGE, areaId: "
                                    + areaId)
                    .that(supportedRanges[i])
                    .isIn(Range.closed(detectionRange[0], detectionRange[1]));
        }
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ULTRASONICS_SENSOR_MEASURED_DISTANCE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer[]>
            getUltrasonicsSensorMeasuredDistanceVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer[]>newDefaultBuilder(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_MEASURED_DISTANCE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                distance) -> {
                            assertWithMessage(
                                            "ULTRASONICS_SENSOR_MEASURED_DISTANCE must "
                                                    + "have at most 2 values, areaId: "
                                                    + areaId)
                                    .that(distance.length)
                                    .isAtMost(2);
                            if (distance.length == 2) {
                                assertWithMessage(
                                                "ULTRASONICS_SENSOR_MEASURED_DISTANCE distance"
                                                    + " error must be greater than zero, areaId: "
                                                        + areaId)
                                        .that(distance[1])
                                        .isAtLeast(0);
                            }
                            verifyUltrasonicsMeasuredDistanceInSupportedRanges(
                                    verifierContext.getCarPropertyManager(), areaId, distance);
                            verifyUltrasonicsMeasuredDistanceWithinDetectionRange(
                                    verifierContext.getCarPropertyManager(), areaId, distance);
                        });
    }

    private static void verifyUltrasonicsMeasuredDistanceInSupportedRanges(
            CarPropertyManager carPropertyManager, int areaId, Integer[] distance) {
        // Distance with length of 0 is valid. return because there are no values to verify.
        if (distance.length == 0) {
            return;
        }

        if (carPropertyManager.getCarPropertyConfig(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_SUPPORTED_RANGES)
                == null) {
            return;
        }

        Integer[] supportedRanges =
                (Integer[])
                        carPropertyManager
                                .getProperty(
                                        VehiclePropertyIds.ULTRASONICS_SENSOR_SUPPORTED_RANGES,
                                        areaId)
                                .getValue();
        ImmutableSet.Builder<Integer> minimumSupportedRangeValues = ImmutableSet.builder();
        for (int i = 0; i < supportedRanges.length; i += 2) {
            minimumSupportedRangeValues.add(supportedRanges[i]);
        }

        assertWithMessage(
                        "ULTRASONICS_SENSOR_MEASURED_DISTANCE distance must be one of the "
                                + "minimum values in ULTRASONICS_SENSOR_SUPPORTED_RANGES, areaId: "
                                + areaId)
                .that(distance[0])
                .isIn(minimumSupportedRangeValues.build());
    }

    private static void verifyUltrasonicsMeasuredDistanceWithinDetectionRange(
            CarPropertyManager carPropertyManager, int areaId, Integer[] distance) {
        // Distance with length of 0 is valid. return because there are no values to verify.
        if (distance.length == 0) {
            return;
        }

        if (carPropertyManager.getCarPropertyConfig(
                        VehiclePropertyIds.ULTRASONICS_SENSOR_DETECTION_RANGE)
                == null) {
            return;
        }

        Integer[] detectionRange =
                (Integer[])
                        carPropertyManager
                                .getProperty(
                                        VehiclePropertyIds.ULTRASONICS_SENSOR_DETECTION_RANGE,
                                        areaId)
                                .getValue();
        assertWithMessage(
                        "ULTRASONICS_SENSOR_MEASURED_DISTANCE distance must "
                                + "be within the ULTRASONICS_SENSOR_DETECTION_RANGE, areaId: "
                                + areaId)
                .that(distance[0])
                .isIn(Range.closed(detectionRange[0], detectionRange[1]));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ELECTRONIC_TOLL_COLLECTION_CARD_TYPE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getElectronicTollCollectionCardTypeVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardType.UNKNOWN,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ELECTRONIC_TOLL_COLLECTION_CARD_STATUS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getElectronicTollCollectionCardStatusVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardStatus.UNKNOWN,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_VALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_INVALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#GENERAL_SAFETY_REGULATION_COMPLIANCE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getGeneralSafetyRegulationComplianceVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.GENERAL_SAFETY_REGULATION_COMPLIANCE)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_NOT_REQUIRED,
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_REQUIRED_V1));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_BRAKE_REGENERATION_LEVEL}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getEvBrakeRegenerationLevelVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_STOPPING_MODE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getEvStoppingModeVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_STOPPING_MODE)
                .setAllPossibleEnumValues(EV_STOPPING_MODES)
                .setAllPossibleUnwritableValues(EV_STOPPING_MODE_UNWRITABLE_STATES);
    }

    /** Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#DOOR_POS}. */
    public static VehiclePropertyVerifier.Builder<Integer> getDoorPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.DOOR_POS)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /** Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#DOOR_MOVE}. */
    public static VehiclePropertyVerifier.Builder<Integer> getDoorMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.DOOR_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#MIRROR_Z_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getMirrorZPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.MIRROR_Z_POS)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#MIRROR_Z_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getMirrorZMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.MIRROR_Z_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#MIRROR_Y_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getMirrorYPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.MIRROR_Y_POS)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#MIRROR_Y_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getMirrorYMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.MIRROR_Y_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /** Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#WINDOW_POS}. */
    public static VehiclePropertyVerifier.Builder<Integer> getWindowPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.WINDOW_POS)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#WINDOW_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getWindowMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.WINDOW_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#WINDSHIELD_WIPERS_PERIOD}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getWindshieldWipersPeriodVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.WINDSHIELD_WIPERS_PERIOD)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#WINDSHIELD_WIPERS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getWindshieldWipersSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH)
                .setAllPossibleEnumValues(WINDSHIELD_WIPERS_SWITCHES)
                .setAllPossibleUnwritableValues(WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES)
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            // Test to ensure that for both INTERMITTENT_LEVEL_* and
                            // CONTINUOUS_LEVEL_* the supportedEnumValues are consecutive.
                            // E.g. levels 1,2,3 is a valid config, but 1,3,4 is not valid because
                            // level 2 must be supported if level 3 or greater is supported.
                            ImmutableList<Integer> intermittentLevels =
                                    ImmutableList.<Integer>builder()
                                            .add(
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_5,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_4,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_3,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_2,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_1)
                                            .build();

                            ImmutableList<Integer> continuousLevels =
                                    ImmutableList.<Integer>builder()
                                            .add(
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_5,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_4,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_3,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_2,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_1)
                                            .build();

                            for (int areaId : carPropertyConfig.getAreaIds()) {
                                AreaIdConfig<Integer> areaIdConfig =
                                        (AreaIdConfig<Integer>)
                                                carPropertyConfig.getAreaIdConfig(areaId);
                                List<Integer> supportedEnumValues =
                                        areaIdConfig.getSupportedEnumValues();

                                verifyWindshieldWipersSwitchLevelsAreConsecutive(
                                        supportedEnumValues, intermittentLevels, areaId);
                                verifyWindshieldWipersSwitchLevelsAreConsecutive(
                                        supportedEnumValues, continuousLevels, areaId);
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_DEPTH_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelDepthPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_DEPTH_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelDepthMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_HEIGHT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelHeightPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_HEIGHT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelHeightMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#GLOVE_BOX_DOOR_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getGloveBoxDoorPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.GLOVE_BOX_DOOR_POS)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#DISTANCE_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getDistanceDisplayUnitsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.DISTANCE_DISPLAY_UNITS)
                .setAllPossibleEnumValues(DISTANCE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(DISTANCE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FUEL_VOLUME_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getFuelVolumeDisplayUnitsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS)
                .setAllPossibleEnumValues(VOLUME_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(VOLUME_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CRITICALLY_LOW_TIRE_PRESSURE}.
     */
    public static VehiclePropertyVerifier.Builder<Float>
            getCriticallyLowTirePressureVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                criticallyLowTirePressure) -> {
                            assertWithMessage(
                                            "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                    + " at Area ID equals to "
                                                    + areaId
                                                    + " must be greater than or equal to 0")
                                    .that(criticallyLowTirePressure)
                                    .isAtLeast(0);

                            CarPropertyConfig<?> tirePressureConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(VehiclePropertyIds.TIRE_PRESSURE);

                            if (tirePressureConfig == null
                                    || tirePressureConfig.getMinValue(areaId) == null) {
                                return;
                            }

                            assertWithMessage(
                                            "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                    + " at Area ID equals to "
                                                    + areaId
                                                    + " must not exceed"
                                                    + " minFloatValue in TIRE_PRESSURE")
                                    .that(criticallyLowTirePressure)
                                    .isAtMost((Float) tirePressureConfig.getMinValue(areaId));
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#TIRE_PRESSURE_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getTirePressureDisplayUnitsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS)
                .setAllPossibleEnumValues(PRESSURE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(PRESSURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_BATTERY_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getEvBatteryDisplayUnitsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS)
                .setAllPossibleEnumValues(BATTERY_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(BATTERY_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#VEHICLE_SPEED_DISPLAY_UNITS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getVehicleSpeedDisplayUnitsVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS)
                .setAllPossibleEnumValues(SPEED_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(SPEED_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_CURRENT_BATTERY_CAPACITY}.
     */
    public static VehiclePropertyVerifier.Builder<Float>
            getEvCurrentBatteryCapacityVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evCurrentBatteryCapacity) -> {
                            assertWithMessage(
                                            "EV_CURRENT_BATTERY_CAPACITY Float value must be"
                                                    + " greater than or equal to 0")
                                    .that(evCurrentBatteryCapacity)
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
                                                    /* areaId= */ 0);

                            assertWithMessage(
                                            "EV_CURRENT_BATTERY_CAPACITY Float value must not"
                                                    + " exceed INFO_EV_BATTERY_CAPACITY Float "
                                                    + "value")
                                    .that(evCurrentBatteryCapacity)
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_CHARGE_CURRENT_DRAW_LIMIT}.
     */
    public static VehiclePropertyVerifier.Builder<Float>
            getEvChargeCurrentDrawLimitVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT)
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT config array must be size"
                                                    + " 1")
                                    .that(configArray.size())
                                    .isEqualTo(1);

                            int maxCurrentDrawThresholdAmps = configArray.get(0);
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT config array first"
                                                + " element specifies max current draw allowed by"
                                                + " vehicle in amperes.")
                                    .that(maxCurrentDrawThresholdAmps)
                                    .isGreaterThan(0);
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evChargeCurrentDrawLimit) -> {
                            List<Integer> evChargeCurrentDrawLimitConfigArray =
                                    carPropertyConfig.getConfigArray();
                            int maxCurrentDrawThresholdAmps =
                                    evChargeCurrentDrawLimitConfigArray.get(0);

                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT value must be greater"
                                                    + " than 0")
                                    .that(evChargeCurrentDrawLimit)
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT value must be less than"
                                                + " or equal to max current draw by the vehicle")
                                    .that(evChargeCurrentDrawLimit)
                                    .isAtMost(maxCurrentDrawThresholdAmps);
                        })
                .setSupportedValuesGenerator(
                        (verifierContext, carPropertyConfig, areaId) -> {
                            // First value in the configArray specifies the max current draw allowed
                            // by the vehicle.
                            return ImmutableList.of(
                                    carPropertyConfig.getConfigArray().get(0).floatValue());
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_CHARGE_PERCENT_LIMIT}.
     */
    public static VehiclePropertyVerifier.Builder<Float> getEvChargePercentLimitVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT)
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            for (int i = 0; i < configArray.size(); i++) {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT configArray["
                                                        + i
                                                        + "] valid charge percent limit must be"
                                                        + " greater than 0")
                                        .that(configArray.get(i))
                                        .isGreaterThan(0);
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT configArray["
                                                        + i
                                                        + "] valid charge percent limit must be at"
                                                        + " most 100")
                                        .that(configArray.get(i))
                                        .isAtMost(100);
                            }
                        })
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evChargePercentLimit) -> {
                            List<Integer> evChargePercentLimitConfigArray =
                                    carPropertyConfig.getConfigArray();

                            if (evChargePercentLimitConfigArray.isEmpty()) {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be greater than"
                                                        + " 0")
                                        .that(evChargePercentLimit)
                                        .isGreaterThan(0);
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be at most 100")
                                        .that(evChargePercentLimit)
                                        .isAtMost(100);
                            } else {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be in the"
                                                        + " configArray valid charge percent limit"
                                                        + " list")
                                        .that(evChargePercentLimit.intValue())
                                        .isIn(evChargePercentLimitConfigArray);
                            }
                        })
                .setSupportedValuesGenerator(
                        (verifierContext, carPropertyConfig, areaId) -> {
                            ImmutableList.Builder<Float> possibleValues = ImmutableList.builder();
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            if (!configArray.isEmpty()) {
                                for (Integer possibleEvChargePercentLimit : configArray) {
                                    possibleValues.add(possibleEvChargePercentLimit.floatValue());
                                }
                            } else {
                                // If the configArray is not specified, then values between 0 and
                                // 100 percent must
                                // be supported.
                                possibleValues.add(0f);
                                possibleValues.add(100f);
                            }
                            return possibleValues.build();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_CHARGE_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getEvChargeStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_CHARGE_STATE)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                EvChargeState.STATE_UNKNOWN,
                                EvChargeState.STATE_CHARGING,
                                EvChargeState.STATE_FULLY_CHARGED,
                                EvChargeState.STATE_NOT_CHARGING,
                                EvChargeState.STATE_ERROR));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_CHARGE_TIME_REMAINING}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getEvChargeTimeRemainingVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_CHARGE_TIME_REMAINING)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                evChargeTimeRemaining) ->
                                assertWithMessage(
                                                "EV_CHARGE_TIME_REMAINING Integer value"
                                                        + " must be greater than or equal to 0")
                                        .that(evChargeTimeRemaining)
                                        .isAtLeast(0));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#EV_REGENERATIVE_BRAKING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getEvRegenerativeBrakingStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                EvRegenerativeBrakingState.STATE_UNKNOWN,
                                EvRegenerativeBrakingState.STATE_DISABLED,
                                EvRegenerativeBrakingState.STATE_PARTIALLY_ENABLED,
                                EvRegenerativeBrakingState.STATE_FULLY_ENABLED));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ENGINE_OIL_LEVEL}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getEngineOilLevelVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ENGINE_OIL_LEVEL)
                .setAllPossibleEnumValues(VEHICLE_OIL_LEVELS);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#IMPACT_DETECTED}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getImpactDetectedVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.IMPACT_DETECTED)
                .setAllPossibleEnumValues(IMPACT_SENSOR_LOCATIONS)
                .setBitMapEnumEnabled(true);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#TURN_SIGNAL_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getTurnSignalStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.TURN_SIGNAL_STATE)
                .setAllPossibleEnumValues(TURN_SIGNAL_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HEADLIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHeadlightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HEADLIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HIGH_BEAM_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHighBeamLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FOG_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getFogLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fogLightsState) -> {
                            assertWithMessage(
                                            "FRONT_FOG_LIGHTS_STATE must not be implemented"
                                                    + " when FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds
                                                                    .FRONT_FOG_LIGHTS_STATE))
                                    .isNull();

                            assertWithMessage(
                                            "REAR_FOG_LIGHTS_STATE must not be implemented"
                                                    + " when FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds
                                                                    .REAR_FOG_LIGHTS_STATE))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HAZARD_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHazardLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FRONT_FOG_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getFrontFogLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                frontFogLightsState) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + " when FRONT_FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds.FOG_LIGHTS_STATE))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#REAR_FOG_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getRearFogLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                rearFogLightsState) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + " when REAR_FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds.FOG_LIGHTS_STATE))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CABIN_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getCabinLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#READING_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getReadingLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.READING_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HEADLIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHeadlightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HEADLIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#TRAILER_PRESENT}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getTrailerPresentVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.TRAILER_PRESENT)
                .setAllPossibleEnumValues(TRAILER_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HIGH_BEAM_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getHighBeamLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FOG_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getFogLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                fogLightsSwitch) -> {
                            assertWithMessage(
                                            "FRONT_FOG_LIGHTS_SWITCH must not be implemented"
                                                    + " when FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds
                                                                    .FRONT_FOG_LIGHTS_SWITCH))
                                    .isNull();

                            assertWithMessage(
                                            "REAR_FOG_LIGHTS_SWITCH must not be implemented"
                                                    + " when FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds
                                                                    .REAR_FOG_LIGHTS_SWITCH))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#HAZARD_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getHazardLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FRONT_FOG_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getFrontFogLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                frontFogLightsSwitch) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_SWITCH must not be implemented when"
                                                    + " FRONT_FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds.FOG_LIGHTS_SWITCH))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#REAR_FOG_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getRearFogLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                rearFogLightsSwitch) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_SWITCH must not be implemented"
                                                    + " when REAR_FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getCarPropertyConfig(
                                                            VehiclePropertyIds.FOG_LIGHTS_SWITCH))
                                    .isNull();
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CABIN_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getCabinLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#READING_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getReadingLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.READING_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#STEERING_WHEEL_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSteeringWheelLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_MEMORY_SELECT}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatMemorySelectVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SELECT)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            int[] areaIds = carPropertyConfig.getAreaIds();
                            CarPropertyConfig<?> seatMemorySetCarPropertyConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
                                                    VehiclePropertyIds.SEAT_MEMORY_SET);

                            assertWithMessage(
                                            "SEAT_MEMORY_SET must be implemented if "
                                                    + "SEAT_MEMORY_SELECT is implemented")
                                    .that(seatMemorySetCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "SEAT_MEMORY_SELECT area IDs must match the area IDs of"
                                                    + " SEAT_MEMORY_SET")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            seatMemorySetCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));

                            for (int areaId : areaIds) {
                                Integer seatMemorySetAreaIdMaxValue =
                                        (Integer)
                                                seatMemorySetCarPropertyConfig.getMaxValue(areaId);
                                assertWithMessage(
                                                "SEAT_MEMORY_SET - area ID: "
                                                        + areaId
                                                        + " must have max value defined")
                                        .that(seatMemorySetAreaIdMaxValue)
                                        .isNotNull();
                                assertWithMessage(
                                                "SEAT_MEMORY_SELECT - area ID: "
                                                        + areaId
                                                        + "'s max value must be equal to"
                                                        + " SEAT_MEMORY_SET's max value under the"
                                                        + " same area ID")
                                        .that(seatMemorySetAreaIdMaxValue)
                                        .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_MEMORY_SET}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatMemorySetVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SET)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .setCarPropertyConfigVerifier(
                        (verifierContext, carPropertyConfig) -> {
                            int[] areaIds = carPropertyConfig.getAreaIds();
                            CarPropertyConfig<?> seatMemorySelectCarPropertyConfig =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
                                                    VehiclePropertyIds.SEAT_MEMORY_SELECT);

                            assertWithMessage(
                                            "SEAT_MEMORY_SELECT must be implemented if "
                                                    + "SEAT_MEMORY_SET is implemented")
                                    .that(seatMemorySelectCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "SEAT_MEMORY_SET area IDs must match the area IDs of "
                                                    + "SEAT_MEMORY_SELECT")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            seatMemorySelectCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));

                            for (int areaId : areaIds) {
                                Integer seatMemorySelectAreaIdMaxValue =
                                        (Integer)
                                                seatMemorySelectCarPropertyConfig.getMaxValue(
                                                        areaId);
                                assertWithMessage(
                                                "SEAT_MEMORY_SELECT - area ID: "
                                                        + areaId
                                                        + " must have max value defined")
                                        .that(seatMemorySelectAreaIdMaxValue)
                                        .isNotNull();
                                assertWithMessage(
                                                "SEAT_MEMORY_SET - area ID: "
                                                        + areaId
                                                        + "'s max value must be equal to"
                                                        + " SEAT_MEMORY_SELECT's max value under"
                                                        + " the same area ID")
                                        .that(seatMemorySelectAreaIdMaxValue)
                                        .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                            }
                        });
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BELT_HEIGHT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatBeltHeightPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BELT_HEIGHT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatBeltHeightMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_FORE_AFT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatForeAftPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BACKREST_ANGLE_1_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatBackrestAngle1PosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_FORE_AFT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatForeAftMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BACKREST_ANGLE_1_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatBackrestAngle1MoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BACKREST_ANGLE_2_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatBackrestAngle2PosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_BACKREST_ANGLE_2_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatBackrestAngle2MoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEIGHT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatHeightPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEIGHT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatHeightMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#SEAT_DEPTH_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatDepthPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.SEAT_DEPTH_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_DEPTH_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatDepthMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_DEPTH_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#SEAT_TILT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatTiltPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.SEAT_TILT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link VehiclePropertyIds#SEAT_TILT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatTiltMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.SEAT_TILT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_FORE_AFT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarForeAftPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_FORE_AFT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarForeAftMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_SIDE_SUPPORT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarSideSupportPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_SIDE_SUPPORT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarSideSupportMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_HEIGHT_POS_V2}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestHeightPosV2VerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS_V2)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_HEIGHT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestHeightMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_ANGLE_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestAnglePosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_ANGLE_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestAngleMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_FORE_AFT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestForeAftPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_HEADREST_FORE_AFT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatHeadrestForeAftMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_FOOTWELL_LIGHTS_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatFootwellLightsStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_STATE)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_FOOTWELL_LIGHTS_SWITCH}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatFootwellLightsSwitchVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_SWITCH)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_CUSHION_SIDE_SUPPORT_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatCushionSideSupportPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_CUSHION_SIDE_SUPPORT_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatCushionSideSupportMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_VERTICAL_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarVerticalPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_POS)
                .requireMinMaxValues();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_LUMBAR_VERTICAL_MOVE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getSeatLumbarVerticalMoveVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_MOVE)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_WALK_IN_POS}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatWalkInPosVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_WALK_IN_POS)
                .requireMinMaxValues()
                .requireMinValuesToBeZero();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#SEAT_AIRBAGS_DEPLOYED}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getSeatAirbagsDeployedVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.SEAT_AIRBAGS_DEPLOYED)
                .setAllPossibleEnumValues(VEHICLE_AIRBAG_LOCATIONS)
                .setBitMapEnumEnabled(true);
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#AUTOMATIC_EMERGENCY_BRAKING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getAutomaticEmergencyBrakingStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(AUTOMATIC_EMERGENCY_BRAKING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#FORWARD_COLLISION_WARNING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getForwardCollisionWarningStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(FORWARD_COLLISION_WARNING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.FORWARD_COLLISION_WARNING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#BLIND_SPOT_WARNING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getBlindSpotWarningStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(BLIND_SPOT_WARNING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.BLIND_SPOT_WARNING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LANE_DEPARTURE_WARNING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLaneDepartureWarningStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(LANE_DEPARTURE_WARNING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LANE_DEPARTURE_WARNING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LANE_KEEP_ASSIST_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer> getLaneKeepAssistStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(LANE_KEEP_ASSIST_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LANE_KEEP_ASSIST_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.LANE_KEEP_ASSIST_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LANE_CENTERING_ASSIST_COMMAND}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLaneCenteringAssistCommandVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_COMMAND)
                .setAllPossibleEnumValues(LANE_CENTERING_ASSIST_COMMANDS)
                .setDependentOnProperty(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS));
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLaneCenteringAssistStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(LANE_CENTERING_ASSIST_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LOW_SPEED_COLLISION_WARNING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLowSpeedCollisionWarningStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(LOW_SPEED_COLLISION_WARNING_STATES)
                        .add(
                                ErrorState.OTHER_ERROR_STATE,
                                ErrorState.NOT_AVAILABLE_DISABLED,
                                ErrorState.NOT_AVAILABLE_SPEED_HIGH,
                                ErrorState.NOT_AVAILABLE_POOR_VISIBILITY,
                                ErrorState.NOT_AVAILABLE_SAFETY)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LOW_SPEED_COLLISION_WARNING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.LOW_SPEED_COLLISION_WARNING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#ELECTRONIC_STABILITY_CONTROL_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getElectronicStabilityControlStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(ELECTRONIC_STABILITY_CONTROL_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.ELECTRONIC_STABILITY_CONTROL_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.ELECTRONIC_STABILITY_CONTROL_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_CAR_DYNAMICS_STATE,
                                Car.PERMISSION_CONTROL_CAR_DYNAMICS_STATE))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#CROSS_TRAFFIC_MONITORING_WARNING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getCrossTrafficMonitoringWarningStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(CROSS_TRAFFIC_MONITORING_WARNING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.CROSS_TRAFFIC_MONITORING_WARNING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.CROSS_TRAFFIC_MONITORING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /**
     * Gets a {@link VehiclePropertyVerifier.Builder} for {@link
     * VehiclePropertyIds#LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE}.
     */
    public static VehiclePropertyVerifier.Builder<Integer>
            getLowSpeedAutomaticEmergencyBrakingStateVerifierBuilder() {
        ImmutableSet<Integer> combinedCarPropertyValues =
                ImmutableSet.<Integer>builder()
                        .addAll(LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATES)
                        .addAll(ERROR_STATES)
                        .build();

        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_STATE)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .setDependentOnProperty(
                        VehiclePropertyIds.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                        ImmutableSet.of(
                                Car.PERMISSION_READ_ADAS_SETTINGS,
                                Car.PERMISSION_CONTROL_ADAS_SETTINGS))
                .verifyErrorStates();
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#IGNITION_STATE}. */
    public static VehiclePropertyVerifier.Builder<Integer> getIgnitionStateVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.IGNITION_STATE)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleIgnitionState.UNDEFINED,
                                VehicleIgnitionState.LOCK,
                                VehicleIgnitionState.OFF,
                                VehicleIgnitionState.ACC,
                                VehicleIgnitionState.ON,
                                VehicleIgnitionState.START));
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_MAKE}. */
    public static VehiclePropertyVerifier.Builder<String> getInfoMakeVerifierBuilder() {
        return VehiclePropertyVerifier.<String>newDefaultBuilder(VehiclePropertyIds.INFO_MAKE)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                make) ->
                                assertWithMessage("INFO_MAKE must not be empty")
                                        .that(make)
                                        .isNotEmpty());
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_MODEL}. */
    public static VehiclePropertyVerifier.Builder<String> getInfoModelVerifierBuilder() {
        return VehiclePropertyVerifier.<String>newDefaultBuilder(VehiclePropertyIds.INFO_MODEL)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                model) ->
                                assertWithMessage("INFO_MODEL must not be empty")
                                        .that(model)
                                        .isNotEmpty());
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#INFO_MODEL_YEAR}. */
    public static VehiclePropertyVerifier.Builder<Integer> getInfoModelYearVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(
                        VehiclePropertyIds.INFO_MODEL_YEAR)
                .setCarPropertyValueVerifier(
                        (verifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                modelYear) -> {
                            int currentYear = Year.now().getValue();
                            int minYear = currentYear + REASONABLE_PAST_MODEL_YEAR_OFFSET;
                            int maxYear = currentYear + REASONABLE_FUTURE_MODEL_YEAR_OFFSET;

                            assertWithMessage(
                                            String.format(
                                                    "INFO_MODEL_YEAR must be within a reasonable"
                                                        + " range. Current year: %d, model year:"
                                                        + " %d, valid range: [%d, %d]",
                                                    currentYear, modelYear, minYear, maxYear))
                                    .that(modelYear)
                                    .isIn(Range.closed(minYear, maxYear));
                        });
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#PARKING_BRAKE_ON}. */
    public static VehiclePropertyVerifier.Builder<Boolean> getParkingBrakeOnVerifierBuilder() {
        return VehiclePropertyVerifier.<Boolean>newDefaultBuilder(
                        VehiclePropertyIds.PARKING_BRAKE_ON)
                .requireProperty();
    }

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
                                                                + " than or equal to 0")
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

    /** Gets the verifier builder for {@link VehiclePropertyIds#GEAR_SELECTION}. */
    public static VehiclePropertyVerifier.Builder<Integer> getGearSelectionVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.GEAR_SELECTION)
                .requireProperty()
                .setAllPossibleEnumValues(VEHICLE_GEARS)
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            assertWithMessage(
                                            "GEAR_SELECTION must list GEAR_REVERSE and GEAR_NEUTRAL"
                                                    + " in the config array.")
                                    .that(configArray)
                                    .containsAtLeast(
                                            VehicleGear.GEAR_REVERSE, VehicleGear.GEAR_NEUTRAL);
                            assertWithMessage(
                                            "GEAR_SELECTION must list GEAR_FIRST or both GEAR_DRIVE"
                                                    + " and GEAR_PARK in the config array.")
                                    .that(
                                            configArray.containsAll(
                                                            ImmutableList.of(
                                                                    VehicleGear.GEAR_DRIVE,
                                                                    VehicleGear.GEAR_PARK))
                                                    || configArray.contains(VehicleGear.GEAR_FIRST))
                                    .isTrue();
                        });
    }

    /** Gets the verifier builder for {@link VehiclePropertyIds#CURRENT_GEAR}. */
    public static VehiclePropertyVerifier.Builder<Integer> getCurrentGearVerifierBuilder() {
        return VehiclePropertyVerifier.<Integer>newDefaultBuilder(VehiclePropertyIds.CURRENT_GEAR)
                .setAllPossibleEnumValues(VEHICLE_GEARS)
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .setConfigArrayVerifier(
                        (verifierContext, configArray) -> {
                            assertWithMessage(
                                            "CURRENT_GEAR must list GEAR_REVERSE and GEAR_NEUTRAL"
                                                    + " in the config array.")
                                    .that(configArray)
                                    .containsAtLeast(
                                            VehicleGear.GEAR_REVERSE, VehicleGear.GEAR_NEUTRAL);
                            assertWithMessage(
                                            "CURRENT_GEAR must list GEAR_FIRST or both GEAR_DRIVE"
                                                    + " and GEAR_PARK in the config array.")
                                    .that(
                                            configArray.containsAll(
                                                            ImmutableList.of(
                                                                    VehicleGear.GEAR_DRIVE,
                                                                    VehicleGear.GEAR_PARK))
                                                    || configArray.contains(VehicleGear.GEAR_FIRST))
                                    .isTrue();
                        });
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
                                                                + " than or equal to 0")
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

        if (VehiclePropertyVerifier.isAtLeastB()) {
            verifierBuilder.requireMinMaxValues();
            if (Flags.vehicleProperty25q23pPermissions()) {
                verifierBuilder.addReadPermission(Car.PERMISSION_TIRES_3P);
            }
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
                                                + " DEAD_RECKONED or RAW_GNSS_ONLY set. They cannot"
                                                + " both be set. Found value: "
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
                        .setSupportedValuesGenerator(
                                (verifierContext, carPropertyConfig, areaId) -> {
                                    ImmutableList.Builder<Integer> possibleValues =
                                            ImmutableList.builder();
                                    int[] availableHvacFanDirections =
                                            verifierContext
                                                    .getCarPropertyManager()
                                                    .getIntArrayProperty(
                                                            VehiclePropertyIds
                                                                    .HVAC_FAN_DIRECTION_AVAILABLE,
                                                            areaId);
                                    for (int direction : availableHvacFanDirections) {
                                        if (!CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES.contains(
                                                direction)) {
                                            possibleValues.add(direction);
                                        }
                                    }
                                    return possibleValues.build();
                                });

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
                        })
                .setSupportedValuesGenerator(
                        (verifierContext, carPropertyConfig, areaId) -> {
                            ImmutableList.Builder<Float> possibleValues = ImmutableList.builder();
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            if (!configArray.isEmpty()) {
                                // For HVAC_TEMPERATURE_SET, the configArray specifies the supported
                                // temperature
                                // values for the property. configArray[0] is the lower bound of the
                                // supported
                                // temperatures in Celsius. configArray[1] is the upper bound of the
                                // supported
                                // temperatures in Celsius. configArray[2] is the supported
                                // temperature increment
                                // between the two bounds. All configArray values are Celsius*10
                                // since the
                                // configArray is List<Integer> but HVAC_TEMPERATURE_SET is a Float
                                // type property.
                                for (int possibleHvacTempSetValue = configArray.get(0);
                                        possibleHvacTempSetValue <= configArray.get(1);
                                        possibleHvacTempSetValue += configArray.get(2)) {
                                    possibleValues.add((float) possibleHvacTempSetValue / 10.0f);
                                }
                            } else {
                                // If the configArray is not specified, then use min/max values.
                                Float minValueFloat = (Float) carPropertyConfig.getMinValue(areaId);
                                Float maxValueFloat = (Float) carPropertyConfig.getMaxValue(areaId);
                                possibleValues.add(minValueFloat);
                                possibleValues.add(maxValueFloat);
                            }
                            return possibleValues.build();
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
            allPossibleFanDirectionsBuilder.addAll(
                    Sets.combinations(SINGLE_HVAC_FAN_DIRECTIONS, i).stream()
                            .map(
                                    hvacFanDirectionCombo -> {
                                        Integer possibleHvacFanDirection = 0;
                                        for (Integer hvacFanDirection : hvacFanDirectionCombo) {
                                            possibleHvacFanDirection |= hvacFanDirection;
                                        }
                                        return possibleHvacFanDirection;
                                    })
                            .collect(Collectors.toList()));
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

    /** Gets the verifier builder for {@link VehiclePropertyIds#PERF_VEHICLE_SPEED}. */
    public static VehiclePropertyVerifier.Builder<Float> getPerfVehicleSpeedVerifierBuilder() {
        return VehiclePropertyVerifier.<Float>newDefaultBuilder(
                        VehiclePropertyIds.PERF_VEHICLE_SPEED)
                .requireProperty();
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
                                                        + " or equal to 0")
                                        .that(rangeRemaining)
                                        .isAtLeast(0))
                .setSupportedValuesGenerator(
                        (verifierContext, carPropertyConfig, areaId) -> {
                            // Test when no range is remaining
                            return ImmutableList.of(0f);
                        });
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
                                                    + " equal to 0")
                                    .that(evBatteryLevel)
                                    .isAtLeast(0);

                            if (verifierContext
                                            .getCarPropertyManager()
                                            .getCarPropertyConfig(
                                                    VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> evCurrentBatteryCapacityValue =
                                    verifierContext
                                            .getCarPropertyManager()
                                            .getProperty(
                                                    VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
                                                    /* areaId= */ 0);

                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must not exceed "
                                                    + "EV_CURRENT_BATTERY_CAPACITY Float "
                                                    + "value")
                                    .that(evBatteryLevel)
                                    .isAtMost((Float) evCurrentBatteryCapacityValue.getValue());
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
                                                    + " to 0")
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
