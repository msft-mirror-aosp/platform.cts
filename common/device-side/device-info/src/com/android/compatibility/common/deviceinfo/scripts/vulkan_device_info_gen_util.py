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

import dataclasses
import inspect
import os
import re
import sys
from typing import get_origin, get_args

#import vk.py
current_script_dir = os.path.dirname(os.path.abspath(__file__))
common_ancestor_dir = os.path.abspath(os.path.join(current_script_dir, '..', '..', '..', '..', '..', '..', '..','..','..','..','..'))
path_to_vk_py_directory = os.path.join(
    common_ancestor_dir,
    "frameworks",
    "native",
    "vulkan",
    "scripts"
)

if path_to_vk_py_directory not in sys.path:
    sys.path.insert(0, path_to_vk_py_directory)

import vk as VK

INDENT = "    "

# These have been named differently in VulkanDeviceInfo
# and we don't want to change the already existing names
special_cases_constant_names ={
    "bit16StorageFeatures": "KEY_BIT16_STORAGE_FEATURES",
    "fullDrawIndexUint32": "KEY_FULL_DRAW_INDEX_UINT32",
    "integerDotProduct16BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_16BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProduct16BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_16BIT_SIGNED_ACCELERATED",
    "integerDotProduct16BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_16BIT_UNSIGNED_ACCELERATED",
    "integerDotProduct32BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_32BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProduct32BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_32BIT_SIGNED_ACCELERATED",
    "integerDotProduct32BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_32BIT_UNSIGNED_ACCELERATED",
    "integerDotProduct4x8BitPackedMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProduct4x8BitPackedSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_SIGNED_ACCELERATED",
    "integerDotProduct4x8BitPackedUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_4X8BIT_PACKED_UNSIGNED_ACCELERATED",
    "integerDotProduct64BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_64BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProduct64BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_64BIT_SIGNED_ACCELERATED",
    "integerDotProduct64BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_64BIT_UNSIGNED_ACCELERATED",
    "integerDotProduct8BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_8BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProduct8BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_8BIT_SIGNED_ACCELERATED",
    "integerDotProduct8BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_8BIT_UNSIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating16BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProductAccumulatingSaturating16BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_SIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating16BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_16BIT_UNSIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating32BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProductAccumulatingSaturating32BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_SIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating32BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_32BIT_UNSIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating4x8BitPackedMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProductAccumulatingSaturating4x8BitPackedSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_SIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating4x8BitPackedUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_4X8BIT_PACKED_UNSIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating64BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProductAccumulatingSaturating64BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_SIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating64BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_64BIT_UNSIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating8BitMixedSignednessAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_MIXED_SIGNEDNESS_ACCELERATED",
    "integerDotProductAccumulatingSaturating8BitSignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_SIGNED_ACCELERATED",
    "integerDotProductAccumulatingSaturating8BitUnsignedAccelerated": "KEY_INTEGER_DOT_PRODUCT_ACCUMULATING_SATURATING_8BIT_UNSIGNED_ACCELERATED",
    "maintenance4": "KEY_MAINTENANCE4",
    "maxImageDimension1D": "KEY_MAX_IMAGE_DIMENSION_1D",
    "maxImageDimension2D": "KEY_MAX_IMAGE_DIMENSION_2D",
    "maxImageDimension3D": "KEY_MAX_IMAGE_DIMENSION_3D",
    "shaderFloat16": "KEY_SHADER_FLOAT16",
    "shaderFloat64": "KEY_SHADER_FLOAT64",
    "shaderDenormFlushToZeroFloat16": "KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT16",
    "shaderDenormFlushToZeroFloat32": "KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT32",
    "shaderDenormFlushToZeroFloat64": "KEY_SHADER_DENORM_FLUSH_TO_ZERO_FLOAT64",
    "shaderDenormPreserveFloat16": "KEY_SHADER_DENORM_PRESERVE_FLOAT16",
    "shaderDenormPreserveFloat32": "KEY_SHADER_DENORM_PRESERVE_FLOAT32",
    "shaderDenormPreserveFloat64": "KEY_SHADER_DENORM_PRESERVE_FLOAT64",
    "residencyStandard2DBlockShape": "KEY_RESIDENCY_STANDARD_2D_BLOCK_SHAPE",
    "residencyStandard2DMultisampleBlockShape": "KEY_RESIDENCY_STANDARD_2D_MULTISAMPLE_BLOCK_SHAPE",
    "residencyStandard3DBlockShape": "KEY_RESIDENCY_STANDARD_3D_BLOCK_SHAPE",
    "shaderBufferInt64Atomics": "KEY_SHADER_BUFFER_INT64_ATOMICS",
    "shaderInt16": "KEY_SHADER_INT16",
    "shaderInt64": "KEY_SHADER_INT64",
    "shaderInt8": "KEY_SHADER_INT8",
    "shaderRoundingModeRTEFloat16": "KEY_SHADER_ROUNDING_MODE_RTE_FLOAT16",
    "shaderRoundingModeRTEFloat32": "KEY_SHADER_ROUNDING_MODE_RTE_FLOAT32",
    "shaderRoundingModeRTEFloat64": "KEY_SHADER_ROUNDING_MODE_RTE_FLOAT64",
    "shaderRoundingModeRTZFloat16": "KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT16",
    "shaderRoundingModeRTZFloat32": "KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT32",
    "shaderRoundingModeRTZFloat64": "KEY_SHADER_ROUNDING_MODE_RTZ_FLOAT64",
    "shaderSignedZeroInfNanPreserveFloat16": "KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT16",
    "shaderSignedZeroInfNanPreserveFloat32": "KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT32",
    "shaderSignedZeroInfNanPreserveFloat64": "KEY_SHADER_SIGNED_ZERO_INF_NAN_PRESERVE_FLOAT64",
    "storageBuffer16BitAccess": "KEY_STORAGE_BUFFER_16BIT_ACCESS",
    "storageBuffer8BitAccess": "KEY_STORAGE_BUFFER_8BIT_ACCESS",
    "storageInputOutput16": "KEY_STORAGE_INPUT_OUTPUT_16",
    "storagePushConstant8": "KEY_STORAGE_PUSH_CONSTANT8",
    "storagePushConstant16": "KEY_STORAGE_PUSH_CONSTANT_16",
    "uniformAndStorageBuffer16BitAccess": "KEY_UNIFORM_AND_STORAGE_BUFFER_16BIT_ACCESS",
    "uniformAndStorageBuffer8BitAccess": "KEY_UNIFORM_AND_STORAGE_BUFFER_8BIT_ACCESS",
    "VK_EXT_image_2d_view_of_3d": "KEY_VK_EXT_IMAGE_2D_VIEW_OF_3D",
    "image2DViewOf3DFeaturesEXT": "KEY_IMAGE_2D_VIEW_OF_3D_FEATURES_EXT",
    "sampler2DViewOf3D": "KEY_SAMPLER_2D_VIEW_OF_3D",
    "image2DViewOf3D": "KEY_IMAGE_2D_VIEW_OF_3D",
    "VK_KHR_shader_float16_int8": "KEY_VK_KHR_SHADER_FLOAT16_INT8 ",
    "shaderFloat16Int8FeaturesKHR": "KEY_SHADER_FLOAT16_INT8_FEATURES_KHR",
    "float16Int8FeaturesKHR": "KEY_FLOAT16_INT8_FEATURES_KHR",
    "VK_KHR_8bit_storage": "KEY_VK_KHR_8BIT_STORAGE",
    "bit8StorageFeaturesKHR": "KEY_BIT8_STORAGE_FEATURES_KHR",
    "bit8StorageFeatures": "KEY_BIT8_STORAGE_FEATURES",
    "indexTypeUint8FeaturesEXT": "KEY_INDEX_TYPE_UINT8_FEATURES_EXT",
    "indexTypeUint8": "KEY_INDEX_TYPE_UINT8",
    "VK_KHR_index_type_uint8": "KEY_VK_KHR_INDEX_TYPE_UINT8",
    "indexTypeUint8FeaturesKHR": "KEY_INDEX_TYPE_UINT8_FEATURES_KHR",
    "core11": "KEY_CORE11",
    "core12": "KEY_CORE12",
    "core13": "KEY_CORE13",
    "core14": "KEY_CORE14",
    "shaderFloatControls2": "KEY_SHADER_FLOAT_CONTROLS2",
    "maintenance5": "KEY_MAINTENANCE5",
    "maintenance6": "KEY_MAINTENANCE6",
    "maintenance5": "KEY_MAINTENANCE5",
    "maintenance6": "KEY_MAINTENANCE6",
    "shaderSharedInt64Atomics": "KEY_SHADER_SHARED_INT64_ATOMICS",
    "sparseResidencyImage2D": "KEY_SPARSE_RESIDENCY_IMAGE_2D",
    "sparseResidencyImage3D": "KEY_SPARSE_RESIDENCY_IMAGE_3D",
    "synchronization2": "KEY_SYNCHRONIZATION2",
    "VK_EXT_index_type_uint8": "KEY_VK_EXT_INDEX_TYPE_UINT8",
    "textureCompressionETC2": "KEY_TEXTURE_COMPRESSION_ETC2",
    "shaderUniformBufferArrayNonUniformIndexingNative" :"KEY_SHADER_UNIFORM_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE",
    "shaderSampledImageArrayNonUniformIndexingNative": "KEY_SHADER_SAMPLED_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE",
    "shaderStorageBufferArrayNonUniformIndexingNative": "KEY_SHADER_STORAGE_BUFFER_ARRAY_NONUNIFORM_INDEXING_NATIVE",
    "shaderStorageImageArrayNonUniformIndexingNative": "KEY_SHADER_STORAGE_IMAGE_ARRAY_NONUNIFORM_INDEXING_NATIVE",
    "shaderInputAttachmentArrayNonUniformIndexingNative": "KEY_SHADER_INPUT_ATTACHMENT_ARRAY_NONUNIFORM_INDEXING_NATIVE"
}

