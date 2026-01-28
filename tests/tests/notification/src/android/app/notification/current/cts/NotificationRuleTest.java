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

package android.app.notification.current.cts;

import static android.app.Notification.CATEGORY_ALARM;
import static android.app.Notification.CATEGORY_MESSAGE;
import static android.app.Notification.FLAG_PROMOTED_ONGOING;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.app.NotificationRule;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Parcel;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.Adjustment;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
public class NotificationRuleTest {

    private Context mContext;

    int ruleId = 101;
    String ruleName = "rules!";
    int primaryAction = NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
    String editAction = "editAction";
    String bundleName = "bundleName";
    String emojiIcon = "\uD83D\uDE42";
    int lightColor = Color.RED;
    List<String> modes = List.of("manual");
    Uri soundHaptics = Uri.EMPTY;
    List<String> categories = List.of(CATEGORY_MESSAGE, CATEGORY_ALARM);
    int contactLevel = NotificationRule.Filter.CONTACT_LEVEL_CONTACT;
    int conversationLevel = NotificationRule.Filter.CONVERSATION_LEVEL_PRIORITY;
    List<Uri> contacts = List.of(Uri.EMPTY);
    List<Integer> excludedPackages = List.of(1234);
    List<Integer> includedPackages = List.of(5678);
    int flagMask = FLAG_PROMOTED_ONGOING;
    List<String> keywords = List.of("keyword");
    List<String> shortcutIds = List.of("abc");
    List<Integer> staticBundleTypes = List.of(Adjustment.TYPE_NEWS, Adjustment.TYPE_PROMOTION);
    List<UserHandle> userHandles = List.of(UserHandle.SYSTEM);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private NotificationRule createFullRule() {
        return new NotificationRule.Builder(ruleId, ruleName)
                .setAction(new NotificationRule.Action.Builder(primaryAction)
                        .setDynamicBundleName(bundleName)
                        .setDynamicBundleEmojiIcon(emojiIcon)
                        .setLightColorOverride(lightColor)
                        .setModeBreakthroughIds(modes)
                        .setSoundHapticOverride(soundHaptics)
                        .build())
                .setFilters(List.of(new NotificationRule.Filter.Builder()
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
                        .build()))
                .setCanBeDisabled(false)
                .setEditIntentAction(editAction)
                .setEnabled(false)
                .build();
    }

    @Test
    public void testBuilderConstructor() {
        NotificationRule rule = createFullRule();

        assertThat(rule.getId()).isEqualTo(ruleId);
        assertThat(rule.getName()).isEqualTo(ruleName);
        assertThat(rule.getEditIntentAction()).isEqualTo(editAction);
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.canBeDisabled()).isFalse();

        assertThat(rule.getAction().getPrimaryAction()).isEqualTo(primaryAction);
        assertThat(rule.getAction().getDynamicBundleName()).isEqualTo(bundleName);
        assertThat(rule.getAction().getDynamicBundleEmojiIcon()).isEqualTo(emojiIcon);
        assertThat(rule.getAction().getLightColorOverride()).isEqualTo(lightColor);
        assertThat(rule.getAction().getModeBreakthroughIds()).isEqualTo(modes);
        assertThat(rule.getAction().getSoundHapticOverride()).isEqualTo(soundHaptics);

        assertThat(rule.getFilters()).hasSize(1);
        NotificationRule.Filter actualFilter = rule.getFilters().get(0);
        assertThat(actualFilter.getCategories()).isEqualTo(categories);
        assertThat(actualFilter.getContactLevel()).isEqualTo(contactLevel);
        assertThat(actualFilter.getContacts()).isEqualTo(contacts);
        assertThat(actualFilter.getConversationLevel()).isEqualTo(conversationLevel);
        assertThat(actualFilter.getExcludedPackageUids()).isEqualTo(excludedPackages);
        assertThat(actualFilter.getFlags()).isEqualTo(flagMask);
        assertThat(actualFilter.getIncludedPackageUids()).isEqualTo(includedPackages);
        assertThat(actualFilter.getKeywords()).isEqualTo(keywords);
        assertThat(actualFilter.getShortcutIds()).isEqualTo(shortcutIds);
        assertThat(actualFilter.getStaticBundleTypes()).isEqualTo(staticBundleTypes);
        assertThat(actualFilter.getUsers()).isEqualTo(userHandles);
    }

    @Test
    public void testBuilderConstructor_minimal() {
        NotificationRule rule = new NotificationRule.Builder(ruleId, ruleName)
                .build();
        assertThat(rule.getId()).isEqualTo(ruleId);
        assertThat(rule.getName()).isEqualTo(ruleName);
        assertThat(rule.getEditIntentAction()).isNull();
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.canBeDisabled()).isTrue();
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        NotificationRule rule = new NotificationRule.Builder(ruleId, ruleName)
                .build();
        assertThat(expected).isEqualTo(rule.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        NotificationRule rule = createFullRule();

        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NotificationRule actual = NotificationRule.CREATOR.createFromParcel(parcel);

        assertThat(actual).isEqualTo(rule);
    }
}

