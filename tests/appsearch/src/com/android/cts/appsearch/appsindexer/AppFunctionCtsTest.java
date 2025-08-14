/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appsearch.cts.appsindexer;

import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_COMMON_SCHEMA_METADATA;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_A_V2_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_B_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.APP_B_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_DISPLAY_NAME_STRING_RES;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_ENABLED_BY_DEFAULT;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_FUNCTION_ID;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_PACKAGE_NAME;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_SCHEMA_CATEGORY;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_SCHEMA_NAME;
import static android.app.appsearch.testutil.AppFunctionTestUtils.PROPERTY_SCHEMA_VERSION;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_A_APP_FUNCTION_SERVICE_DISABLED;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_A_DYNAMIC_SCHEMA_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_A_V3_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_B_DYNAMIC_SCHEMA_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_B_PKG;
import static android.app.appsearch.testutil.AppFunctionTestUtils.TEST_APP_B_V1_PATH;
import static android.app.appsearch.testutil.AppFunctionTestUtils.clearTimestampsAndParentTypesInDocument;
import static android.app.appsearch.testutil.AppFunctionTestUtils.searchAppFunctionDocumentsIntoMap;
import static android.app.appsearch.testutil.AppFunctionTestUtils.searchAppFunctionsWithPackageName;
import static android.app.appsearch.testutil.AppFunctionTestUtils.updateAppFunctionServiceEnabledState;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.TEST_APP_A_PKG;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.TEST_APP_A_V1_PATH;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.TEST_APP_A_V2_PATH;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.installPackage;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.retryAssert;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.searchMobileApplicationWithId;
import static android.app.appsearch.testutil.AppsIndexerTestUtils.uninstallPackage;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.testutil.AppSearchTestUtils;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.appsearch.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiresFlagsEnabled(Flags.FLAG_APPS_INDEXER_ENABLED)
public class AppFunctionCtsTest {

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    @After
    public void uninstallTestApks() throws Throwable {
        uninstallPackage(TEST_APP_A_PKG);
        uninstallPackage(TEST_APP_B_PKG);

        retryAssert(
                () -> {
                    assertThat(searchMobileApplicationWithId(TEST_APP_A_PKG)).isNull();
                    assertThat(searchMobileApplicationWithId(TEST_APP_B_PKG)).isNull();
                });
    }

