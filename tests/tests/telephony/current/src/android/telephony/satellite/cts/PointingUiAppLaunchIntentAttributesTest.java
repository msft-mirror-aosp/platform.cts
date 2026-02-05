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

package android.telephony.satellite.cts;

import static com.google.common.truth.Truth.assertThat;

import android.telephony.satellite.PointingUiAppLaunchIntentAttributes;
import org.junit.Test;

public class PointingUiAppLaunchIntentAttributesTest {

    @Test
    public void testBuilder() {
        PointingUiAppLaunchIntentAttributes attributes =
                new PointingUiAppLaunchIntentAttributes.Builder()
                        .setFullScreen(true)
                        .setDemoMode(true)
                        .setEmergencyMode(true)
                        .build();

        assertThat(attributes.isFullScreen()).isTrue();
        assertThat(attributes.isDemoMode()).isTrue();
        assertThat(attributes.isEmergencyMode()).isTrue();

        attributes = new PointingUiAppLaunchIntentAttributes.Builder()
                .setFullScreen(false)
                .setDemoMode(false)
                .setEmergencyMode(false)
                .build();

        assertThat(attributes.isFullScreen()).isFalse();
        assertThat(attributes.isDemoMode()).isFalse();
        assertThat(attributes.isEmergencyMode()).isFalse();
    }
}
