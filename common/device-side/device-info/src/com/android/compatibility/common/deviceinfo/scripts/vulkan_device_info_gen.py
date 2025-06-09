#!/usr/bin/env python3
#
# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
    Generates the VulkanDeviceInfoUtils.java file from vk.py
"""

import dataclasses
import os
import vulkan_device_info_gen_util as util
import sys

dataclass_field = dataclasses.field

def gen_vulkan_device_info_utils():
    """Generates vulkanDeviceInfoUtils.java file.
    """
    genfile = os.path.join(os.path.dirname(__file__), "..", "VulkanDeviceInfoUtils.java")

    with open(genfile, "w") as f:
        f.write(f'{util.get_copyright_warnings(2025)}\n')

        f.write("""\

package com.android.compatibility.common.deviceinfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 * This class converts vkjson names to DeviceInfo names
 *
 * VkJSON uses the original Vulkan field names, while VulkanDeviceInfo follows the DeviceInfo
 * naming convention. So VkJSON fields named like "sparseProperties" will be converted to names
 * like "sparse_properties".
 *
 */

public final class VulkanDeviceInfoUtils {
\n""")

        f.write(util.generate_constants())

        f.write("""\

    private static Map<String, String> keyToConvertedName;

    static {
        createKeyToConvertedNameMap();
    }

    // Creates a map of vkjson names and VulkanDeviceInfo names
    private static void createKeyToConvertedNameMap() {
        keyToConvertedName = new HashMap<>();

        // Loop over all the constants of VulkanDeviceInfo
        for (Field field : VulkanDeviceInfoUtils.class.getDeclaredFields()) {
            if (field.getName().startsWith("KEY_")) {
                try {
                    String fieldName = field.getName();
                    String value = (String) field.get(null);
                    String convertedName = fieldName.replaceFirst("KEY_", "").toLowerCase(Locale.ROOT);
                    keyToConvertedName.put(value, convertedName);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static String getConvertedName(String name) {
        if (keyToConvertedName.containsKey(name)) {
            return keyToConvertedName.get(name);
        }
        else {
            throw new RuntimeException("unknown key name: " + name);
        }
    }
}
\n""")
        f.close()
