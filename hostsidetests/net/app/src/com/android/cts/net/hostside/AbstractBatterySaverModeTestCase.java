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

package com.android.cts.net.hostside;

/**
 * Base class for metered and non-metered Battery Saver Mode tests.
 */
abstract class AbstractBatterySaverModeTestCase extends AbstractRestrictBackgroundNetworkTestCase {

    @Override
    protected final void setUp() throws Exception {
        super.setUp();

        // Set initial state.
        setUpMeteredNetwork();
        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        setBatterySaverMode(false);

        registerBroadcastReceiver();
    }

    @Override
    protected final void tearDown() throws Exception {
        super.tearDown();

        try {
            tearDownMeteredNetwork();
        } finally {
            setBatterySaverMode(false);
        }
    }

    /**
     * Sets the initial (non) metered network state.
     *
     * <p>By default is empty - it's up to subclasses to override.
     */
    protected void setUpMeteredNetwork() throws Exception {
    }

    /**
     * Resets the (non) metered network state.
     *
     * <p>By default is empty - it's up to subclasses to override.
     */
    protected void tearDownMeteredNetwork() throws Exception {
    }

    public void testBackgroundNetworkAccess_enabled() throws Exception {
        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);

        // Make sure foreground app doesn't lose access upon enabling it.
        setBatterySaverMode(false);
        launchActivity();
        assertForegroundNetworkAccess();
        setBatterySaverMode(true);
        assertForegroundNetworkAccess();
        finishActivity();
        assertBackgroundNetworkAccess(false);

        // Same for foreground service.
        setBatterySaverMode(false);
        startForegroundService();
        assertForegroundNetworkAccess();
        setBatterySaverMode(true);
        assertForegroundNetworkAccess();
        stopForegroundService();
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_whitelisted() throws Exception {
        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);

        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(true);

        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        assertBackgroundNetworkAccess(false);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(false);
    }

    public void testBackgroundNetworkAccess_disabled() throws Exception {
        assertBackgroundNetworkAccess(true);

        assertsForegroundAlwaysHasNetworkAccess();
        assertBackgroundNetworkAccess(true);
    }
}
