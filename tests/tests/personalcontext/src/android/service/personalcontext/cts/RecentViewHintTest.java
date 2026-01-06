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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.graphics.Rect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.CapturedText;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.RecentViewHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

/** Build/Install/Run: atest CtsPersonalContextTestCases:RecentViewHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class RecentViewHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.RecentViewHint.Builder#build",
                "android.service.personalcontext.hint.RecentViewHint.Builder#setSourceAppActivityComponentName",
                "android.service.personalcontext.hint.RecentViewHint#getCapturedTexts",
                "android.service.personalcontext.hint.RecentViewHint#getLocusId",
                "android.service.personalcontext.hint.RecentViewHint#getSourceAppActivityComponentName",
                "android.service.personalcontext.hint.ContextHint#createHintFromBundle",
                "android.service.personalcontext.hint.RecentViewHint#toBundle"
            })
    @Test
    public void testRecentViewHint_bundleUnbundle() {
        final CapturedText capturedText =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final RecentViewHint hint =
                new RecentViewHint.Builder()
                        .addCapturedText(capturedText)
                        .setLocusId("locusId")
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(RecentViewHint.class);
        final RecentViewHint outputRecentViewHint = (RecentViewHint) outputHint;
        assertThat(outputRecentViewHint.getCapturedTexts()).containsExactly(capturedText);
        assertThat(outputRecentViewHint.getLocusId()).isEqualTo("locusId");
        assertThat(outputRecentViewHint.getSourceAppActivityComponentName()).isEqualTo(componentName);
        assertThat(outputRecentViewHint.getCapturedTexts().get(0).getViewNodeLastSeen()).isNull();
        assertThat(outputRecentViewHint).isEqualTo(hint);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.CapturedText.Builder#setViewNodeText",
                "android.service.personalcontext.hint.CapturedText.Builder#setViewNodeDescription",
                "android.service.personalcontext.hint.CapturedText.Builder#setViewId",
                "android.service.personalcontext.hint.CapturedText.Builder#setViewNodeBoundingBox",
                "android.service.personalcontext.hint.CapturedText.Builder#setResourceId",
                "android.service.personalcontext.hint.CapturedText.Builder#setViewNodeLastUpdated",
                "android.service.personalcontext.hint.CapturedText.Builder#setViewNodeLastSeen",
                "android.service.personalcontext.hint.CapturedText.Builder#build",
                "android.service.personalcontext.hint.CapturedText#getViewNodeText",
                "android.service.personalcontext.hint.CapturedText#getViewNodeDescription",
                "android.service.personalcontext.hint.CapturedText#getViewId",
                "android.service.personalcontext.hint.CapturedText#getViewNodeBoundingBox",
                "android.service.personalcontext.hint.CapturedText#getResourceId",
                "android.service.personalcontext.hint.CapturedText#getViewNodeLastUpdated",
                "android.service.personalcontext.hint.CapturedText#getViewNodeLastSeen"
            })
    @Test
    public void testCapturedText_getters() {
        final Rect boundingBox = new Rect(1, 2, 3, 4);
        final Instant lastSeen = Instant.ofEpochMilli(67890L);
        final CapturedText capturedText =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .setViewNodeLastSeen(lastSeen)
                        .build();

        assertThat(capturedText.getViewNodeText()).isEqualTo("text");
        assertThat(capturedText.getViewNodeDescription()).isEqualTo("description");
        assertThat(capturedText.getViewId()).isEqualTo("viewId");
        assertThat(capturedText.getViewNodeBoundingBox()).isEqualTo(boundingBox);
        assertThat(capturedText.getResourceId()).isEqualTo("resourceId");
        assertThat(capturedText.getViewNodeLastUpdated()).isEqualTo(Instant.ofEpochMilli(12345L));
        assertThat(capturedText.getViewNodeLastSeen()).isEqualTo(lastSeen);

        final CapturedText capturedText2 =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .build();
        assertThat(capturedText2.getViewNodeLastSeen()).isNull();
    }

    private ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.RecentViewHint#equals",
                "android.service.personalcontext.hint.RecentViewHint#hashCode",
                "android.service.personalcontext.hint.RecentViewHint#toString"
            })
    @Test
    public void testRecentViewHint_equalsHashCodeToString() {
        final CapturedText capturedText =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final RecentViewHint hint =
                new RecentViewHint.Builder()
                        .addCapturedText(capturedText)
                        .setLocusId("locusId")
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final RecentViewHint unbundledHint = (RecentViewHint) bundleUnbundle(hint);
        assertThat(unbundledHint).isEqualTo(hint);
        assertThat(unbundledHint.hashCode()).isEqualTo(hint.hashCode());
        assertThat(hint.toString()).isNotNull();

        final RecentViewHint differentHint =
                new RecentViewHint.Builder()
                        .addCapturedText(capturedText)
                        .setLocusId("different locusId")
                        .setSourceAppActivityComponentName(componentName)
                        .build();
        assertThat(hint).isNotEqualTo(differentHint);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.CapturedText#equals",
                "android.service.personalcontext.hint.CapturedText#hashCode",
                "android.service.personalcontext.hint.CapturedText#toString"
            })
    @Test
    public void testCapturedText_equalsHashCodeToString() {
        final Rect boundingBox = new Rect(1, 2, 3, 4);
        final Instant lastSeen = Instant.ofEpochMilli(67890L);
        final CapturedText capturedText1 =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .setViewNodeLastSeen(lastSeen)
                        .build();

        final CapturedText capturedText2 =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .setViewNodeLastSeen(lastSeen)
                        .build();

        final CapturedText differentText =
                new CapturedText.Builder()
                        .setViewNodeText("different text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .setViewNodeLastSeen(lastSeen)
                        .build();

        final CapturedText differentLastSeen =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .setViewNodeLastSeen(Instant.ofEpochMilli(1L))
                        .build();

        final CapturedText nullLastSeen =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(boundingBox)
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .build();

        assertThat(capturedText1).isEqualTo(capturedText2);
        assertThat(capturedText1.hashCode()).isEqualTo(capturedText2.hashCode());
        assertThat(capturedText1.toString()).isNotNull();

        assertThat(capturedText1).isNotEqualTo(differentText);
        assertThat(capturedText1).isNotEqualTo(differentLastSeen);
        assertThat(capturedText1).isNotEqualTo(nullLastSeen);
        assertThat(nullLastSeen).isNotEqualTo(capturedText1);
    }
}