    @Test
    public void indexAppFunctions_packageChanges() throws Throwable {
        {
            // Install A V1 which does not have app functions.
            installPackage(mContext, TEST_APP_A_V1_PATH);

            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctions).isEmpty();
                    });
        }

        {
            // Update to v2 which has one app function
            installPackage(mContext, TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).containsExactly("com.example.utils#print1");
                    });
        }

        {
            // Update to v3 which no longer has print1 but has print2 and print3.
            installPackage(mContext, TEST_APP_A_V3_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds)
                                .containsExactly(
                                        "com.example.utils#print2", "com.example.utils#print3");
                    });
        }

        {
            // Uninstall package A
            uninstallPackage(TEST_APP_A_PKG);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).isEmpty();
                    });
        }
    }

    @Test
    public void indexAppFunctions_fullXml() throws Throwable {
        // The XML in A v2 has the full XML which specifies all the properties. Here we verify
        // all the properties are being indexed properly.
        installPackage(mContext, TEST_APP_A_V2_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.get(0);
                    assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                            .isEqualTo("com.example.utils#print1");
                    assertThat(appFunction.getPropertyString(PROPERTY_PACKAGE_NAME))
                            .isEqualTo(TEST_APP_A_PKG);
                    assertThat(appFunction.getPropertyBoolean(PROPERTY_ENABLED_BY_DEFAULT))
                            .isEqualTo(false);
                    assertThat(appFunction.getPropertyString(PROPERTY_SCHEMA_NAME))
                            .isEqualTo("print");
                    assertThat(appFunction.getPropertyString(PROPERTY_SCHEMA_CATEGORY))
                            .isEqualTo("utils");
                    assertThat(appFunction.getPropertyLong(PROPERTY_SCHEMA_VERSION)).isEqualTo(1);
                    assertThat(
                                    appFunction.getPropertyBoolean(
                                            PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS))
                            .isEqualTo(true);
                    assertThat(appFunction.getPropertyLong(PROPERTY_DISPLAY_NAME_STRING_RES))
                            .isEqualTo(10);
                });
    }

    @Test
    public void indexAppFunctions_defaultValue() throws Throwable {
        // The XML in B V1 only have functionId, schema_name, schema_version and schema_category.
        // Here, we check the default value of the optional properties are set properly.
        installPackage(mContext, TEST_APP_B_V1_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.get(0);
                    assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                            .isEqualTo("com.example.utils#print5");
                    assertThat(appFunction.getPropertyBoolean(PROPERTY_ENABLED_BY_DEFAULT))
                            .isEqualTo(true);
                    assertThat(
                                    appFunction.getPropertyBoolean(
                                            PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS))
                            .isEqualTo(false);
                });
    }

    @Test
    public void indexAppFunctions_installAppWithNoAppFunction_retainIndexedFunctions()
            throws Throwable {
        // Install the test app B V1 which has one app function. That function should be indexed.
        {
            installPackage(mContext, TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v1 which does not have any app function. The functions from B
        // should be retained.
        {
            installPackage(mContext, TEST_APP_A_V1_PATH);
            retryAssert(
                    () -> {
                        // Ensure the app A is indexed before checking if the function is retained.
                        // This prevents a false positive result if the indexer hasn't finished
                        // running yet.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();

                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }
    }

    @Test
    public void indexAppFunctionsFromTwoApps() throws Throwable {
        // Install the test app B V1 which has one app function. That function should be indexed.
        {
            installPackage(mContext, TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v2 which also has one app function. The function from B should be
        // retained and the new function from A should be indexed.
        {
            installPackage(mContext, TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctionsFromB =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctionsFromB).hasSize(1);
                        GenericDocument appFunctionFromB = appFunctionsFromB.get(0);
                        assertThat(appFunctionFromB.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");

                        List<GenericDocument> appFunctionsFromA =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctionsFromA).hasSize(1);
                        GenericDocument appFunctionFromA = appFunctionsFromA.get(0);
                        assertThat(appFunctionFromA.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print1");
                    });
        }
    }

    @Test
    public void indexMobileApplicationAndAppFunction_withoutLauncherIcon() throws Throwable {
        {
            // Install B V1 which does not have a launcher icon but have app functions.
            installPackage(mContext, TEST_APP_B_V1_PATH);

            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_B_PKG);
                        assertThat(mobileApplication).isNotNull();
                        // Its app functions should be indexed.
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).containsExactly("com.example.utils#print5");
                    });
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @Test
    public void indexAppWithDynamicSchema_dynamicSchemasDisabled_indexesPredefinedSchemaFieldsOnly()
            throws Throwable {
        installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppWithDynamicSchema() throws Throwable {
        installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppWithDynamicSchema_multipleRootSchemas() throws Throwable {
        installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(2);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PRINT_APP_FUNCTION);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(
                                        TEST_APP_A_PKG + "/topLevelSchemaMetadata#commonSchema")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_COMMON_SCHEMA_METADATA);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexMultipleAppsWithDynamicSchema() throws Throwable {

        installPackage(mContext, TEST_APP_B_DYNAMIC_SCHEMA_PATH);
        installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        retryAssert(
                () -> {
                    Map<String, GenericDocument> appFnMapAppB =
                            searchAppFunctionDocumentsIntoMap(TEST_APP_B_PKG);
                    assertThat(appFnMapAppB).hasSize(1);
                    assertThat(
                                    clearTimestampsAndParentTypesInDocument(
                                            appFnMapAppB.get(
                                                    TEST_APP_B_PKG + "/com.example.utils#print1")))
                            .isEqualTo(APP_B_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                });

        retryAssert(
                () -> {
                    Map<String, GenericDocument> appFnMapAppA =
                            searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                    assertThat(appFnMapAppA).hasSize(1);
                    assertThat(
                                    clearTimestampsAndParentTypesInDocument(
                                            appFnMapAppA.get(
                                                    TEST_APP_A_PKG + "/com.example.utils#print1")))
                            .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppsWithAndWithoutDynamicSchema() throws Throwable {
        installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);
        installPackage(mContext, TEST_APP_B_V1_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    assertThat(searchMobileApplicationWithId(TEST_APP_A_PKG)).isNotNull();
                    assertThat(searchMobileApplicationWithId(TEST_APP_B_PKG)).isNotNull();
                });
        // Verify dynamic schema app function.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        // Verify app B app function.
        appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_B_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_B_PKG + "/com.example.utils#print5")))
                .isEqualTo(APP_B_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchema() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_V2_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // Its app functions should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
                    });
        }

        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // Its app functions should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToWithoutDynamicSchema() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        }

        {
            installPackage(mContext, TEST_APP_A_V2_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchemaWithFewerTypes() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        }

        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchemaWithMoreTypesThanBefore() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION);
        }

        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_INDEXER_RUN_ON_APP_FUNCTION_COMPONENT_CHANGE)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_appFunctionServiceEnabledInRuntime_functionsIndexed() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_APP_FUNCTION_SERVICE_DISABLED);
            installPackage(mContext, TEST_APP_B_V1_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for AppB should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_B_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // AppFunctions for App A should not be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).isEmpty();
        }

        {
            updateAppFunctionServiceEnabledState(
                    mContext, TEST_APP_A_PKG, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        // Make sure after enabling app is indexed.
                        assertThat(appFnMap).hasSize(1);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_INDEXER_RUN_ON_APP_FUNCTION_COMPONENT_CHANGE)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_appFunctionServiceDisabledInRuntime_functionsRemoved() throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // AppFunctions for App A should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                    });
        }

        {
            updateAppFunctionServiceEnabledState(
                    mContext, TEST_APP_A_PKG, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        // Functions removed.
                        assertThat(appFnMap).isEmpty();
                    });
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_INDEXER_RUN_ON_APP_FUNCTION_COMPONENT_CHANGE)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void
            indexApp_compChangeFlagDisabled_appFunctionServiceDisabledInRuntime_functionNotRemoved()
                    throws Throwable {
        {
            installPackage(mContext, TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // AppFunctions for App A should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                    });
        }

        {
            updateAppFunctionServiceEnabledState(
                    mContext, TEST_APP_A_PKG, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        // Since flag is disabled app is not re-indexed and functions were not
                        // removed.
                        assertThat(appFnMap).hasSize(1);
                    });
        }
    }
}
