/**
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

#define LOG_TAG "Bug-486597445"

#include <log/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <system/camera_metadata.h>

/**
 * Redefine internal structures copied from system/media/camera/src/camera_metadata.c
 * to allow direct field access for testing validation bypass.
 */

typedef uint32_t metadata_uptrdiff_t;
typedef uint32_t metadata_size_t;

// 16-byte metadata entry
typedef struct camera_metadata_buffer_entry {
    uint32_t tag;
    uint32_t count;
    union {
        uint32_t offset;
        uint8_t  value[4];
    } data;
    uint8_t  type;
    uint8_t  reserved[3];
} camera_metadata_buffer_entry_t;

// Metadata blob header
struct camera_metadata {
    metadata_size_t          size;
    uint32_t                 version;
    uint32_t                 flags;
    metadata_size_t          entry_count;
    metadata_size_t          entry_capacity;
    metadata_uptrdiff_t      entries_start;
    metadata_size_t          data_count;
    metadata_size_t          data_capacity;
    metadata_uptrdiff_t      data_start;
    uint32_t                 padding;
    metadata_vendor_id_t     vendor_id;
};

// Alignment macros from camera_metadata.c
#define ALIGN_TO(val, alignment) \
    (((uintptr_t)(val) + ((alignment) - 1)) & ~((alignment) - 1))

#define ENTRY_ALIGNMENT ((size_t) 4)
#define DATA_ALIGNMENT  ((size_t) 8)

#define CHECK_EQUAL(expr, expected, case_name) \
    do { \
        int result = (expr); \
        if (result == expected) { \
            ALOGI("[TEST] %s: PASS", case_name); \
        } else { \
            ALOGE("[TEST] %s: FAIL (Expected %d, got %d)", case_name, expected, result); \
            return 113; \
        } \
    } while (0)

// Helper to initialize a valid metadata header in memory
void init_base_metadata(uint8_t* buffer, size_t buffer_size) {
    memset(buffer, 0, buffer_size);
    struct camera_metadata* header = (struct camera_metadata*)buffer;

    header->size = buffer_size;
    header->version = 1;
    header->entry_capacity = 10;
    header->entries_start = ALIGN_TO(sizeof(struct camera_metadata), ENTRY_ALIGNMENT);
    header->data_capacity = 100;

    // Align data region after entries
    size_t data_unaligned = header->entries_start +
                            (header->entry_capacity * sizeof(camera_metadata_buffer_entry_t));
    header->data_start = ALIGN_TO(data_unaligned, DATA_ALIGNMENT);
}

// Verify that a correctly constructed header passes validation
int test_valid_baseline() {
    size_t buffer_size = 512;
    uint8_t buffer[buffer_size];
    init_base_metadata(buffer, buffer_size);

    camera_metadata_t* metadata = (camera_metadata_t*)buffer;
    return validate_camera_metadata_structure(metadata, &buffer_size);
}

/**
 * Reproduce b/486597445 validation bypass where
 * entry_capacity was treated as raw units instead of being multiplied by entry size.
 * Vulnerable check: data_start > entries_start + entry_capacity, and
 *                              < entries_start + (entry_capacity * sizeof(camera_metadata_buffer_entry_t))
 */
int test_entry_capacity_validation() {
    size_t buffer_size = 512;
    uint8_t buffer[buffer_size];
    init_base_metadata(buffer, buffer_size);

    struct camera_metadata* header = (struct camera_metadata*)buffer;

    // sizeof(camera_metadata_buffer_entry_t) is 16.
    metadata_uptrdiff_t raw_data_start = header->entries_start + header->entry_capacity + 7;
    header->data_start = ALIGN_TO(raw_data_start, DATA_ALIGNMENT);

    camera_metadata_t* metadata = (camera_metadata_t*)buffer;
    return validate_camera_metadata_structure(metadata, &buffer_size);
}

// Validation must fail if entry_count > entry_capacity
int test_entry_count_overflow() {
    size_t buffer_size = 512;
    uint8_t buffer[buffer_size];
    init_base_metadata(buffer, buffer_size);

    struct camera_metadata* header = (struct camera_metadata*)buffer;
    header->entry_count = header->entry_capacity + 1;

    camera_metadata_t* metadata = (camera_metadata_t*)buffer;
    return validate_camera_metadata_structure(metadata, &buffer_size);
}

// Validation must fail if data_count > data_capacity
int test_data_count_overflow() {
    size_t buffer_size = 512;
    uint8_t buffer[buffer_size];
    init_base_metadata(buffer, buffer_size);

    struct camera_metadata* header = (struct camera_metadata*)buffer;
    header->data_count = header->data_capacity + 10;

    camera_metadata_t* metadata = (camera_metadata_t*)buffer;
    return validate_camera_metadata_structure(metadata, &buffer_size);
}

// Validation must fail if data region extends beyond buffer size
int test_data_region_overflow() {
    size_t buffer_size = 512;
    uint8_t buffer[buffer_size];
    init_base_metadata(buffer, buffer_size);

    struct camera_metadata* header = (struct camera_metadata*)buffer;
    // Set data capacity to go beyond buffer size
    header->data_capacity = (buffer_size - header->data_start) + 10;

    camera_metadata_t* metadata = (camera_metadata_t*)buffer;
    return validate_camera_metadata_structure(metadata, &buffer_size);
}

// Unaligned buffer must return CAMERA_METADATA_VALIDATION_SHIFTED
int test_unaligned_buffer() {
    size_t buffer_size = 512;
    uint8_t* raw_buffer = (uint8_t*)malloc(buffer_size + 16);
    uintptr_t addr = (uintptr_t)raw_buffer;

    uint8_t* unaligned_buffer = (addr % 8 == 0) ? raw_buffer + 1 : raw_buffer;
    init_base_metadata(unaligned_buffer, buffer_size);

    camera_metadata_t* metadata = (camera_metadata_t*)unaligned_buffer;
    size_t expected_size = buffer_size;
    int result = validate_camera_metadata_structure(metadata, &expected_size);
    free(raw_buffer);
    return result;
}

int main() {
    CHECK_EQUAL(test_valid_baseline(), 0, "Valid Baseline");
    CHECK_EQUAL(test_entry_capacity_validation(), CAMERA_METADATA_VALIDATION_ERROR, "Entry Capacity Validation");
    CHECK_EQUAL(test_entry_count_overflow(), CAMERA_METADATA_VALIDATION_ERROR, "Entry Count Overflow");
    CHECK_EQUAL(test_data_count_overflow(), CAMERA_METADATA_VALIDATION_ERROR, "Data Count Overflow");
    CHECK_EQUAL(test_data_region_overflow(), CAMERA_METADATA_VALIDATION_ERROR, "Data Region Overflow");
    CHECK_EQUAL(test_unaligned_buffer(), CAMERA_METADATA_VALIDATION_SHIFTED, "Unaligned Buffer");

    ALOGI("[TEST] ALL TESTS PASSED");
    return 0;
}