# Modified vkjson fields
modified_vkjson_field = {
    "iDProperties": "idProperties",
    "formatProperties": "formats",
    "queueFamilyProperties": "queues",
    "layerProperties": "layers",
    "memoryProperties": "memory",
    "extensionProperties": "extensions",
    "textureCompressionASTCHDRFeatures": "textureCompressionAstchdrFeatures"
}

# What datatype in vk.py will be mapped to which emit method in VulkanDeviceInfo
datatypes_map = {
    "c_ubyte": "Long",
    "c_uint_Array_3": "LongArray",
    "Enum": "Long",
    "c_ubyte_Array_16": "LongArray",
    "c_ubyte_Array_8": "LongArray",
    "bool": "Boolean",
    "c_float_Array_2": "DoubleArray",
    "c_ulong": "String",
    "c_uint": "Long",
    "c_uint_Array_2": "LongArray",
    "str": "String",
    "int": "Long",
    "List": "LongArray",
    "float": "Double"
}

extension_types = ["EXT", "KHR", "IMG", "VALVE", "SEC", "QCOM", "NV", "NVX", "MSFT", "MESA", "INTEL", "HUAWEI", "ARM", "AMD", "ANDROID"]

def get_copyright_warnings(year):
    """Returns the standard copyright and warning codes.

    Args:
    year: An integer year for the copyright.
    """
    return """\
/*
 * Copyright """ + str(year) + """ The Android Open Source Project
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

// This file is autogenerated by vulkan_device_info_gen.py. Do not edit directly.
"""

