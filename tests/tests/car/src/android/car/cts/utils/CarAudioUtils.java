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

package android.car.cts.utils;

import android.media.AudioAttributes;

public final class CarAudioUtils {
    private CarAudioUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Convert usage to string.
     *
     * @param usage usage value
     * @return usage string representation for debug purposes
     */
    public static String usageToString(int usage) {
        return switch (usage) {
            case AudioAttributes.USAGE_UNKNOWN -> "USAGE_UNKNOWN";
            case AudioAttributes.USAGE_MEDIA -> "USAGE_MEDIA";
            case AudioAttributes.USAGE_VOICE_COMMUNICATION -> "USAGE_VOICE_COMMUNICATION";
            case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ->
                    "USAGE_VOICE_COMMUNICATION_SIGNALLING";
            case AudioAttributes.USAGE_ALARM -> "USAGE_ALARM";
            case AudioAttributes.USAGE_NOTIFICATION -> "USAGE_NOTIFICATION";
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "USAGE_NOTIFICATION_RINGTONE";
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST ->
                    "USAGE_NOTIFICATION_COMMUNICATION_REQUEST";
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT ->
                    "USAGE_NOTIFICATION_COMMUNICATION_INSTANT";
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED ->
                    "USAGE_NOTIFICATION_COMMUNICATION_DELAYED";
            case AudioAttributes.USAGE_NOTIFICATION_EVENT -> "USAGE_NOTIFICATION_EVENT";
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY -> "USAGE_ASSISTANCE_ACCESSIBILITY";
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ->
                    "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE";
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "USAGE_ASSISTANCE_SONIFICATION";
            case AudioAttributes.USAGE_GAME -> "USAGE_GAME";
            case AudioAttributes.USAGE_ASSISTANT -> "USAGE_ASSISTANT";
            case AudioAttributes.USAGE_CALL_ASSISTANT -> "USAGE_CALL_ASSISTANT";
            case AudioAttributes.USAGE_EMERGENCY -> "USAGE_EMERGENCY";
            case AudioAttributes.USAGE_SAFETY -> "USAGE_SAFETY";
            case AudioAttributes.USAGE_VEHICLE_STATUS -> "USAGE_VEHICLE_STATUS";
            case AudioAttributes.USAGE_ANNOUNCEMENT -> "USAGE_ANNOUNCEMENT";
            case AudioAttributes.USAGE_SPEAKER_CLEANUP -> "USAGE_SPEAKER_CLEANUP";
            default -> "unknown usage " + usage;
        };
    }
}
