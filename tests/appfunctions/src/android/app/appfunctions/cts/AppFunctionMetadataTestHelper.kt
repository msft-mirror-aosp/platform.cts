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

import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appsearch.GenericDocument

class AppFunctionMetadataTestHelper {
    object LegacySchemaHelperApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.helper"

        object FunctionNames {
            val ADD_ENABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "add")
            val ADD_DISABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "add_disabledByDefault")
            val NO_OP = AppFunctionName(PACKAGE_NAME, "noOp")
            val RESTRICT_CALLER_FALSE =
                AppFunctionName(PACKAGE_NAME, "addWithRestrictCallersWithExecuteAppFunctionsFalse")
            val RESTRICT_CALLER_TRUE =
                AppFunctionName(PACKAGE_NAME, "addWithRestrictCallersWithExecuteAppFunctionsTrue")
            val GET_URIS = AppFunctionName(PACKAGE_NAME, "getUris")
            val ECHO_BYTES = AppFunctionName(PACKAGE_NAME, "echoBytes")

            val ALL_FUNCTIONS =
                setOf(
                    ADD_ENABLED_BY_DEFAULT,
                    ADD_DISABLED_BY_DEFAULT,
                    NO_OP,
                    RESTRICT_CALLER_FALSE,
                    RESTRICT_CALLER_TRUE,
                    GET_URIS,
                    ECHO_BYTES,
                )
        }

        object FunctionMetadata {
            val ADD_ENABLED_BY_DEFAULT =
                AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/add",
                                "AppFunctionStaticMetadata-$PACKAGE_NAME",
                            )
                            .setPropertyString("packageName", PACKAGE_NAME)
                            .setPropertyString("functionId", "add")
                            .setPropertyBoolean(
                                AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                                true,
                            )
                            .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "utils")
                            .setPropertyString(
                                PROPERTY_SCHEMA_NAME,
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts.helper",
                            )
                            .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                            .setPropertyLong("displayNameStringRes", 10)
                            .setPropertyBoolean("restrictCallersWithExecuteAppFunctions", true)
                            .setPropertyString("serviceName", TEST_SERVICE_NAME)
                            .build(),
                        AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf()),
                    )
                    .setEnabled(true)
                    .build()

            val ADD_DISABLED_BY_DEFAULT =
                AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/add_disabledByDefault",
                                "AppFunctionStaticMetadata-$PACKAGE_NAME",
                            )
                            .setPropertyString("packageName", PACKAGE_NAME)
                            .setPropertyString("functionId", "add_disabledByDefault")
                            .setPropertyBoolean(
                                AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                                false,
                            )
                            .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "utils")
                            .setPropertyString(
                                PROPERTY_SCHEMA_NAME,
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts.helper",
                            )
                            .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                            .setPropertyLong("displayNameStringRes", 10)
                            .setPropertyBoolean("restrictCallersWithExecuteAppFunctions", true)
                            .setPropertyString("serviceName", TEST_SERVICE_NAME)
                            .build(),
                        AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf()),
                    )
                    .setEnabled(false)
                    .build()
        }
    }

    object DynamicSchemaHelperApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.dynamic.schema"

        object FunctionNames {
            val ENABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "appFunctionEnabledByDefault")
            val DISABLED_BY_DEFAULT =
                AppFunctionName(PACKAGE_NAME, "appFunctionDisabledByDefault_noSchema")
            val HIGH_SCHEMA_VERSION =
                AppFunctionName(PACKAGE_NAME, "appFunctionWithHighSchemaVersion")

            val DYNAMIC_CONCAT_STRINGS = AppFunctionName(PACKAGE_NAME, "contextConcatStrings")

            val DYNAMIC_LONG_RUNNING = AppFunctionName(PACKAGE_NAME, "contextLongRunning")

            val DYNAMIC_OUTPUT_INVALID_ARGUMENT =
                AppFunctionName(PACKAGE_NAME, "contextOutputInvalidArgument")

            val DYNAMIC_THROW_UNKNOWN_EXCEPTION =
                AppFunctionName(PACKAGE_NAME, "contextThrowUnknownException")

            val DYNAMIC_THROW_INVALID_ARGUMENT =
                AppFunctionName(PACKAGE_NAME, "contextThrowInvalidArgument")

            val DYNAMIC_STOP_PROCESS = AppFunctionName(PACKAGE_NAME, "contextStopProcess")
            val DYNAMIC_GET_URIS = AppFunctionName(PACKAGE_NAME, "contextGetUris")

            val APP_LEVEL_FUNCTIONS: Set<AppFunctionName> =
                setOf(
                    DYNAMIC_CONCAT_STRINGS,
                    DYNAMIC_LONG_RUNNING,
                    DYNAMIC_OUTPUT_INVALID_ARGUMENT,
                    DYNAMIC_THROW_UNKNOWN_EXCEPTION,
                    DYNAMIC_THROW_INVALID_ARGUMENT,
                    DYNAMIC_STOP_PROCESS,
                    DYNAMIC_STOP_PROCESS,
                    DYNAMIC_GET_URIS,
                )

            val SERVICE_LEVEL_FUNCTIONS: Set<AppFunctionName> =
                setOf(HIGH_SCHEMA_VERSION, ENABLED_BY_DEFAULT, DISABLED_BY_DEFAULT)

            val ALL_FUNCTIONS = APP_LEVEL_FUNCTIONS + SERVICE_LEVEL_FUNCTIONS
        }

        object Components {
            val TOP_LEVEL_COMPONENT_1 =
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$PACKAGE_NAME/testTopLevelComponentId",
                        "CustomTopLevelComponent1-android.app.appfunctions.cts.dynamic.schema",
                    )
                    .setPropertyString("packageName", "android.app.appfunctions.cts.dynamic.schema")
                    .setPropertyString("customStringProperty", "testValue")
                    .build()
            val TOP_LEVEL_COMPONENT_2 =
                GenericDocument.Builder<GenericDocument.Builder<*>>(
                        "app_functions",
                        "$PACKAGE_NAME/testTopLevelComponentId2",
                        "CustomTopLevelComponent2-android.app.appfunctions.cts.dynamic.schema",
                    )
                    .setPropertyString("packageName", "android.app.appfunctions.cts.dynamic.schema")
                    .setPropertyDocument(
                        "nestedDocumentProperty",
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/" +
                                    "testTopLevelComponentId2/nestedDocumentProperty",
                                "NestedDocument-android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyLong("nestedIntProperty", 333L)
                            .setPropertyString("nestedRepeatedString", "value 1", "value 2")
                            .build(),
                    )
                    .build()
        }

        object FunctionMetadata {
            val ENABLED_BY_DEFAULT =
                android.app.appfunctions.AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/appFunctionEnabledByDefault",
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyString(
                                "packageName",
                                "android.app.appfunctions.cts.dynamic.schema",
                            )
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
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .setEnabled(true)
                    .build()

            val DISABLED_BY_DEFAULT_NO_SCHEMA =
                android.app.appfunctions.AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/" + "appFunctionDisabledByDefault_noSchema",
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyBoolean(
                                AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                                false,
                            )
                            .setPropertyString(
                                "functionId",
                                "appFunctionDisabledByDefault_noSchema",
                            )
                            .setPropertyString(
                                "packageName",
                                "android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyString("serviceName", TEST_SERVICE_NAME)
                            .build(),
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .setEnabled(false)
                    .build()

            val HIGH_SCHEMA_VERSION =
                android.app.appfunctions.AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/appFunctionWithHighSchemaVersion",
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyString("functionId", "appFunctionWithHighSchemaVersion")
                            .setPropertyString(
                                "packageName",
                                "android.app.appfunctions.cts.dynamic.schema",
                            )
                            .setPropertyBoolean(
                                AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                                true,
                            )
                            .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "myUtils")
                            .setPropertyString(PROPERTY_SCHEMA_NAME, "testSchema")
                            .setPropertyLong(PROPERTY_SCHEMA_VERSION, 7L)
                            .setPropertyString("serviceName", TEST_SERVICE_NAME)
                            .build(),
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .setEnabled(true)
                    .build()
        }

        object PackageMetadata {
            val DYNAMIC_SCHEMA_PACKAGE_METADATA =
                AppFunctionPackageMetadata.create(
                    PACKAGE_NAME,
                    listOf(Components.TOP_LEVEL_COMPONENT_2, Components.TOP_LEVEL_COMPONENT_1),
                )
        }
    }

    object CtsApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts"

        object FunctionNames {
            val THROW_EXCEPTION = AppFunctionName(PACKAGE_NAME, "throwException")
            val UNCAUGHT_CLIENT_EXCEPTION = AppFunctionName(PACKAGE_NAME, "uncaughtClientException")
            val ADD_INVOKE_CALLBACK_TWICE = AppFunctionName(PACKAGE_NAME, "add_invokeCallbackTwice")
            val DYNAMIC_LONG_RUNNING = AppFunctionName(PACKAGE_NAME, "contextLongRunning")
            val ADD_ASYNC = AppFunctionName(PACKAGE_NAME, "addAsync")
            val NOT_INVOKE_CALLBACK = AppFunctionName(PACKAGE_NAME, "notInvokeCallback")
            val DYNAMIC_CONCAT_STRINGS = AppFunctionName(PACKAGE_NAME, "contextConcatStrings")
            val RUN_FOREVER = AppFunctionName(PACKAGE_NAME, "runForever")
            val ADD = AppFunctionName(PACKAGE_NAME, "add")
            val ADD_DISABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "add_disabledByDefault")
            val NO_OP = AppFunctionName(PACKAGE_NAME, "noOp")
            val KILL = AppFunctionName(PACKAGE_NAME, "kill")
            val LONG_RUNNING_FUNCTION = AppFunctionName(PACKAGE_NAME, "longRunningFunction")
            val NO_SCHEMA = AppFunctionName(PACKAGE_NAME, "noSchema")
            val CONTEXT = AppFunctionName(PACKAGE_NAME, "contextDisabledByDefault")
        }

        object FunctionMetadata {
            val ENABLED_BY_DEFAULT =
                AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/add",
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
                        PackageMetadata.PACKAGE_METADATA,
                    )
                    .setEnabled(true)
                    .build()
        }

        object PackageMetadata {
            val PACKAGE_METADATA = AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf())
        }
    }

    companion object {
        private const val TEST_SERVICE_NAME =
            "android.app.appfunctions.testutils.TestAppFunctionService"
    }
}
