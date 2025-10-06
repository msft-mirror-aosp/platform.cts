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

package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.TestOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Remove [Ignore] along with triggering test in [TestOrderTest] to test
 * restricted value exception
 */
@Ignore("Excluded from normal runs - used only in internal runner validation")
@RunWith(BedsteadJUnit4::class)
class TestOrderInvalidOrderTestingSample {

    @TestOrder(order = Int.MIN_VALUE)
    @Test
    fun testOrder_invalidOrderValue_throwsTestInitializationError() {
        // This will never run the body of the test due to runner throwing exception
    }
}
