/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.widget.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.cts.util.TestUtils;
import android.widget.cts.util.ViewTestUtils;

/**
 * Test {@link Switch}.
 */
@SmallTest
public class SwitchTest extends ActivityInstrumentationTestCase2<SwitchCtsActivity> {
    private Activity mActivity;
    private Switch mSwitch;

    public SwitchTest() {
        super("android.widget.cts", SwitchCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    private Switch findSwitchById(int id) {
        return (Switch) mActivity.findViewById(id);
    }

    @UiThreadTest
    public void testConstructor() {
        new Switch(mActivity);

        new Switch(mActivity, null);

        new Switch(mActivity, null, android.R.attr.switchStyle);

        new Switch(mActivity, null, 0, android.R.style.Widget_Material_Light_CompoundButton_Switch);
    }

    @UiThreadTest
    public void testAccessThumbTint() {
        mSwitch = findSwitchById(R.id.switch1);

        // These are the default set in layout XML
        assertEquals(Color.WHITE, mSwitch.getThumbTintList().getDefaultColor());
        assertEquals(Mode.SRC_OVER, mSwitch.getThumbTintMode());

        ColorStateList colors = ColorStateList.valueOf(Color.RED);
        mSwitch.setThumbTintList(colors);
        mSwitch.setThumbTintMode(Mode.XOR);

        assertSame(colors, mSwitch.getThumbTintList());
        assertEquals(Mode.XOR, mSwitch.getThumbTintMode());
    }

    @UiThreadTest
    public void testAccessTrackTint() {
        mSwitch = findSwitchById(R.id.switch1);

        // These are the default set in layout XML
        assertEquals(Color.BLACK, mSwitch.getTrackTintList().getDefaultColor());
        assertEquals(Mode.SRC_ATOP, mSwitch.getTrackTintMode());

        ColorStateList colors = ColorStateList.valueOf(Color.RED);
        mSwitch.setTrackTintList(colors);
        mSwitch.setTrackTintMode(Mode.XOR);

        assertSame(colors, mSwitch.getTrackTintList());
        assertEquals(Mode.XOR, mSwitch.getTrackTintMode());
    }

    public void testAccessThumbDrawable() {
        mSwitch = findSwitchById(R.id.switch2);
        final Instrumentation instrumentation = getInstrumentation();

        // This is the default set in layout XML
        Drawable thumbDrawable = mSwitch.getThumbDrawable();
        TestUtils.assertAllPixelsOfColor("Thumb drawable should be blue", thumbDrawable,
                thumbDrawable.getIntrinsicWidth(), thumbDrawable.getIntrinsicHeight(), false,
                Color.BLUE, 1, true);

        // Change thumb drawable to red
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setThumbDrawable(mActivity.getDrawable(R.drawable.icon_red)));
        thumbDrawable = mSwitch.getThumbDrawable();
        TestUtils.assertAllPixelsOfColor("Thumb drawable should be red", thumbDrawable,
                thumbDrawable.getIntrinsicWidth(), thumbDrawable.getIntrinsicHeight(), false,
                Color.RED, 1, true);

        // Change thumb drawable to green
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setThumbResource(R.drawable.icon_green));
        thumbDrawable = mSwitch.getThumbDrawable();
        TestUtils.assertAllPixelsOfColor("Thumb drawable should be green", thumbDrawable,
                thumbDrawable.getIntrinsicWidth(), thumbDrawable.getIntrinsicHeight(), false,
                Color.GREEN, 1, true);

        // Now tint the latest (green) thumb drawable with 50% blue SRC_OVER
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch, () -> {
            mSwitch.setThumbTintList(ColorStateList.valueOf(0x800000FF));
            mSwitch.setThumbTintMode(Mode.SRC_OVER);
        });
        thumbDrawable = mSwitch.getThumbDrawable();
        TestUtils.assertAllPixelsOfColor("Thumb drawable should be green / blue", thumbDrawable,
                thumbDrawable.getIntrinsicWidth(), thumbDrawable.getIntrinsicHeight(), false,
                TestUtils.compositeColors(0x800000FF, Color.GREEN), 1, true);
    }

    public void testAccessTrackDrawable() {
        mSwitch = findSwitchById(R.id.switch2);
        final Instrumentation instrumentation = getInstrumentation();
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
            () -> mSwitch.setTrackTintMode(Mode.DST));

        // This is the default set in layout XML
        Drawable trackDrawable = mSwitch.getTrackDrawable();
        Rect trackDrawableBounds = trackDrawable.getBounds();
        TestUtils.assertAllPixelsOfColor("Track drawable should be 50% red", trackDrawable,
                trackDrawableBounds.width(), trackDrawableBounds.height(), false,
                0x80FF0000, 1, true);

        // Change track drawable to blue
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setTrackDrawable(mActivity.getDrawable(R.drawable.blue_fill)));
        trackDrawable = mSwitch.getTrackDrawable();
        trackDrawableBounds = trackDrawable.getBounds();
        TestUtils.assertAllPixelsOfColor("Track drawable should be blue", trackDrawable,
                trackDrawableBounds.width(), trackDrawableBounds.height(), false,
                Color.BLUE, 1, true);

        // Change track drawable to green
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setTrackResource(R.drawable.green_fill));
        trackDrawable = mSwitch.getTrackDrawable();
        trackDrawableBounds = trackDrawable.getBounds();
        TestUtils.assertAllPixelsOfColor("Track drawable should be green", trackDrawable,
                trackDrawableBounds.width(), trackDrawableBounds.height(), false,
                Color.GREEN, 1, true);

        // Now tint the latest (green) track drawable with 50% blue SRC_OVER
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch, () -> {
            mSwitch.setTrackTintList(ColorStateList.valueOf(0x800000FF));
            mSwitch.setTrackTintMode(Mode.SRC_OVER);
        });
        trackDrawable = mSwitch.getTrackDrawable();
        trackDrawableBounds = trackDrawable.getBounds();
        TestUtils.assertAllPixelsOfColor("Track drawable should be green / blue", trackDrawable,
                trackDrawableBounds.width(), trackDrawableBounds.height(), false,
                TestUtils.compositeColors(0x800000FF, Color.GREEN), 1, true);
    }

    public void testAccessText() {
        final Instrumentation instrumentation = getInstrumentation();

        // Run text-related tests on a Holo-themed switch, since under Material themes we
        // are not showing texts by default.
        mSwitch = new Switch(new ContextThemeWrapper(mActivity, android.R.style.Theme_Holo_Light));
        instrumentation.runOnMainSync(
                () -> ((ViewGroup) mActivity.findViewById(R.id.container)).addView(mSwitch));

        // Set "on" text and verify it
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setTextOn("Text on"));
        assertEquals("Text on", mSwitch.getTextOn());

        // Set "off" text and verify it
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setTextOff("Text off"));
        assertEquals("Text off", mSwitch.getTextOff());

        // Turn text display on
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setShowText(true));
        assertTrue(mSwitch.getShowText());

        // Use custom text appearance. Since we don't have APIs to query this facet of Switch,
        // just test that it's not crashing.
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchTextAppearance(mActivity, R.style.TextAppearance_WithColor));

        // Use custom typeface. Since we don't have APIs to query this facet of Switch,
        // just test that it's not crashing.
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchTypeface(Typeface.MONOSPACE));

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchTypeface(Typeface.SERIF, Typeface.ITALIC));

        // Set and verify padding between the thumb and the text
        final int thumbTextPadding = mActivity.getResources().getDimensionPixelSize(
                R.dimen.switch_thumb_text_padding);
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setThumbTextPadding(thumbTextPadding));
        assertEquals(thumbTextPadding, mSwitch.getThumbTextPadding());

        // Set and verify padding between the switch and the text
        final int switchPadding = mActivity.getResources().getDimensionPixelSize(
                R.dimen.switch_padding);
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchPadding(switchPadding));
        assertEquals(switchPadding, mSwitch.getSwitchPadding());

        // Turn text display off
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setShowText(false));
        assertFalse(mSwitch.getShowText());
    }

    public void testAccessMinWidth() {
        mSwitch = findSwitchById(R.id.switch3);
        final Instrumentation instrumentation = getInstrumentation();

        // Set custom min width on the switch and verify that it's at least that wide
        final int switchMinWidth = mActivity.getResources().getDimensionPixelSize(
                R.dimen.switch_min_width);
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchMinWidth(switchMinWidth));
        assertEquals(switchMinWidth, mSwitch.getSwitchMinWidth());
        assertTrue(mSwitch.getWidth() >= switchMinWidth);

        // And set another (larger) min width
        final int switchMinWidth2 = mActivity.getResources().getDimensionPixelSize(
                R.dimen.switch_min_width2);
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSwitchMinWidth(switchMinWidth2));
        assertEquals(switchMinWidth2, mSwitch.getSwitchMinWidth());
        assertTrue(mSwitch.getWidth() >= switchMinWidth2);
    }

    public void testAccessSplitTrack() {
        mSwitch = findSwitchById(R.id.switch3);
        final Instrumentation instrumentation = getInstrumentation();

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSplitTrack(true));
        assertTrue(mSwitch.getSplitTrack());

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mSwitch,
                () -> mSwitch.setSplitTrack(false));
        assertFalse(mSwitch.getSplitTrack());
    }
}
