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

import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26436 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 322159724)
    public void testPocCVE_2025_26436() {
        try {
            // Create class loader for 'services.jar'.
            final PathClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/services.jar", ClassLoader.getSystemClassLoader());

            // Create an 'arrayMay' with key and value as IBinder and TempAllowListDuration
            // respectively. Further, object of TempAllowListDuration class is created with its
            // 'type' field set to 'TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED'. IBinder
            // (key) is created with 'tempAllowListDuration' as its value and is inserted in the
            // 'arrayMap'.
            final int temporaryAllowlistTypeForegroundServiceAllowed = 0;
            final Constructor tempAllowListDurationConstructor =
                    classLoader
                            .loadClass(
                                    "com.android.server.am.PendingIntentRecord"
                                            + "$TempAllowListDuration")
                            .getDeclaredConstructor(long.class, int.class, int.class, String.class);
            tempAllowListDurationConstructor.setAccessible(true);
            final IBinder binder = new Binder();
            final ArrayMap<Object /* IBinder */, Object /* TempAllowListDuration */> arrayMap =
                    new ArrayMap<>();
            arrayMap.put(
                    binder,
                    tempAllowListDurationConstructor.newInstance(
                            0 /* duration */,
                            temporaryAllowlistTypeForegroundServiceAllowed /* type */,
                            0 /* reasonCode */,
                            "cve_2025_26436_reason" /* reason */));

            // Load 'PendingIntentRecord' class and create its object.
            final Class pendingIntentRecord =
                    classLoader.loadClass("com.android.server.am.PendingIntentRecord");
            final Constructor pendingIntentRecordConstructor =
                    pendingIntentRecord.getDeclaredConstructor(
                            classLoader.loadClass("com.android.server.am.PendingIntentController"),
                            classLoader.loadClass("com.android.server.am.PendingIntentRecord$Key"),
                            int.class);
            final Object pendingIntentRecordObject =
                    pendingIntentRecordConstructor.newInstance(
                            null /* PendingIntentController */, null /* Key */, 0 /* uid */);

            // Fetch 'mAllowlistDuration' field of 'pendingIntentRecordObject' and initialize it
            // with an 'arrayMap'.
            final Field allowlistDurationField =
                    pendingIntentRecord.getDeclaredField("mAllowlistDuration");
            allowlistDurationField.setAccessible(true);
            allowlistDurationField.set(pendingIntentRecordObject, arrayMap);

            // Fetch 'mAllowBgActivityStartsForServiceSender' field of 'pendingIntentRecordObject'
            // and insert 'binder' in it.
            ArraySet<IBinder> arraySet = new ArraySet<>();
            arraySet.add(binder);
            final Field allowBgActivityStartsForServiceSenderField =
                    pendingIntentRecord.getDeclaredField("mAllowBgActivityStartsForServiceSender");
            allowBgActivityStartsForServiceSenderField.setAccessible(true);
            allowBgActivityStartsForServiceSenderField.set(pendingIntentRecordObject, arraySet);

            // Call the vulnerable function 'clearAllowBgActivityStarts'.
            final Method clearAllowBgActivityStartsMethod =
                    pendingIntentRecord.getDeclaredMethod(
                            "clearAllowBgActivityStarts", android.os.IBinder.class);
            clearAllowBgActivityStartsMethod.setAccessible(true);
            clearAllowBgActivityStartsMethod.invoke(pendingIntentRecordObject, binder);

            // Check if 'binder' is removed from 'mAllowBgActivityStartsForServiceSender'. 'binder'
            // will be removed in both cases (with and without fix).
            assume().withMessage(
                            "binder not removed from 'mAllowBgActivityStartsForServiceSender' !!")
                    .that(binder)
                    .isNotIn(arraySet);

            // Fetch 'type' from 'mAllowlistDuration' after vulnerable function is called.
            final Object currentAllowlistDuration = arrayMap.get(binder);
            final Field typeField = currentAllowlistDuration.getClass().getDeclaredField("type");
            typeField.setAccessible(true);

            // Without the fix, 'type' field of 'tempAllowListDuration' remains unchanged and its
            // value is TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED. With fix, the value
            // of 'type' is changed to TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED
            assertWithMessage(
                            "Device is vulnerable to b/322159724, 'duration.type' is not changed"
                                + " to TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED to"
                                + " disallow launch of arbitrary activity !!")
                    .that(typeField.getInt(currentAllowlistDuration))
                    .isNotEqualTo(temporaryAllowlistTypeForegroundServiceAllowed);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
