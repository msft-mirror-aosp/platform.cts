/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appsearch.cts.functions;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.functions.ExecuteAppFunctionResponse;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.appsearch.flags.Flags;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS)
public class ExecuteAppFunctionResponseCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(apis = {
            "android.app.appsearch.functions.ExecuteAppFunctionResponse.Builder#Builder",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse.Builder#setExtras",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse.Builder#build",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse#writeToParcel",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse#CREATOR",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse#getResult",
            "android.app.appsearch.functions.ExecuteAppFunctionResponse#getExtras",
    })
    @Test
    public void build() {
        Bundle extras = new Bundle();
        extras.putString("extra", "value");
        GenericDocument functionResult = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RESULT, 42)
                .build();
        ExecuteAppFunctionResponse response =
                new ExecuteAppFunctionResponse.Builder()
                        .setResult(functionResult)
                        .setExtras(extras)
                        .build();

        ExecuteAppFunctionResponse restored =
                parcelizeAndDeparcelize(response);

        assertThat(restored.getResult()).isEqualTo(functionResult);
        assertThat(restored.getExtras().size()).isEqualTo(1);
        assertThat(restored.getExtras().getString("extra")).isEqualTo("value");
    }

    private static ExecuteAppFunctionResponse parcelizeAndDeparcelize(
            ExecuteAppFunctionResponse original) {
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return ExecuteAppFunctionResponse.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