def convert_camel_to_snake(camel_case_name):
    """
    Converts a camelCase string to snake_case, adding underscores

    Examples:
    "alphaToOne"                   -> "alpha_to_one"
    "vulkan11Features"             -> "vulkan_11_features"
    "shaderRoundingModeRTEFloat16" -> "shader_rounding_mode_rte_float_16"
    """
    if not camel_case_name:
        return ""

    # Insert an underscore between a lowercase letter/digit and an uppercase letter.
    s1 = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', camel_case_name)

    # Insert an underscore between an uppercase letter and an uppercase letter
    # followed by a lowercase letter.
    s2 = re.sub(r'([A-Z])([A-Z][a-z])', r'\1_\2', s1)

    # Insert an underscore between a letter and a digit.
    s3 = re.sub(r'([a-zA-Z])([0-9])', r'\1_\2', s2)

    # Insert an underscore between a digit and a letter.
    s4 = re.sub(r'([0-9])([a-zA-Z])', r'\1_\2', s3)

    return s4.lower()

def get_struct_obj(attribute_name):
    """
    Gets the struct corresponding to a given attribute name from the 'VK' module,
    only if the attribute is a class. Returns None otherwise.
    """
    try:
        attribute = getattr(VK, attribute_name)
        if inspect.isclass(attribute) and dataclasses.is_dataclass(attribute):
            return attribute
        else:
            return None
    except AttributeError:
        return None

