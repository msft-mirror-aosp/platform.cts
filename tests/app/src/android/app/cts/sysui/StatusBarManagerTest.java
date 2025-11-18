/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.cts.sysui;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Flags;
import android.app.ShowPowerMenuCallback;
import android.app.StatusBarManager;
import android.app.StatusBarManager.DisableInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// TODO(b/375675436): Remove this annotation after control of the status bar for visible background
// users is allowed.
@RequireRunNotOnVisibleBackgroundNonProfileUser(reason = "This annotation is added to prevent"
        + " running as a visible background user, because access to control the status bar from"
        + " visible background users is currently not allowed. (b/332222893)")
@RunWith(AndroidJUnit4.class)
@SmallTest
public class StatusBarManagerTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PERMISSION_STATUS_BAR = android.Manifest.permission.STATUS_BAR;
    private static final String SLOT_MUTE = "telecom_mute";

    private StatusBarManager mStatusBarManager;
    private Context mContext;
    private UiAutomation mUiAutomation;

    /**
     * Setup
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mStatusBarManager = (StatusBarManager) mContext.getSystemService(
                Context.STATUS_BAR_SERVICE);
        mUiAutomation = getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_STATUS_BAR);
    }

    @After
    public void tearDown() throws Exception {

        if (mStatusBarManager != null) {
            // Adopt again since tests could've dropped it
            mUiAutomation.adoptShellPermissionIdentity(PERMISSION_STATUS_BAR);

            // Give the UI thread a chance to finish any animations that happened during the test,
            // otherwise it seems to just drop these calls
            // (b/233937748)
            Thread.sleep(100);

            mStatusBarManager.collapsePanels();
            mStatusBarManager.setDisabledForSetup(false);
            mStatusBarManager.setExpansionDisabledForSimNetworkLock(false);
            mStatusBarManager.setNavBarMode(StatusBarManager.NAV_BAR_MODE_DEFAULT);
            mStatusBarManager.removeIcon(SLOT_MUTE);
        }

        mUiAutomation.dropShellPermissionIdentity();
    }


    /**
     * Test StatusBarManager.setDisabledForSetup(true)
     * @throws Exception
     */
    @Test
    public void testDisableForSetup_setDisabledTrue() throws Exception {
        mStatusBarManager.setDisabledForSetup(true);

        // Check for the default set of disable flags
        assertDefaultFlagsArePresent(mStatusBarManager.getDisableInfo());
    }

    private void assertDefaultFlagsArePresent(DisableInfo info) {
        assertTrue(info.isNotificationPeekingDisabled());
        assertTrue(info.isNavigateToHomeDisabled());
        assertTrue(info.isStatusBarExpansionDisabled());
        assertTrue(info.isRecentsDisabled());
        assertTrue(info.isSearchDisabled());
        assertFalse(info.isRotationSuggestionDisabled());
    }

    /**
     * Test StatusBarManager.setDisabledForSetup(false)
     * @throws Exception
     */
    @Test
    public void testDisableForSetup_setDisabledFalse() throws Exception {
        // First disable, then re-enable
        mStatusBarManager.setDisabledForSetup(true);
        mStatusBarManager.setDisabledForSetup(false);

        DisableInfo info = mStatusBarManager.getDisableInfo();

        assertTrue("Invalid disableFlags", info.areAllComponentsEnabled());
    }

    @Test
    public void testDisableForSimLock_setDisabledTrue() throws Exception {
        mStatusBarManager.setExpansionDisabledForSimNetworkLock(true);

        // Check for the default set of disable flags
        assertTrue(mStatusBarManager.getDisableInfo().isStatusBarExpansionDisabled());
    }

    @Test
    public void testDisableForSimLock_setDisabledFalse() throws Exception {
        // First disable, then re-enable
        mStatusBarManager.setExpansionDisabledForSimNetworkLock(true);
        mStatusBarManager.setExpansionDisabledForSimNetworkLock(false);

        DisableInfo info = mStatusBarManager.getDisableInfo();
        assertTrue("Invalid disableFlags", info.areAllComponentsEnabled());
    }

    @Test(expected = SecurityException.class)
    public void testCollapsePanels_withoutStatusBarPermission_throws() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp(), so drop it now
        mUiAutomation.dropShellPermissionIdentity();

        mStatusBarManager.collapsePanels();
    }

    @Test
    public void testCollapsePanels_withStatusBarPermission_doesNotThrow() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp()

        mStatusBarManager.collapsePanels();

        // Nothing thrown, passed
    }

    @Test(expected = SecurityException.class)
    public void testTogglePanel_withoutStatusBarPermission_throws() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp(), so drop it now
        mUiAutomation.dropShellPermissionIdentity();

        mStatusBarManager.togglePanel();
    }

    @Test
    public void testTogglePanel_withStatusBarPermission_doesNotThrow() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp()

        mStatusBarManager.togglePanel();

        // Nothing thrown, passed
    }

    @Test(expected = SecurityException.class)
    public void testHandleSystemKey_withoutStatusBarPermission_throws() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp(), so drop it now
        mUiAutomation.dropShellPermissionIdentity();

        mStatusBarManager.handleSystemKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP));
    }

    @Test
    public void testHandleSystemKey_withStatusBarPermission_doesNotThrow() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp()

        mStatusBarManager.handleSystemKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP));

        // Nothing thrown, passed
    }

    /**
     * Test StatusBarManager.setNavBarMode(NAV_BAR_MODE_KIDS)
     *
     * @throws Exception
     */
    @CddTest(requirement = "7.2.3/C-9-1")
    @Test
    public void testSetNavBarMode_kids_doesNotThrow() throws Exception {
        int navBarModeKids = StatusBarManager.NAV_BAR_MODE_KIDS;
        mStatusBarManager.setNavBarMode(navBarModeKids);

        assertEquals(mStatusBarManager.getNavBarMode(), navBarModeKids);
    }

    /**
     * Test StatusBarManager.setNavBarMode(NAV_BAR_MODE_NONE)
     *
     * @throws Exception
     */
    @Test
    public void testSetNavBarMode_none_doesNotThrow() throws Exception {
        int navBarModeNone = StatusBarManager.NAV_BAR_MODE_DEFAULT;
        mStatusBarManager.setNavBarMode(navBarModeNone);

        assertEquals(mStatusBarManager.getNavBarMode(), navBarModeNone);
    }

    /**
     * Test StatusBarManager.setNavBarMode(-1) // invalid input
     *
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSetNavBarMode_invalid_throws() throws Exception {
        int invalidInput = -1;
        mStatusBarManager.setNavBarMode(invalidInput);
    }

    @RequiresFlagsEnabled(android.telecom.flags.Flags.FLAG_RELEASE_ICON_AS_API)
    @ApiTest(apis = {"android.app.StatusBarManager#setIcon"})
    @Test(expected = SecurityException.class)
    public void testSetIcon_withoutStatusBarPermission_throws() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp(), so drop it now
        mUiAutomation.dropShellPermissionIdentity();

        mStatusBarManager.setIcon(SLOT_MUTE, android.R.drawable.stat_notify_call_mute, 0, null);
    }

    @RequiresFlagsEnabled(android.telecom.flags.Flags.FLAG_RELEASE_ICON_AS_API)
    @ApiTest(apis = {"android.app.StatusBarManager#setIcon"})
    @Test
    public void testSetIcon_withStatusBarPermission_doesNotThrow() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp()

        mStatusBarManager.setIcon(SLOT_MUTE, android.R.drawable.stat_notify_call_mute, 0, null);

        // Nothing thrown, passed
    }

    @RequiresFlagsEnabled(android.telecom.flags.Flags.FLAG_RELEASE_ICON_AS_API)
    @ApiTest(apis = {"android.app.StatusBarManager#removeIcon"})
    @Test(expected = SecurityException.class)
    public void testRemoveIcon_withoutStatusBarPermission_throws() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp(), so drop it now
        mUiAutomation.dropShellPermissionIdentity();

        mStatusBarManager.removeIcon(SLOT_MUTE);
    }

    @RequiresFlagsEnabled(android.telecom.flags.Flags.FLAG_RELEASE_ICON_AS_API)
    @ApiTest(apis = {"android.app.StatusBarManager#removeIcon"})
    @Test
    public void testRemoveIcon_withStatusBarPermission_doesNotThrow() throws Exception {
        // We've adopted shell identity for STATUS_BAR in setUp()

        mStatusBarManager.removeIcon(SLOT_MUTE);
        // Nothing thrown, passed
    }

    @RequiresFlagsEnabled(android.telecom.flags.Flags.FLAG_RELEASE_ICON_AS_API)
    @ApiTest(
            apis = {
                "android.app.StatusBarManager#setIcon",
                "android.app.StatusBarManager#removeIcon"
            })
    @Test
    public void testSetIcon_thenRemoveIcon() throws Exception {
        mStatusBarManager.removeIcon(SLOT_MUTE);
        mStatusBarManager.setIcon(SLOT_MUTE, android.R.drawable.stat_notify_call_mute, 0, null);

        // No exception to get the resource id of the icon
        mStatusBarManager.getIcon(SLOT_MUTE);

        mStatusBarManager.removeIcon(SLOT_MUTE);

        assertThrows(IllegalArgumentException.class, () -> mStatusBarManager.getIcon(SLOT_MUTE));
    }

    @RequiresFlagsEnabled(Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    @ApiTest(apis = {"android.app.StatusBarManager#showPowerMenu"})
    @EnsureDoesNotHavePermission(Manifest.permission.SHOW_POWER_MENU)
    @Test
    public void testShowPowerMenu_noPermission_securityException() throws Exception {
        ShowPowerMenuCallback callback =
                new ShowPowerMenuCallback() {
                    @Override
                    public void onPowerMenuShown(boolean showing) {
                        fail("onPowerMenuShown");
                    }

                    @Override
                    public void onError(int error) {
                        fail("onError");
                    }
                };
        assertThrows(
                SecurityException.class,
                () -> mStatusBarManager.showPowerMenu(Runnable::run, callback));
    }

    @ApiTest(apis = {"android.app.StatusBarManager#showPowerMenu"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    public void testShowPowerMenu_hasPermissionPrivileged_succeeds() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ShowPowerMenuCallback callback =
                new ShowPowerMenuCallback() {
                    @Override
                    public void onPowerMenuShown(boolean showing) {
                        assertTrue(showing);
                        latch.countDown();
                    }

                    @Override
                    public void onError(int error) {
                        fail("onError");
                    }
                };
        try (PermissionContext ignored =
                TestApis.permissions()
                        .withPermission(Manifest.permission.SHOW_POWER_MENU_PRIVILEGED)) {
            mStatusBarManager.showPowerMenu(Runnable::run, callback);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            mUiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        }
    }

    @ApiTest(apis = {"android.app.StatusBarManager#showPowerMenu"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    public void testShowPowerMenu_hasPermissionRole_succeeds() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ShowPowerMenuCallback callback =
                new ShowPowerMenuCallback() {
                    @Override
                    public void onPowerMenuShown(boolean showing) {
                        assertTrue(showing);
                        latch.countDown();
                    }

                    @Override
                    public void onError(int error) {
                        fail("onError");
                    }
                };
        try (PermissionContext ignored =
                TestApis.permissions().withPermission(Manifest.permission.SHOW_POWER_MENU)) {
            mStatusBarManager.showPowerMenu(Runnable::run, callback);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            mUiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        }
    }
}
