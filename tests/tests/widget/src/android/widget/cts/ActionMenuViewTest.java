/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.Instrumentation;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ActionMenuView;
import android.widget.Toolbar;
import android.widget.cts.util.TestUtils;
import android.widget.cts.util.ViewTestUtils;

import static org.mockito.Mockito.*;

@MediumTest
public class ActionMenuViewTest
        extends ActivityInstrumentationTestCase2<ActionMenuViewCtsActivity> {
    private Instrumentation mInstrumentation;
    private ActionMenuViewCtsActivity mActivity;
    private ActionMenuView mActionMenuView;

    public ActionMenuViewTest() {
        super("android.widget.cts", ActionMenuViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mActionMenuView = (ActionMenuView) mActivity.findViewById(R.id.action_menu_view);
    }

    public void testConstructor() {
        new ActionMenuView(mActivity);

        new ActionMenuView(mActivity, null);
    }

    public void testMenuContent() {
        final Menu menu = mActionMenuView.getMenu();
        assertNotNull(menu);

        mInstrumentation.runOnMainSync(
                () -> mActivity.getMenuInflater().inflate(
                        R.menu.toolbar_menu, menu));

        assertEquals(6, menu.size());
        assertEquals(R.id.action_highlight, menu.getItem(0).getItemId());
        assertEquals(R.id.action_edit, menu.getItem(1).getItemId());
        assertEquals(R.id.action_delete, menu.getItem(2).getItemId());
        assertEquals(R.id.action_ignore, menu.getItem(3).getItemId());
        assertEquals(R.id.action_share, menu.getItem(4).getItemId());
        assertEquals(R.id.action_print, menu.getItem(5).getItemId());

        ActionMenuView.OnMenuItemClickListener menuItemClickListener =
                mock(ActionMenuView.OnMenuItemClickListener.class);
        mActionMenuView.setOnMenuItemClickListener(menuItemClickListener);

        menu.performIdentifierAction(R.id.action_highlight, 0);
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_highlight));

        menu.performIdentifierAction(R.id.action_share, 0);
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_share));
    }

    public void testMenuOverflowShowHide() {
        // Inflate menu and check that we're not showing overflow menu yet
        mInstrumentation.runOnMainSync(
                () -> mActivity.getMenuInflater().inflate(
                        R.menu.toolbar_menu, mActionMenuView.getMenu()));
        assertFalse(mActionMenuView.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        mInstrumentation.runOnMainSync(() -> mActionMenuView.showOverflowMenu());
        mInstrumentation.waitForIdleSync();
        assertTrue(mActionMenuView.isOverflowMenuShowing());

        // Ask to hide the overflow menu and check that it's not showing
        mInstrumentation.runOnMainSync(() -> mActionMenuView.hideOverflowMenu());
        mInstrumentation.waitForIdleSync();
        assertFalse(mActionMenuView.isOverflowMenuShowing());
    }

    public void testMenuOverflowSubmenu() {
        // Inflate menu and check that we're not showing overflow menu yet
        mInstrumentation.runOnMainSync(
                () -> mActivity.getMenuInflater().inflate(
                        R.menu.toolbar_menu, mActionMenuView.getMenu()));
        assertFalse(mActionMenuView.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        mInstrumentation.runOnMainSync(() -> mActionMenuView.showOverflowMenu());
        mInstrumentation.waitForIdleSync();
        assertTrue(mActionMenuView.isOverflowMenuShowing());

        // Register a mock menu item click listener on the toolbar
        ActionMenuView.OnMenuItemClickListener menuItemClickListener =
                mock(ActionMenuView.OnMenuItemClickListener.class);
        mActionMenuView.setOnMenuItemClickListener(menuItemClickListener);

        final Menu menu = mActionMenuView.getMenu();

        // Ask to "perform" the share action and check that the menu click listener has
        // been notified
        mInstrumentation.runOnMainSync(() -> menu.performIdentifierAction(R.id.action_share, 0));
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_share));

        // Ask to dismiss all the popups and check that we're not showing the overflow menu
        mInstrumentation.runOnMainSync(() -> mActionMenuView.dismissPopupMenus());
        mInstrumentation.waitForIdleSync();
        assertFalse(mActionMenuView.isOverflowMenuShowing());
    }

    public void testMenuOverflowIcon() {
        // Inflate menu and check that we're not showing overflow menu yet
        mInstrumentation.runOnMainSync(
                () -> mActivity.getMenuInflater().inflate(
                        R.menu.toolbar_menu, mActionMenuView.getMenu()));

        final Drawable overflowIcon = mActivity.getDrawable(R.drawable.icon_red);
        ViewTestUtils.runOnMainAndDrawSync(mInstrumentation, mActionMenuView,
                () -> mActionMenuView.setOverflowIcon(overflowIcon));

        final Drawable toolbarOverflowIcon = mActionMenuView.getOverflowIcon();
        TestUtils.assertAllPixelsOfColor("Overflow icon is red", toolbarOverflowIcon,
                toolbarOverflowIcon.getIntrinsicWidth(), toolbarOverflowIcon.getIntrinsicHeight(),
                true, Color.RED, 1, false);
    }

    public void testPopupTheme() {
        mInstrumentation.runOnMainSync(() -> {
                mActivity.getMenuInflater().inflate(R.menu.toolbar_menu, mActionMenuView.getMenu());
                mActionMenuView.setPopupTheme(R.style.ToolbarPopupTheme_Test);
        });
        assertEquals(R.style.ToolbarPopupTheme_Test, mActionMenuView.getPopupTheme());
    }
}