def lowercase_first_letter(input_string):
    """
    Converts the first letter of a string to lowercase.
    """
    if not input_string:
        return input_string
    return input_string[0].lower() + input_string[1:]

def convert_vkpy_name_to_vkjson(field_name):
    """
    Modifies the field_name by removing some prefixes, handling leading digits,
    converting the first char to lowercase and finally handling special cases
    """
    field_name = field_name.removeprefix("VkPhysicalDevice").removeprefix("Vk")

    # Move the leading digits after the first capitalized word
    field_name = re.sub(r"^(\d+)([A-Z][a-z]*)", r"\2\1", field_name)

    # Lowercase the first character
    field_name = lowercase_first_letter(field_name)

    if field_name in modified_vkjson_field:
        field_name = modified_vkjson_field[field_name]

    return field_name

def get_json_object_name_from_key(constant_name):
    """
    Converts a constant name string (e.g., "KEY_FORMATS_4444_FEATURES_EXT")
    to a camelCase JSON object name (e.g., "formats4444FeaturesEXT").
    """

    suffix_str = None
    for ext in extension_types:
        if constant_name.endswith("_" + ext):
            suffix_str = ext
            break

    snake_case_part = constant_name.removeprefix("KEY_")

    if suffix_str:
        snake_case_part = snake_case_part.removesuffix("_" + suffix_str)

    components = snake_case_part.split('_')
    camel_case_name = components[0].lower() + ''.join(word.title() for word in components[1:])

    if suffix_str:
        camel_case_name = camel_case_name + suffix_str

    return camel_case_name

def get_constants_map():
    """
    Returns a map of all constants names and values in form of key-value pairs.
    """
    constants_map = {
        "KEY_DEVICE_GROUPS": "deviceGroups",
        "KEY_DEVICES": "devices",
        "KEY_SUBSET_ALLOCATION": "subsetAllocation",
        "KEY_EXTERNAL_FENCE_FEATURES": "externalFenceFeatures",
        "KEY_EXTERNAL_FENCE_PROPERTIES": "externalFenceProperties",
        "KEY_EXTERNAL_SEMAPHORE_FEATURES": "externalSemaphoreFeatures",
        "KEY_EXTERNAL_SEMAPHORE_PROPERTIES": "externalSemaphoreProperties",
        "KEY_INSTANCE_API_VERSION": "instanceApiVersion",
        "KEY_COMPATIBLE_HANDLE_TYPES": "compatibleHandleTypes",
        "KEY_EXPORT_FROM_IMPORTED_HANDLE_TYPES": "exportFromImportedHandleTypes"
    }

    for struct in VK.ALL_STRUCTS_EXTENDING_FEATURES_OR_PROPERTIES:
        add_struct_members_in_constants_map(struct, constants_map)

    for struct_name in VK.EXTENSION_INDEPENDENT_STRUCTS:
        vkjson_field = convert_vkpy_name_to_vkjson(struct_name)

        constant_name = "KEY_" + convert_camel_to_snake(vkjson_field).upper()

        if vkjson_field in special_cases_constant_names:
            constant_name = special_cases_constant_names[vkjson_field]

        if constant_name not in constants_map:
            constants_map[constant_name] = vkjson_field

    for struct_obj in VK.VULKAN_API_1_0_STRUCTS:
        add_struct_members_in_constants_map(struct_obj, constants_map)

    # Get keys from extensions:
    for extension_name, structs in VK.VULKAN_EXTENSIONS_AND_STRUCTS_MAPPING["extensions"].items():
        constant_name = "KEY_" + extension_name.upper()
        constants_map[constant_name] = extension_name

        for struct_dict in structs:
            for struct in struct_dict.keys():
                vkjson_field = convert_vkpy_name_to_vkjson(struct)
                constant_name = "KEY_" + convert_camel_to_snake(vkjson_field).upper()
                if vkjson_field in special_cases_constant_names:
                    constant_name = special_cases_constant_names[vkjson_field]
                if constant_name not in constants_map:
                    constants_map[constant_name] = vkjson_field

    # Add vulkan_cores
    # e.g: "KEY_CORE11" : "core11"
    for vulkan_core in VK.VULKAN_CORES_AND_STRUCTS_MAPPING["versions"].keys():
        constant_name = "KEY_" + vulkan_core.upper()
        key_val = lowercase_first_letter(vulkan_core)
        constants_map[constant_name] = key_val

    return constants_map

