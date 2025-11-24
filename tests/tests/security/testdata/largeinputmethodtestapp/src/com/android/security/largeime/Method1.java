/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.security.largeime;

import android.content.ComponentName;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.stream.IntStream;

public class Method1 extends InputMethodService {

    // Note that 1400 (chars) * 70 (subtypes) * 6 (string attrs) = 588K is still quite smaller than
    // the max of single binder size (1MB).
    // Also without change Ied64a9f018fd3e79cfc51ccd82d361b43e5f29dc, this creates a large enough
    // data that can sent from application but exceeds the 1MB if combined with manifest subtypes.
    private static final int NUM_ADDITIONAL_SUBTYPES = 70;
    private static final int LONG_STRING_LENGTH = 1400;

    // The value is used as a key to check if the subtypes are added from the test.
    private static final String ADDITIONAL_SUBTYPE_NAME = "Additional Type";

    // The number of subtypes defined in method1.xml.
    private static final int STATIC_SUBTYPE_COUNT = 130;

    @Override
    public void onCreate() {
        super.onCreate();

        InputMethodManager imm = getSystemService(InputMethodManager.class);

        ArrayList<InputMethodSubtype> additionalSubtypes = new ArrayList<>(NUM_ADDITIONAL_SUBTYPES);
        InputMethodSubtype normalSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeId(0x12345)
                        .setSubtypeNameOverride(ADDITIONAL_SUBTYPE_NAME)
                        .build();
        additionalSubtypes.add(normalSubtype);

        for (int i = 1; i < NUM_ADDITIONAL_SUBTYPES; i++) {
            String longText = "a".repeat(LONG_STRING_LENGTH);

            InputMethodSubtype largeSubtype =
                    new InputMethodSubtype.InputMethodSubtypeBuilder()
                            .setSubtypeId(0x12345 + i)
                            .setSubtypeNameOverride(longText)
                            .setPhysicalKeyboardHint(null, longText)
                            .setLanguageTag(longText)
                            .setSubtypeLocale(longText)
                            .setSubtypeMode(longText)
                            .setSubtypeExtraValue(longText)
                            .build();
            additionalSubtypes.add(largeSubtype);
        }

        String imeId = new ComponentName(this, Method1.class.getName()).flattenToShortString();
        imm.setAdditionalInputMethodSubtypes(
                imeId, additionalSubtypes.toArray(new InputMethodSubtype[0]));

        int[] subtypeHashCodes =
                IntStream.concat(
                                // Static subtype IDs
                                IntStream.rangeClosed(1, STATIC_SUBTYPE_COUNT),
                                // Dynamically added IDs.
                                additionalSubtypes.stream().mapToInt(InputMethodSubtype::hashCode))
                        .toArray();

        imm.setExplicitlyEnabledInputMethodSubtypes(imeId, subtypeHashCodes);
    }
}
