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

package android.widget.cts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.cts.util.TestUtils;
import android.widget.cts.util.ViewTestUtils;

import static org.mockito.Mockito.mock;

/**
 * Test {@link Spinner}.
 */
@MediumTest
public class SpinnerTest extends ActivityInstrumentationTestCase2<SpinnerCtsActivity> {
    private Activity mActivity;
    private Spinner mSpinnerDialogMode;
    private Spinner mSpinnerDropdownMode;

    public SpinnerTest() {
        super("android.widget.cts", SpinnerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mSpinnerDialogMode = (Spinner) mActivity.findViewById(R.id.spinner_dialog_mode);
        mSpinnerDropdownMode = (Spinner) mActivity.findViewById(R.id.spinner_dropdown_mode);
    }

    public void testConstructor() {
        new Spinner(mActivity);

        new Spinner(mActivity, null);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle);

        new Spinner(mActivity, Spinner.MODE_DIALOG);

        new Spinner(mActivity, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DROPDOWN);

        final Resources.Theme popupTheme = mActivity.getResources().newTheme();
        popupTheme.applyStyle(android.R.style.Theme_Material, true);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG,
                popupTheme);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DROPDOWN,
                popupTheme);
    }

    private void verifyGetBaseline(Spinner spinner) {
        assertEquals(-1, spinner.getBaseline());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        getInstrumentation().runOnMainSync(() -> {
            spinner.setAdapter(adapter);
            assertTrue(spinner.getBaseline() > 0);
        });
    }

    public void testGetBaseline() {
        verifyGetBaseline(mSpinnerDialogMode);
        verifyGetBaseline(mSpinnerDropdownMode);
    }

    private void verifySetOnItemClickListener(Spinner spinner) {
        try {
            spinner.setOnItemClickListener(null);
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }

        try {
            spinner.setOnItemClickListener(mock(Spinner.OnItemClickListener.class));
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }
    }

    public void testSetOnItemClickListener() {
        verifySetOnItemClickListener(mSpinnerDialogMode);
        verifySetOnItemClickListener(mSpinnerDropdownMode);
    }

    private void verifyPerformClick(Spinner spinner) {
        getInstrumentation().runOnMainSync(() -> assertTrue(spinner.performClick()));
    }

    public void testPerformClick() {
        verifyPerformClick(mSpinnerDialogMode);
        verifyPerformClick(mSpinnerDropdownMode);
    }

    private void verifyOnClick(Spinner spinner) {
        // normal value
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        AlertDialog alertDialog = builder.show();
        assertTrue(alertDialog.isShowing());

        spinner.onClick(alertDialog, 10);
        assertEquals(10, spinner.getSelectedItemPosition());
        assertFalse(alertDialog.isShowing());

        // exceptional
        try {
            spinner.onClick(null, 10);
            fail("did not throw NullPointerException");
        } catch (NullPointerException e) {
        }

        Dialog dialog = new Dialog(getActivity());
        dialog.show();
        assertTrue(dialog.isShowing());

        spinner.onClick(dialog, -10);
        assertEquals(-10, spinner.getSelectedItemPosition());
        assertFalse(dialog.isShowing());
    }

    @UiThreadTest
    public void testOnClick() {
        verifyOnClick(mSpinnerDialogMode);
        verifyOnClick(mSpinnerDropdownMode);
    }

    private void verifyAccessPrompt(Spinner spinner) {
        final String initialPrompt = mActivity.getString(R.string.text_view_hello);
        assertEquals(initialPrompt, spinner.getPrompt());

        final Instrumentation instrumentation = getInstrumentation();
        final String promptText = "prompt text";

        instrumentation.runOnMainSync(() -> spinner.setPrompt(promptText));
        assertEquals(promptText, spinner.getPrompt());

        spinner.setPrompt(null);
        assertNull(spinner.getPrompt());
    }

    public void testAccessPrompt() {
        verifyAccessPrompt(mSpinnerDialogMode);
        verifyAccessPrompt(mSpinnerDropdownMode);
    }

    private void verifySetPromptId(Spinner spinner) {
        final Instrumentation instrumentation = getInstrumentation();

        instrumentation.runOnMainSync(() -> spinner.setPromptId(R.string.hello_world));
        assertEquals(mActivity.getString(R.string.hello_world), spinner.getPrompt());

        try {
            spinner.setPromptId(-1);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }

        try {
            spinner.setPromptId(Integer.MAX_VALUE);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }
    }

    public void testSetPromptId() {
        verifySetPromptId(mSpinnerDialogMode);
        verifySetPromptId(mSpinnerDropdownMode);
    }

    @UiThreadTest
    public void testGetPopupContext() {
        Theme theme = mActivity.getResources().newTheme();
        Spinner themeSpinner = new Spinner(mActivity, null,
                android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG, theme);
        assertNotSame(mActivity, themeSpinner.getPopupContext());
        assertSame(theme, themeSpinner.getPopupContext().getTheme());

        ContextThemeWrapper context = (ContextThemeWrapper)themeSpinner.getPopupContext();
        assertSame(mActivity, context.getBaseContext());
    }

    private void verifyGravity(Spinner spinner) {
        final Instrumentation instrumentation = getInstrumentation();

        // Note that here we're using a custom layout for the spinner's selected item
        // that doesn't span the whole width of the parent. That way we're exercising the
        // relevant path in spinner's layout pass that handles the currently set gravity
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, R.layout.simple_spinner_item_layout);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        instrumentation.runOnMainSync(() -> spinner.setAdapter(adapter));

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, spinner, () -> {
            spinner.setSelection(1);
            spinner.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            spinner.requestLayout();
        });

        instrumentation.runOnMainSync(() -> spinner.setGravity(Gravity.LEFT));
        assertEquals(Gravity.LEFT, spinner.getGravity());

        instrumentation.runOnMainSync(() -> spinner.setGravity(Gravity.CENTER_HORIZONTAL));
        assertEquals(Gravity.CENTER_HORIZONTAL, spinner.getGravity());

        instrumentation.runOnMainSync((() -> spinner.setGravity(Gravity.RIGHT)));
        assertEquals(Gravity.RIGHT, spinner.getGravity());

        instrumentation.runOnMainSync(() -> spinner.setGravity(Gravity.START));
        assertEquals(Gravity.START, spinner.getGravity());

        instrumentation.runOnMainSync(() -> spinner.setGravity(Gravity.END));
        assertEquals(Gravity.END, spinner.getGravity());
    }

    public void testGravity() {
        verifyGravity(mSpinnerDialogMode);
        verifyGravity(mSpinnerDropdownMode);
    }

    public void testDropDownMetricsDropdownMode() {
        final Instrumentation instrumentation = getInstrumentation();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        instrumentation.runOnMainSync(() -> mSpinnerDropdownMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        instrumentation.runOnMainSync(() -> {
            mSpinnerDropdownMode.setDropDownWidth(dropDownWidth);
            mSpinnerDropdownMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDropdownMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());

        // And test its attributes
        assertEquals(dropDownWidth, mSpinnerDropdownMode.getDropDownWidth());
        // TODO: restore when b/28089349 is addressed
        // assertEquals(dropDownOffsetHorizontal,
        //      mSpinnerDropdownMode.getDropDownHorizontalOffset());
        assertEquals(dropDownOffsetVertical, mSpinnerDropdownMode.getDropDownVerticalOffset());
    }

    public void testDropDownMetricsDialogMode() {
        final Instrumentation instrumentation = getInstrumentation();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        instrumentation.runOnMainSync(() -> mSpinnerDialogMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        instrumentation.runOnMainSync(() -> {
            // These are all expected to be no-ops
            mSpinnerDialogMode.setDropDownWidth(dropDownWidth);
            mSpinnerDialogMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDialogMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());

        // And test its attributes. Note that we are not testing the result of getDropDownWidth
        // for this mode
        assertEquals(0, mSpinnerDialogMode.getDropDownHorizontalOffset());
        assertEquals(0, mSpinnerDialogMode.getDropDownVerticalOffset());
    }

    public void testDropDownBackgroundDropdownMode() {
        final Instrumentation instrumentation = getInstrumentation();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        instrumentation.runOnMainSync(() -> mSpinnerDropdownMode.setAdapter(adapter));

        // Set blue background on the popup
        instrumentation.runOnMainSync(() ->
                mSpinnerDropdownMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());
        // And test its fill
        Drawable dropDownBackground = mSpinnerDropdownMode.getPopupBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be blue", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.BLUE, 1, true);

        // Dismiss the popup with the emulated back key
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        instrumentation.waitForIdleSync();
        // Verify that we're not showing the popup
        assertFalse(mSpinnerDropdownMode.isPopupShowing());

        // Set yellow background on the popup
        instrumentation.runOnMainSync(() ->
                mSpinnerDropdownMode.setPopupBackgroundDrawable(
                        mActivity.getDrawable(R.drawable.yellow_fill)));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());
        // And test its fill
        dropDownBackground = mSpinnerDropdownMode.getPopupBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be yellow", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.YELLOW, 1, true);
    }

    public void testDropDownBackgroundDialogMode() {
        final Instrumentation instrumentation = getInstrumentation();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        instrumentation.runOnMainSync(() -> mSpinnerDialogMode.setAdapter(adapter));

        // Set blue background on the popup
        instrumentation.runOnMainSync(() ->
                mSpinnerDialogMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());
        // And test that getPopupBackground returns null
        assertNull(mSpinnerDialogMode.getPopupBackground());

        // Dismiss the popup with the emulated back key
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        instrumentation.waitForIdleSync();
        // Verify that we're not showing the popup
        assertFalse(mSpinnerDialogMode.isPopupShowing());

        // Set yellow background on the popup
        instrumentation.runOnMainSync(() ->
                mSpinnerDialogMode.setPopupBackgroundDrawable(
                        mActivity.getDrawable(R.drawable.yellow_fill)));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        ViewTestUtils.emulateTapOnViewCenter(instrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());
        // And test that getPopupBackground returns null
        assertNull(mSpinnerDialogMode.getPopupBackground());
    }
}