def add_struct_members_in_constants_map(struct_obj, constants_map):
    """
    Adds struct and its members in constants_map
    """
    struct_name = struct_obj.__name__

    vkjson_field = convert_vkpy_name_to_vkjson(struct_name)

    constant_name = "KEY_" + convert_camel_to_snake(vkjson_field).upper()

    if vkjson_field in special_cases_constant_names:
        constant_name = special_cases_constant_names[vkjson_field]

    if constant_name not in constants_map:
        constants_map[constant_name] = vkjson_field

    struct_fields = dataclasses.fields(struct_obj)
    for field in struct_fields:
        field_type = field.type

        # Member variable is of also a struct
        if dataclasses.is_dataclass(field_type):
            add_struct_members_in_constants_map(field_type, constants_map)

        vkjson_field = convert_vkpy_name_to_vkjson(field.name)

        constant_name = "KEY_" + convert_camel_to_snake(vkjson_field).upper()
        if vkjson_field in special_cases_constant_names:
            constant_name = special_cases_constant_names[vkjson_field]

        if constant_name not in constants_map:
            constants_map[constant_name] = vkjson_field

        # If field type is list of one of vkjson struct
        if get_origin(field_type) is list and dataclasses.is_dataclass(get_args(field_type)[0]):
            add_struct_members_in_constants_map(get_args(field_type)[0], constants_map)

def generate_constants():
    """
    Generates all the string constants and VK_API_VERSION constants
    public static final String KEY_VULKAN_MEMORY_MODEL = "vulkanMemoryModel";
    public static final int VK_API_VERSION_1_1 = 4198400;
    """
    template_code = []

    constants_map = get_constants_map()

    sorted_keys_list = sorted(constants_map)

    for key in sorted_keys_list:
        val = constants_map[key]
        template_code.append(INDENT + "public static final String "+ key + " = \"" + val + "\";")

    for version_name, version in VK.VK_API_VERSION_MAP.items():
        template_code.append(INDENT + "public static final int "+ version_name + " = " + str(version) + ";")

    return "\n".join(template_code)

def generate_vk_dependent_groups(vk_version):
    """ Generates group for structs depending on vulkan versions
    """
    dependent_classes = []
    for item in VK.VULKAN_VERSIONS_AND_STRUCTS_MAPPING[vk_version]:
        for key in item.keys():
            dependent_class_lines = create_group(key)
            for line in dependent_class_lines:
                dependent_classes.append(INDENT*5 + line)
            dependent_classes.append("\n")
    return "\n".join(dependent_classes)


def start_vulkan_group(struct_type, version):
    """ Generates vulkan properties and features
        for a particular vulkan version

        struct_type can be "features" or "properties"
    """
    vulkan_group = [""]
    vulkan_group.append(INDENT*6 + "JSONObject core"+version+" = device.getJSONObject(KEY_CORE"+ version+");")
    vulkan_group.append(INDENT*6 + "JSONObject vulkan"+version+ struct_type + " = core"+ version +".getJSONObject(KEY_"+struct_type.upper()+");")
    vulkan_group.append(INDENT*6 + "store.startGroup(getConvertedName(KEY_VULKAN_"+version+"_" + struct_type.upper() + "));")
    vulkan_group.append(INDENT*6 + "{")

    obj_name = "vulkan" + version + struct_type.capitalize()
    struct_name = "VkPhysicalDeviceVulkan" + version + struct_type.capitalize()
    vulkan_members = emit_members(obj_name, struct_name)
    for member in vulkan_members:
        vulkan_group.append(INDENT*7 + member)
    vulkan_group.append(INDENT*6 + "}")
    vulkan_group.append(INDENT*6 + "store.endGroup();")
    return "\n".join(vulkan_group)

