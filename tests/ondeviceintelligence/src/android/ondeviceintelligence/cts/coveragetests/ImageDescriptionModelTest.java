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

package android.ondeviceintelligence.cts.coveragetests;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2;

import static com.google.common.truth.Truth.assertThat;

import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImageDescriptionModelTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testImageDescriptionModelMethods() {
        Feature feature = new Feature.Builder(1).build();
        android.os.LocaleList locales = android.os.LocaleList.forLanguageTags("en-US");
        ImageDescriptionModel model = new ImageDescriptionModel(feature, "signature", 100, locales);

        assertThat(model.getMaxTokenLimit()).isEqualTo(100);
        assertThat(model.getModelSignature()).isEqualTo("signature");
        assertThat(model.getSupportedLocales()).isEqualTo(locales);
    }
}
