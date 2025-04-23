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

package android.server.wm.activity

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.SetFlagsRule
import android.server.wm.WindowManagerTestBase
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceActivity:HandoffActivityTests
 */
@ApiTest(
    apis = [
    "android.app.Activity#setHandoffEnabled",
    "android.app.Activity#isHandoffEnabled",
    "android.app.Activity#isFullTaskRecreationAllowed"]
)
class HandoffActivityTests : WindowManagerTestBase() {

    @get:Rule val checkFlagsRule = SetFlagsRule()

    /**
     * Tests that [ActivityRecord.mIsHandoffEnabled] is correctly set when
     * [Activity.setHandoffEnabled] is called
     */
    @Test
    @RequiresFlagsEnabled(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
    fun setHandoffEnabled_updatesActivity() {
        val activity = startTestActivity()

        assertFalse(activity.isHandoffEnabled)
        assertFalse(activity.isHandoffFullTaskRecreationAllowed)

        activity.setHandoffEnabled(true, true)
        assertTrue(activity.isHandoffEnabled)
        assertTrue(activity.isHandoffFullTaskRecreationAllowed)

        activity.setHandoffEnabled(false, true)
        assertFalse(activity.isHandoffEnabled)
        assertFalse(activity.isHandoffFullTaskRecreationAllowed)
    }

    private fun startTestActivity(): TestActivity {
        val activity = startActivity(TestActivity::class.java)
        val activityName = activity!!.componentName
        waitAndAssertResumedActivity(activityName, "$activityName must be resumed")
        return activity
    }

    class TestActivity : FocusableActivity()
}
