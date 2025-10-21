/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.webkit.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.MenuItem;
import android.webkit.Flags;
import android.webkit.SelectionActionMenuClient;
import android.webkit.WebViewDelegate;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@MediumTest
@RequiresFlagsEnabled(Flags.FLAG_SELECTION_ACTION_MENU_CLIENT)
@RunWith(AndroidJUnit4.class)
public class SelectionActionMenuClientTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final WebViewDelegate mDelegate = new WebViewDelegate();

    private Context mContext;
    private SelectionActionMenuClient mClient;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mClient = mDelegate.getSelectionActionMenuClient(mContext);
    }

    @Test
    public void defaultMenuItemOrderIsWellFormed_floatingMenu() {
        int[] floatingOrder =
                mClient.getDefaultMenuItemOrder(SelectionActionMenuClient.MENU_TYPE_FLOATING);
        HashSet<Integer> allItemsFloating =
                new HashSet<>(
                        Set.of(
                                SelectionActionMenuClient.DEFAULT_ITEM_CUT,
                                SelectionActionMenuClient.DEFAULT_ITEM_COPY,
                                SelectionActionMenuClient.DEFAULT_ITEM_PASTE,
                                SelectionActionMenuClient.DEFAULT_ITEM_PASTE_AS_PLAIN_TEXT,
                                SelectionActionMenuClient.DEFAULT_ITEM_SHARE,
                                SelectionActionMenuClient.DEFAULT_ITEM_SELECT_ALL,
                                SelectionActionMenuClient.DEFAULT_ITEM_WEB_SEARCH));

        Assert.assertNotNull(floatingOrder);
        // Remove each item in the returned order from the set of all items to ensure that each item
        // appears exactly once.
        for (Integer item : floatingOrder) {
            allItemsFloating.remove(item);
        }

        Assert.assertEquals(7, floatingOrder.length);
        Assert.assertEquals(0, allItemsFloating.size());
    }

    @Test
    public void defaultMenuItemOrderIsWellFormed_dropdownMenu() {
        int[] dropdownOrder =
                mClient.getDefaultMenuItemOrder(SelectionActionMenuClient.MENU_TYPE_DROPDOWN);
        HashSet<Integer> allItemsDropdown =
                new HashSet<>(
                        Set.of(
                                SelectionActionMenuClient.DEFAULT_ITEM_CUT,
                                SelectionActionMenuClient.DEFAULT_ITEM_COPY,
                                SelectionActionMenuClient.DEFAULT_ITEM_PASTE,
                                SelectionActionMenuClient.DEFAULT_ITEM_PASTE_AS_PLAIN_TEXT,
                                SelectionActionMenuClient.DEFAULT_ITEM_SHARE,
                                SelectionActionMenuClient.DEFAULT_ITEM_SELECT_ALL,
                                SelectionActionMenuClient.DEFAULT_ITEM_WEB_SEARCH));

        Assert.assertNotNull(dropdownOrder);
        // Remove each item in the returned order from the set of all items to ensure that each item
        // appears exactly once.
        for (Integer item : dropdownOrder) {
            allItemsDropdown.remove(item);
        }

        Assert.assertEquals(7, dropdownOrder.length);
        Assert.assertEquals(0, allItemsDropdown.size());
    }

    @Test
    public void textProcessingActivitiesAreNotNull() {
        Intent processTextIntent =
                new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
        List<ResolveInfo> activities =
                mContext.getPackageManager().queryIntentActivities(processTextIntent, 0);
        List<ResolveInfo> filteredActivities =
                mClient.filterTextProcessingActivities(
                        mContext, SelectionActionMenuClient.MENU_TYPE_FLOATING, activities);
        Assert.assertNotNull(filteredActivities);
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_notPassword_notReadOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_FLOATING, false, false, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_notPassword_notReadOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_FLOATING,
                        false,
                        false,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_notPassword_readOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_FLOATING, false, true, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_notPassword_readOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_FLOATING,
                        false,
                        true,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_password_notReadOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_FLOATING, true, false, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_password_notReadOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_FLOATING,
                        true,
                        false,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_password_readOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_FLOATING, true, true, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_floatingMenu_password_readOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_FLOATING,
                        true,
                        true,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_notPassword_notReadOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_DROPDOWN, false, false, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_notPassword_notReadOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_DROPDOWN,
                        false,
                        false,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_notPassword_readOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_DROPDOWN, false, true, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_notPassword_readOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_DROPDOWN,
                        false,
                        true,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_password_notReadOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_DROPDOWN, true, false, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_password_notReadOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_DROPDOWN,
                        true,
                        false,
                        "Example text"));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_password_readOnly_emptyText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext, SelectionActionMenuClient.MENU_TYPE_DROPDOWN, true, true, ""));
    }

    @Test
    public void handlesAllClicksForAddedItems_dropdownMenu_password_readOnly_withText() {
        verifyItemsAreHandled(
                mClient.getAdditionalMenuItems(
                        mContext,
                        SelectionActionMenuClient.MENU_TYPE_DROPDOWN,
                        true,
                        true,
                        "Example text"));
    }

    private void verifyItemsAreHandled(List<MenuItem> items) {
        Assert.assertNotNull(items);
        for (MenuItem item : items) {
            Assert.assertTrue(
                    String.format(
                            "Expected handleMenuItemClick to return true for MenuItem with title:"
                                    + " '%s'",
                            item.getTitle()),
                    mClient.handleMenuItemClick(mContext, item));
        }
    }
}
