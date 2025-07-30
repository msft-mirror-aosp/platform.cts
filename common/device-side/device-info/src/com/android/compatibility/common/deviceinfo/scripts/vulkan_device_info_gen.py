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
    Generates VulkanDeviceInfoUtils.java and  VulkanDeviceInfo.java from vk.py
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




def gen_vulkan_device_info():
    """Generates vulkanDeviceInfo.java file.
    """
    genfile = os.path.join(os.path.dirname(__file__), "..", "VulkanDeviceInfo.java")

    with open(genfile, "w") as f:
        f.write(f'{util.get_copyright_warnings(2016)}\n')

        f.write("""\
package com.android.compatibility.common.deviceinfo;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;
import static com.android.compatibility.common.deviceinfo.VulkanDeviceInfoUtils.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Vulkan info collector.
 *
 * This collector gathers a VkJSONInstance representing the Vulkan capabilities of the Android
 * device, and translates it into a DeviceInfoStore. The goal is to be as faithful to the original
 * VkJSON as possible, so that the DeviceInfo can later be turned back into VkJSON without loss,
 * while still allow complex queries against the DeviceInfo database.
 *
 * There are a few places were translation isn't perfect:
 *
 * - Most JSON implementations handle JSON Numbers as doubles (64-bit floating point), which can't
 *   faithfully transfer 64-bit integers. So Vulkan uint64_t and VkDeviceSize values are encoded as
 *   Strings containing the hexadecimal representation of the value (with "0x" prefix).
 *
 * - Vulkan enum values are represented as Numbers. This is most convenient for processing, though
 *   isn't very human-readable. Pretty-printing these as strings is left for other tools.
 *
 * - For implementation convenience, VkJSON represents VkBool32 values as JSON Numbers (0/1). This
 *   collector converts them to JSON Boolean values (false/true).
 *
 * - DeviceInfoStore doesn't allow arrays of non-uniform or non-primitive types. VkJSON stores
 *   format capabilities as an array of formats, where each format is an array containing a number
 *   (the format enum value) and an object (the format properties). Since DeviceInfoStore doesn't
 *   allow array-of-array, we instead store formats as an array of uniform structs, So instead of
 *       [[3, {
 *           "linearTilingFeatures": 0,
 *           "optimalTilingFeatures": 5121,
 *           "bufferFeatures": 0
 *       }]]
 *   the format with enum value "3" will be represented as
 *       {
 *           "id": 3,
 *           "linear_tiling_features": 0,
 *           "optimal_tiling_features": 5121,
 *           "buffer_features": 0
 *       }
 *
 * - Device layers are deprecated, but instance layers can still add device extensions. VkJSON
 *   doesn't yet include device extensions provided by layers, though. So VulkanDeviceInfo omits
 *   device layers altogether. Eventually VkJSON and VulkanDeviceInfo should report device layers
 *   and their extensions the same way instance layers and their extensions are reported.
 *
 * - VkJSON uses the original Vulkan field names, while VulkanDeviceInfo follows the DeviceInfo
 *   naming convention. So VkJSON fields named like "sparseProperties" will be converted to names
 *   like "sparse_properties".
 */

public final class VulkanDeviceInfo extends DeviceInfo {

    static {
        System.loadLibrary("ctsdeviceinfo");
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        try {
            JSONObject instance = new JSONObject(nativeGetVkJSON());
            emitDeviceGroups(store, instance);
            emitLayers(store, instance);
            emitExtensions(store, instance);
            emitDevices(store, instance);

            // Access to Instance API version was only added alongside 1.2 support in instance
            if (instance.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                emitInstanceApiVersion(store, instance);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void emitDeviceGroups(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray deviceGroups = parent.getJSONArray(KEY_DEVICE_GROUPS);
        store.startArray(getConvertedName(KEY_DEVICE_GROUPS));
        for (int deviceGroupIdx = 0; deviceGroupIdx < deviceGroups.length(); deviceGroupIdx++) {
            JSONObject deviceGroup = deviceGroups.getJSONObject(deviceGroupIdx);
            store.startGroup();
            {
                emitLongArray(store, deviceGroup, KEY_DEVICES);
                emitBoolean(store, deviceGroup, KEY_SUBSET_ALLOCATION);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitDevices(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray devices = parent.getJSONArray(KEY_DEVICES);
        store.startArray(getConvertedName(KEY_DEVICES));
        for (int deviceIdx = 0; deviceIdx < devices.length(); deviceIdx++) {
            JSONObject device = devices.getJSONObject(deviceIdx);
            store.startGroup();
            {
                """)

        f.write(util.create_groups_with_indentation("VkPhysicalDeviceProperties","device", "Properties"))
        f.write("\n\n                ")
        f.write(util.create_groups_with_indentation("VkPhysicalDeviceFeatures","device", "Features"))

        f.write("""

                JSONObject memory = device.getJSONObject(KEY_MEMORY);
                store.startGroup(getConvertedName(KEY_MEMORY));
                {
                    emitLong(store, memory, KEY_MEMORY_TYPE_COUNT);
                    JSONArray memoryTypes = memory.getJSONArray(KEY_MEMORY_TYPES);
                    store.startArray(getConvertedName(KEY_MEMORY_TYPES));
                    for (int memoryTypeIdx = 0; memoryTypeIdx < memoryTypes.length();
                            memoryTypeIdx++) {
                        JSONObject memoryType = memoryTypes.getJSONObject(memoryTypeIdx);
                        store.startGroup();
                        {
                            emitLong(store, memoryType, KEY_PROPERTY_FLAGS);
                            emitLong(store, memoryType, KEY_HEAP_INDEX);
                        }
                        store.endGroup();
                    }
                    store.endArray();

                    emitLong(store, memory, KEY_MEMORY_HEAP_COUNT);
                    JSONArray memoryHeaps = memory.getJSONArray(KEY_MEMORY_HEAPS);
                    store.startArray(getConvertedName(KEY_MEMORY_HEAPS));
                    for (int memoryHeapIdx = 0; memoryHeapIdx < memoryHeaps.length();
                            memoryHeapIdx++) {
                        JSONObject memoryHeap = memoryHeaps.getJSONObject(memoryHeapIdx);
                        store.startGroup();
                        {
                            emitString(store, memoryHeap, KEY_SIZE);
                            emitLong(store, memoryHeap, KEY_FLAGS);
                        }
                        store.endGroup();
                    }
                    store.endArray();
                }
                store.endGroup();

                JSONArray queues = device.getJSONArray(KEY_QUEUES);
                store.startArray(getConvertedName(KEY_QUEUES));
                for (int queueIdx = 0; queueIdx < queues.length(); queueIdx++) {
                    JSONObject queue = queues.getJSONObject(queueIdx);
                    store.startGroup();
                    {
                        emitLong(store, queue, KEY_QUEUE_FLAGS);
                        emitLong(store, queue, KEY_QUEUE_COUNT);
                        emitLong(store, queue, KEY_TIMESTAMP_VALID_BITS);
                        JSONObject extent = queue.getJSONObject(KEY_MIN_IMAGE_TRANSFER_GRANULARITY);
                        store.startGroup(getConvertedName(KEY_MIN_IMAGE_TRANSFER_GRANULARITY));
                        {
                            emitLong(store, extent, KEY_WIDTH);
                            emitLong(store, extent, KEY_HEIGHT);
                            emitLong(store, extent, KEY_DEPTH);
                        }
                        store.endGroup();
                    }
                    store.endGroup();
                }
                store.endArray();

                // Skip layers for now. VkJSON doesn't yet include device layer extensions, so
                // this is entirely redundant with the instance extension information.
                // emitLayers(store, device);
                store.startArray(getConvertedName(KEY_LAYERS));
                store.endArray();

                emitExtensions(store, device);

                JSONArray formats = device.getJSONArray(KEY_FORMATS);
                // Note: Earlier code used field named 'formats' with different data structure.
                // In order to have the mix of old and new data, we cannot reuse that name.
                store.startArray(KEY_SUPPORTED_FORMATS);
                for (int formatIdx = 0; formatIdx < formats.length(); formatIdx++) {
                    JSONArray formatPair = formats.getJSONArray(formatIdx);
                    JSONObject formatProperties = formatPair.getJSONObject(1);
                    store.startGroup();
                    {
                        store.addResult(KEY_FORMAT, (long)formatPair.getInt(0));
                        emitLong(store, formatProperties, KEY_LINEAR_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_OPTIMAL_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_BUFFER_FEATURES);
                    }
                    store.endGroup();
                }
                store.endArray();

                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_1) {
\n""")
        f.write(util.generate_vk_dependent_groups("VK_VERSION_1_1"))

        f.write("""
                    JSONArray externalFences = device.getJSONArray(KEY_EXTERNAL_FENCE_PROPERTIES);
                    store.startArray(getConvertedName(KEY_EXTERNAL_FENCE_PROPERTIES));
                    for (int idx = 0; idx < externalFences.length(); ++idx) {
                        JSONArray externalFencePair = externalFences.getJSONArray(idx);
                        JSONObject externalFenceProperties = externalFencePair.getJSONObject(1);
                        store.startGroup();
                        {
                            store.addResult(KEY_HANDLE_TYPE, externalFencePair.getLong(0));
                            emitLong(store, externalFenceProperties, KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES);
                            emitLong(store, externalFenceProperties, KEY_COMPATIBLE_HANDLE_TYPES);
                            emitLong(store, externalFenceProperties, KEY_EXTERNAL_FENCE_FEATURES);
                        }
                        store.endGroup();
                    }
                    store.endArray();

                    JSONArray externalSemaphores = device.getJSONArray(KEY_EXTERNAL_SEMAPHORE_PROPERTIES);
                    store.startArray(getConvertedName(KEY_EXTERNAL_SEMAPHORE_PROPERTIES));
                    for (int idx = 0; idx < externalSemaphores.length(); ++idx) {
                        JSONArray externalSemaphorePair = externalSemaphores.getJSONArray(idx);
                        JSONObject externalSemaphoreProperties = externalSemaphorePair.getJSONObject(1);
                        store.startGroup();
                        {
                            store.addResult(KEY_HANDLE_TYPE, externalSemaphorePair.getLong(0));
                            emitLong(store, externalSemaphoreProperties, KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_COMPATIBLE_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_EXTERNAL_SEMAPHORE_FEATURES);
                        }
                        store.endGroup();
                    }
                    store.endArray();
                }
                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
\n""")
        f.write(util.generate_vk_dependent_groups("VK_VERSION_1_2"))

        f.write("""
                }

                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_3) {
\n""")
        f.write(util.generate_vk_dependent_groups("VK_VERSION_1_3"))

        f.write("""
                }
                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_4) {
\n""")
        f.write(util.generate_vk_dependent_groups("VK_VERSION_1_4"))

        f.write("""
                }
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitLayers(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray layers = parent.getJSONArray(KEY_LAYERS);
        store.startArray(getConvertedName(KEY_LAYERS));
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            store.startGroup();
            {
                JSONObject properties = layer.getJSONObject(KEY_PROPERTIES);
                store.startGroup(getConvertedName(KEY_PROPERTIES));
                {
                    emitString(store, properties, KEY_LAYER_NAME);
                    emitLong(store, properties, KEY_SPEC_VERSION);
                    emitLong(store, properties, KEY_IMPLEMENTATION_VERSION);
                    emitString(store, properties, KEY_DESCRIPTION);
                }
                store.endGroup();
                emitExtensions(store, layer);
            }
            store.endGroup();
        }
        store.endArray();
    }

    private static void emitInstanceApiVersion(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        store.addResult(getConvertedName(KEY_INSTANCE_API_VERSION), parent.getLong(KEY_API_VERSION));
    }

\n""")


        f.write(util.generate_emit_methods())

        f.write(util.generate_emit_extensions())

        f.write("""\

    private static void emitExtensions(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        JSONArray extensions = parent.getJSONArray(KEY_EXTENSIONS);
        store.startArray(getConvertedName(KEY_EXTENSIONS));
        for (int i = 0; i < extensions.length(); i++) {
            JSONObject extension = extensions.getJSONObject(i);
            store.startGroup();
            {
                emitString(store, extension, KEY_EXTENSION_NAME);
                emitLong(store, extension, KEY_SPEC_VERSION);
            }
            store.endGroup();
        }
        store.endArray();

        for (int i = 0; i < extensions.length(); i++) {
            JSONObject extension = extensions.getJSONObject(i);
            String key = extension.getString(KEY_EXTENSION_NAME);
            emitExtension(key, store, parent);
        }
    }

    private static void emitBoolean(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(getConvertedName(name), parent.getInt(name) != 0 ? true : false);
    }

    private static void emitLong(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(getConvertedName(name), parent.getLong(name));
    }

    private static void emitDouble(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(getConvertedName(name), parent.getDouble(name));
    }

    private static void emitString(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        store.addResult(getConvertedName(name), parent.getString(name));
    }

    private static void emitLongArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        long[] array = new long[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getLong(i);
        }
        store.addArrayResult(getConvertedName(name), array);
    }

    private static void emitDoubleArray(DeviceInfoStore store, JSONObject parent, String name)
            throws Exception {
        JSONArray jsonArray = parent.getJSONArray(name);
        double[] array = new double[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            array[i] = jsonArray.getDouble(i);
        }
        store.addArrayResult(getConvertedName(name), array);
    }

    private static native String nativeGetVkJSON();

}
\n""")
        f.close()
