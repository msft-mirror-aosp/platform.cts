/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.VibrationAttributes;
import android.os.Vibrator;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom rule that temporarily sets all vibration intensity settings to HIGH for the duration of a
 * test and restores original settings on teardown. This rule is only applied to XR form factors
 * where default vibration intensity is OFF.
 */
public class XrVibrationSettingsRule extends ExternalResource {
    // Hidden Settings.System keys for vibration intensity
    private static final String KEY_HAPTIC_FEEDBACK_INTENSITY = "haptic_feedback_intensity";
    private static final String KEY_NOTIFICATION_VIBRATION_INTENSITY =
            "notification_vibration_intensity";
    private static final String KEY_RING_VIBRATION_INTENSITY = "ring_vibration_intensity";
    private static final String KEY_ALARM_VIBRATION_INTENSITY = "alarm_vibration_intensity";
    private static final String KEY_MEDIA_VIBRATION_INTENSITY = "media_vibration_intensity";
    private static final String KEY_HARDWARE_HAPTIC_FEEDBACK_INTENSITY =
            "hardware_haptic_feedback_intensity";

    private static final String[] VIBRATION_INTENSITY_SETTINGS = {
        KEY_HAPTIC_FEEDBACK_INTENSITY,
        KEY_NOTIFICATION_VIBRATION_INTENSITY,
        KEY_RING_VIBRATION_INTENSITY,
        KEY_ALARM_VIBRATION_INTENSITY,
        KEY_MEDIA_VIBRATION_INTENSITY,
        KEY_HARDWARE_HAPTIC_FEEDBACK_INTENSITY,
    };

    private static final String INTENSITY_HIGH =
            Integer.toString(Vibrator.VIBRATION_INTENSITY_HIGH);

    private static final String FEATURE_XR_API_SPATIAL = "android.software.xr.api.spatial";

    private final Map<String, String> mOriginalIntensitySettings = new HashMap<>();
    private boolean mShouldOverride = false;

    /** Checks whether the device is XR headset. */
    private static boolean isXrHeadset(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(FEATURE_XR_API_SPATIAL);
    }

    private boolean shouldOverrideSettings(Context context) {
        if (!isXrHeadset(context)) {
            return false;
        }

        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }

        int defaultTouchIntensity =
                vibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_TOUCH);
        return defaultTouchIntensity == Vibrator.VIBRATION_INTENSITY_OFF;
    }

    @Override
    protected void before() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mShouldOverride = shouldOverrideSettings(context);
        if (!mShouldOverride) {
            return;
        }

        mOriginalIntensitySettings.clear();
        for (String setting : VIBRATION_INTENSITY_SETTINGS) {
            String val = getSystemSetting(setting);
            mOriginalIntensitySettings.put(
                    setting, ("null".equals(val) || val.isEmpty()) ? null : val);
            putSystemSetting(setting, INTENSITY_HIGH);
        }
    }

    @Override
    protected void after() {
        if (!mShouldOverride) {
            return;
        }

        for (Map.Entry<String, String> entry : mOriginalIntensitySettings.entrySet()) {
            if (entry.getValue() != null) {
                putSystemSetting(entry.getKey(), entry.getValue());
            } else {
                deleteSystemSetting(entry.getKey());
            }
        }
    }

    private static String getSystemSetting(String key) {
        return SystemUtil.runShellCommand(String.format("settings get system %s", key)).trim();
    }

    private static void putSystemSetting(String key, String value) {
        SystemUtil.runShellCommand(String.format("settings put system %s %s", key, value));
    }

    private static void deleteSystemSetting(String key) {
        SystemUtil.runShellCommand(String.format("settings delete system %s", key));
    }
}
