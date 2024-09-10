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
import android.app.appsearch.functions.ExecuteAppFunctionRequest;
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
public class ExecuteAppFunctionRequestCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(apis = {
            "android.app.appsearch.functions.ExecuteAppFunctionRequest.Builder#Builder",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest.Builder#setParameter",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest.Builder#setExtras",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest"
                    + ".Builder#setSha256Certificate",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest.Builder#build",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#writeToParcel",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#CREATOR",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#getTargetPackageName",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#getParameters",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#getExtras",
            "android.app.appsearch.functions.ExecuteAppFunctionRequest#getSha256Certificate",
    })
    @Test
    public void build() {
        Bundle extras = new Bundle();
        extras.putString("extra", "value");
        GenericDocument parameters = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong("a", 42)
                .build();
        ExecuteAppFunctionRequest request = new ExecuteAppFunctionRequest.Builder("pkg", "method")
                .setParameters(parameters)
                .setSha256Certificate(new byte[] {100})
                .setExtras(extras)
                .build();

        ExecuteAppFunctionRequest restored = parcelizeAndDeparcelize(request);

        assertThat(restored.getTargetPackageName()).isEqualTo("pkg");
        assertThat(restored.getParameters()).isEqualTo(parameters);
        assertThat(restored.getSha256Certificate()).isEqualTo(new byte[] {100});
        assertThat(restored.getExtras().size()).isEqualTo(1);
        assertThat(restored.getExtras().getString("extra")).isEqualTo("value");
    }


    private static ExecuteAppFunctionRequest parcelizeAndDeparcelize(
            ExecuteAppFunctionRequest original) {
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return ExecuteAppFunctionRequest.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
