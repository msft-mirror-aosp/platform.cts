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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26449 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 387498139)
    @Test
    public void testPocCVE_2025_26449() {
        Parcel parcel = null;
        try {
            // Load the vulnerable method 'ZenModeConfig'.
            final ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
            final Class ZenModeConfigClass =
                    systemLoader.loadClass("android.service.notification.ZenModeConfig");
            final Constructor ZenModeConfigConstructor =
                    ZenModeConfigClass.getDeclaredConstructor(Parcel.class);
            ZenModeConfigConstructor.setAccessible(true);

            // Call the vulnerable method 'ZenModeConfig' with malformed 'parcel'.
            parcel = parcel.obtain();
            final Object ZenModeConfigInstance =
                    ZenModeConfigConstructor.newInstance(
                            createMalformedParcel(systemLoader, parcel));

            // Set the 'automaticRules' field accessible.
            final Field autoRulesField = ZenModeConfigClass.getDeclaredField("automaticRules");
            autoRulesField.setAccessible(true);

            // Fetch 'automaticRules' of 'ZenModeConfigInstance'.
            final ArrayMap<String, Object> automaticRules =
                    (ArrayMap<String, Object>) autoRulesField.get(ZenModeConfigInstance);

            // Without the fix, 'ZenRule' present in malformed parcel is inserted into
            // 'automaticRules' field.
            assertWithMessage(
                            "Device is vulnerable to b/387498139 !! 'automaticRules' field of"
                                    + " 'ZenModeConfig' instance contains Zenrule from malformed"
                                    + " parcel.")
                    .that(automaticRules.containsKey("cve_2025_26449_zen_rule"))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                parcel.recycle();
            } catch (Exception unexpected) {
                Log.d("cve_2025_26449", "Unexpected error : " + unexpected.getMessage());
            }
        }
    }

    private Parcel createMalformedParcel(ClassLoader systemLoader, Parcel parcel) throws Exception {
        // Load 'ZenRule' construtor.
        final Constructor ZenRuleConstructor =
                systemLoader
                        .loadClass("android.service.notification.ZenModeConfig$ZenRule")
                        .getDeclaredConstructor();
        ZenRuleConstructor.setAccessible(true);

        // Create the malformed parcel.
        parcel.writeInt(0 /* allowCalls */);
        parcel.writeInt(0 /* allowRepeatCallers */);
        parcel.writeInt(0 /* allowMessages */);
        parcel.writeInt(0 /* allowReminders */);
        parcel.writeInt(0 /* allowEvents */);
        parcel.writeInt(0 /* allowCallsFrom */);
        parcel.writeInt(0 /* allowMessagesFrom */);
        parcel.writeInt(0 /* user */);
        parcel.writeParcelable((Parcelable) ZenRuleConstructor.newInstance(), 0);
        parcel.writeInt(1 /* num of Rules */);
        parcel.writeStringArray(new String[] {"cve_2025_26449_zen_rule"} /* id's */);

        // Without fix, 'readTypedArray' is called on malicious parcel, and 'automaticRules' is set
        // with the 'ZenRule' present in malformed parcel.
        // With fix, 'parceledRules' becomes 'null', and so 'rules.size()' is zero. Non-zero check
        // on 'rules.size()' is present, which prevent 'automaticRules' from being set with the
        // 'ZenRule' present in malformed parcel.
        parcel.writeTypedArray(
                new Parcelable[] {
                    (Parcelable) ZenRuleConstructor.newInstance()
                } /* ZenRule array */,
                0);
        parcel.setDataPosition(0);
        return parcel;
    }
}
