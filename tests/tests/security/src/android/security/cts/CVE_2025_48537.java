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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.net.Uri;
import android.os.Looper;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.ZenRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48537 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 391894257)
    public void testPocCVE_2025_48537() {
        try {
            final Context context = getApplicationContext();
            final String packageName = context.getPackageName();
            final ComponentName componentName =
                    new ComponentName(
                            "android", "com.android.server.notification.ScheduleConditionProvider");
            final Uri conditionProviderId = Uri.fromParts("condition", packageName, null);

            // Create 'ZenRule' object which is passed as an argument in constructor of
            // 'ZenModeConfig'. 'ZenRule' is created with system's 'ConditionProvider' and with
            // current package name.
            // Without fix, if non system package is linked with system's 'ConditionProvider', it
            // is not inserted in 'mConditionProviders' when vulnerable function 'evaluateConfig'
            // is called.
            final ZenRule zenRule = new ZenRule();
            zenRule.id = "cve_2025_48537_id";
            zenRule.pkg = packageName;
            zenRule.component = componentName;
            zenRule.conditionId = conditionProviderId;

            // Create 'ZenModeConfig' object with 'ZenRule' which is passed as an argument in
            // vulnerable function 'evaluateConfig'.
            final ZenModeConfig zenModeConfig = new ZenModeConfig();
            zenModeConfig.automaticRules.put(zenRule.id, zenRule);

            // Fetch class loader for 'services.jar'.
            final ClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/services.jar", ClassLoader.getSystemClassLoader());

            // Load the desired classes.
            final Class conditionProvidersClass =
                    classLoader.loadClass("com.android.server.notification.ConditionProviders");
            final Class zenModeConditionsClass =
                    classLoader.loadClass("com.android.server.notification.ZenModeConditions");

            // Create an instance of 'ConditionProviders' which is passed as an argument in
            // 'zenModeConditions'.
            final Constructor conditionProvidersConstructor =
                    conditionProvidersClass.getDeclaredConstructor(
                            Context.class,
                            classLoader.loadClass(
                                    "com.android.server.notification.ManagedServices$UserProfiles"),
                            IPackageManager.class);
            conditionProvidersConstructor.setAccessible(true);
            final Object conditionProvidersInstance =
                    conditionProvidersConstructor.newInstance(context, null, null);

            // Create looper to set 'mLooper'.
            Looper.prepare();

            // Create instance of 'zenModeConditions'
            final Constructor zenModeConditionsConstructor =
                    zenModeConditionsClass.getConstructor(
                            classLoader.loadClass("com.android.server.notification.ZenModeHelper"),
                            conditionProvidersClass);
            zenModeConditionsConstructor.setAccessible(true);
            final Object zenModeConditionsInstance =
                    zenModeConditionsConstructor.newInstance(null, conditionProvidersInstance);

            // Check if 'zenModeConditions' instance is not null.
            assume().withMessage("Failed to create zenModeConditions instance !!")
                    .that(zenModeConditionsInstance)
                    .isNotNull();

            // Invoke the vulnerable function 'evaluateConfig' with 'ZenModeConfig'.
            final Method evaluateConfigMethod =
                    zenModeConditionsClass.getDeclaredMethod(
                            "evaluateConfig",
                            ZenModeConfig.class,
                            ComponentName.class,
                            boolean.class);
            evaluateConfigMethod.setAccessible(true);
            evaluateConfigMethod.invoke(zenModeConditionsInstance, zenModeConfig, null, true);

            // Fetch 'mConditionProviders' field of 'zenModeConditionsInstance'
            final Object fieldValue =
                    getFieldValue(zenModeConditionsInstance, "mConditionProviders", Object.class);

            // Call 'getRecordLocked' with 'conditionProviderId' and 'componentName' to get
            // 'ConditionRecord'.
            final Method getRecordMethod =
                    conditionProvidersClass.getDeclaredMethod(
                            "getRecordLocked", Uri.class, ComponentName.class, boolean.class);
            getRecordMethod.setAccessible(true);
            final Object result =
                    getRecordMethod.invoke(fieldValue, conditionProviderId, componentName, false);

            // With fix, due to checks on 'conditionProvider', entry is not
            // added in 'mConditionProviders' and 'result' is nill.
            if (result != null) {
                // Access 'component' field
                final ComponentName componentNameEntry =
                        getFieldValue(result, "component", ComponentName.class);

                // Access 'id' field
                final Uri insertedId = getFieldValue(result, "id", Uri.class);

                // Check if 'ConditionRecord' exists for system's 'ConditionProvider' and 'ZenRule'
                // id. Without fix, system's 'conditionProvider' is inserted in
                // 'mConditionProviders' from 'ZenRule'.
                assertWithMessage(
                                "Device is vulnerable to b/391894257 !!, Rule which belongs to"
                                        + " test package and has component as system's"
                                        + " ConditionProvider is inserted")
                        .that(
                                componentName.equals(componentNameEntry)
                                        && conditionProviderId.equals(insertedId))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private <T> T getFieldValue(Object targetObject, String fieldName, Class<T> fieldType)
            throws Exception {
        // Fetch field from class.
        final Field field = targetObject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        // Fetch value of field from object and check it is not null.
        final Object value = field.get(targetObject);
        assume().withMessage("Failed find field instance" + fieldName)
                .that(value != null && fieldType.isAssignableFrom(field.getType()))
                .isTrue();

        return (T) value;
    }
}
