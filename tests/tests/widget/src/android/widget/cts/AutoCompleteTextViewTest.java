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
import android.app.Instrumentation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.cts.util.PollingCheck;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.AutoCompleteTextView.Validator;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.cts.util.TestUtils;
import org.xmlpull.v1.XmlPullParser;

import static org.mockito.Mockito.*;

@MediumTest
public class AutoCompleteTextViewTest extends
        ActivityInstrumentationTestCase2<AutoCompleteCtsActivity> {
    private final static String[] WORDS =
            new String[] { "testOne", "testTwo", "testThree", "testFour" };
    private final static String STRING_TEST = "To be tested";
    private final static String STRING_VALIDATED = "String Validated";
    private final static String STRING_CHECK = "To be checked";

    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private AutoCompleteTextView mAutoCompleteTextView;
    private boolean mNumeric = false;
    private ArrayAdapter<String> mAdapter;

    private final Validator mValidator = new Validator() {
        public CharSequence fixText(CharSequence invalidText) {
            return STRING_VALIDATED;
        }

        public boolean isValid(CharSequence text) {
            return false;
        }
    };

    protected class MyTextWatcher implements TextWatcher {
        private CharSequence mExpectedAfter;

        public MyTextWatcher(CharSequence expectedAfter) {
            mExpectedAfter = expectedAfter;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            assertEquals(mExpectedAfter.toString(), s.toString());
            // This watcher is expected to be notified in the middle of completion
            assertTrue(mAutoCompleteTextView.isPerformingCompletion());
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    /**
     * Instantiates a new text view test.
     */
    public AutoCompleteTextViewTest() {
        super("android.widget.cts", AutoCompleteCtsActivity.class);
    }

    /*
     * (non-Javadoc)
     *
     * @see android.test.ActivityInstrumentationTestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        PollingCheck.waitFor(() -> mActivity.hasWindowFocus());

        mInstrumentation = getInstrumentation();
        mAutoCompleteTextView = (AutoCompleteTextView) mActivity
                .findViewById(R.id.autocompletetv_edit);
        mAdapter = new ArrayAdapter<String>(mActivity,
                android.R.layout.simple_dropdown_item_1line, WORDS);
        KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            mNumeric = true;
        }
    }

    boolean isTvMode() {
        UiModeManager uiModeManager = (UiModeManager) getActivity().getSystemService(
                Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @UiThreadTest
    public void testConstructor() {
        XmlPullParser parser;

        new AutoCompleteTextView(mActivity);
        new AutoCompleteTextView(mActivity, null);
        new AutoCompleteTextView(mActivity, null, android.R.attr.autoCompleteTextViewStyle);
        new AutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_Material_Light_AutoCompleteTextView);

        final Resources.Theme popupTheme = mActivity.getResources().newTheme();
        popupTheme.applyStyle(android.R.style.Theme_Material, true);
        new AutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_Material_Light_AutoCompleteTextView, popupTheme);

        // new the AutoCompleteTextView instance
        parser = mActivity.getResources().getXml(R.layout.simple_dropdown_item_1line);
        AttributeSet attributeSet = Xml.asAttributeSet(parser);
        new AutoCompleteTextView(mActivity, attributeSet);

        // new the AutoCompleteTextView instance
        parser = mActivity.getResources().getXml(R.layout.framelayout_layout);
        attributeSet = Xml.asAttributeSet(parser);
        new AutoCompleteTextView(mActivity, attributeSet, 0);
        // Test constructor with null Context, in fact, previous two functions will
        // finally invoke this version.
        try {
            // Test with null Context
            new AutoCompleteTextView(null, attributeSet, 0);
            fail("should throw NullPointerException");
        } catch (Exception e) {
        }

        // Test for negative style resource ID
        new AutoCompleteTextView(mActivity, attributeSet, -1);
        // Test null AttributeSet
        new AutoCompleteTextView(mActivity, null, -1);
    }

    public void testEnoughToFilter() {
        mAutoCompleteTextView.setThreshold(3);
        assertEquals(3, mAutoCompleteTextView.getThreshold());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setText("TryToTest"));
        mInstrumentation.waitForIdleSync();
        assertTrue(mAutoCompleteTextView.enoughToFilter());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setText("No"));
        mInstrumentation.waitForIdleSync();
        assertFalse(mAutoCompleteTextView.enoughToFilter());
    }

    public void testAccessAdapter() {
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setAdapter(null));
        assertNull(mAutoCompleteTextView.getAdapter());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setAdapter(mAdapter));
        assertSame(mAdapter, mAutoCompleteTextView.getAdapter());

        // Re-set adapter to null
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setAdapter(null));
        assertNull(mAutoCompleteTextView.getAdapter());
    }

    @UiThreadTest
    public void testAccessFilter() {
        MockAutoCompleteTextView autoCompleteTextView = new MockAutoCompleteTextView(mActivity);

        // Set Threshold to 4 characters
        autoCompleteTextView.setThreshold(4);

        autoCompleteTextView.setAdapter(null);
        assertNull(autoCompleteTextView.getAdapter());
        assertNull(autoCompleteTextView.getFilter());

        Filter filter = mAdapter.getFilter();
        assertNotNull(filter);
        autoCompleteTextView.setAdapter(mAdapter);
        assertSame(mAdapter, autoCompleteTextView.getAdapter());
        assertSame(filter, autoCompleteTextView.getFilter());

        // Re-set adapter to null
        autoCompleteTextView.setAdapter(null);
        assertNull(autoCompleteTextView.getAdapter());
        assertNull(autoCompleteTextView.getFilter());
    }

    public void testAccessItemClickListener() {
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);

        // To ensure null listener
        mAutoCompleteTextView.setOnItemClickListener(null);
        assertNull(mAutoCompleteTextView.getItemClickListener());
        assertNull(mAutoCompleteTextView.getOnItemClickListener());

        mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
        assertSame(mockItemClickListener, mAutoCompleteTextView.getItemClickListener());
        assertSame(mockItemClickListener, mAutoCompleteTextView.getOnItemClickListener());
        verifyZeroInteractions(mockItemClickListener);

        // re-clear listener by setOnItemClickListener
        mAutoCompleteTextView.setOnItemClickListener(null);
        assertNull(mAutoCompleteTextView.getItemClickListener());
        assertNull(mAutoCompleteTextView.getOnItemClickListener());
        verifyZeroInteractions(mockItemClickListener);
    }

    public void testAccessItemSelectedListener() {
        final AdapterView.OnItemSelectedListener mockItemSelectedListener =
                mock(AdapterView.OnItemSelectedListener.class);

        // To ensure null listener
        mAutoCompleteTextView.setOnItemSelectedListener(null);
        assertNull(mAutoCompleteTextView.getItemSelectedListener());
        assertNull(mAutoCompleteTextView.getOnItemSelectedListener());

        mAutoCompleteTextView.setOnItemSelectedListener(mockItemSelectedListener);
        assertSame(mockItemSelectedListener, mAutoCompleteTextView.getItemSelectedListener());
        assertSame(mockItemSelectedListener, mAutoCompleteTextView.getOnItemSelectedListener());
        verifyZeroInteractions(mockItemSelectedListener);

        //re-clear listener by setOnItemClickListener
        mAutoCompleteTextView.setOnItemSelectedListener(null);
        assertNull(mAutoCompleteTextView.getItemSelectedListener());
        assertNull(mAutoCompleteTextView.getOnItemSelectedListener());
        verifyZeroInteractions(mockItemSelectedListener);
    }

    @UiThreadTest
    public void testConvertSelectionToString() {
        MockAutoCompleteTextView autoCompleteTextView = new MockAutoCompleteTextView(mActivity);

        // Set Threshold to 4 characters
        autoCompleteTextView.setThreshold(4);
        autoCompleteTextView.setAdapter(mAdapter);
        assertNotNull(autoCompleteTextView.getAdapter());

        assertEquals("", autoCompleteTextView.convertSelectionToString(null));
        assertEquals(STRING_TEST, autoCompleteTextView.convertSelectionToString(STRING_TEST));
    }

    @UiThreadTest
    public void testOnTextChanged() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);
        mAutoCompleteTextView.addTextChangedListener(mockTextWatcher);
        verify(mockTextWatcher, never()).onTextChanged(any(CharSequence.class),
                anyInt(), anyInt(), anyInt());

        mAutoCompleteTextView.setText(STRING_TEST);
        verify(mockTextWatcher, times(1)).onTextChanged(TestUtils.sameCharSequence(STRING_TEST),
                eq(0), eq(0), eq(STRING_TEST.length()));

        // Test replacing text.
        mAutoCompleteTextView.setText(STRING_CHECK);
        verify(mockTextWatcher, times(1)).onTextChanged(TestUtils.sameCharSequence(STRING_CHECK),
                eq(0), eq(STRING_TEST.length()), eq(STRING_CHECK.length()));
    }

    public void testPopupWindow() {
        final AutoCompleteTextView.OnDismissListener mockDismissListener =
                mock(AutoCompleteTextView.OnDismissListener.class);
        mAutoCompleteTextView.setOnDismissListener(mockDismissListener);

        assertFalse(mAutoCompleteTextView.isPopupShowing());
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.showDropDown());
        assertTrue(mAutoCompleteTextView.isPopupShowing());
        verifyZeroInteractions(mockDismissListener);

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.dismissDropDown());
        assertFalse(mAutoCompleteTextView.isPopupShowing());
        verify(mockDismissListener, times(1)).onDismiss();

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.showDropDown());
        assertTrue(mAutoCompleteTextView.isPopupShowing());
        verify(mockDismissListener, times(1)).onDismiss();

        final MockValidator validator = new MockValidator();
        mAutoCompleteTextView.setValidator(validator);
        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.requestFocus();
            mAutoCompleteTextView.showDropDown();
            mAutoCompleteTextView.setText(STRING_TEST);
        });
        assertEquals(STRING_TEST, mAutoCompleteTextView.getText().toString());

        // clearFocus will trigger onFocusChanged, and onFocusChanged will validate the text.
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.clearFocus());
        assertFalse(mAutoCompleteTextView.isPopupShowing());
        assertEquals(STRING_VALIDATED, mAutoCompleteTextView.getText().toString());
        verify(mockDismissListener, times(2)).onDismiss();

        verifyNoMoreInteractions(mockDismissListener);
    }

    public void testDropDownMetrics() {
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setAdapter(mAdapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth =
                res.getDimensionPixelSize(R.dimen.autocomplete_textview_dropdown_width);
        final int dropDownHeight =
                res.getDimensionPixelSize(R.dimen.autocomplete_textview_dropdown_height);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.autocomplete_textview_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.autocomplete_textview_dropdown_offset_v);

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setDropDownWidth(dropDownWidth);
            mAutoCompleteTextView.setDropDownHeight(dropDownHeight);
            mAutoCompleteTextView.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mAutoCompleteTextView.setDropDownVerticalOffset(dropDownOffsetVertical);

            mAutoCompleteTextView.showDropDown();
        });

        assertEquals(dropDownWidth, mAutoCompleteTextView.getDropDownWidth());
        assertEquals(dropDownHeight, mAutoCompleteTextView.getDropDownHeight());
        assertEquals(dropDownOffsetHorizontal, mAutoCompleteTextView.getDropDownHorizontalOffset());
        assertEquals(dropDownOffsetVertical, mAutoCompleteTextView.getDropDownVerticalOffset());
    }

    public void testDropDownBackground() {
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setAdapter(mAdapter));

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setDropDownBackgroundResource(R.drawable.blue_fill);
            mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();

        Drawable dropDownBackground = mAutoCompleteTextView.getDropDownBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be blue", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.BLUE, 1, true);

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.dismissDropDown();
            mAutoCompleteTextView.setDropDownBackgroundDrawable(
                    mActivity.getDrawable(R.drawable.yellow_fill));
            mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();

        dropDownBackground = mAutoCompleteTextView.getDropDownBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be yellow", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.YELLOW, 1, true);
    }

    @UiThreadTest
    public void testReplaceText() {
        MockAutoCompleteTextView autoCompleteTextView = new MockAutoCompleteTextView(mActivity);
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);
        autoCompleteTextView.addTextChangedListener(mockTextWatcher);
        verify(mockTextWatcher, never()).onTextChanged(any(CharSequence.class),
                anyInt(), anyInt(), anyInt());

        autoCompleteTextView.replaceText("Text");
        assertEquals("Text", autoCompleteTextView.getText().toString());
        verify(mockTextWatcher, times(1)).onTextChanged(TestUtils.sameCharSequence("Text"),
                eq(0), eq(0), eq("Text".length()));

        autoCompleteTextView.replaceText("Another");
        assertEquals("Another", autoCompleteTextView.getText().toString());
        verify(mockTextWatcher, times(1)).onTextChanged(TestUtils.sameCharSequence("Another"),
                eq(0), eq("Text".length()), eq("Another".length()));
    }

    @UiThreadTest
    public void testSetFrame() {
        MockAutoCompleteTextView autoCompleteTextView = new MockAutoCompleteTextView(mActivity);

        assertTrue(autoCompleteTextView.setFrame(0, 1, 2, 3));
        assertEquals(0, autoCompleteTextView.getLeft());
        assertEquals(1, autoCompleteTextView.getTop());
        assertEquals(2, autoCompleteTextView.getRight());
        assertEquals(3, autoCompleteTextView.getBottom());

        // If the values are the same as old ones, function will return false
        assertFalse(autoCompleteTextView.setFrame(0, 1, 2, 3));
        assertEquals(0, autoCompleteTextView.getLeft());
        assertEquals(1, autoCompleteTextView.getTop());
        assertEquals(2, autoCompleteTextView.getRight());
        assertEquals(3, autoCompleteTextView.getBottom());

        // If the values are not the same as old ones, function will return true
        assertTrue(autoCompleteTextView.setFrame(2, 3, 4, 5));
        assertEquals(2, autoCompleteTextView.getLeft());
        assertEquals(3, autoCompleteTextView.getTop());
        assertEquals(4, autoCompleteTextView.getRight());
        assertEquals(5, autoCompleteTextView.getBottom());
    }

    public void testGetThreshold() {
        assertEquals(1, mAutoCompleteTextView.getThreshold());
        mAutoCompleteTextView.setThreshold(3);
        assertEquals(3, mAutoCompleteTextView.getThreshold());

        // Test negative value input
        mAutoCompleteTextView.setThreshold(-5);
        assertEquals(1, mAutoCompleteTextView.getThreshold());

        // Test zero
        mAutoCompleteTextView.setThreshold(0);
        assertEquals(1, mAutoCompleteTextView.getThreshold());
    }

    public void testAccessValidater() {
        final MockValidator validator = new MockValidator();

        assertNull(mAutoCompleteTextView.getValidator());
        mAutoCompleteTextView.setValidator(validator);
        assertSame(validator, mAutoCompleteTextView.getValidator());

        // Set to null
        mAutoCompleteTextView.setValidator(null);
        assertNull(mAutoCompleteTextView.getValidator());
    }

    public void testOnFilterComplete() {
        // Set Threshold to 4 characters
        mAutoCompleteTextView.setThreshold(4);

        String testString = "";
        if (mNumeric) {
            // "tes" in case of 12-key(NUMERIC) keyboard
            testString = "8337777";
        } else {
            testString = "tes";
        }

        // Test the filter if the input string is not long enough to threshold
        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setAdapter(mAdapter);
                mAutoCompleteTextView.setText("");
                mAutoCompleteTextView.requestFocus();
        });
        mInstrumentation.sendStringSync(testString);

        // onFilterComplete will close the popup.
        PollingCheck.waitFor(() -> !mAutoCompleteTextView.isPopupShowing());

        if (mNumeric) {
            // "that" in case of 12-key(NUMERIC) keyboard
            testString = "84428";
        } else {
            testString = "that";
        }
        mInstrumentation.sendStringSync(testString);
        PollingCheck.waitFor(() -> !mAutoCompleteTextView.isPopupShowing());

        // Test the expected filter matching scene
        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setFocusable(true);
                mAutoCompleteTextView.requestFocus();
                mAutoCompleteTextView.setText("");
        });
        if (mNumeric) {
            // "test" in case of 12-key(NUMERIC) keyboard
            mInstrumentation.sendStringSync("83377778");
        } else {
            mInstrumentation.sendStringSync("test");
        }
        assertTrue(mAutoCompleteTextView.hasFocus());
        assertTrue(mAutoCompleteTextView.hasWindowFocus());
        PollingCheck.waitFor(() -> mAutoCompleteTextView.isPopupShowing());
    }

    public void testPerformFiltering() {
        if (isTvMode()) {
            return;
        }
        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setAdapter(mAdapter);
                mAutoCompleteTextView.setValidator(mValidator);

                mAutoCompleteTextView.setText("test");
                mAutoCompleteTextView.setFocusable(true);
                mAutoCompleteTextView.requestFocus();
                mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mAutoCompleteTextView.isPopupShowing());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        // KeyBack will close the popup.
        assertFalse(mAutoCompleteTextView.isPopupShowing());

        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.dismissDropDown();
                mAutoCompleteTextView.setText(STRING_TEST);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(STRING_TEST, mAutoCompleteTextView.getText().toString());
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        // If the popup is closed, onKeyDown will invoke performValidation.
        assertEquals(STRING_VALIDATED, mAutoCompleteTextView.getText().toString());

        final MockAdapter<String> adapter = new MockAdapter<String>(mActivity,
                android.R.layout.simple_dropdown_item_1line, WORDS);

        // Set Threshold to 4 charactersonKeyDown
        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setAdapter(adapter);
                mAutoCompleteTextView.requestFocus();
                mAutoCompleteTextView.setText("");
        });
        mInstrumentation.waitForIdleSync();
        // Create and get the filter.
        final MockFilter filter = (MockFilter) adapter.getFilter();

        // performFiltering will be indirectly invoked by onKeyDown
        assertNull(filter.getResult());
        // 12-key support
        if (mNumeric) {
            // "numeric" in case of 12-key(NUMERIC) keyboard
            mInstrumentation.sendStringSync("6688633777444222");
            PollingCheck.waitFor(() -> "numeric".equals(filter.getResult()));
        } else {
            SystemClock.sleep(200);
            mInstrumentation.sendStringSync(STRING_TEST);
            PollingCheck.waitFor(() -> STRING_TEST.equals(filter.getResult()));
        }
    }

    public void testPerformCompletionWithDPad() {
        if (isTvMode()) {
            return;
        }
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
            mAutoCompleteTextView.setAdapter(mAdapter);
            mAutoCompleteTextView.requestFocus();
            mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        // Key is ENTER or DPAD_ENTER, will invoke completion
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        mInstrumentation.waitForIdleSync();
        verify(mockItemClickListener, times(1)).onItemClick(any(AdapterView.class), any(View.class),
                eq(0), eq(0L));
        assertEquals(WORDS[0], mAutoCompleteTextView.getText().toString());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.showDropDown());
        mInstrumentation.waitForIdleSync();
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        verify(mockItemClickListener, times(2)).onItemClick(any(AdapterView.class), any(View.class),
                eq(0), eq(0L));
        assertEquals(WORDS[0], mAutoCompleteTextView.getText().toString());
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.showDropDown());
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        // Test normal key code.
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_0);
        verifyNoMoreInteractions(mockItemClickListener);
        assertNotSame("", mAutoCompleteTextView.getText().toString());
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        // Test the method on the scene of popup is closed.
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.dismissDropDown());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        verifyNoMoreInteractions(mockItemClickListener);
        assertNotSame("", mAutoCompleteTextView.getText().toString());
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());
    }

    public void testPerformCompletionExplicit() {
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        // Create a custom watcher that checks isPerformingCompletion to return true
        // in the "middle" of the performCompletion processing. We also spy on this watcher
        // to make sure that its onTextChanged is invoked.
        final TextWatcher myTextWatcher = new MyTextWatcher(WORDS[1]);
        final TextWatcher spyTextWatcher = spy(myTextWatcher);
        mAutoCompleteTextView.addTextChangedListener(spyTextWatcher);

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
            mAutoCompleteTextView.setAdapter(mAdapter);
            mAutoCompleteTextView.requestFocus();
            mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(mAutoCompleteTextView.isPopupShowing());
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.performCompletion());
        verify(mockItemClickListener, times(1)).onItemClick(any(AdapterView.class), any(View.class),
                eq(1), eq(1L));
        assertEquals(WORDS[1], mAutoCompleteTextView.getText().toString());
        assertFalse(mAutoCompleteTextView.isPerformingCompletion());
        assertFalse(mAutoCompleteTextView.isPopupShowing());

        verify(spyTextWatcher, atLeastOnce()).onTextChanged(TestUtils.sameCharSequence(WORDS[1]),
                eq(0), eq(0), eq(WORDS[1].length()));
        verifyNoMoreInteractions(mockItemClickListener);
    }

    public void testSetTextWithCompletion() {
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
            mAutoCompleteTextView.setAdapter(mAdapter);
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(mAutoCompleteTextView.isPopupShowing());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setText("testO", true));
        mInstrumentation.waitForIdleSync();

        assertTrue(mAutoCompleteTextView.isPopupShowing());
        verifyZeroInteractions(mockItemClickListener);
    }

    public void testSetTextWithNoCompletion() {
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);

        mInstrumentation.runOnMainSync(() -> {
            mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
            mAutoCompleteTextView.setAdapter(mAdapter);
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(mAutoCompleteTextView.isPopupShowing());

        mInstrumentation.runOnMainSync(() -> mAutoCompleteTextView.setText("testO", false));
        mInstrumentation.waitForIdleSync();

        assertFalse(mAutoCompleteTextView.isPopupShowing());
        verifyZeroInteractions(mockItemClickListener);
    }

    @UiThreadTest
    public void testPerformValidation() {
        final CharSequence text = "this";

        mAutoCompleteTextView.setValidator(mValidator);
        mAutoCompleteTextView.setAdapter((ArrayAdapter<String>) null);
        mAutoCompleteTextView.setText(text);
        mAutoCompleteTextView.performValidation();

        assertEquals(STRING_VALIDATED, mAutoCompleteTextView.getText().toString());
        mAutoCompleteTextView.setValidator(null);
    }

    public void testAccessCompletionHint() {
        mAutoCompleteTextView.setCompletionHint("TEST HINT");
        assertEquals("TEST HINT", mAutoCompleteTextView.getCompletionHint());

        mAutoCompleteTextView.setCompletionHint(null);
        assertNull(mAutoCompleteTextView.getCompletionHint());
    }

    public void testAccessListSelection() {
        final AdapterView.OnItemClickListener mockItemClickListener =
                mock(AdapterView.OnItemClickListener.class);

        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setOnItemClickListener(mockItemClickListener);
                mAutoCompleteTextView.setAdapter(mAdapter);
                mAutoCompleteTextView.requestFocus();
                mAutoCompleteTextView.showDropDown();
        });
        mInstrumentation.waitForIdleSync();

        mInstrumentation.runOnMainSync(() -> {
                mAutoCompleteTextView.setListSelection(1);
                assertEquals(1, mAutoCompleteTextView.getListSelection());

                mAutoCompleteTextView.setListSelection(2);
                assertEquals(2, mAutoCompleteTextView.getListSelection());

                mAutoCompleteTextView.clearListSelection();
                assertEquals(2, mAutoCompleteTextView.getListSelection());
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testAccessDropDownAnchor() {
        mAutoCompleteTextView.setDropDownAnchor(View.NO_ID);
        assertEquals(View.NO_ID, mAutoCompleteTextView.getDropDownAnchor());

        mAutoCompleteTextView.setDropDownAnchor(0x5555);
        assertEquals(0x5555, mAutoCompleteTextView.getDropDownAnchor());
    }

    public void testAccessDropDownWidth() {
        mAutoCompleteTextView.setDropDownWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, mAutoCompleteTextView.getDropDownWidth());

        mAutoCompleteTextView.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, mAutoCompleteTextView.getDropDownWidth());
    }

    private class MockValidator implements AutoCompleteTextView.Validator {
        public CharSequence fixText(CharSequence invalidText) {
            return STRING_VALIDATED;
        }

        public boolean isValid(CharSequence text) {
            return (text == STRING_TEST);
        }
    }

    protected static class MockAutoCompleteTextView extends AutoCompleteTextView {
        public MockAutoCompleteTextView(Context context) {
            super(context);
        }

        public MockAutoCompleteTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected CharSequence convertSelectionToString(Object selectedItem) {
            return super.convertSelectionToString(selectedItem);
        }

        @Override
        protected Filter getFilter() {
            return super.getFilter();
        }

        @Override
        protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
        }

        @Override
        protected void performFiltering(CharSequence text, int keyCode) {
            super.performFiltering(text, keyCode);
        }

        @Override
        protected void replaceText(CharSequence text) {
            super.replaceText(text);
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            return super.setFrame(l, t, r, b);
        }
    }

    private static class MockFilter extends Filter {
        private String mFilterResult;

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint != null) {
                mFilterResult = constraint.toString();
            }
            return null;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
        }

        public String getResult() {
            return mFilterResult;
        }
    }

    private static class MockAdapter<T> extends ArrayAdapter<T> implements Filterable {
        private MockFilter mFilter;

        public MockAdapter(Context context, int textViewResourceId, T[] objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new MockFilter();
            }
            return mFilter;
        }
    }
}
