/*
 * Copyright 2025 The Android Open Source Project
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
package android.hardware.input.cts.tests.virtualdevices

import com.android.compatibility.common.util.UserHelper
import org.junit.Assume.assumeFalse

abstract class VirtualDeviceSettingTestCase : VirtualDeviceTestCase() {

    abstract fun onSetupSetting()
    abstract fun onTearDownSetting()

    override fun onSetUp() {
        // TODO(b/454344508): Consider making InputManagerService multi-user aware.
        assumeFalse(
            "InputManagerService only tracks the current user. " +
                    "Settings changes for non-current users are not applied, causing tests " +
                    "to fail for visible background users.",
            UserHelper().isVisibleBackgroundUser()
        )
        onSetupSetting()
        super.onSetUp()
    }

    override fun onTearDown() {
        if (UserHelper().isVisibleBackgroundUser()) {
            return
        }
        onTearDownSetting()
        super.onTearDown()
    }
}
