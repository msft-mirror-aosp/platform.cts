/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Build/Install/Run: atest CtsPersonalContextTestCases:RenderTokenTest */
@SmallTest
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class RenderTokenTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.RenderToken#RenderToken",
                "android.service.personalcontext.RenderToken#getTokenId",
                "android.service.personalcontext.RenderToken#getRendererComponentId",
                "android.service.personalcontext.RenderToken#getTag",
                "android.service.personalcontext.RenderToken#equals",
                "android.service.personalcontext.RenderToken#hashCode",
                "android.service.personalcontext.RenderToken#writeToParcel",
                "android.service.personalcontext.RenderToken.CREATOR#createFromParcel",
            })
    @Test
    public void testParcelUnparcel() {
        final UUID componentId = UUID.randomUUID();
        String tag = "testTag";
        final RenderToken token = new RenderToken(componentId, tag);

        final RenderToken fromParcel = parcelUnparcel(token);

        assertThat(fromParcel.getTokenId()).isEqualTo(token.getTokenId());
        assertThat(fromParcel.getRendererComponentId()).isEqualTo(token.getRendererComponentId());
        assertThat(fromParcel.getTag()).isEqualTo(token.getTag());

        assertThat(fromParcel).isEqualTo(token);
        assertThat(fromParcel.hashCode()).isEqualTo(token.hashCode());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.RenderToken#RenderToken",
                "android.service.personalcontext.RenderToken#getTokenId",
                "android.service.personalcontext.RenderToken#getRendererComponentId",
                "android.service.personalcontext.RenderToken#getTag",
                "android.service.personalcontext.RenderToken#equals",
                "android.service.personalcontext.RenderToken#hashCode",
                "android.service.personalcontext.RenderToken#writeToParcel",
                "android.service.personalcontext.RenderToken.CREATOR#createFromParcel",
            })
    @Test
    public void testParcelUnparcel_nullTag() {
        final UUID componentId = UUID.randomUUID();
        final RenderToken token = new RenderToken(componentId, null);

        final RenderToken fromParcel = parcelUnparcel(token);

        assertThat(fromParcel.getTokenId()).isEqualTo(token.getTokenId());
        assertThat(fromParcel.getRendererComponentId()).isEqualTo(token.getRendererComponentId());
        assertThat(fromParcel.getTag()).isNull();

        assertThat(fromParcel).isEqualTo(token);
        assertThat(fromParcel.hashCode()).isEqualTo(token.hashCode());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.RenderToken#RenderToken",
                "android.service.personalcontext.RenderToken#compareTo",
                "android.service.personalcontext.RenderToken#getTokenId"
            })
    @Test
    public void testComparable() {
        final RenderToken token1 = new RenderToken(UUID.randomUUID(), null);
        final RenderToken token2 = new RenderToken(UUID.randomUUID(), null);

        assertThat(token1.compareTo(token2))
                .isEqualTo(token1.getTokenId().compareTo(token2.getTokenId()));
    }

    private RenderToken parcelUnparcel(RenderToken in) {
        final Parcel parcel = Parcel.obtain();
        try {
            in.writeToParcel(parcel, 0);
            final int dataSize = parcel.dataPosition();
            parcel.setDataPosition(0);

            final RenderToken fromParcel = RenderToken.CREATOR.createFromParcel(parcel);
            // Same size of data is written and read.
            assertThat(dataSize).isEqualTo(parcel.dataPosition());
            return fromParcel;
        } finally {
            parcel.recycle();
        }
    }
}
