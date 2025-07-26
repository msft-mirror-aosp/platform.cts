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

package com.android.cts.managedprofile;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

import java.util.List;
import java.util.Set;

/**
 * This class contains tests for cross profile widget providers that are run on the managed profile.
 * Policies are set using {@link SetPolicyActivity} and then verified in these tests. The tests
 * cannot be run independently, but are part of one hostside test.
 */
public class CrossProfileWidgetTest extends BaseManagedProfileTest {
    static final String WIDGET_PROVIDER_PKG = "com.android.cts.widgetprovider";
    static final String WIDGET_PROVIDER_PKG_2 = "com.android.cts.widgetprovider_2";
    static final String WIDGET_PROVIDER_PKG_3 = "com.android.cts.widgetprovider_3";

    private AppWidgetManager mAppWidgetManager;

    public void setUp() throws Exception {
        super.setUp();
        mAppWidgetManager = (AppWidgetManager) mContext.getSystemService(Context.APPWIDGET_SERVICE);
    }

    /**
     * This test checks that the widget provider was successfully allowlisted and verifies that if
     * was added successfully and can be found inside the profile.
     */
    public void testCrossProfileWidgetProviderAdded() {
        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);
        assertEquals(1, providers.size());
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG));
        // check that widget can be found inside the profile
        assertTrue(containsWidgetProviderPkg(mAppWidgetManager.getInstalledProviders()));
    }

    public void testCrossProfileWidgetProviderSet() {
        mDevicePolicyManager.setCrossProfileWidgetProviders(
                Set.of(WIDGET_PROVIDER_PKG, WIDGET_PROVIDER_PKG_2, WIDGET_PROVIDER_PKG_3));

        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);

        assertEquals(3, providers.size());
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG));
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG_2));
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG_3));
    }

    public void testCrossProfileWidgetProviderSetThenAdd() {
        mDevicePolicyManager.setCrossProfileWidgetProviders(
                Set.of(WIDGET_PROVIDER_PKG, WIDGET_PROVIDER_PKG_2));

        mDevicePolicyManager.addCrossProfileWidgetProvider(
                ADMIN_RECEIVER_COMPONENT,
                WIDGET_PROVIDER_PKG_3);

        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);

        assertEquals(3, providers.size());
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG_3));
    }

    public void testCrossProfileWidgetProviderSetThenRemove() {
        mDevicePolicyManager.setCrossProfileWidgetProviders(
                Set.of(WIDGET_PROVIDER_PKG, WIDGET_PROVIDER_PKG_2, WIDGET_PROVIDER_PKG_3));

        mDevicePolicyManager.removeCrossProfileWidgetProvider(
                ADMIN_RECEIVER_COMPONENT,
                WIDGET_PROVIDER_PKG);

        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);

        assertEquals(2, providers.size());
        assertFalse(providers.contains(WIDGET_PROVIDER_PKG));
    }

    public void testCrossProfileWidgetProviderAddThenSet() {
        mDevicePolicyManager.addCrossProfileWidgetProvider(
                ADMIN_RECEIVER_COMPONENT,
                WIDGET_PROVIDER_PKG);

        mDevicePolicyManager.setCrossProfileWidgetProviders(
                Set.of(WIDGET_PROVIDER_PKG_2, WIDGET_PROVIDER_PKG_3));

        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);

        assertEquals(2, providers.size());
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG_2));
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG_3));
    }

    /** This test verifies that the widget provider was successfully removed from the allowlist. */
    public void testCrossProfileWidgetProviderRemoved() {
        List<String> providers =
                mDevicePolicyManager.getCrossProfileWidgetProviders(ADMIN_RECEIVER_COMPONENT);
        assertTrue(providers.isEmpty());
        // Check that widget can still be found inside the profile
        assertTrue(containsWidgetProviderPkg(mAppWidgetManager.getInstalledProviders()));
    }

    public void testClearCrossProfileWidgetProviders() {
         mDevicePolicyManager.setCrossProfileWidgetProviders(Set.of());
    }

    private boolean containsWidgetProviderPkg(List<AppWidgetProviderInfo> widgets) {
        for (AppWidgetProviderInfo widget : widgets) {
            if (WIDGET_PROVIDER_PKG.equals(widget.provider.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
