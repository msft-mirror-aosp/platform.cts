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
import android.app.appfunctions.cts.AppFunctionUtils.TestAllowlistPackage
import android.app.appsearch.GenericDocument
import kotlin.collections.minus

class AppFunctionMetadataTestHelper {
    object LegacySchemaHelperApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.helper"
        const val APK_PATH = TEST_APP_ROOT_FOLDER + "CtsAppFunctionTestHelper.apk"
        const val CERTIFICATE = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"

        val TEST_ALLOWLIST_PACKAGE = TestAllowlistPackage(PACKAGE_NAME, CERTIFICATE)

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
            val UNCAUGHT_CLIENT_EXCEPTION = AppFunctionName(PACKAGE_NAME, "uncaughtClientException")
            val ADD_INVOKE_CALLBACK_TWICE = AppFunctionName(PACKAGE_NAME, "add_invokeCallbackTwice")
            val KILL = AppFunctionName(PACKAGE_NAME, "kill")
            val ADD_ASYNC = AppFunctionName(PACKAGE_NAME, "addAsync")
            val THROW_EXCEPTION = AppFunctionName(PACKAGE_NAME, "throwException")
            val LONG_RUNNING_FUNCTION = AppFunctionName(PACKAGE_NAME, "longRunningFunction")

            val ALL_FUNCTIONS =
                setOf(
                    ADD_ENABLED_BY_DEFAULT,
                    ADD_DISABLED_BY_DEFAULT,
                    NO_OP,
                    RESTRICT_CALLER_FALSE,
                    RESTRICT_CALLER_TRUE,
                    GET_URIS,
                    ECHO_BYTES,
                    UNCAUGHT_CLIENT_EXCEPTION,
                    ADD_INVOKE_CALLBACK_TWICE,
                    KILL,
                    ADD_ASYNC,
                    THROW_EXCEPTION,
                    LONG_RUNNING_FUNCTION,
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
                            .setPropertyString("scope", "global")
                            .build(),
                        AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf()),
                    )
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
                            .setPropertyString("scope", "global")
                            .build(),
                        AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf()),
                    )
                    .build()
        }
    }

    object DynamicSchemaHelperApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.dynamic.schema"
        const val CERTIFICATE = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"

        val TEST_ALLOWLIST_PACKAGE = TestAllowlistPackage(PACKAGE_NAME, CERTIFICATE)

        object FunctionNames {
            val ENABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "appFunctionEnabledByDefault")
            val DISABLED_BY_DEFAULT =
                AppFunctionName(PACKAGE_NAME, "appFunctionDisabledByDefault_noSchema")
            val HIGH_SCHEMA_VERSION =
                AppFunctionName(PACKAGE_NAME, "appFunctionWithHighSchemaVersion")

            val DYNAMIC_CONCAT_STRINGS = AppFunctionName(PACKAGE_NAME, "contextConcatStrings")

            val DYNAMIC_ACTIVITY_CONCAT_STRINGS =
                AppFunctionName(PACKAGE_NAME, "activityConcatStrings")

            val DYNAMIC_LONG_RUNNING = AppFunctionName(PACKAGE_NAME, "contextLongRunning")

            val DYNAMIC_OUTPUT_INVALID_ARGUMENT =
                AppFunctionName(PACKAGE_NAME, "contextOutputInvalidArgument")

            val DYNAMIC_THROW_UNKNOWN_EXCEPTION =
                AppFunctionName(PACKAGE_NAME, "contextThrowUnknownException")

            val DYNAMIC_THROW_INVALID_ARGUMENT =
                AppFunctionName(PACKAGE_NAME, "contextThrowInvalidArgument")

            val DYNAMIC_STOP_PROCESS = AppFunctionName(PACKAGE_NAME, "contextStopProcess")
            val DYNAMIC_GET_URIS = AppFunctionName(PACKAGE_NAME, "contextGetUris")

            val GLOBAL_SCOPE = AppFunctionName(PACKAGE_NAME, "appFunctionGlobalScope")
            val ACTIVITY_SCOPE = AppFunctionName(PACKAGE_NAME, "appFunctionActivityScope")
            val CONTEXT_CHECK_ATTRIBUTION = AppFunctionName(PACKAGE_NAME, "contextCheckAttribution")

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
                    GLOBAL_SCOPE,
                    ACTIVITY_SCOPE,
                    DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                    CONTEXT_CHECK_ATTRIBUTION,
                )

            val SERVICE_LEVEL_FUNCTIONS: Set<AppFunctionName> =
                setOf(HIGH_SCHEMA_VERSION, ENABLED_BY_DEFAULT, DISABLED_BY_DEFAULT)

            val ALL_FUNCTIONS = APP_LEVEL_FUNCTIONS + SERVICE_LEVEL_FUNCTIONS

            val ACTIVITY_SCOPED_FUNCTIONS = setOf(ACTIVITY_SCOPE, DYNAMIC_ACTIVITY_CONCAT_STRINGS)

            val ALL_GLOBAL_FUNCTIONS = ALL_FUNCTIONS - ACTIVITY_SCOPED_FUNCTIONS

            val APK_WITH_ONE_FUNCTION_REMOVED_FUNCTIONS = ALL_FUNCTIONS - HIGH_SCHEMA_VERSION
        }

        object ApkPaths {
            const val BASE_APP: String =
                TEST_APP_ROOT_FOLDER + "CtsAppFunctionsTestHelperDynamicSchema.apk"
            const val NO_TOP_LEVEL_DOCS: String =
                TEST_APP_ROOT_FOLDER + "CtsAppFunctionsTestHelperDynamicSchemaNoTopLevelDocs.apk"
            const val ONE_FUNCTION_REMOVED: String =
                TEST_APP_ROOT_FOLDER + "CtsAppFunctionsTestHelperDynamicSchemaLessOneFunction.apk"
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
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts" +
                                    ".dynamic.schema",
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
                                        "NestedDocument-android.app.appfunctions.cts" +
                                            ".dynamic.schema",
                                    )
                                    .setPropertyString("nestedRepeatedString", "value 1", "value 2")
                                    .build(),
                            )
                            .setPropertyString("serviceName", TEST_SERVICE_NAME)
                            .setPropertyString("scope", "global")
                            .build(),
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .build()

            val DISABLED_BY_DEFAULT_NO_SCHEMA =
                android.app.appfunctions.AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/" + "appFunctionDisabledByDefault_noSchema",
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts" +
                                    ".dynamic.schema",
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
                            .setPropertyString("scope", "global")
                            .build(),
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .build()

            val HIGH_SCHEMA_VERSION =
                android.app.appfunctions.AppFunctionMetadata.Builder(
                        GenericDocument.Builder<GenericDocument.Builder<*>>(
                                "app_functions",
                                "$PACKAGE_NAME/appFunctionWithHighSchemaVersion",
                                "AppFunctionStaticMetadata-android.app.appfunctions.cts" +
                                    ".dynamic.schema",
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
                            .setPropertyString("scope", "global")
                            .build(),
                        PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                    )
                    .build()
        }

        object PackageMetadata {
            val DYNAMIC_SCHEMA_PACKAGE_METADATA =
                AppFunctionPackageMetadata.create(
                    PACKAGE_NAME,
                    listOf(Components.TOP_LEVEL_COMPONENT_2, Components.TOP_LEVEL_COMPONENT_1),
                )

            val EMPTY_PACKAGE_METADATA =
                AppFunctionPackageMetadata.create(PACKAGE_NAME, emptyList())
        }
    }

    object CtsApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts"
        const val CERTIFICATE = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"
        val TEST_ALLOWLIST_PACKAGE = TestAllowlistPackage(PACKAGE_NAME, CERTIFICATE)

        object FunctionNames {
            val THROW_EXCEPTION = AppFunctionName(PACKAGE_NAME, "throwException")
            val UNCAUGHT_CLIENT_EXCEPTION = AppFunctionName(PACKAGE_NAME, "uncaughtClientException")
            val ADD_INVOKE_CALLBACK_TWICE = AppFunctionName(PACKAGE_NAME, "add_invokeCallbackTwice")
            val DYNAMIC_LONG_RUNNING = AppFunctionName(PACKAGE_NAME, "contextLongRunning")
            val ADD_ASYNC = AppFunctionName(PACKAGE_NAME, "addAsync")
            val NOT_INVOKE_CALLBACK = AppFunctionName(PACKAGE_NAME, "notInvokeCallback")
            val DYNAMIC_CONCAT_STRINGS = AppFunctionName(PACKAGE_NAME, "contextConcatStrings")
            val ACTIVITY_SCOPE_CONCAT_STRINGS =
                AppFunctionName(PACKAGE_NAME, "activityConcatStrings")
            val RUN_FOREVER = AppFunctionName(PACKAGE_NAME, "runForever")
            val ADD = AppFunctionName(PACKAGE_NAME, "add")
            val ADD_DISABLED_BY_DEFAULT = AppFunctionName(PACKAGE_NAME, "add_disabledByDefault")
            val NO_OP = AppFunctionName(PACKAGE_NAME, "noOp")
            val KILL = AppFunctionName(PACKAGE_NAME, "kill")
            val LONG_RUNNING_FUNCTION = AppFunctionName(PACKAGE_NAME, "longRunningFunction")
            val NO_SCHEMA = AppFunctionName(PACKAGE_NAME, "noSchema")
            val CONTEXT = AppFunctionName(PACKAGE_NAME, "contextDisabledByDefault")
            val ACTIVITY_CONCAT_STRINGS = AppFunctionName(PACKAGE_NAME, "activityConcatStrings")
            val CHECK_ATTRIBUTION = AppFunctionName(PACKAGE_NAME, "checkAttribution")

            val ALL_FUNCTIONS =
                setOf(
                    THROW_EXCEPTION,
                    UNCAUGHT_CLIENT_EXCEPTION,
                    ADD_INVOKE_CALLBACK_TWICE,
                    DYNAMIC_LONG_RUNNING,
                    ADD_ASYNC,
                    NOT_INVOKE_CALLBACK,
                    DYNAMIC_CONCAT_STRINGS,
                    ACTIVITY_SCOPE_CONCAT_STRINGS,
                    RUN_FOREVER,
                    ADD,
                    ADD_DISABLED_BY_DEFAULT,
                    NO_OP,
                    KILL,
                    LONG_RUNNING_FUNCTION,
                    NO_SCHEMA,
                    CONTEXT,
                    ACTIVITY_CONCAT_STRINGS,
                    CHECK_ATTRIBUTION,
                )
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
                            .setPropertyString("scope", "global")
                            .build(),
                        PackageMetadata.PACKAGE_METADATA,
                    )
                    .build()
        }

        object PackageMetadata {
            val PACKAGE_METADATA = AppFunctionPackageMetadata.create(PACKAGE_NAME, listOf())
        }
    }

    object UpdatableHelperApp {
        const val PACKAGE_NAME: String = "com.android.cts.appsearch.indexertestapp.a"

        object FunctionNames {
            val PRINT_1 = AppFunctionName(PACKAGE_NAME, "com.example.utils#print1")
            val PRINT_2 = AppFunctionName(PACKAGE_NAME, "com.example.utils#print2")
            val PRINT_3 = AppFunctionName(PACKAGE_NAME, "com.example.utils#print3")
        }

        object ApkPaths {
            const val BASE_APP: String = TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk"

            const val NO_FUNCTIONS: String =
                TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk"

            const val STATIC_ONLY_FUNCTIONS: String =
                TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk"

            const val DYNAMIC_ONLY_FUNCTIONS =
                TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAAppLevelFunctionsReg.apk"
        }
    }

    object ServiceHelperApp {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.service.helper"
        const val CERTIFICATE = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"
        val TEST_ALLOWLIST_PACKAGE = TestAllowlistPackage(PACKAGE_NAME, CERTIFICATE)

        object FunctionNames {
            val TEST_FUNCTION = AppFunctionName(PACKAGE_NAME, "test")
        }
    }

    object SideCarTestHelper {
        const val PACKAGE_NAME = "android.app.appfunctions.cts.helper.sidecar"
        const val CERTIFICATE = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"
        val TEST_ALLOWLIST_PACKAGE = TestAllowlistPackage(PACKAGE_NAME, CERTIFICATE)
    }

    companion object {
        private const val TEST_SERVICE_NAME =
            "android.app.appfunctions.testutils.TestAppFunctionService"

        private const val TEST_APP_ROOT_FOLDER: String = "/data/local/tmp/cts/appfunctions/"
    }
}
