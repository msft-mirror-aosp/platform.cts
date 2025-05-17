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

package android.os.cts;

import static android.os.VibrationAttributes.USAGE_ACCESSIBILITY;
import static android.os.VibrationAttributes.USAGE_TOUCH;
import static android.os.VibrationAttributes.USAGE_UNKNOWN;
import static android.os.vibrator.Flags.FLAG_HAPTIC_FEEDBACK_WITH_CUSTOM_USAGE;
import static android.view.HapticFeedbackConstants.CONFIRM;
import static android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
import static android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
import static android.view.HapticFeedbackConstants.TOGGLE_ON;

import static com.google.common.truth.Truth.assertThat;

import android.os.vibrator.HapticFeedbackRequest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link HapticFeedbackRequest}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_HAPTIC_FEEDBACK_WITH_CUSTOM_USAGE)
public class HapticFeedbackRequestTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testBuildRequest_validFeedbackParams() {
        HapticFeedbackRequest request =
                new HapticFeedbackRequest.Builder(TOGGLE_ON)
                        .setUsage(USAGE_ACCESSIBILITY)
                        .setFlags(FLAG_IGNORE_VIEW_SETTING)
                        .build();

        assertThat(request.getFeedbackConstant()).isEqualTo(TOGGLE_ON);
        assertThat(request.getUsage()).isEqualTo(USAGE_ACCESSIBILITY);
        assertThat(request.getFlags()).isEqualTo(FLAG_IGNORE_VIEW_SETTING);
    }

    @Test
    public void testBuildRequest_fromAnotherRequest() {
        HapticFeedbackRequest origRequest =
                new HapticFeedbackRequest.Builder(TOGGLE_ON)
                        .setUsage(USAGE_ACCESSIBILITY)
                        .setFlags(FLAG_IGNORE_VIEW_SETTING)
                        .build();

        HapticFeedbackRequest newRequest = new HapticFeedbackRequest.Builder(origRequest).build();

        assertThat(newRequest.getFeedbackConstant()).isEqualTo(TOGGLE_ON);
        assertThat(newRequest.getUsage()).isEqualTo(USAGE_ACCESSIBILITY);
        assertThat(newRequest.getFlags()).isEqualTo(FLAG_IGNORE_VIEW_SETTING);

        newRequest =
                new HapticFeedbackRequest.Builder(origRequest)
                        .setUsage(USAGE_TOUCH)
                        .setFlags(0)
                        .build();

        assertThat(newRequest.getFeedbackConstant()).isEqualTo(TOGGLE_ON);
        assertThat(newRequest.getUsage()).isEqualTo(USAGE_TOUCH);
        assertThat(newRequest.getFlags()).isEqualTo(0);
    }

    @Test
    public void testBuildRequest_defaultUsageIsUnknown() {
        HapticFeedbackRequest request = new HapticFeedbackRequest.Builder(TOGGLE_ON).build();

        assertThat(request.getUsage()).isEqualTo(USAGE_UNKNOWN);
    }

    @Test
    public void testBuildRequest_defaultFlagIsZero() {
        HapticFeedbackRequest request = new HapticFeedbackRequest.Builder(TOGGLE_ON).build();

        assertThat(request.getFlags()).isEqualTo(0);
    }

    @Test
    public void testEqualityAndHashCode() {
        HapticFeedbackRequest.Builder builder =
                new HapticFeedbackRequest.Builder(TOGGLE_ON)
                        .setUsage(USAGE_ACCESSIBILITY)
                        .setFlags(FLAG_IGNORE_VIEW_SETTING);
        HapticFeedbackRequest request = builder.build();

        assertThat(request).isEqualTo(request);
        assertThat(request.hashCode()).isEqualTo(request.hashCode());

        HapticFeedbackRequest sameRequest =
                new HapticFeedbackRequest.Builder(TOGGLE_ON)
                        .setUsage(USAGE_ACCESSIBILITY)
                        .setFlags(FLAG_IGNORE_VIEW_SETTING)
                        .build();
        assertThat(request).isEqualTo(request);
        assertThat(request.hashCode()).isEqualTo(sameRequest.hashCode());

        sameRequest = new HapticFeedbackRequest.Builder(request).build();
        assertThat(request).isEqualTo(request);
        assertThat(request.hashCode()).isEqualTo(sameRequest.hashCode());

        // Constant values affect equality
        HapticFeedbackRequest otherRequest =
                new HapticFeedbackRequest.Builder(CONFIRM)
                        .setUsage(USAGE_ACCESSIBILITY)
                        .setFlags(FLAG_IGNORE_VIEW_SETTING)
                        .build();
        assertThat(request).isNotEqualTo(otherRequest);
        assertThat(request.hashCode()).isNotEqualTo(otherRequest.hashCode());

        // Flag values affect equality
        otherRequest =
                builder.setFlags(FLAG_IGNORE_GLOBAL_SETTING | FLAG_IGNORE_VIEW_SETTING).build();
        assertThat(request).isNotEqualTo(otherRequest);
        assertThat(request.hashCode()).isNotEqualTo(otherRequest.hashCode());

        otherRequest = builder.setFlags(0).build();
        assertThat(request).isNotEqualTo(otherRequest);
        assertThat(request.hashCode()).isNotEqualTo(otherRequest.hashCode());

        // Usage values affect equality
        otherRequest = builder.setUsage(USAGE_UNKNOWN).build();
        assertThat(request).isNotEqualTo(otherRequest);
        assertThat(request.hashCode()).isNotEqualTo(otherRequest.hashCode());

        otherRequest = builder.setUsage(USAGE_TOUCH).build();
        assertThat(request).isNotEqualTo(otherRequest);
        assertThat(request.hashCode()).isNotEqualTo(otherRequest.hashCode());
    }
}
