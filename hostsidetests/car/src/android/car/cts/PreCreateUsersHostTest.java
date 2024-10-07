/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.cts;


import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for pre-created users. Feature no longer supported.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class PreCreateUsersHostTest extends CarHostJUnit4TestCase {
    @Test
    public void testAppsAreNotInstalledOnPreCreatedUser() throws Exception {
    }

    @Test
    public void testAppsAreNotInstalledOnPreCreatedGuest() throws Exception {
    }

    @Test
    public void testAppPermissionsPreCreatedUserPackages() throws Exception {
    }

    @Test
    public void testAppPermissionsPreCreatedGuestPackages() throws Exception {
    }

}
