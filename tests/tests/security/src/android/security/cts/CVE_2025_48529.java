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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48529 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 325030433)
    @Test
    public void testPocCVE_2025_48529() {
        try {
            // Verify whether DUT supports multiple users.
            final Context context = getApplicationContext();
            assume().withMessage("DUT does not support multiple users")
                    .that(context.getSystemService(UserManager.class).supportsMultipleUsers())
                    .isTrue();

            // Dynamically load class loader for frameworks.jar to load
            // 'NotificationChannelController' and 'VoicemailNotificationSettingsUtil' classes.
            final PathClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/framework.jar", ClassLoader.getSystemClassLoader());
            assume().withMessage("Failed to load frameworks.jar").that(classLoader).isNotNull();
            final Class notificationChannelControllerClass =
                    classLoader.loadClass(
                            "com.android.internal.telephony.util.NotificationChannelController");
            assume().withMessage("Failed to load NotificationChannelController class")
                    .that(notificationChannelControllerClass)
                    .isNotNull();
            final Class voicemailNotificationSettingsUtilClass =
                    classLoader.loadClass(
                            "com.android.internal.telephony.util.VoicemailNotificationSettingsUtil");
            assume().withMessage("Failed to load VoicemailNotificationSettingsUtil class")
                    .that(voicemailNotificationSettingsUtilClass)
                    .isNotNull();

            // Fetch default sharedpreferences using 'PreferenceManager' and insert an URI with
            // key 'voicemail_notification_ringtone_<defaultSubscriptionId>'.
            final SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            final SharedPreferences.Editor editor = sharedPreferences.edit();
            final Method getVoicemailRingtoneSharedPrefsKeyMethod =
                    getDeclaredMethod(
                            voicemailNotificationSettingsUtilClass,
                            "getVoicemailRingtoneSharedPrefsKey");
            final String key;
            if (getVoicemailRingtoneSharedPrefsKeyMethod != null) {
                key = (String) getVoicemailRingtoneSharedPrefsKeyMethod.invoke(null);
            } else {
                final Field voiceMailNotificationRingtoneSharedPrefsKeyPrefix =
                        getDeclaredField(
                                voicemailNotificationSettingsUtilClass,
                                "VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY_PREFIX");
                final String keyPrefix;
                if (voiceMailNotificationRingtoneSharedPrefsKeyPrefix != null) {
                    keyPrefix =
                            (String) voiceMailNotificationRingtoneSharedPrefsKeyPrefix.get(null);
                } else {
                    keyPrefix = "voicemail_notification_ringtone_";
                }
                key = keyPrefix + SubscriptionManager.getDefaultSubscriptionId();
            }
            final String soundUriString =
                    String.format("android.resource:/%s/R.raw.good", context.getPackageName());
            editor.putString(key, soundUriString);
            editor.commit();

            // Load and invoke 'migrateVoicemailNotificationSettings()' to reproduce the
            // vulnerability.
            final Method migrateVoicemailNotificationSettingsMethod =
                    getDeclaredMethod(
                            notificationChannelControllerClass,
                            "migrateVoicemailNotificationSettings");
            assume().withMessage("Failed to find migrateVoicemailNotificationSettings()")
                    .that(migrateVoicemailNotificationSettingsMethod)
                    .isNotNull();
            migrateVoicemailNotificationSettingsMethod.invoke(null, context);

            // Load 'VoicemailNotificationSettingsUtil' class and fetch
            // 'OLD_VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY' value.
            final Field channelIdVoiceMailField =
                    getDeclaredField(notificationChannelControllerClass, "CHANNEL_ID_VOICE_MAIL");
            assume().withMessage("Failed to find CHANNEL_ID_VOICE_MAIL field")
                    .that(channelIdVoiceMailField)
                    .isNotNull();
            final NotificationChannel notificationChannel =
                    context.getSystemService(NotificationManager.class)
                            .getNotificationChannel((String) channelIdVoiceMailField.get(null));
            final Uri sound = notificationChannel.getSound();

            // Without fix, notification channel's sound gets set with 'soundUriString'.
            assertWithMessage(
                            "Device is vulnerable to b/325030433!! A malicious app can gain read"
                                    + " access to cross-user content providers")
                    .that(sound.toString())
                    .isNotEqualTo(soundUriString);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Field getDeclaredField(Class cls, String fieldName) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.getName().endsWith(fieldName)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private Method getDeclaredMethod(Class cls, String methodName) {
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getName().endsWith(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
