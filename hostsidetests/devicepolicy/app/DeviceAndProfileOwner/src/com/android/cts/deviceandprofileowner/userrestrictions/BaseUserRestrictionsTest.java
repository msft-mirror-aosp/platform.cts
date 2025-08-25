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
package com.android.cts.deviceandprofileowner.userrestrictions;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class BaseUserRestrictionsTest extends BaseDeviceAdminTest {
    protected static final String[] ALL_USER_RESTRICTIONS = new String[]{
            UserManager.DISALLOW_CONFIG_WIFI,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SHARE_LOCATION,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_CREDENTIALS,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.ENSURE_VERIFY_APPS,
            UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_UNMUTE_MICROPHONE,
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS,
            UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE,
            UserManager.DISALLOW_OUTGOING_BEAM,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.ALLOW_PARENT_PROFILE_APP_LINKING,
            UserManager.DISALLOW_DATA_ROAMING,
            UserManager.DISALLOW_SET_USER_ICON,
            UserManager.DISALLOW_BLUETOOTH,
            UserManager.DISALLOW_BLUETOOTH_SHARING,
            UserManager.DISALLOW_CAMERA_TOGGLE,
            UserManager.DISALLOW_MICROPHONE_TOGGLE,
            UserManager.DISALLOW_CHANGE_WIFI_STATE,
            UserManager.DISALLOW_WIFI_TETHERING,
            UserManager.DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI,
            UserManager.DISALLOW_WIFI_DIRECT,
            UserManager.DISALLOW_ADD_WIFI_CONFIG,
            UserManager.DISALLOW_CELLULAR_2G,
    };

    /**
     * Restrictions that affect all users when DO sets.
     */
    protected static final String[] DO_GLOBAL_RESTRICTIONS = new String[] {
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_BLUETOOTH,
            // UserManager.DISALLOW_DATA_ROAMING, // Not set during CTS
            UserManager.DISALLOW_CAMERA_TOGGLE,
            UserManager.DISALLOW_MICROPHONE_TOGGLE,
            UserManager.DISALLOW_CHANGE_WIFI_STATE,
            UserManager.DISALLOW_WIFI_TETHERING,
            UserManager.DISALLOW_WIFI_DIRECT,
            UserManager.DISALLOW_ADD_WIFI_CONFIG,

            // PO can set them too, but when DO sets them, they're global.
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_UNMUTE_MICROPHONE,
            UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS
    };

    public static final String[] HIDDEN_AND_PROHIBITED = new String[] {
            "no_record_audio",
            "no_wallpaper",
            "no_oem_unlock"
    };

    protected final void assertLayeredRestriction(String restriction, boolean expected) {
        boolean hasIt = hasUserRestriction(restriction);
        assertWithMessage("UM.hasUserRestriction(%s) on user %s", restriction, mContext.getUser())
                .that(hasIt).isEqualTo(expected);
    }

    protected final void assertOwnerRestriction(String restriction, boolean expected) {
        var restrictions = getUserRestrictions();
        assertWithMessage("restriction %s on user %s (allRestrictions=%s)", restriction,
                mContext.getUser(), toString(restrictions))
                        .that(restrictions.getBoolean(restriction)).isEqualTo(expected);
    }

    /** Returns whether {@link UserManager} itself has applied the given restriction to the user. */
    protected final boolean hasBaseUserRestriction(String restriction, UserHandle userHandle) {
        boolean hasIt = ShellIdentityUtils.invokeMethodWithShellPermissions(mUserManager,
                (um) -> um.hasBaseUserRestriction(restriction, userHandle));
        mLogger.logV("hasBaseUserRestriction(%s, %s): %b", restriction, userHandle, hasIt);
        return hasIt;
    }

    /**
     * Check that {@link UserManager#hasUserRestriction} gives the expected results for each
     * restriction.
     * @param expected the list of user restrictions that are expected to have been applied due
     *                 to DO/PO
     */
    protected final void assertRestrictions(Set<String> expected) {
        final UserHandle userHandle = Process.myUserHandle();
        for (String r : ALL_USER_RESTRICTIONS) {
            assertLayeredRestriction(r,
                    expected.contains(r) || hasBaseUserRestriction(r, userHandle));
        }
    }

    /**
     * Test that the given restriction can be set and cleared, then leave it set again.
     */
    protected final void assertSetClearUserRestriction(String restriction) {
        boolean hadRestriction = hasUserRestriction(restriction);

        assertOwnerRestriction(restriction, false);

        // Set.  Shouldn't throw.
        addUserRestriction(restriction);

        assertOwnerRestriction(restriction, true);
        assertLayeredRestriction(restriction, true);

        // Then clear.
        assertClearUserRestriction(restriction);

        assertLayeredRestriction(restriction, hadRestriction);

        // Then set again.
        addUserRestriction(restriction);
    }

    /**
     * Test that the given restriction can be cleared.  (and leave it cleared.)
     */
    protected final void assertClearUserRestriction(String restriction) {
        clearUserRestriction(restriction);

        assertOwnerRestriction(restriction, false);
    }

    protected final void assertClearDefaultRestrictions() {
        for (String restriction : getDefaultEnabledRestrictions()) {
            assertClearUserRestriction(restriction);
        }
    }

    /**
     * Test that the given restriction *cannot* be set (or clear).
     */
    protected final void assertCannotSetUserRestriction(String restriction) {
        boolean hadRestriction = hasUserRestriction(restriction);

        assertOwnerRestriction(restriction, false);

        // Set should fail.
        try {
            addUserRestriction(restriction);
            fail("Restriction=" + restriction);
        } catch (SecurityException e) {
            // SecurityException expected
        }

        // Shouldn't have changed.
        assertOwnerRestriction(restriction, false);
        assertLayeredRestriction(restriction, hadRestriction);

        // Clear should fail too.
        try {
            clearUserRestriction(restriction);
            fail("Restriction=" + restriction);
        } catch (SecurityException e) {
            // SecurityException expected
        }

        // Shouldn't have changed.
        assertOwnerRestriction(restriction, false);
        assertLayeredRestriction(restriction, hadRestriction);
    }

    /** For {@link #testSetAllRestrictions} */
    protected abstract String[] getAllowedRestrictions();

    /** For {@link #testSetAllRestrictions} */
    protected abstract String[] getDisallowedRestrictions();

    /** For {@link #testDefaultRestrictions()} */
    protected abstract String[] getDefaultEnabledRestrictions();

    /**
     * Test restrictions that should be enabled by default
     */
    public final void testDefaultRestrictions() {
        for (String restriction : getDefaultEnabledRestrictions()) {
            assertOwnerRestriction(restriction, true);
        }

        Set<String> offByDefaultRestrictions = new HashSet<>(Arrays.asList(ALL_USER_RESTRICTIONS));
        offByDefaultRestrictions.removeAll(
                new HashSet<>(Arrays.asList(getDefaultEnabledRestrictions())));
        for (String restriction : offByDefaultRestrictions) {
            assertOwnerRestriction(restriction, false);
        }
    }

    /**
     * Set only one restriction, and make sure only that's set, and then clear it.
     */
    public final void testSetAllRestrictionsIndividually() {
        assertClearDefaultRestrictions();
        for (String r : getAllowedRestrictions()) {
            // Set it.
            assertSetClearUserRestriction(r);

            assertRestrictions(new HashSet<>(Arrays.asList(new String[]{r})));

            // Then clear it.
            assertClearUserRestriction(r);
        }
    }

    /**
     * Make sure all allowed restrictions can be set, and the others can't.
     */
    public final void testSetAllRestrictions() {
        assertClearDefaultRestrictions();
        for (String r : getAllowedRestrictions()) {
            assertSetClearUserRestriction(r);
        }
        for (String r : getDisallowedRestrictions()) {
            assertCannotSetUserRestriction(r);
        }
        for (String r : HIDDEN_AND_PROHIBITED) {
            assertCannotSetUserRestriction(r);
        }
    }

    /**
     * Clear all allowed restrictions.
     */
    public final void testClearAllRestrictions() {
        for (String r : getAllowedRestrictions()) {
            assertClearUserRestriction(r);
        }
    }

    public final void testBroadcast() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        IntentFilter filter = new IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mLogger.logD("Received broadcast: %s", intent);
                latch.countDown();
            }
        }, filter);

        String restriction = UserManager.DISALLOW_CONFIG_WIFI;
        addUserRestriction(restriction);

        assertWithMessage("Received broadcast in 2 minutes)")
                .that(latch.await(120, TimeUnit.SECONDS)).isTrue();

        clearUserRestriction(restriction);
    }

    protected final boolean hasUserRestriction(String restriction) {
        boolean hasIt = mUserManager.hasUserRestriction(restriction);
        if (VERBOSE) {
            mLogger.logV("hasUserRestriction(%s): %b", restriction, hasIt);
        }
        return hasIt;
    }

    protected final Bundle getUserRestrictions() {
        return getUserRestrictions(VERBOSE);
    }

    private Bundle getUserRestrictions(boolean logResult) {
        Bundle restrictions = mDevicePolicyManager.getUserRestrictions(ADMIN_RECEIVER_COMPONENT);
        if (logResult) {
            mLogger.logV("getUserRestrictions(): %s", toString(restrictions));
        }
        return restrictions;
    }

    protected final void addUserRestriction(String restriction) {
        mLogger.logD("calling addUserRestriction(%s, %s)",
                ADMIN_RECEIVER_COMPONENT.flattenToShortString(), restriction);
        try {
            mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
        } catch (RuntimeException e) {
            mLogger.logE("addUserRestriction(%s, %s) failed: %s",
                    ADMIN_RECEIVER_COMPONENT.flattenToShortString(), restriction, e);
            throw e;
        }
        logUserRestrictionsAfterChangingThem();
    }

    protected final void clearUserRestriction(String restriction) {
        mLogger.logD("calling clearUserRestriction(%s, %s)",
                ADMIN_RECEIVER_COMPONENT.flattenToShortString(), restriction);
        try {
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
        } catch (RuntimeException e) {
            mLogger.logE("clearUserRestriction(%s, %s) failed: %s",
                    ADMIN_RECEIVER_COMPONENT.flattenToShortString(), restriction, e);
            throw e;
        }
        logUserRestrictionsAfterChangingThem();
    }

    private void logUserRestrictionsAfterChangingThem() {
        if (!VERBOSE) {
            return;
        }
        Bundle restrictions = getUserRestrictions(/* logResult= */ false);
        mLogger.logV("getUserRestrictions() afterwards: %s", toString(restrictions));
    }
}