def emit_vk_features_properties(struct_type):
    """ Calls start_vulkan_group method for features and properties
        Of each Vulkan version.
    """
    vulkan_code = []

    vulkan_versions = []

    for core in VK.VULKAN_CORES_AND_STRUCTS_MAPPING["versions"].keys():
        vulkan_versions.append(core.lstrip("Core"))

    #print(len(vulkan_versions))
    for version in vulkan_versions[1:]:
        vulkan_code.append("\n")
        vulkan_code.append(INDENT*5 + "if (properties.getLong(KEY_API_VERSION) >= VK_API_VERSION_" + version[0] + "_"+ version[1]+") {")
        if version == '12':
            vulkan_code.append(start_vulkan_group(struct_type, vulkan_versions[0]))
        vulkan_code.append(start_vulkan_group(struct_type, version))
        vulkan_code.append(INDENT*5 + "}")
    return "\n".join(vulkan_code)

def create_groups_with_indentation(struct_name,parent_obj_name="device", struct_type = None):
    """
    Takes the output of create_groups method and joins that into a string with proper indentation
    """
    groups = create_group(struct_name,parent_obj_name,struct_type)
    return "\n                ".join(groups)

def create_group(struct_name, parent_obj_name = "device", struct_type = None, obj_name = None):
    """
    Generates group for a class

    Example:
    struct_name: "VkPhysicalDeviceSubgroupProperties"

    Output:
    ["JSONObject pointClippingProperties = device.getJSONObject(KEY_POINT_CLIPPING_PROPERTIES);",
    "store.startGroup(getConvertedName(KEY_POINT_CLIPPING_PROPERTIES));",
    "{",
    "    emitBoolean(store, pointClippingProperties, KEY_POINT_CLIPPING_BEHAVIOR);",
    "}",
    "store.endGroup();"]

    """
    group = []
    constant_name = "KEY_" + convert_camel_to_snake(convert_vkpy_name_to_vkjson(struct_name)).upper()
    if obj_name:
        constant_name = "KEY_" + convert_camel_to_snake(obj_name).upper()
    else:
        obj_name = get_json_object_name_from_key(constant_name)

    if obj_name in special_cases_constant_names:
        constant_name = special_cases_constant_names[obj_name]

    group.append("JSONObject "+obj_name+" = " + parent_obj_name + ".getJSONObject("+ constant_name +");")
    group.append("store.startGroup(getConvertedName(" + constant_name +"));")
    group.append("{")

    struct_class = get_struct_obj(struct_name)

    struct_fields = dataclasses.fields(struct_class)
    for struct_field in struct_fields:
        field_name = struct_field.name
        field_type = struct_field.type.__name__
        datatype = "Long"
        if field_type in datatypes_map:
            datatype = datatypes_map[field_type]

        struct_name = get_struct_obj(field_type)
        if get_struct_obj(field_type):
            class_group_lines = create_group(str(field_type), obj_name,None, field_name)
            for line in class_group_lines:
                group.append(INDENT + line)
        else:
            group.append(INDENT + emit_member(obj_name,field_name,datatype))
    if struct_type is not None:
        group.append(emit_vk_features_properties(struct_type))
    group.append("}")
    group.append("store.endGroup();")

    return group

