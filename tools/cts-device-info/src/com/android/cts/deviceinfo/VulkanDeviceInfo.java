/*
 * Copyright 2016 The Android Open Source Project
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

package com.android.cts.deviceinfo;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;
import static com.android.cts.deviceinfo.VulkanDeviceInfoUtils.*;

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
 * We inherit some design decisions from VkJSON, and there are a few places were translation isn't
 * perfect:
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

    private static HashMap<String, Integer> extensionNameToEnum;

    static {
        System.loadLibrary("ctsdeviceinfo");
        extensionNameToEnum = new HashMap<>();
        extensionNameToEnum.put(KEY_VK_KHR_DRIVER_PROPERTIES, ENUM_VK_KHR_DRIVER_PROPERTIES);
        extensionNameToEnum.put(KEY_VK_KHR_VARIABLE_POINTERS, ENUM_VK_KHR_VARIABLE_POINTERS);
        extensionNameToEnum.put(KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D, ENUM_KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D);
        extensionNameToEnum.put(KEY_VK_EXT_CUSTOM_BORDER_COLOR, ENUM_KEY_VK_EXT_CUSTOM_BORDER_COLOR);
        extensionNameToEnum.put(KEY_VK_EXT_PRIMITIVE_TOPOLOGY_LIST_RESTART,
ENUM_KEY_VK_EXT_PRIMITIVE_TOPOLOGY_LIST_RESTART);
        extensionNameToEnum.put(KEY_VK_EXT_PROVOKING_VERTEX, ENUM_KEY_VK_EXT_PROVOKING_VERTEX);
        extensionNameToEnum.put(KEY_VK_EXT_TRANSFORM_FEEDBACK, ENUM_KEY_VK_EXT_TRANSFORM_FEEDBACK);
        extensionNameToEnum.put(
                KEY_VK_KHR_SHADER_FLOAT16_INT8, ENUM_KEY_VK_KHR_SHADER_FLOAT16_INT8);
        extensionNameToEnum.put(
                KEY_VK_KHR_SHADER_SUBGROUP_EXTENDED_TYPES,
                ENUM_KEY_VK_KHR_SHADER_SUBGROUP_EXTENDED_TYPES);
        extensionNameToEnum.put(
                KEY_VK_KHR_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW,
                ENUM_KEY_VK_KHR_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW);
        extensionNameToEnum.put(KEY_VK_KHR_8BIT_STORAGE, ENUM_KEY_VK_KHR_8BIT_STORAGE);
        extensionNameToEnum.put(
                KEY_VK_KHR_SHADER_INTEGER_DOT_PRODUCT, ENUM_KEY_VK_KHR_SHADER_INTEGER_DOT_PRODUCT);
        extensionNameToEnum.put(KEY_VK_EXT_INDEX_TYPE_UINT8, ENUM_KEY_VK_EXT_INDEX_TYPE_UINT8);
        extensionNameToEnum.put(KEY_VK_KHR_INDEX_TYPE_UINT8, ENUM_KEY_VK_KHR_INDEX_TYPE_UINT8);
        extensionNameToEnum.put(KEY_VK_EXT_LINE_RASTERIZATION, ENUM_KEY_VK_EXT_LINE_RASTERIZATION);
        extensionNameToEnum.put(
                KEY_VK_EXT_PRIMITIVES_GENERATED_QUERY, ENUM_KEY_VK_EXT_PRIMITIVES_GENERATED_QUERY);
        extensionNameToEnum.put(
                KEY_VK_KHR_SHADER_FLOAT_CONTROLS, ENUM_KEY_VK_KHR_SHADER_FLOAT_CONTROLS);
        extensionNameToEnum.put(
                KEY_VK_IMG_RELAXED_LINE_RASTERIZATION, ENUM_KEY_VK_IMG_RELAXED_LINE_RASTERIZATION);
        extensionNameToEnum.put(
                KEY_VK_KHR_VERTEX_ATTRIBUTE_DIVISOR, ENUM_KEY_VK_KHR_VERTEX_ATTRIBUTE_DIVISOR);
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
                JSONObject properties = device.getJSONObject(KEY_PROPERTIES);
                store.startGroup(getConvertedName(KEY_PROPERTIES));
                {
                    emitLong(store, properties, KEY_API_VERSION);
                    emitLong(store, properties, KEY_DRIVER_VERSION);
                    emitLong(store, properties, KEY_VENDOR_ID);
                    emitLong(store, properties, KEY_DEVICE_ID);
                    emitLong(store, properties, KEY_DEVICE_TYPE);
                    emitString(store, properties, KEY_DEVICE_NAME);
                    emitLongArray(store, properties, KEY_PIPELINE_CACHE_UUID);

                    JSONObject limits = properties.getJSONObject(KEY_LIMITS);
                    store.startGroup(getConvertedName(KEY_LIMITS));
                    {
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_1D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_2D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_3D);
                        emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_CUBE);
                        emitLong(store, limits, KEY_MAX_IMAGE_ARRAY_LAYERS);
                        emitLong(store, limits, KEY_MAX_TEXEL_BUFFER_ELEMENTS);
                        emitLong(store, limits, KEY_MAX_UNIFORM_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_STORAGE_BUFFER_RANGE);
                        emitLong(store, limits, KEY_MAX_PUSH_CONSTANTS_SIZE);
                        emitLong(store, limits, KEY_MAX_MEMORY_ALLOCATION_COUNT);
                        emitLong(store, limits, KEY_MAX_SAMPLER_ALLOCATION_COUNT);
                        emitString(store, limits, KEY_BUFFER_IMAGE_GRANULARITY);
                        emitString(store, limits, KEY_SPARSE_ADDRESS_SPACE_SIZE);
                        emitLong(store, limits, KEY_MAX_BOUND_DESCRIPTOR_SETS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_DESCRIPTOR_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_PER_STAGE_RESOURCES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_UNIFORM_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_BUFFERS_DYNAMIC);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_SAMPLED_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_STORAGE_IMAGES);
                        emitLong(store, limits, KEY_MAX_DESCRIPTOR_SET_INPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTES);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDINGS);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_ATTRIBUTE_OFFSET);
                        emitLong(store, limits, KEY_MAX_VERTEX_INPUT_BINDING_STRIDE);
                        emitLong(store, limits, KEY_MAX_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_GENERATION_LEVEL);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_PATCH_SIZE);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_VERTEX_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_PER_PATCH_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_CONTROL_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_TESSELLATION_EVALUATION_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_SHADER_INVOCATIONS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_OUTPUT_VERTICES);
                        emitLong(store, limits, KEY_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_INPUT_COMPONENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_OUTPUT_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_DUAL_SRC_ATTACHMENTS);
                        emitLong(store, limits, KEY_MAX_FRAGMENT_COMBINED_OUTPUT_RESOURCES);
                        emitLong(store, limits, KEY_MAX_COMPUTE_SHARED_MEMORY_SIZE);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_COUNT);
                        emitLong(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
                        emitLongArray(store, limits, KEY_MAX_COMPUTE_WORK_GROUP_SIZE);
                        emitLong(store, limits, KEY_SUB_PIXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_SUB_TEXEL_PRECISION_BITS);
                        emitLong(store, limits, KEY_MIPMAP_PRECISION_BITS);
                        emitLong(store, limits, KEY_MAX_DRAW_INDEXED_INDEX_VALUE);
                        emitLong(store, limits, KEY_MAX_DRAW_INDIRECT_COUNT);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_LOD_BIAS);
                        emitDouble(store, limits, KEY_MAX_SAMPLER_ANISOTROPY);
                        emitLong(store, limits, KEY_MAX_VIEWPORTS);
                        emitLongArray(store, limits, KEY_MAX_VIEWPORT_DIMENSIONS);
                        emitDoubleArray(store, limits, KEY_VIEWPORT_BOUNDS_RANGE);
                        emitLong(store, limits, KEY_VIEWPORT_SUB_PIXEL_BITS);
                        emitString(store, limits, KEY_MIN_MEMORY_MAP_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_TEXEL_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_MIN_STORAGE_BUFFER_OFFSET_ALIGNMENT);
                        emitLong(store, limits, KEY_MIN_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_OFFSET);
                        emitLong(store, limits, KEY_MIN_TEXEL_GATHER_OFFSET);
                        emitLong(store, limits, KEY_MAX_TEXEL_GATHER_OFFSET);
                        emitDouble(store, limits, KEY_MIN_INTERPOLATION_OFFSET);
                        emitDouble(store, limits, KEY_MAX_INTERPOLATION_OFFSET);
                        emitLong(store, limits, KEY_SUB_PIXEL_INTERPOLATION_OFFSET_BITS);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_WIDTH);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_HEIGHT);
                        emitLong(store, limits, KEY_MAX_FRAMEBUFFER_LAYERS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_FRAMEBUFFER_NO_ATTACHMENTS_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_COLOR_ATTACHMENTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_COLOR_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_INTEGER_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_DEPTH_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_SAMPLED_IMAGE_STENCIL_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_STORAGE_IMAGE_SAMPLE_COUNTS);
                        emitLong(store, limits, KEY_MAX_SAMPLE_MASK_WORDS);
                        emitBoolean(store, limits, KEY_TIMESTAMP_COMPUTE_AND_GRAPHICS);
                        emitDouble(store, limits, KEY_TIMESTAMP_PERIOD);
                        emitLong(store, limits, KEY_MAX_CLIP_DISTANCES);
                        emitLong(store, limits, KEY_MAX_CULL_DISTANCES);
                        emitLong(store, limits, KEY_MAX_COMBINED_CLIP_AND_CULL_DISTANCES);
                        emitLong(store, limits, KEY_DISCRETE_QUEUE_PRIORITIES);
                        emitDoubleArray(store, limits, KEY_POINT_SIZE_RANGE);
                        emitDoubleArray(store, limits, KEY_LINE_WIDTH_RANGE);
                        emitDouble(store, limits, KEY_POINT_SIZE_GRANULARITY);
                        emitDouble(store, limits, KEY_LINE_WIDTH_GRANULARITY);
                        emitBoolean(store, limits, KEY_STRICT_LINES);
                        emitBoolean(store, limits, KEY_STANDARD_SAMPLE_LOCATIONS);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_OFFSET_ALIGNMENT);
                        emitString(store, limits, KEY_OPTIMAL_BUFFER_COPY_ROW_PITCH_ALIGNMENT);
                        emitString(store, limits, KEY_NON_COHERENT_ATOM_SIZE);
                    }
                    store.endGroup();

                    JSONObject sparse = properties.getJSONObject(KEY_SPARSE_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_SPARSE_PROPERTIES));
                    {
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_ALIGNED_MIP_SIZE);
                        emitBoolean(store, sparse, KEY_RESIDENCY_NON_RESIDENT_STRICT);
                    }
                    store.endGroup();

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                        JSONObject core11 = device.getJSONObject(KEY_CORE11);
                        JSONObject vulkan11Properties = core11.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(getConvertedName(KEY_VULKAN_11_PROPERTIES));
                        {
                            emitLong(store, vulkan11Properties, KEY_DEVICE_NODE_MASK);
                            emitBoolean(store, vulkan11Properties, KEY_DEVICE_LUID_VALID);
                            emitLong(store, vulkan11Properties, KEY_SUBGROUP_SIZE);
                            emitBoolean(
                                    store,
                                    vulkan11Properties,
                                    KEY_SUBGROUP_QUAD_OPERATIONS_IN_ALL_STAGES);
                            emitLong(store, vulkan11Properties, KEY_MAX_MULTIVIEW_INSTANCE_INDEX);
                            emitLong(store, vulkan11Properties, KEY_MAX_MULTIVIEW_VIEW_COUNT);
                            emitLong(store, vulkan11Properties, KEY_MAX_PER_SET_DESCRIPTORS);
                            emitLong(store, vulkan11Properties, KEY_PROTECTED_NO_FAULT);

                            emitLongArray(store, vulkan11Properties, KEY_DEVICE_LUID);

                            emitLongArray(store, vulkan11Properties, KEY_DEVICE_UUID);
                            emitLongArray(store, vulkan11Properties, KEY_DRIVER_UUID);
                            emitString(store, vulkan11Properties, KEY_MAX_MEMORY_ALLOCATION_SIZE);
                            emitLong(store, vulkan11Properties, KEY_POINT_CLIPPING_BEHAVIOR);
                            emitLong(store, vulkan11Properties, KEY_SUBGROUP_SUPPORTED_OPERATIONS);
                            emitLong(store, vulkan11Properties, KEY_SUBGROUP_SUPPORTED_STAGES);
                        }
                        store.endGroup();

                        JSONObject core12 = device.getJSONObject(KEY_CORE12);
                        JSONObject vulkan12Properties = core12.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(getConvertedName(KEY_VULKAN_12_PROPERTIES));
                        {
                            emitLong(store, vulkan12Properties, KEY_DRIVER_ID);
                            emitString(store, vulkan12Properties, KEY_DRIVER_NAME);
                            emitString(store, vulkan12Properties, KEY_DRIVER_INFO);

                            JSONObject conformanceVersion = vulkan12Properties.getJSONObject(KEY_CONFORMANCE_VERSION);
                            store.startGroup(getConvertedName(KEY_CONFORMANCE_VERSION));
                            {
                                emitLong(store, conformanceVersion, KEY_MAJOR);
                                emitLong(store, conformanceVersion, KEY_MINOR);
                                emitLong(store, conformanceVersion, KEY_SUBMINOR);
                                emitLong(store, conformanceVersion, KEY_PATCH);
                            }
                            store.endGroup();

                            emitLong(store, vulkan12Properties, KEY_DENORM_BEHAVIOR_INDEPENDENCE);
                            emitLong(store, vulkan12Properties, KEY_ROUNDING_MODE_INDEPENDENCE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_PRESERVE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64);
                            emitLong(store, vulkan12Properties, KEY_MAX_UPDATE_AFTER_BIND_DESCRIPTORS_IN_ALL_POOLS);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_UNIFORM_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_SAMPLED_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_STORAGE_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_STORAGE_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NONUNIFORM_INDEXING_NATIVE);
                            emitBoolean(store, vulkan12Properties, KEY_ROBUST_BUFFER_ACCESS_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Properties, KEY_QUAD_DIVERGENT_IMPLICIT_LOD);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_UNIFORM_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_SAMPLED_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_STORAGE_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS);
                            emitLong(store, vulkan12Properties, KEY_MAX_PER_STAGE_UPDATE_AFTER_BIND_RESOURCES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_UNIFORM_BUFFERS_DYNAMIC);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_BUFFERS_DYNAMIC);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_SAMPLED_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_STORAGE_IMAGES);
                            emitLong(store, vulkan12Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INPUT_ATTACHMENTS);
                            emitLong(store, vulkan12Properties, KEY_SUPPORTED_DEPTH_RESOLVE_MODES);
                            emitLong(store, vulkan12Properties, KEY_SUPPORTED_STENCIL_RESOLVE_MODES);
                            emitBoolean(store, vulkan12Properties, KEY_INDEPENDENT_RESOLVE_NONE);
                            emitBoolean(store, vulkan12Properties, KEY_INDEPENDENT_RESOLVE);
                            emitBoolean(store, vulkan12Properties, KEY_FILTER_MINMAX_SINGLE_COMPONENT_FORMATS);
                            emitBoolean(store, vulkan12Properties, KEY_FILTER_MINMAX_IMAGE_COMPONENT_MAPPING);
                            emitString(store, vulkan12Properties, KEY_MAX_TIMELINE_SEMAPHORE_VALUE_DIFFERENCE);
                            emitLong(store, vulkan12Properties, KEY_FRAMEBUFFER_INTEGER_COLOR_SAMPLE_COUNTS);
                        }
                        store.endGroup();
                    }

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_3) {
                        JSONObject core13 = device.getJSONObject(KEY_CORE13);
                        JSONObject vulkan13Properties = core13.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(getConvertedName(KEY_VULKAN_13_PROPERTIES));
                        {
                            emitLong(store, vulkan13Properties, KEY_MIN_SUBGROUP_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_SUBGROUP_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_COMPUTE_WORKGROUP_SUBGROUPS);
                            emitLong(store, vulkan13Properties, KEY_REQUIRED_SUBGROUP_SIZE_STAGES);
                            emitLong(store, vulkan13Properties, KEY_MAX_INLINE_UNIFORM_BLOCK_SIZE);
                            emitLong(store, vulkan13Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_PER_STAGE_DESCRIPTOR_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_DESCRIPTOR_SET_UPDATE_AFTER_BIND_INLINE_UNIFORM_BLOCKS);
                            emitLong(store, vulkan13Properties, KEY_MAX_INLINE_UNIFORM_TOTAL_SIZE);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_8BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_16BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_32BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_64BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_UNSIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_SIGNED_ACCELERATED);
                            emitBoolean(store, vulkan13Properties, KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_MIXED_SIGNEDNESS_ACCELERATED);
                            emitString(store, vulkan13Properties, KEY_STORAGE_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES);
                            emitBoolean(store, vulkan13Properties, KEY_STORAGE_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT);
                            emitString(store, vulkan13Properties, KEY_UNIFORM_TEXEL_BUFFER_OFFSET_ALIGNMENT_BYTES);
                            emitBoolean(store, vulkan13Properties, KEY_UNIFORM_TEXEL_BUFFER_OFFSET_SINGLE_TEXEL_ALIGNMENT);
                            emitString(store, vulkan13Properties, KEY_MAX_BUFFER_SIZE);
                        }
                        store.endGroup();
                    }
                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_4) {
                        JSONObject core14 = device.getJSONObject(KEY_CORE14);
                        JSONObject vulkan14Properties = core14.getJSONObject(KEY_PROPERTIES);
                        store.startGroup(getConvertedName(KEY_VULKAN_14_PROPERTIES));
                        {
                            emitLong(store, vulkan14Properties, KEY_LINE_SUB_PIXEL_PRECISION_BITS);
                            emitLong(store, vulkan14Properties, KEY_MAX_VERTEX_ATTRIB_DIVISOR);
                            emitBoolean(store, vulkan14Properties, KEY_SUPPORTS_NON_ZERO_FIRST_INSTANCE);
                            emitLong(store, vulkan14Properties, KEY_MAX_PUSH_DESCRIPTORS);
                            emitBoolean(store, vulkan14Properties, KEY_DYNAMIC_RENDERING_LOCAL_READ_DEPTH_STENCIL_ATTACHMENTS);
                            emitBoolean(store, vulkan14Properties,  KEY_DYNAMIC_RENDERING_LOCAL_READ_MULTISAMPLED_ATTACHMENTS);
                            emitBoolean(store, vulkan14Properties,  KEY_EARLY_FRAGMENT_MULTISAMPLE_COVERAGE_AFTER_SAMPLE_COUNTING);
                            emitBoolean(store, vulkan14Properties,  KEY_EARLY_FRAGMENT_SAMPLE_MASK_TEST_BEFORE_SAMPLE_COUNTING);
                            emitBoolean(store, vulkan14Properties, KEY_DEPTH_STENCIL_SWIZZLE_ONE_SUPPORT);
                            emitBoolean(store, vulkan14Properties, KEY_POLYGON_MODE_POINT_SIZE);
                            emitBoolean(store, vulkan14Properties, KEY_NON_STRICT_SINGLE_PIXEL_WIDE_LINES_USE_PARALLELOGRAM);
                            emitBoolean(store, vulkan14Properties, KEY_NON_STRICT_WIDE_LINES_USE_PARALLELOGRAM);
                            emitBoolean(store, vulkan14Properties, KEY_BLOCK_TEXEL_VIEW_COMPATIBLE_MULTIPLE_LAYERS);
                            emitLong(store, vulkan14Properties, KEY_MAX_COMBINED_IMAGE_SAMPLER_DESCRIPTOR_COUNT);
                            emitBoolean(store, vulkan14Properties, KEY_FRAGMENT_SHADING_RATE_CLAMP_COMBINER_INPUTS);
                            emitLong(store, vulkan14Properties, KEY_DEFAULT_ROBUSTNESS_STORAGE_BUFFERS);
                            emitLong(store, vulkan14Properties, KEY_DEFAULT_ROBUSTNESS_UNIFORM_BUFFERS);
                            emitLong(store, vulkan14Properties, KEY_DEFAULT_ROBUSTNESS_VERTEX_INPUTS);
                            emitLong(store, vulkan14Properties, KEY_DEFAULT_ROBUSTNESS_IMAGES);
                            emitLong(store, vulkan14Properties, KEY_COPY_SRC_LAYOUT_COUNT);
                            emitLongArray(store, vulkan14Properties, KEY_P_COPY_SRC_LAYOUTS);
                            emitLong(store, vulkan14Properties, KEY_COPY_DST_LAYOUT_COUNT);
                            emitLongArray(store, vulkan14Properties, KEY_P_COPY_DST_LAYOUTS);
                            emitLongArray(store, vulkan14Properties, KEY_OPTIMAL_TILING_LAYOUT_UUID);
                            emitBoolean(store, vulkan14Properties, KEY_IDENTICAL_MEMORY_TYPE_REQUIREMENTS);
                        }
                        store.endGroup();
                    }
                }
                store.endGroup();

                JSONObject features = device.getJSONObject(KEY_FEATURES);
                store.startGroup(getConvertedName(KEY_FEATURES));
                {
                    emitBoolean(store, features, KEY_ROBUST_BUFFER_ACCESS);
                    emitBoolean(store, features, KEY_FULL_DRAW_INDEX_UINT32);
                    emitBoolean(store, features, KEY_IMAGE_CUBE_ARRAY);
                    emitBoolean(store, features, KEY_INDEPENDENT_BLEND);
                    emitBoolean(store, features, KEY_GEOMETRY_SHADER);
                    emitBoolean(store, features, KEY_TESSELLATION_SHADER);
                    emitBoolean(store, features, KEY_SAMPLE_RATE_SHADING);
                    emitBoolean(store, features, KEY_DUAL_SRC_BLEND);
                    emitBoolean(store, features, KEY_LOGIC_OP);
                    emitBoolean(store, features, KEY_MULTI_DRAW_INDIRECT);
                    emitBoolean(store, features, KEY_DRAW_INDIRECT_FIRST_INSTANCE);
                    emitBoolean(store, features, KEY_DEPTH_CLAMP);
                    emitBoolean(store, features, KEY_DEPTH_BIAS_CLAMP);
                    emitBoolean(store, features, KEY_FILL_MODE_NON_SOLID);
                    emitBoolean(store, features, KEY_DEPTH_BOUNDS);
                    emitBoolean(store, features, KEY_WIDE_LINES);
                    emitBoolean(store, features, KEY_LARGE_POINTS);
                    emitBoolean(store, features, KEY_ALPHA_TO_ONE);
                    emitBoolean(store, features, KEY_MULTI_VIEWPORT);
                    emitBoolean(store, features, KEY_SAMPLER_ANISOTROPY);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ETC2);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_ASTC_LDR);
                    emitBoolean(store, features, KEY_TEXTURE_COMPRESSION_BC);
                    emitBoolean(store, features, KEY_OCCLUSION_QUERY_PRECISE);
                    emitBoolean(store, features, KEY_PIPELINE_STATISTICS_QUERY);
                    emitBoolean(store, features, KEY_VERTEX_PIPELINE_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_FRAGMENT_STORES_AND_ATOMICS);
                    emitBoolean(store, features, KEY_SHADER_TESSELLATION_AND_GEOMETRY_POINT_SIZE);
                    emitBoolean(store, features, KEY_SHADER_IMAGE_GATHER_EXTENDED);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_EXTENDED_FORMATS);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_MULTISAMPLE);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_READ_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_WRITE_WITHOUT_FORMAT);
                    emitBoolean(store, features, KEY_SHADER_UNIFORM_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_SAMPLED_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_BUFFER_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_STORAGE_IMAGE_ARRAY_DYNAMIC_INDEXING);
                    emitBoolean(store, features, KEY_SHADER_CLIP_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_CULL_DISTANCE);
                    emitBoolean(store, features, KEY_SHADER_FLOAT64);
                    emitBoolean(store, features, KEY_SHADER_INT64);
                    emitBoolean(store, features, KEY_SHADER_INT16);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_RESIDENCY);
                    emitBoolean(store, features, KEY_SHADER_RESOURCE_MIN_LOD);
                    emitBoolean(store, features, KEY_SPARSE_BINDING);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_BUFFER);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_2D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_IMAGE_3D);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_2_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_4_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_8_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_16_SAMPLES);
                    emitBoolean(store, features, KEY_SPARSE_RESIDENCY_ALIASED);
                    emitBoolean(store, features, KEY_VARIABLE_MULTISAMPLE_RATE);
                    emitBoolean(store, features, KEY_INHERITED_QUERIES);


                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_2) {
                        JSONObject core11 = device.getJSONObject(KEY_CORE11);
                        JSONObject vulkan11Features = core11.getJSONObject(KEY_FEATURES);
                        store.startGroup(getConvertedName(KEY_VULKAN_11_FEATURES));
                        {
                            emitBoolean(store, vulkan11Features, KEY_MULTIVIEW);
                            emitBoolean(store, vulkan11Features, KEY_MULTIVIEW_GEOMETRY_SHADER);
                            emitBoolean(store, vulkan11Features, KEY_MULTIVIEW_TESSELLATION_SHADER);
                            emitBoolean(store, vulkan11Features, KEY_PROTECTED_MEMORY);
                            emitBoolean(store, vulkan11Features, KEY_SAMPLER_YCBCR_CONVERSION);
                            emitBoolean(store, vulkan11Features, KEY_SHADER_DRAW_PARAMETERS);
                            emitBoolean(store, vulkan11Features, KEY_STORAGE_BUFFER_16BIT_ACCESS);
                            emitBoolean(store, vulkan11Features, KEY_STORAGE_INPUT_OUTPUT_16);
                            emitBoolean(store, vulkan11Features, KEY_STORAGE_PUSH_CONSTANT_16);
                            emitBoolean(store, vulkan11Features, KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS);
                            emitBoolean(store, vulkan11Features, KEY_VARIABLE_POINTERS);
                            emitBoolean(store, vulkan11Features, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        }
                        store.endGroup();

                        JSONObject core12 = device.getJSONObject(KEY_CORE12);
                        JSONObject vulkan12Features = core12.getJSONObject(KEY_FEATURES);
                        store.startGroup(getConvertedName(KEY_VULKAN_12_FEATURES));
                        {
                            emitBoolean(store, vulkan12Features, KEY_SAMPLER_MIRROR_CLAMP_TO_EDGE);
                            emitBoolean(store, vulkan12Features, KEY_DRAW_INDIRECT_COUNT);
                            emitBoolean(store, vulkan12Features, KEY_STORAGE_BUFFER_8BIT_ACCESS);
                            emitBoolean(store, vulkan12Features, KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS);
                            emitBoolean(store, vulkan12Features, KEY_STORAGE_PUSH_CONSTANT8);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_BUFFER_INT64_ATOMICS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SHARED_INT64_ATOMICS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_FLOAT16);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INT8);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_DYNAMIC_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SAMPLED_IMAGE_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_IMAGE_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_UNIFORM_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_STORAGE_TEXEL_BUFFER_ARRAY_NON_UNIFORM_INDEXING);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UNIFORM_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_SAMPLED_IMAGE_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_IMAGE_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UNIFORM_TEXEL_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_STORAGE_TEXEL_BUFFER_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_PARTIALLY_BOUND);
                            emitBoolean(store, vulkan12Features, KEY_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT);
                            emitBoolean(store, vulkan12Features, KEY_RUNTIME_DESCRIPTOR_ARRAY);
                            emitBoolean(store, vulkan12Features, KEY_SAMPLER_FILTER_MINMAX);
                            emitBoolean(store, vulkan12Features, KEY_SCALAR_BLOCK_LAYOUT);
                            emitBoolean(store, vulkan12Features, KEY_IMAGELESS_FRAMEBUFFER);
                            emitBoolean(store, vulkan12Features, KEY_UNIFORM_BUFFER_STANDARD_LAYOUT);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_SUBGROUP_EXTENDED_TYPES);
                            emitBoolean(store, vulkan12Features, KEY_SEPARATE_DEPTH_STENCIL_LAYOUTS);
                            emitBoolean(store, vulkan12Features, KEY_HOST_QUERY_RESET);
                            emitBoolean(store, vulkan12Features, KEY_TIMELINE_SEMAPHORE);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS_CAPTURE_REPLAY);
                            emitBoolean(store, vulkan12Features, KEY_BUFFER_DEVICE_ADDRESS_MULTI_DEVICE);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL_DEVICE_SCOPE);
                            emitBoolean(store, vulkan12Features, KEY_VULKAN_MEMORY_MODEL_AVAILABILITY_VISIBILITY_CHAINS);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_OUTPUT_VIEWPORT_INDEX);
                            emitBoolean(store, vulkan12Features, KEY_SHADER_OUTPUT_LAYER);
                            // subgroupBroadcastDynamicId was erroneously left out of vkjson reporting in Android T
                            //   and later added in U, so we need to explicitly check if the feature is reported
                            if (vulkan12Features.has(KEY_SUBGROUP_BROADCAST_DYNAMIC_ID)) {
                                emitBoolean(store, vulkan12Features, KEY_SUBGROUP_BROADCAST_DYNAMIC_ID);
                            }
                        }
                        store.endGroup();
                    }

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_3) {
                        JSONObject core13 = device.getJSONObject(KEY_CORE13);
                        JSONObject vulkan13Features = core13.getJSONObject(KEY_FEATURES);
                        store.startGroup(getConvertedName(KEY_VULKAN_13_FEATURES));
                        {
                            emitBoolean(store, vulkan13Features, KEY_ROBUST_IMAGE_ACCESS);
                            emitBoolean(store, vulkan13Features, KEY_INLINE_UNIFORM_BLOCK);
                            emitBoolean(store, vulkan13Features, KEY_DESCRIPTOR_BINDING_INLINE_UNIFORM_BLOCK_UPDATE_AFTER_BIND);
                            emitBoolean(store, vulkan13Features, KEY_PIPELINE_CREATION_CACHE_CONTROL);
                            emitBoolean(store, vulkan13Features, KEY_PRIVATE_DATA);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_DEMOTE_TO_HELPER_INVOCATION);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_TERMINATE_INVOCATION);
                            emitBoolean(store, vulkan13Features, KEY_SUBGROUP_SIZE_CONTROL);
                            emitBoolean(store, vulkan13Features, KEY_COMPUTE_FULL_SUBGROUPS);
                            emitBoolean(store, vulkan13Features, KEY_SYNCHRONIZATION2);
                            emitBoolean(store, vulkan13Features, KEY_TEXTURE_COMPRESSION_ASTC_HDR);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_ZERO_INITIALIZE_WORKGROUP_MEMORY);
                            emitBoolean(store, vulkan13Features, KEY_DYNAMIC_RENDERING);
                            emitBoolean(store, vulkan13Features, KEY_SHADER_INTEGER_DOT_PRODUCT);
                            emitBoolean(store, vulkan13Features, KEY_MAINTENANCE4);
                        }
                        store.endGroup();
                    }

                    if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_4) {
                        JSONObject core14 = device.getJSONObject(KEY_CORE14);
                        JSONObject vulkan14Features = core14.getJSONObject(KEY_FEATURES);
                        store.startGroup(getConvertedName(KEY_VULKAN_14_FEATURES));
                        {
                            emitBoolean(store, vulkan14Features, KEY_GLOBAL_PRIORITY_QUERY);
                            emitBoolean(store, vulkan14Features, KEY_SHADER_SUBGROUP_ROTATE);
                            emitBoolean(store, vulkan14Features, KEY_SHADER_SUBGROUP_ROTATE_CLUSTERED);
                            emitBoolean(store, vulkan14Features, KEY_SHADER_FLOAT_CONTROLS2);
                            emitBoolean(store, vulkan14Features, KEY_SHADER_EXPECT_ASSUME);
                            emitBoolean(store, vulkan14Features, KEY_RECTANGULAR_LINES);
                            emitBoolean(store, vulkan14Features, KEY_BRESENHAM_LINES);
                            emitBoolean(store, vulkan14Features, KEY_SMOOTH_LINES);
                            emitBoolean(store, vulkan14Features, KEY_STIPPLED_RECTANGULAR_LINES);
                            emitBoolean(store, vulkan14Features, KEY_STIPPLED_BRESENHAM_LINES);
                            emitBoolean(store, vulkan14Features, KEY_STIPPLED_SMOOTH_LINES);
                            emitBoolean(store, vulkan14Features, KEY_VERTEX_ATTRIBUTE_INSTANCE_RATE_DIVISOR);
                            emitBoolean(store, vulkan14Features, KEY_VERTEX_ATTRIBUTE_INSTANCE_RATE_ZERO_DIVISOR);
                            emitBoolean(store, vulkan14Features, KEY_INDEX_TYPE_UINT8);
                            emitBoolean(store, vulkan14Features, KEY_DYNAMIC_RENDERING_LOCAL_READ);
                            emitBoolean(store, vulkan14Features, KEY_MAINTENANCE5);
                            emitBoolean(store, vulkan14Features, KEY_MAINTENANCE6);
                            emitBoolean(store, vulkan14Features, KEY_PIPELINE_PROTECTED_ACCESS);
                            emitBoolean(store, vulkan14Features, KEY_PIPELINE_ROBUSTNESS);
                            emitBoolean(store, vulkan14Features, KEY_HOST_IMAGE_COPY);
                            emitBoolean(store, vulkan14Features, KEY_PUSH_DESCRIPTOR);
                        }
                        store.endGroup();
                    }
                }
                store.endGroup();

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
                store.startArray("supported_formats");
                for (int formatIdx = 0; formatIdx < formats.length(); formatIdx++) {
                    JSONArray formatPair = formats.getJSONArray(formatIdx);
                    JSONObject formatProperties = formatPair.getJSONObject(1);
                    store.startGroup();
                    {
                        store.addResult("format", (long)formatPair.getInt(0));
                        emitLong(store, formatProperties, KEY_LINEAR_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_OPTIMAL_TILING_FEATURES);
                        emitLong(store, formatProperties, KEY_BUFFER_FEATURES);
                    }
                    store.endGroup();
                }
                store.endArray();

                if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_1_1) {
                    JSONObject subgroupProperties = device.getJSONObject(KEY_SUBGROUP_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_SUBGROUP_PROPERTIES));
                    {
                        emitLong(store, subgroupProperties, KEY_SUBGROUP_SIZE);
                        emitLong(store, subgroupProperties, KEY_SUPPORTED_STAGES);
                        emitLong(store, subgroupProperties, KEY_SUPPORTED_OPERATIONS);
                        emitBoolean(store, subgroupProperties, KEY_QUAD_OPERATIONS_IN_ALL_STAGES);
                    }
                    store.endGroup();

                    JSONObject pointClippingProperties = device.getJSONObject(KEY_POINT_CLIPPING_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_POINT_CLIPPING_PROPERTIES));
                    {
                        emitLong(store, pointClippingProperties, KEY_POINT_CLIPPING_BEHAVIOR);
                    }
                    store.endGroup();

                    JSONObject multiviewProperties = device.getJSONObject(KEY_MULTIVIEW_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_MULTIVIEW_PROPERTIES));
                    {
                        emitLong(store, multiviewProperties, KEY_MAX_MULTIVIEW_VIEW_COUNT);
                        emitLong(store, multiviewProperties, KEY_MAX_MULTIVIEW_INSTANCE_INDEX);
                    }
                    store.endGroup();

                    JSONObject idProperties = device.getJSONObject(KEY_ID_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_ID_PROPERTIES));
                    {
                        emitLongArray(store, idProperties, KEY_DEVICE_UUID);
                        emitLongArray(store, idProperties, KEY_DRIVER_UUID);
                        emitLongArray(store, idProperties, KEY_DEVICE_LUID);
                        emitLong(store, idProperties, KEY_DEVICE_NODE_MASK);
                        emitBoolean(store, idProperties, KEY_DEVICE_LUID_VALID);
                    }
                    store.endGroup();

                    JSONObject maintenance3Properties = device.getJSONObject(KEY_MAINTENANCE_3_PROPERTIES);
                    store.startGroup(getConvertedName(KEY_MAINTENANCE_3_PROPERTIES));
                    {
                        emitLong(store, maintenance3Properties, KEY_MAX_PER_SET_DESCRIPTORS);
                        emitString(store, maintenance3Properties, KEY_MAX_MEMORY_ALLOCATION_SIZE);
                    }
                    store.endGroup();

                    JSONObject bit16StorageFeatures = device.getJSONObject(KEY_BIT16_STORAGE_FEATURES);
                    store.startGroup(getConvertedName(KEY_BIT16_STORAGE_FEATURES));
                    {
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_BUFFER_16BIT_ACCESS);
                        emitBoolean(store, bit16StorageFeatures, KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS);
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_PUSH_CONSTANT_16);
                        emitBoolean(store, bit16StorageFeatures, KEY_STORAGE_INPUT_OUTPUT_16);
                    }
                    store.endGroup();

                    JSONObject multiviewFeatures = device.getJSONObject(KEY_MULTIVIEW_FEATURES);
                    store.startGroup(getConvertedName(KEY_MULTIVIEW_FEATURES));
                    {
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW);
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW_GEOMETRY_SHADER);
                        emitBoolean(store, multiviewFeatures, KEY_MULTIVIEW_TESSELLATION_SHADER);
                    }
                    store.endGroup();

                    JSONObject variablePointersFeatures = device.getJSONObject(KEY_VARIABLE_POINTERS_FEATURES);
                    store.startGroup(getConvertedName(KEY_VARIABLE_POINTERS_FEATURES));
                    {
                        emitBoolean(store, variablePointersFeatures, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        emitBoolean(store, variablePointersFeatures, KEY_VARIABLE_POINTERS);
                    }
                    store.endGroup();

                    JSONObject protectedMemoryFeatures = device.getJSONObject(KEY_PROTECTED_MEMORY_FEATURES);
                    store.startGroup(getConvertedName(KEY_PROTECTED_MEMORY_FEATURES));
                    {
                        emitBoolean(store, protectedMemoryFeatures, KEY_PROTECTED_MEMORY);
                    }
                    store.endGroup();

                    JSONObject samplerYcbcrConversionFeatures = device.getJSONObject(KEY_SAMPLER_YCBCR_CONVERSION_FEATURES);
                    store.startGroup(getConvertedName(KEY_SAMPLER_YCBCR_CONVERSION_FEATURES));
                    {
                        emitBoolean(store, samplerYcbcrConversionFeatures, KEY_SAMPLER_YCBCR_CONVERSION);
                    }
                    store.endGroup();

                    JSONObject shaderDrawParameterFeatures = device.getJSONObject(KEY_SHADER_DRAW_PARAMETER_FEATURES);
                    store.startGroup(getConvertedName(KEY_SHADER_DRAW_PARAMETER_FEATURES));
                    {
                        emitBoolean(store, shaderDrawParameterFeatures, KEY_SHADER_DRAW_PARAMETERS);
                    }
                    store.endGroup();

                    JSONArray externalFences = device.getJSONArray(KEY_EXTERNAL_FENCE_PROPERTIES);
                    store.startArray(getConvertedName(KEY_EXTERNAL_FENCE_PROPERTIES));
                    for (int idx = 0; idx < externalFences.length(); ++idx) {
                        JSONArray externalFencePair = externalFences.getJSONArray(idx);
                        JSONObject externalFenceProperties = externalFencePair.getJSONObject(1);
                        store.startGroup();
                        {
                            store.addResult("handle_type", externalFencePair.getLong(0));
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
                            store.addResult("handle_type", externalSemaphorePair.getLong(0));
                            emitLong(store, externalSemaphoreProperties, KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_COMPATIBLE_HANDLE_TYPES);
                            emitLong(store, externalSemaphoreProperties, KEY_EXTERNAL_SEMAPHORE_FEATURES);
                        }
                        store.endGroup();
                    }
                    store.endArray();
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

    private static void emitDriverPropertiesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extDriverProperties = parent.getJSONObject(KEY_VK_KHR_DRIVER_PROPERTIES);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_DRIVER_PROPERTIES));
                {
                    JSONObject driverPropertiesKHR = extDriverProperties.getJSONObject(KEY_DRIVER_PROPERTIES_KHR);
                    store.startGroup(getConvertedName(KEY_DRIVER_PROPERTIES_KHR));
                    {
                        emitLong(store, driverPropertiesKHR, KEY_DRIVER_ID);
                        emitString(store, driverPropertiesKHR, KEY_DRIVER_NAME);
                        emitString(store, driverPropertiesKHR, KEY_DRIVER_INFO);

                        JSONObject conformanceVersion = driverPropertiesKHR.getJSONObject(KEY_CONFORMANCE_VERSION);
                        store.startGroup(getConvertedName(KEY_CONFORMANCE_VERSION));
                        {
                            emitLong(store, conformanceVersion, KEY_MAJOR);
                            emitLong(store, conformanceVersion, KEY_MINOR);
                            emitLong(store, conformanceVersion, KEY_SUBMINOR);
                            emitLong(store, conformanceVersion, KEY_PATCH);
                        }
                        store.endGroup();
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitVariablePointerFeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extVariablePointerFeatures = parent.getJSONObject(KEY_VK_KHR_VARIABLE_POINTERS);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_VARIABLE_POINTERS));
                {
                    JSONObject variablePointerFeaturesKHR = extVariablePointerFeatures.getJSONObject(KEY_VARIABLE_POINTER_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_VARIABLE_POINTER_FEATURES_KHR));
                    {
                        emitBoolean(store, variablePointerFeaturesKHR, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        emitBoolean(store, variablePointerFeaturesKHR, KEY_VARIABLE_POINTERS);
                    }
                    store.endGroup();
                    JSONObject variablePointersFeaturesKHR = extVariablePointerFeatures.getJSONObject(KEY_VARIABLE_POINTERS_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_VARIABLE_POINTERS_FEATURES_KHR));
                    {
                        emitBoolean(store, variablePointersFeaturesKHR, KEY_VARIABLE_POINTERS_STORAGE_BUFFER);
                        emitBoolean(store, variablePointersFeaturesKHR, KEY_VARIABLE_POINTERS);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue

        }
    }

    private static void emitImage2DViewOf3DFeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extImage2DViewOf3DFeatures = parent.getJSONObject(KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D));
                {
                    JSONObject image2DViewOf3DFeaturesEXT = extImage2DViewOf3DFeatures.getJSONObject(KEY_IMAGE_2D_VIEW_OF_3D_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_IMAGE_2D_VIEW_OF_3D_FEATURES_EXT));
                    {
                        emitBoolean(store, image2DViewOf3DFeaturesEXT, KEY_IMAGE_2D_VIEW_OF_3D);
                        emitBoolean(store, image2DViewOf3DFeaturesEXT, KEY_SAMPLER_2D_VIEW_OF_3D);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitVertexAttributeDivisorFeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject khrVertexAttributeDivisorFeatures = parent.getJSONObject(KEY_VK_KHR_VERTEX_ATTRIBUTE_DIVISOR);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_VERTEX_ATTRIBUTE_DIVISOR));
                {
                    JSONObject vertexAttributeDivisorFeaturesKHR = khrVertexAttributeDivisorFeatures.getJSONObject(KEY_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_KHR));
                    {
                        emitBoolean(store, vertexAttributeDivisorFeaturesKHR, KEY_VERTEX_ATTRIBUTE_INSTANCE_RATE_DIVISOR);
                        emitBoolean(store, vertexAttributeDivisorFeaturesKHR, KEY_VERTEX_ATTRIBUTE_INSTANCE_RATE_ZERO_DIVISOR);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitCustomBorderColorFeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extCustomborderColorFeatures =
                    parent.getJSONObject(KEY_VK_EXT_CUSTOM_BORDER_COLOR);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_CUSTOM_BORDER_COLOR));
                {
                    JSONObject customBorderColorFeaturesEXT =
                            extCustomborderColorFeatures.getJSONObject(
                                    KEY_CUSTOM_BORDER_COLOR_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_CUSTOM_BORDER_COLOR_FEATURES_EXT));
                    {
                        emitBoolean(
                                store,
                                customBorderColorFeaturesEXT,
                                KEY_CUSTOM_BORDER_COLOR_WITHOUT_FORMAT);
                        emitBoolean(store, customBorderColorFeaturesEXT, KEY_CUSTOM_BORDER_COLORS);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitPrimitiveTopologyListRestartFeaturesEXT(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject extPrimitiveTopologyListRestartFeatures =
                    parent.getJSONObject(KEY_VK_EXT_PRIMITIVE_TOPOLOGY_LIST_RESTART);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_PRIMITIVE_TOPOLOGY_LIST_RESTART));
                {
                    JSONObject primitiveTopologyListRestartFeaturesEXT =
                            extPrimitiveTopologyListRestartFeatures.getJSONObject(
                                    KEY_PRIMITIVE_TOPOLOGY_LIST_RESTART_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_PRIMITIVE_TOPOLOGY_LIST_RESTART_FEATURES_EXT));
                    {
                        emitBoolean(
                                store,
                                primitiveTopologyListRestartFeaturesEXT,
                                KEY_PRIMITIVE_TOPOLOGY_LIST_RESTART);
                        emitBoolean(
                                store,
                                primitiveTopologyListRestartFeaturesEXT,
                                KEY_PRIMITIVE_TOPOLOGY_PATCH_LIST_RESTART);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitransformFeedbackFeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extTransformFeedbackFeatures =
                    parent.getJSONObject(KEY_VK_EXT_TRANSFORM_FEEDBACK);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_TRANSFORM_FEEDBACK));
                {
                    JSONObject transformFeedbackFeaturesEXT =
                            extTransformFeedbackFeatures.getJSONObject(
                                    KEY_TRANSFORM_FEEDBACK_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_TRANSFORM_FEEDBACK_FEATURES_EXT));
                    {
                        emitBoolean(store, transformFeedbackFeaturesEXT, KEY_GEOMETRY_STREAMS);
                        emitBoolean(store, transformFeedbackFeaturesEXT, KEY_TRANSFORM_FEEDBACK);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitProvokingVertexFeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extProvokingVertexFeatures =
                    parent.getJSONObject(KEY_VK_EXT_PROVOKING_VERTEX);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_PROVOKING_VERTEX));
                {
                    JSONObject provokingVertexFeaturesEXT =
                            extProvokingVertexFeatures.getJSONObject(
                                    KEY_PROVOKING_VERTEX_FEATURES_EXT);

                    store.startGroup(getConvertedName(KEY_PROVOKING_VERTEX_FEATURES_EXT));
                    {
                        emitBoolean(store, provokingVertexFeaturesEXT, KEY_PROVOKING_VERTEX_LAST);

                        emitBoolean(
                                store,
                                provokingVertexFeaturesEXT,
                                KEY_TRANSFORM_FEEDBACK_PRESERVES_PROVOKING_VERTEX);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitIndexTypeUint8FeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extIndexTypeUint8Features =
                    parent.getJSONObject(KEY_VK_EXT_INDEX_TYPE_UINT8);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_INDEX_TYPE_UINT8));
                {
                    JSONObject indexTypeUint8FeaturesEXT =
                            extIndexTypeUint8Features.getJSONObject(
                                    KEY_INDEX_TYPE_UINT8_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_INDEX_TYPE_UINT8_FEATURES_EXT));
                    {
                        emitBoolean(store, indexTypeUint8FeaturesEXT, KEY_INDEX_TYPE_UINT8);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitIndexTypeUint8FeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject khrIndexTypeUint8Features =
                    parent.getJSONObject(KEY_VK_KHR_INDEX_TYPE_UINT8);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_INDEX_TYPE_UINT8));
                {
                    JSONObject indexTypeUint8FeaturesKHR =
                            khrIndexTypeUint8Features.getJSONObject(
                                    KEY_INDEX_TYPE_UINT8_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_INDEX_TYPE_UINT8_FEATURES_KHR));
                    {
                        emitBoolean(store, indexTypeUint8FeaturesKHR, KEY_INDEX_TYPE_UINT8);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emit8bitStorageFeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject khr8bitStorageFeatures = parent.getJSONObject(KEY_VK_KHR_8BIT_STORAGE);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_8BIT_STORAGE));
                {
                    JSONObject bit8StorageFeaturesKHR=
                            khr8bitStorageFeatures.getJSONObject(KEY_BIT8_STORAGE_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_BIT8_STORAGE_FEATURES_KHR));
                    {
                        emitBoolean(store, bit8StorageFeaturesKHR, KEY_STORAGE_BUFFER_8BIT_ACCESS);
                        emitBoolean(store, bit8StorageFeaturesKHR, KEY_STORAGE_PUSH_CONSTANT8);
                        emitBoolean(store, bit8StorageFeaturesKHR, KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitShaderFloat16Int8FeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject khrShaderFloat16Int8Features =
                    parent.getJSONObject(KEY_VK_KHR_SHADER_FLOAT16_INT8);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_SHADER_FLOAT16_INT8));
                {
                    JSONObject shaderFloat16Int8FeaturesKHR =
                            khrShaderFloat16Int8Features.getJSONObject(
                                    KEY_SHADER_FLOAT16_INT8_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_SHADER_FLOAT16_INT8_FEATURES_KHR));
                    {
                        emitBoolean(store, shaderFloat16Int8FeaturesKHR, KEY_SHADER_FLOAT16);
                        emitBoolean(store, shaderFloat16Int8FeaturesKHR, KEY_SHADER_INT8);
                    }
                    store.endGroup();

                    JSONObject float16Int8FeaturesKHR =
                            khrShaderFloat16Int8Features.getJSONObject(
                                    KEY_FLOAT16_INT8_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_FLOAT16_INT8_FEATURES_KHR));
                    {
                        emitBoolean(store, float16Int8FeaturesKHR, KEY_SHADER_FLOAT16);
                        emitBoolean(store, float16Int8FeaturesKHR, KEY_SHADER_INT8);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitShaderIntegerDotProductFeaturesKHR(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject khrShaderIntegerDotProductFeatures =
                    parent.getJSONObject(KEY_VK_KHR_SHADER_INTEGER_DOT_PRODUCT);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_SHADER_INTEGER_DOT_PRODUCT));
                {
                    JSONObject shaderIntegerDotProductFeaturesKHR =
                            khrShaderIntegerDotProductFeatures.getJSONObject(
                                    KEY_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR));
                    {
                        emitBoolean(
                                store,
                                shaderIntegerDotProductFeaturesKHR,
                                KEY_SHADER_INTEGER_DOT_PRODUCT);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitShaderSubgroupExtendedTypesFeaturesKHR(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject khrShaderSubgroupExtendedTypesFeatures =
                    parent.getJSONObject(KEY_VK_KHR_SHADER_SUBGROUP_EXTENDED_TYPES);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_SHADER_SUBGROUP_EXTENDED_TYPES));
                {
                    JSONObject shaderSubgroupExtendedTypesFeaturesKHR =
                            khrShaderSubgroupExtendedTypesFeatures.getJSONObject(
                                    KEY_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES_KHR);
                    store.startGroup(getConvertedName(KEY_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES_KHR));
                    {
                        emitBoolean(
                                store,
                                shaderSubgroupExtendedTypesFeaturesKHR,
                                KEY_SHADER_SUBGROUP_EXTENDED_TYPES);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitShaderSubgroupUniformControlFlowFeaturesKHR(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject extShaderSubgroupUniformControlFlowFeatures =
                    parent.getJSONObject(KEY_VK_KHR_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW));
                {
                    JSONObject shaderSubgroupUniformControlFlowFeaturesKHR =
                            extShaderSubgroupUniformControlFlowFeatures.getJSONObject(
                                    KEY_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW_FEATURES_KHR);
                    store.startGroup(
                            getConvertedName(KEY_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW_FEATURES_KHR));
                    {
                        emitBoolean(
                                store,
                                shaderSubgroupUniformControlFlowFeaturesKHR,
                                KEY_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitLineRasterizationFeaturesEXT(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject extLineRasterizationFeatures =
                    parent.getJSONObject(KEY_VK_EXT_LINE_RASTERIZATION);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_LINE_RASTERIZATION));
                {
                    JSONObject lineRasterizationFeaturesEXT =
                            extLineRasterizationFeatures.getJSONObject(
                                    KEY_LINE_RASTERIZATION_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_LINE_RASTERIZATION_FEATURES_EXT));
                    {
                        emitBoolean(store, lineRasterizationFeaturesEXT, KEY_BRESENHAM_LINES);
                        emitBoolean(store, lineRasterizationFeaturesEXT, KEY_RECTANGULAR_LINES);
                        emitBoolean(store, lineRasterizationFeaturesEXT, KEY_SMOOTH_LINES);
                        emitBoolean(
                                store, lineRasterizationFeaturesEXT, KEY_STIPPLED_BRESENHAM_LINES);
                        emitBoolean(
                                store,
                                lineRasterizationFeaturesEXT,
                                KEY_STIPPLED_RECTANGULAR_LINES);
                        emitBoolean(store, lineRasterizationFeaturesEXT, KEY_STIPPLED_SMOOTH_LINES);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitPrimitivesGeneratedQueryFeaturesEXT(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject extPrimitivesGeneratedQueryFeatures =
                    parent.getJSONObject(KEY_VK_EXT_PRIMITIVES_GENERATED_QUERY);
            try {
                store.startGroup(getConvertedName(KEY_VK_EXT_PRIMITIVES_GENERATED_QUERY));
                {
                    JSONObject primitivesGeneratedQueryFeaturesEXT =
                            extPrimitivesGeneratedQueryFeatures.getJSONObject(
                                    KEY_PRIMITIVES_GENERATED_QUERY_FEATURES_EXT);
                    store.startGroup(getConvertedName(KEY_PRIMITIVES_GENERATED_QUERY_FEATURES_EXT));
                    {
                        emitBoolean(
                                store,
                                primitivesGeneratedQueryFeaturesEXT,
                                KEY_PRIMITIVES_GENERATED_QUERY);
                        emitBoolean(
                                store,
                                primitivesGeneratedQueryFeaturesEXT,
                                KEY_PRIMITIVES_GENERATED_QUERY_WITH_NON_ZERO_STREAMS);
                        emitBoolean(
                                store,
                                primitivesGeneratedQueryFeaturesEXT,
                                KEY_PRIMITIVES_GENERATED_QUERY_WITH_RASTERIZER_DISCARD);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitFloatControlsPropertiesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
        try {
            JSONObject khrFloatControlsProperties =
                    parent.getJSONObject(KEY_VK_KHR_SHADER_FLOAT_CONTROLS);
            try {
                store.startGroup(getConvertedName(KEY_VK_KHR_SHADER_FLOAT_CONTROLS));
                {
                    JSONObject floatControlsPropertiesKHR =
                            khrFloatControlsProperties.getJSONObject(
                                    KEY_FLOAT_CONTROLS_PROPERTIES_KHR);
                    store.startGroup(getConvertedName(KEY_FLOAT_CONTROLS_PROPERTIES_KHR));
                    {
                        emitLong(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_DENORM_BEHAVIOR_INDEPENDENCE);
                        emitLong(
                                store, floatControlsPropertiesKHR, KEY_ROUNDING_MODE_INDEPENDENCE);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_PRESERVE_FLOAT16);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_PRESERVE_FLOAT32);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_DENORM_PRESERVE_FLOAT64);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32);
                        emitBoolean(
                                store,
                                floatControlsPropertiesKHR,
                                KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitRelaxedLineRasterizationFeaturesIMG(
            DeviceInfoStore store, JSONObject parent) throws Exception {
        try {
            JSONObject imgRelaxedLineRasterizationFeatures =
                    parent.getJSONObject(KEY_VK_IMG_RELAXED_LINE_RASTERIZATION);
            try {
                store.startGroup(getConvertedName(KEY_VK_IMG_RELAXED_LINE_RASTERIZATION));
                {
                    JSONObject relaxedLineRasterizationFeaturesIMG =
                            imgRelaxedLineRasterizationFeatures.getJSONObject(
                                    KEY_RELAXED_LINE_RASTERIZATION_FEATURES_IMG);
                    store.startGroup(getConvertedName(KEY_RELAXED_LINE_RASTERIZATION_FEATURES_IMG));
                    {
                        emitBoolean(
                                store,
                                relaxedLineRasterizationFeaturesIMG,
                                KEY_RELAXED_LINE_RASTERIZATION);
                    }
                    store.endGroup();
                }
                store.endGroup();
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (JSONException ok) {
            // The tag is not present in vkjson; that's fine, just continue
        }
    }

    private static void emitExtension(String key, DeviceInfoStore store, JSONObject parent)
            throws Exception {
        if (!extensionNameToEnum.containsKey(key)) return;
        switch (extensionNameToEnum.get(key)) {
            case ENUM_VK_KHR_VARIABLE_POINTERS:
                emitVariablePointerFeaturesKHR(store, parent);
                break;
            case ENUM_VK_KHR_DRIVER_PROPERTIES:
                emitDriverPropertiesKHR(store, parent);
                break;
            case ENUM_KEY_VK_EXT_CUSTOM_BORDER_COLOR:
                emitCustomBorderColorFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D:
                emitImage2DViewOf3DFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_TRANSFORM_FEEDBACK:
                emitransformFeedbackFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_PRIMITIVE_TOPOLOGY_LIST_RESTART:
                emitPrimitiveTopologyListRestartFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_PROVOKING_VERTEX:
                emitProvokingVertexFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_INDEX_TYPE_UINT8:
                emitIndexTypeUint8FeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_KHR_INDEX_TYPE_UINT8:
                emitIndexTypeUint8FeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_KHR_8BIT_STORAGE:
                emit8bitStorageFeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_KHR_SHADER_FLOAT16_INT8:
                emitShaderFloat16Int8FeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_KHR_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW:
                emitShaderSubgroupUniformControlFlowFeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_KHR_SHADER_SUBGROUP_EXTENDED_TYPES:
                emitShaderSubgroupExtendedTypesFeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_KHR_SHADER_INTEGER_DOT_PRODUCT:
                emitShaderIntegerDotProductFeaturesKHR(store, parent);
                break;
            case ENUM_KEY_VK_EXT_LINE_RASTERIZATION:
                emitLineRasterizationFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_EXT_PRIMITIVES_GENERATED_QUERY:
                emitPrimitivesGeneratedQueryFeaturesEXT(store, parent);
                break;
            case ENUM_KEY_VK_KHR_SHADER_FLOAT_CONTROLS:
                emitFloatControlsPropertiesKHR(store, parent);
                break;
            case ENUM_KEY_VK_IMG_RELAXED_LINE_RASTERIZATION:
                emitRelaxedLineRasterizationFeaturesIMG(store, parent);
                break;
            case ENUM_KEY_VK_KHR_VERTEX_ATTRIBUTE_DIVISOR:
                emitVertexAttributeDivisorFeaturesKHR(store, parent);
                break;
        }
    }

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
