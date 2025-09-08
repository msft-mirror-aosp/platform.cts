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

package android.content.res.flaggedresourcesrw.cts

import android.app.LocaleConfig
import android.content.res.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class FlaggedResourcesRWTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAYOUT_READWRITE_FLAGS)
    fun testEnabledFlagStays() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val resources = context.getResources()
        val inflater = LayoutInflater.from(context)
        val rootView = inflater.inflate(R.layout.layout, null) as LinearLayout
        assertNull(rootView.findViewById<TextView>(R.id.text1))
        assertNotNull(rootView.findViewById<TextView>(R.id.text2))
        assertNull(rootView.findViewById<LinearLayout>(R.id.ll1))
        assertNull(rootView.findViewById<TextView>(R.id.text3))
        assertNotNull(rootView.findViewById<TextView>(R.id.ll2))
        assertNotNull(rootView.findViewById<TextView>(R.id.text4))
        assertNull(rootView.findViewById<TextView>(R.id.text5))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAYOUT_READWRITE_FLAGS)
    fun testLocaleConfig() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val localeConfig = LocaleConfig.fromContextIgnoringOverride(context)
        val list = localeConfig.supportedLocales
        assertEquals("en-US,ja,en-GB", list!!.toLanguageTags())
    }
}