def generate_emit_methods():
    """
    Generates the emit for all the extensions.

    example:

    private static void emitVariablePointerFeaturesKHR(DeviceInfoStore store, JSONObject parent)
            throws Exception {
            ...
    }

    It takes the method name from the first struct of an extension.
    """
    emit_method = []
    for extension_name, structs in VK.VULKAN_EXTENSIONS_AND_STRUCTS_MAPPING["extensions"].items():
        flag = True  #To handle multiple structs in a single extension
        obj_name = ""
        for struct_dict in structs:
            if len(struct_dict) > 1:
                continue
            for key in struct_dict.keys():
                key_val = convert_vkpy_name_to_vkjson(key)
                prefix_str = None
                for ext in extension_types:
                    if ext in key:
                        prefix_str = ext.lower()
                suffix_str = None
                for ext in extension_types:
                    if ext in key:
                        suffix_str = ext
                struct_class = get_struct_obj(key)
                struct_class_name = struct_class.__name__.removeprefix("VkPhysicalDevice").removeprefix("Vk")
                for ext_type in extension_types:
                    struct_class_name = struct_class_name.removesuffix(ext_type)
                method_name = "emit" + struct_class_name +suffix_str
                if flag:
                    flag = False
                    obj_name = prefix_str + struct_class_name
                    emit_method.append(INDENT + "private static void "+ method_name + "(DeviceInfoStore store, JSONObject parent)\n")
                    emit_method.append(INDENT*4 + "throws IOException, JSONException {")
                    emit_method.append(INDENT*2 + "JSONObject " + obj_name + " = parent.optJSONObject(KEY_"+ extension_name.upper()+");")
                    emit_method.append(INDENT*2 + "if (" + obj_name + " != null) {")
                    emit_method.append(INDENT*3 + "store.startGroup(getConvertedName(KEY_"+extension_name.upper()+"));")
                    emit_method.append(INDENT*3 + "{")

                constant_name = "KEY_" +  convert_camel_to_snake(key_val).upper()
                if key_val in special_cases_constant_names:
                    constant_name = special_cases_constant_names[key_val]

                inner_object_name = get_json_object_name_from_key(constant_name)
                emit_method.append(INDENT*4 + "JSONObject "+inner_object_name + " = " + obj_name + ".getJSONObject(" + constant_name +");")
                emit_method.append(INDENT*4 + "store.startGroup(getConvertedName(" + constant_name + "));" )
                emit_method.append(INDENT*4 + "{")
                method_members = emit_members(inner_object_name, key)
                for member in method_members:
                    emit_method.append(INDENT * 5 + member)
                emit_method.append("                }\n                store.endGroup();")
        emit_method.append("""            }
            store.endGroup();
        }
    }
""")
    return "\n".join(emit_method)

def generate_emit_extensions():
    """
    Generates the emitExtension method of VulkanDeviceInfo
    which is responsible for calling the emit method for
    an extension based on  switch-case expressions.
    """
    emit_extension = [""]
    emit_extension.append("""
    private static void emitExtension(String key, DeviceInfoStore store, JSONObject parent)
            throws IOException, JSONException {
        switch (key) {
""")
    for extension_name, structs in VK.VULKAN_EXTENSIONS_AND_STRUCTS_MAPPING["extensions"].items():
        flag = True  #To handle multiple structs in a single extension
        for struct_dict in structs:
            for key in struct_dict.keys():
                suffix_str = None
                for ext in extension_types:
                    if ext in key:
                        suffix_str = ext
                struct_class = get_struct_obj(key)
                struct_class_name = struct_class.__name__.removeprefix("VkPhysicalDevice").removeprefix("Vk")
                for ext_type in extension_types:
                    struct_class_name = struct_class_name.removesuffix(ext_type)
                emit_extension.append(INDENT*3 + "case KEY_" + extension_name.upper() + ":\n                emit" + struct_class_name + suffix_str + "(store, parent);")
                emit_extension.append(INDENT*4 + "break;")
                break
            break
    emit_extension.append("""
        }
    }
""")
    return "\n".join(emit_extension)

def emit_member(obj_name, var_name, datatype):
    """
    Generates emit method call for a particular member variable
    emitLong(store, limits, KEY_MAX_IMAGE_DIMENSION_1D);
    """
    constant_name = "KEY_" + convert_camel_to_snake(var_name).upper()
    if var_name in special_cases_constant_names:
        constant_name = special_cases_constant_names[var_name]
    member = "emit" + datatype + "(store, " + obj_name + ", " + constant_name + ");"

    if constant_name ==  'KEY_SUBGROUP_BROADCAST_DYNAMIC_ID':
            member = f"""// subgroupBroadcastDynamicId was erroneously left out of vkjson reporting in Android T
                            //   and later added in U, so we need to explicitly check if the feature is reported
                            if (vulkan12Features.has(KEY_SUBGROUP_BROADCAST_DYNAMIC_ID)) {{
                                {member}
                            }}"""
    return member

def emit_members(obj_name, struct_name):
    """
    Generates emit method calls for all the members of a struct
    """
    members = []
    struct_class = get_struct_obj(struct_name)
    struct_fields = dataclasses.fields(struct_class)
    for struct_field in struct_fields:
        field_name = struct_field.name
        field_type = struct_field.type.__name__
        datatype = "Long"
        if field_type in datatypes_map:
            datatype = datatypes_map[field_type]

        # The member itself is a struct
        if(get_struct_obj(field_type)):
            class_group_lines = create_group(str(field_type),obj_name, None, field_name)
            for line in class_group_lines:
                members.append(line)
        else :
            members.append(emit_member(obj_name, field_name, datatype,))
    return members
