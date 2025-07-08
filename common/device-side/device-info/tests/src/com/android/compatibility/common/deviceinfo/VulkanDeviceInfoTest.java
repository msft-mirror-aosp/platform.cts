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
package com.android.compatibility.common.deviceinfo;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Locale;

public class VulkanDeviceInfoTest {

    /**
     * Compares two strings ignoring underscores.
     *
     * name = "key_vendor_id" and value = "vendorid" -> true
     * name = "key_vendor_id" and value = "vendoridd" -> false
     * name = "key_vulkan_12_features" and value = "vulkan_12_features" -> true
     * name = "key_vulkan_12_features" and value = "vulkan_12f_eatures" -> false
     */
    private static boolean compareWithoutUnderscores(String name, String value) {
        int nameIndex = 4, valueIndex = 0; //skipping "KEY_" in name;
        for (; valueIndex < value.length() && nameIndex < name.length(); valueIndex++, nameIndex++) {
            if (name.charAt(nameIndex) == '_' && value.charAt(valueIndex) != '_') {
                nameIndex ++;
            }
            if (nameIndex < name.length()
                && valueIndex < value.length()
                && name.charAt(nameIndex) != value.charAt(valueIndex)) {
                    return false;
            }
        }
        return (valueIndex >= value.length() && nameIndex >= name.length());
    }

    @Test
    public void testMemberVariableNamesMatchValues()
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException,
        NoSuchMethodException, InvocationTargetException {

        // Getting hold of the vulkanDeviceInfoUtils class
        Class vulkanDeviceInfoUtilsClass = Class.forName("com.android.compatibility.common.deviceinfo.VulkanDeviceInfoUtils");

        // Store the member variables of VulkanDeviceInfo class
        Field[] fields = vulkanDeviceInfoUtilsClass.getDeclaredFields();
        assertTrue(fields.length > 0);

        // Special cases for converted names
        // KEY_VARIABLE_POINTERS_FEATURES has been intentionally given a different value
        Set<String> specialCasesForConvertedNames = new HashSet<>(Arrays.asList(
            "KEY_VARIABLE_POINTERS_FEATURES"
            ));

        Method getConvertedNameMethod = vulkanDeviceInfoUtilsClass.getDeclaredMethod("getConvertedName", String.class);
        getConvertedNameMethod.setAccessible(true);

        for (Field field : fields) {
            String fieldName = field.getName();

            if (fieldName.startsWith("KEY_")) {
                field.setAccessible(true);
                String value = (String) field.get(null);

                assertTrue(compareWithoutUnderscores(fieldName.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT)));

                if (!specialCasesForConvertedNames.contains(fieldName)) {
                    String convertedName = (String) getConvertedNameMethod.invoke(null, value);

                    // Converted name should not start with a digit as protos cannot start with digit
                    assertFalse(Character.isDigit(convertedName.charAt(0)));

                    assertTrue(compareWithoutUnderscores(fieldName.toLowerCase(Locale.ROOT), convertedName));
                }
            }
        }
    }
}
