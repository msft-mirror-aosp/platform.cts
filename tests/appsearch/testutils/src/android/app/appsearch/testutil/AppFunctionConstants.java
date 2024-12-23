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

package android.app.appsearch.testutil;

import android.app.appsearch.GenericDocument;

/** Constants for AppFunction indexer tests. */
public final class AppFunctionConstants {
    private static final String NAMESPACE_APP_FUNCTIONS = "app_functions";

    /**
     * Print app function generic document as defined in the appfunctions_v2.xml of dynamic schema
     * test app.
     */
    public static final GenericDocument DYNAMIC_SCHEMA_PRINT_APP_FUNCTION =
            buildPrintAppFunctionDocument();

    /**
     * Builds the generic document for print app function defined in app A with dynamic schema.
     *
     * <p>Document Fields:
     *
     * <p><b>AppFunctionStaticMetadata Document Fields:</b>
     *
     * <ul>
     *   <li>enabledByDefault (Boolean)
     *   <li>functionId (String)
     *   <li>packageName (String)
     *   <li>schemaName (String)
     *   <li>schemaCategory (String)
     *   <li>schemaVersion (Long)
     *   <li>restrictCallersWithExecuteAppFunctions (Boolean)
     *   <li>displayNameStringRes (Long)
     *   <li>mobileApplicationQualifiedId (String)
     *   <li>schemaMetadata (Document)
     *   <li>parameters (Array of Documents)
     *   <li>response (Document)
     *   <li>components (Document)
     * </ul>
     *
     * <p><b>Schema Metadata Document Fields:</b>
     *
     * <ul>
     *   <li>schemaCategory (String)
     *   <li>schemaName (String)
     *   <li>schemaVersion (Long)
     * </ul>
     *
     * <p><b>Parameter Document Fields:</b>
     *
     * <ul>
     *   <li>name (String)
     *   <li>required (Boolean)
     *   <li>schema (Document)
     * </ul>
     *
     * <p><b>Schema Document Fields for Parameters:</b>
     *
     * <ul>
     *   <li>dataType (Long)
     *   <li>documentSchemaType (String, optional)
     * </ul>
     *
     * <p><b>Response Document Fields:</b>
     *
     * <ul>
     *   <li>isNullable (Boolean)
     *   <li>schema (Document)
     * </ul>
     *
     * <p><b>Response Schema Document Fields:</b>
     *
     * <ul>
     *   <li>dataType (Long)
     *   <li>properties (Document)
     * </ul>
     *
     * <p><b>Component Document Fields:</b>
     *
     * <ul>
     *   <li>schemas (Document)
     * </ul>
     *
     * <p><b>Component Schema Document Fields:</b>
     *
     * <ul>
     *   <li>dataType (Long)
     *   <li>documentSchemaType (String)
     *   <li>properties (Document)
     * </ul>
     *
     * <p><b>Component Property Document Fields:</b>
     *
     * <ul>
     *   <li>name (String)
     *   <li>required (Boolean)
     *   <li>schema (Document)
     * </ul>
     */
    private static GenericDocument buildPrintAppFunctionDocument() {
        String dynamicSchemaAppPackage = "com.android.cts.appsearch.indexertestapp.a";
        GenericDocument.Builder builder =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                dynamicSchemaAppPackage + "/com.example.utils#print1",
                                "AppFunctionStaticMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0);

        // Add properties from AppFunctionMetadata
        builder.setPropertyBoolean("enabledByDefault", true)
                .setPropertyString("functionId", "com.example.utils#print1")
                .setPropertyString("packageName", dynamicSchemaAppPackage)
                .setPropertyString("schemaName", "print")
                .setPropertyString("schemaCategory", "utils")
                .setPropertyLong("schemaVersion", 1L)
                .setPropertyBoolean("enabledByDefault", true)
                .setPropertyBoolean("restrictCallersWithExecuteAppFunctions", false)
                .setPropertyLong("displayNameStringRes", 12)
                .setPropertyString(
                        "mobileApplicationQualifiedId", "android$apps-db/apps#com.example.utils");

        GenericDocument schemaMetadata =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/schemaMetadata",
                                "SchemaMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyString("schemaCategory", "utils")
                        .setPropertyString("schemaName", "print")
                        .setPropertyLong("schemaVersion", 1)
                        .build();

        builder.setPropertyDocument("schemaMetadata", schemaMetadata);

        GenericDocument parameterSchema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/parameter0/message/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 8)
                        .setPropertyString("documentSchemaType", "string")
                        .build();
        GenericDocument parameter =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/parameter0/message",
                                "AppFunctionValueParameterMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyString("name", "message")
                        .setPropertyBoolean("required", true)
                        .setPropertyDocument("schema", parameterSchema)
                        .build();

        GenericDocument parameter1Schema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/parameter1/message1/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 8)
                        .setPropertyString("documentSchemaType", "string")
                        .build();
        GenericDocument parameter1 =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/parameter1/message1",
                                "AppFunctionValueParameterMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyString("name", "message1")
                        .setPropertyBoolean("required", true)
                        .setPropertyDocument("schema", parameter1Schema)
                        .build();

        builder.setPropertyDocument("parameters", parameter, parameter1);

        GenericDocument responsePropertySchema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/response/schema/properties0/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 8)
                        .build();

        GenericDocument responseProperty =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/response/schema/properties0",
                                "AppFunctionValueParameterMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyString("name", "result")
                        .setPropertyBoolean("required", true)
                        .setPropertyDocument("schema", responsePropertySchema)
                        .build();

        GenericDocument responseSchema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/response/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 3)
                        .setPropertyDocument("properties", responseProperty)
                        .build();

        GenericDocument response =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/response",
                                "AppFunctionResponseMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyBoolean("isNullable", false)
                        .setPropertyDocument("schema", responseSchema)
                        .build();

        builder.setPropertyDocument("response", response);

        GenericDocument componentPropertySchema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/components0/schema/properties0/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 8)
                        .build();

        GenericDocument componentProperty =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/components0/schema/properties0",
                                "AppFunctionValueParameterMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyString("name", "email")
                        .setPropertyBoolean("required", true)
                        .setPropertyDocument("schema", componentPropertySchema)
                        .build();

        GenericDocument componentSchema =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/components0/schema",
                                "AppFunctionSchema-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("dataType", 3)
                        .setPropertyString("documentSchemaType", "object")
                        .setPropertyDocument("properties", componentProperty)
                        .build();

        GenericDocument components =
                new GenericDocument.Builder<>(
                                NAMESPACE_APP_FUNCTIONS,
                                "com.example.utils#print/components0",
                                "AppFunctionComponentMetadata-" + dynamicSchemaAppPackage)
                        .setCreationTimestampMillis(0)
                        .setPropertyDocument("schemas", componentSchema)
                        .build();

        builder.setPropertyDocument("components", components);

        return builder.build();
    }

    private AppFunctionConstants() {}
}
