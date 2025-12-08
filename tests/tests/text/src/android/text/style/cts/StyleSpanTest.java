/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.style.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Flags;
import android.graphics.Typeface;
import android.graphics.fonts.FontStyle;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.cts.R;
import android.text.style.StyleSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StyleSpanTest {
    private static final long TIMEOUT = 5000;
    private static final String FONT_WEIGHT_ADJUSTMENT_SETTING = "font_weight_adjustment";

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 2)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testConstructor() {
        StyleSpan styleSpan = new StyleSpan(2);

        Parcel p = Parcel.obtain();
        try {
            styleSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            StyleSpan fromParcel = new StyleSpan(p);
            assertEquals(2, fromParcel.getStyle());
            assertEquals(Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED,
                    fromParcel.getFontWeightAdjustment());
            new StyleSpan(-2);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetStyle() {
        StyleSpan styleSpan = new StyleSpan(2);
        assertEquals(2, styleSpan.getStyle());

        styleSpan = new StyleSpan(-2);
        assertEquals(-2, styleSpan.getStyle());
    }

    @Test
    public void testGetFontWeightAdjustment() {
        StyleSpan styleSpan = new StyleSpan(2, 300);
        assertEquals(300, styleSpan.getFontWeightAdjustment());
    }


    @Test
    public void testUpdateMeasureState_withStyle() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateMeasureState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
    }

    @Test
    public void testUpdateMeasureState_withFontWeightAdjustment() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD, 300);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateMeasureState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
        assertEquals(tp.getTypeface().getWeight(), FontStyle.FONT_WEIGHT_MAX);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        styleSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState_withStyle() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateDrawState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
    }

    @Test
    public void testUpdateDrawState_withFontWeightAdjustment() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD, 300);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateDrawState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
        assertEquals(tp.getTypeface().getWeight(), FontStyle.FONT_WEIGHT_MAX);
    }


    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        styleSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        styleSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        styleSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
            styleSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            StyleSpan newSpan = new StyleSpan(p);
            assertEquals(Typeface.BOLD, newSpan.getStyle());
        } finally {
            p.recycle();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APPLY_FONT_WEIGHT_ADJUSTMENT_ON_CACHED_STRINGS)
    @android.platform.test.annotations.DisabledOnRavenwood(
            bug = 457840588, blockedBy = Settings.class)
    public void testRecreateWithUpdatedFontWeight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int defaultConfigFontWeight =
                context.getResources().getConfiguration().fontWeightAdjustment;

        CharSequence text = context.getResources().getText(R.string.text_view_hello_bolded);
        Spanned spanned = (Spanned) text;
        StyleSpan[] styleSpans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertThat(styleSpans).hasLength(1);
        StyleSpan boldSpan = styleSpans[0];
        assertThat(boldSpan.getStyle()).isEqualTo(Typeface.BOLD);
        assertThat(boldSpan.getFontWeightAdjustment()).isEqualTo(defaultConfigFontWeight);

        Configuration config = new Configuration();
        final int fontWeightAdjustment = FontStyle.FONT_WEIGHT_EXTRA_BOLD - defaultConfigFontWeight;
        config.fontWeightAdjustment =
                fontWeightAdjustment <= 0 ? FontStyle.FONT_WEIGHT_MAX : fontWeightAdjustment;
        try {
            // Set the font weight adjustment setting so the Context is recreated with this new
            // font weight adjustment (and the subsequent StringBlock calls will use the new weight)
            SystemUtil.runWithShellPermissionIdentity(
                    () ->
                            Settings.Secure.putInt(
                                    context.getContentResolver(),
                                    FONT_WEIGHT_ADJUSTMENT_SETTING,
                                    fontWeightAdjustment));

            PollingCheck.waitFor(
                    TIMEOUT,
                    () ->
                            context.getResources().getConfiguration().fontWeightAdjustment
                                    == config.fontWeightAdjustment,
                    "Font weight adjustment in Configuration failed to update after config"
                            + " change.");

            // Get the string resource again from StringBlock
            CharSequence currentText =
                    context.getResources().getText(R.string.text_view_hello_bolded);
            Spanned currentSpanned = (Spanned) currentText;
            StyleSpan[] currentStyleSpans =
                    currentSpanned.getSpans(0, currentSpanned.length(), StyleSpan.class);

            StyleSpan updatedBoldSpan = currentStyleSpans[0];
            assertThat(currentStyleSpans).hasLength(1);
            assertEquals(Typeface.BOLD, updatedBoldSpan.getStyle());
            assertEquals(updatedBoldSpan.getFontWeightAdjustment(), config.fontWeightAdjustment);
        } finally {
            SystemUtil.runWithShellPermissionIdentity(
                    () ->
                            Settings.Secure.putInt(
                                    context.getContentResolver(),
                                    FONT_WEIGHT_ADJUSTMENT_SETTING,
                                    defaultConfigFontWeight));
        }
    }
}
