/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.app.notification.current.cts

import android.app.Flags
import android.app.Notification
import android.app.NotificationRule
import android.app.NotificationRule.Condition
import android.app.NotificationRule.Condition.CONDITION_TYPE_LOCATION
import android.app.NotificationRule.Condition.CONDITION_TYPE_TIME
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Parcel
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.notification.Adjustment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.List
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
class NotificationRuleTest {
    private var mContext: Context? = null

    val ruleId: Int = 101
    val ruleName: String = "rules!"
    val primaryAction: Int = NotificationRule.Action.PRIMARY_ACTION_BUNDLE
    val editAction: String = "editAction"
    val bundleName: String = "bundleName"
    val emojiIcon: String = "\uD83D\uDE42"
    val lightColor: Int = Color.RED
    val modes = listOf("manual")
    val soundHaptics: Uri = Uri.EMPTY
    val categories = listOf(Notification.CATEGORY_MESSAGE, Notification.CATEGORY_ALARM)
    val contactLevel: Int = NotificationRule.Filter.CONTACT_LEVEL_CONTACT
    val conversationLevel: Int = NotificationRule.Filter.CONVERSATION_LEVEL_PRIORITY
    val contacts = listOf(Uri.EMPTY)
    val excludedPackages = listOf(1234)
    val includedPackages = listOf(5678)
    val flagMask: Int = Notification.FLAG_PROMOTED_ONGOING
    val keywords = listOf("keyword")
    val shortcutIds = listOf("abc")
    val staticBundleTypes = listOf(Adjustment.TYPE_NEWS, Adjustment.TYPE_PROMOTION)
    val userHandles = listOf(UserHandle.SYSTEM)
    val days = listOf(0,1)
    val startHour = 1
    val startMinute = 30
    val endHour = 3
    val endMinute = 45
    val lat = 5.0
    val long = 20.0
    val radius = 10f


    @JvmField
    @Rule
    public val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
    }

    private fun createFullRule(): NotificationRule {
        return NotificationRule.Builder(ruleId, ruleName)
            .setAction(
                NotificationRule.Action.Builder(primaryAction)
                    .setDynamicBundleName(bundleName)
                    .setDynamicBundleEmojiIcon(emojiIcon)
                    .setLightColorOverride(lightColor)
                    .setModeBreakthroughIds(modes)
                    .setSoundHapticOverride(soundHaptics)
                    .build()
            )
            .setConditions(
                listOf(
                    Condition.createTimeCondition(days, startHour, startMinute, endHour, endMinute),
                    Condition.createLocationCondition(lat, long, radius),
                )
            )
            .setFilters(
                listOf(
                    NotificationRule.Filter.Builder()
                        .setCategories(categories)
                        .setContactLevel(contactLevel)
                        .setConversationLevel(conversationLevel)
                        .setContacts(contacts)
                        .setExcludedPackageUids(excludedPackages)
                        .setFlags(flagMask)
                        .setIncludedPackageUids(includedPackages)
                        .setKeywords(keywords)
                        .setShortcutIds(shortcutIds)
                        .setStaticBundleTypes(staticBundleTypes)
                        .setUsers(userHandles)
                        .build(),
                    NotificationRule.Filter.Builder().build(),
                )
            )
            .setCanBeDisabled(false)
            .setEditIntentAction(editAction)
            .setEnabled(false)
            .build()
    }

    @Test
    fun testBuilderConstructor() {
        val rule = createFullRule()

        assertThat(rule.id).isEqualTo(ruleId)
        assertThat(rule.name).isEqualTo(ruleName)
        assertThat(rule.editIntentAction).isEqualTo(editAction)
        assertThat(rule.isEnabled).isFalse()
        assertThat(rule.canBeDisabled()).isFalse()

        assertThat(rule.action.primaryAction).isEqualTo(primaryAction)
        assertThat(rule.action.dynamicBundleName).isEqualTo(bundleName)
        assertThat(rule.action.dynamicBundleEmojiIcon).isEqualTo(emojiIcon)
        assertThat(rule.action.lightColorOverride).isEqualTo(lightColor)
        assertThat(rule.action.modeBreakthroughIds).isEqualTo(modes)
        assertThat(rule.action.soundHapticOverride).isEqualTo(soundHaptics)

        assertThat(rule.filters).hasSize(2)
        val actualFilter = rule.filters.get(0)
        assertThat(actualFilter.categories).isEqualTo(categories)
        assertThat(actualFilter.contactLevel).isEqualTo(contactLevel)
        assertThat(actualFilter.contacts).isEqualTo(contacts)
        assertThat(actualFilter.conversationLevel).isEqualTo(conversationLevel)
        assertThat(actualFilter.excludedPackageUids).isEqualTo(excludedPackages)
        assertThat(actualFilter.flags).isEqualTo(flagMask)
        assertThat(actualFilter.includedPackageUids).isEqualTo(includedPackages)
        assertThat(actualFilter.keywords).isEqualTo(keywords)
        assertThat(actualFilter.shortcutIds).isEqualTo(shortcutIds)
        assertThat(actualFilter.staticBundleTypes).isEqualTo(staticBundleTypes)
        assertThat(actualFilter.users).isEqualTo(userHandles)

        val timeCondition = rule.conditions.get(0)
        assertThat(timeCondition.conditionType).isEqualTo(CONDITION_TYPE_TIME)
        assertThat(timeCondition.days).isEqualTo(days)
        assertThat(timeCondition.startHour).isEqualTo(startHour)
        assertThat(timeCondition.startMinute).isEqualTo(startMinute)
        assertThat(timeCondition.endHour).isEqualTo(endHour)
        assertThat(timeCondition.endMinute).isEqualTo(endMinute)

        val locationCondition = rule.conditions.get(1)
        assertThat(locationCondition.conditionType).isEqualTo(CONDITION_TYPE_LOCATION)
        assertThat(locationCondition.latitude).isEqualTo(lat)
        assertThat(locationCondition.longitude).isEqualTo(long)
        assertThat(locationCondition.radiusMeters).isEqualTo(radius)
    }

    @Test
    fun testBuilderConstructor_minimal() {
        val rule = NotificationRule.Builder(ruleId, ruleName)
            .build()
        assertThat(rule.getId()).isEqualTo(ruleId)
        assertThat(rule.getName()).isEqualTo(ruleName)
        assertThat(rule.getEditIntentAction()).isNull()
        assertThat(rule.isEnabled()).isTrue()
        assertThat(rule.canBeDisabled()).isTrue()
    }

    @Test
    fun testDescribeContents() {
        val expected = 0
        val rule = NotificationRule.Builder(ruleId, ruleName)
            .build()
        assertThat(expected).isEqualTo(rule.describeContents())
    }

    @Test
    fun testWriteToParcel() {
        val rule = createFullRule()

        val parcel = Parcel.obtain()
        rule.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val actual = NotificationRule.CREATOR.createFromParcel(parcel)

        assertThat(actual).isEqualTo(rule)
    }
}

