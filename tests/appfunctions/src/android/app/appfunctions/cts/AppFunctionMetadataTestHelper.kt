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
package android.app.appfunctions.cts

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appsearch.GenericDocument

class AppFunctionMetadataTestHelper {

    object FunctionName {
        val ENABLED_BY_DEFAULT =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "appFunctionEnabledByDefault")
        val DISABLED_BY_DEFAULT =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "appFunctionDisabledByDefault_noSchema")
        val HIGH_SCHEMA_VERSION =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "appFunctionWithHighSchemaVersion")

        val DYNAMIC_CONCAT_STRINGS =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextConcatStrings")

        val DYNAMIC_LONG_RUNNING =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextLongRunning")

        val DYNAMIC_OUTPUT_INVALID_ARGUMENT =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextOutputInvalidArgument")

        val DYNAMIC_THROW_UNKNOWN_EXCEPTION =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextThrowUnknownException")

        val DYNAMIC_THROW_INVALID_ARGUMENT =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextThrowInvalidArgument")

        val DYNAMIC_STOP_PROCESS =
            AppFunctionName(TEST_HELPER_DYNAMIC_SCHEMA_PKG, "contextStopProcess")
        val SAME_PACKAGE_THROW_EXCEPTION = AppFunctionName(CURRENT_PKG, "throwException")
        val SAME_PACKAGE_UNCAUGHT_CLIENT_EXCEPTION =
            AppFunctionName(CURRENT_PKG, "uncaughtClientException")
        val SAME_PACKAGE_ADD_INVOKE_CALLBACK_TWICE =
            AppFunctionName(CURRENT_PKG, "add_invokeCallbackTwice")
        val SAME_PACKAGE_DYNAMIC_LONG_RUNNING = AppFunctionName(CURRENT_PKG, "contextLongRunning")
        val SAME_PACKAGE_ADD_ASYNC = AppFunctionName(CURRENT_PKG, "addAsync")
        val SAME_PACKAGE_NOT_INVOKE_CALLBACK = AppFunctionName(CURRENT_PKG, "notInvokeCallback")
        val SAME_PACKAGE_DYNAMIC_CONCAT_STRINGS =
            AppFunctionName(CURRENT_PKG, "contextConcatStrings")
        val SAME_PACKAGE_RUN_FOREVER = AppFunctionName(CURRENT_PKG, "runForever")
        val SAME_PACKAGE_ADD = AppFunctionName(CURRENT_PKG, "add")
        val SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT =
            AppFunctionName(CURRENT_PKG, "add_disabledByDefault")
        val SAME_PACKAGE_NO_OP = AppFunctionName(CURRENT_PKG, "noOp")
        val SAME_PACKAGE_KILL = AppFunctionName(CURRENT_PKG, "kill")
        val SAME_PACKAGE_LONG_RUNNING_FUNCTION = AppFunctionName(CURRENT_PKG, "longRunningFunction")
        val SAME_PACKAGE_NO_SCHEMA = AppFunctionName(CURRENT_PKG, "noSchema")

        val HELPER_PACKAGE_APP_LEVEL_FUNCTIONS: Set<AppFunctionName> = setOf(
            DYNAMIC_CONCAT_STRINGS,
            DYNAMIC_LONG_RUNNING,
            DYNAMIC_OUTPUT_INVALID_ARGUMENT,
            DYNAMIC_THROW_UNKNOWN_EXCEPTION,
            DYNAMIC_THROW_INVALID_ARGUMENT,
            DYNAMIC_STOP_PROCESS,
        )

        val HELPER_PACKAGE_SERVICE_LEVEL_FUNCTIONS: Set<AppFunctionName> = setOf(
            HIGH_SCHEMA_VERSION,
            ENABLED_BY_DEFAULT,
            DISABLED_BY_DEFAULT
        )

        val HELPER_PACKAGE_FUNCTIONS = HELPER_PACKAGE_APP_LEVEL_FUNCTIONS +
                HELPER_PACKAGE_SERVICE_LEVEL_FUNCTIONS
    }

    object PackageMetadata {
        val DYNAMIC_SCHEMA_PACKAGE_METADATA =
            AppFunctionPackageMetadata.create(
                TEST_HELPER_DYNAMIC_SCHEMA_PKG,
                listOf(Components.TOP_LEVEL_COMPONENT_1, Components.TOP_LEVEL_COMPONENT_2),
            )
        val CURRENT_PACKAGE_METADATA = AppFunctionPackageMetadata.create(CURRENT_PKG, listOf())
    }

    object FunctionMetadata {
        val RUNTIME_METADATA =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, TEST_HELPER_DYNAMIC_SCHEMA_PKG)
                .setPropertyLong(
                    AppFunctionRuntimeMetadata.PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong(),
                )
                .build()

        val ENABLED_BY_DEFAULT =
            AppFunctionMetadata.create(
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/appFunctionEnabledByDefault",
                        "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                    )
                    .setPropertyString("packageName", "android.app.appfunctions.cts.dynamic.schema")
                    .setPropertyString("functionId", "appFunctionEnabledByDefault")
                    .setPropertyBoolean(
                        AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                        true,
                    )
                    .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "myUtils")
                    .setPropertyString(PROPERTY_SCHEMA_NAME, "testSchema")
                    .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                    .setPropertyLong("customIntProperty", 255L)
                    .setPropertyDocument(
                        "nestedDocumentProperty",
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "android.app.appfunctions.cts.dynamic.schema/" +
                                        "appFunctionEnabledByDefault/" +
                                        "nestedDocumentProperty",
                                "NestedDocument-android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyString("nestedRepeatedString", "value 1", "value 2")
                            .build(),
                    )
                    .setPropertyString("serviceName", TEST_SERVICE_NAME)
                    .build(),
                RUNTIME_METADATA,
                PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
            )

        val DISABLED_BY_DEFAULT_NO_SCHEMA =
            AppFunctionMetadata.create(
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/" +
                            "appFunctionDisabledByDefault_noSchema",
                        "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                    )
                    .setPropertyBoolean(
                        AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                        false,
                    )
                    .setPropertyString("functionId", "appFunctionDisabledByDefault_noSchema")
                    .setPropertyString("packageName", "android.app.appfunctions.cts.dynamic.schema")
                    .setPropertyString("serviceName", TEST_SERVICE_NAME)
                    .build(),
                RUNTIME_METADATA,
                PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
            )

        val HIGH_SCHEMA_VERSION =
            AppFunctionMetadata.create(
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/appFunctionWithHighSchemaVersion",
                        "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                    )
                    .setPropertyString("functionId", "appFunctionWithHighSchemaVersion")
                    .setPropertyString("packageName", "android.app.appfunctions.cts.dynamic.schema")
                    .setPropertyBoolean(
                        AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                        true,
                    )
                    .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "myUtils")
                    .setPropertyString(PROPERTY_SCHEMA_NAME, "testSchema")
                    .setPropertyLong(PROPERTY_SCHEMA_VERSION, 7L)
                    .setPropertyString("serviceName", TEST_SERVICE_NAME)
                    .build(),
                RUNTIME_METADATA,
                PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
            )

        val SAME_PACKAGE_ENABLED_BY_DEFAULT =
            AppFunctionMetadata.create(
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$CURRENT_PKG/add",
                        "AppFunctionStaticMetadata-android.app.appfunctions.cts",
                    )
                    .setPropertyString("functionId", "add")
                    .setPropertyString("packageName", "android.app.appfunctions.cts")
                    .setPropertyBoolean(
                        AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                        true,
                    )
                    .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "utils")
                    .setPropertyString(
                        PROPERTY_SCHEMA_NAME,
                        "AppFunctionStaticMetadata-android.app.appfunctions.cts",
                    )
                    .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                    .setPropertyLong("displayNameStringRes", 10)
                    .setPropertyBoolean("restrictCallersWithExecuteAppFunctions", true)
                    .setPropertyString("serviceName", TEST_SERVICE_NAME)
                    .build(),
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString(PROPERTY_PACKAGE_NAME, CURRENT_PKG)
                    .setPropertyLong(
                        AppFunctionRuntimeMetadata.PROPERTY_ENABLED,
                        AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong(),
                    )
                    .build(),
                PackageMetadata.CURRENT_PACKAGE_METADATA,
            )

        private const val TEST_SERVICE_NAME =
            "android.app.appfunctions.testutils.TestAppFunctionService"
    }

    object Components {
        val TOP_LEVEL_COMPONENT_1 =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "app_functions",
                    "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/testTopLevelComponentId",
                    "",
                )
                .setPropertyString("customStringProperty", "testValue")
                .build()
        val TOP_LEVEL_COMPONENT_2 =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "app_functions",
                    "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/testTopLevelComponentId2",
                    "",
                )
                .setPropertyDocument(
                    "nestedDocumentProperty",
                    GenericDocument.Builder<GenericDocument.Builder<*>>(
                            "",
                            "$TEST_HELPER_DYNAMIC_SCHEMA_PKG/" +
                                "testTopLevelComponentId2/nestedDocumentProperty",
                            "",
                        )
                        .setPropertyLong("nestedIntProperty", 333L)
                        .setPropertyString("nestedRepeatedString", "value 1", "value 2")
                        .build(),
                )
                .build()
    }

    companion object {
        const val TEST_HELPER_DYNAMIC_SCHEMA_PKG: String =
            "android.app.appfunctions.cts.dynamic.schema"
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"
        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
    }
}
