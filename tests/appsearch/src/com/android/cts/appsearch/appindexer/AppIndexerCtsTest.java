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
package android.app.appsearch.cts.appindexer;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.appsearch.flags.Flags;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RequiresFlagsEnabled(Flags.FLAG_APPS_INDEXER_ENABLED)
public class AppIndexerCtsTest {
    public static final String INDEXER_PACKAGE_NAME = "android";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_APP_ROOT_FOLDER = "/data/local/tmp/cts/appsearch/";
    private static final String TEST_APP_A_V1_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk";
    private static final String TEST_APP_A_V2_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk";
    private static final String TEST_APP_A_V3_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk";
    private static final String TEST_APP_B_V1_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppBV1.apk";
    private static final String TEST_APP_A_PKG = "com.android.cts.appsearch.indexertestapp.a";
    private static final String TEST_APP_B_PKG = "com.android.cts.appsearch.indexertestapp.b";
    private static final String NAMESPACE_MOBILE_APPLICATION = "apps";
    private static final String NAMESPACE_APP_FUNCTIONS = "app_functions";
    private static final String APP_PROPERTY_DISPLAY_NAME = "displayName";
    private static final String PROPERTY_FUNCTION_ID = "functionId";
    private static final String PROPERTY_PACKAGE_NAME = "packageName";
    private static final String PROPERTY_SCHEMA_NAME = "schemaName";
    private static final String PROPERTY_SCHEMA_VERSION = "schemaVersion";
    private static final String PROPERTY_SCHEMA_CATEGORY = "schemaCategory";
    private static final String PROPERTY_DISPLAY_NAME_STRING_RES = "displayNameStringRes";
    private static final String PROPERTY_ENABLED_BY_DEFAULT = "enabledByDefault";
    private static final String PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS =
            "restrictCallersWithExecuteAppFunctions";

    private static final long RETRY_CHECK_INTERVAL_MILLIS = 500;
    private static final long RETRY_MAX_INTERVALS = 10;

    @Before
    @After
    public void uninstallTestApks() {
        uninstallPackage(TEST_APP_A_PKG);
    }

    @Test
    public void indexMobileApplications_packageChanges() throws Throwable {
        {
            // Install a new app
            installPackage(TEST_APP_A_V1_PATH);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                        assertThat(mobileApplication.getPropertyString(APP_PROPERTY_DISPLAY_NAME))
                                .isEqualTo("App A [v1]");
                    });
        }

        {
            // Update it
            installPackage(TEST_APP_A_V2_PATH);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                        assertThat(mobileApplication.getPropertyString(APP_PROPERTY_DISPLAY_NAME))
                                .isEqualTo("App A [v2]");
                    });
        }

        {
            // Uninstall it
            uninstallPackage(TEST_APP_A_PKG);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNull();
                    });
        }
    }

    @Test
    public void indexAppFunctions_packageChanges() throws Throwable {
        {
            // Install A V1 which does not have app functions.
            installPackage(TEST_APP_A_V1_PATH);

            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctions).isEmpty();
                    });
        }

        {
            // Update to v2 which has one app function
            installPackage(TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds =
                                appFunctions.stream()
                                        .map(doc -> doc.getPropertyString(PROPERTY_FUNCTION_ID))
                                        .toList();
                        assertThat(functionIds).containsExactly("com.example.utils#print1");
                    });
        }

        {
            // Update to v3 which no longer has print1 but has print2 and print3.
            installPackage(TEST_APP_A_V3_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds =
                                appFunctions.stream()
                                        .map(doc -> doc.getPropertyString(PROPERTY_FUNCTION_ID))
                                        .toList();
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
                        List<String> functionIds =
                                appFunctions.stream()
                                        .map(doc -> doc.getPropertyString(PROPERTY_FUNCTION_ID))
                                        .toList();
                        assertThat(functionIds).isEmpty();
                    });
        }
    }

    @Test
    public void indexAppFunctions_fullXml() throws Throwable {
        // The XML in A v2 has the full XML which specifies all the properties. Here we verify
        // all the properties are being indexed properly.
        installPackage(TEST_APP_A_V2_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.getFirst();
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
        installPackage(TEST_APP_B_V1_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.getFirst();
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
            installPackage(TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.getFirst();
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v1 which does not have any app function. The functions from B
        // should be retained.
        {
            installPackage(TEST_APP_A_V1_PATH);
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
                        GenericDocument appFunction = appFunctions.getFirst();
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }
    }

    @Test
    public void indexAppFunctionsFromTwoApps() throws Throwable {
        // Install the test app B V1 which has one app function. That function should be indexed.
        {
            installPackage(TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.getFirst();
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v2 which also has one app function. The function from B should be
        // retained and the new function from A should be indexed.
        {
            installPackage(TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctionsFromB =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctionsFromB).hasSize(1);
                        GenericDocument appFunctionFromB = appFunctionsFromB.getFirst();
                        assertThat(appFunctionFromB.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");

                        List<GenericDocument> appFunctionsFromA =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctionsFromA).hasSize(1);
                        GenericDocument appFunctionFromA = appFunctionsFromA.getFirst();
                        assertThat(appFunctionFromA.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print1");
                    });
        }
    }

    @Test
    public void indexMobileApplicationAndAppFunction_withoutLauncherIcon() throws Throwable {
        {
            // Install B V1 which does not have a launcher icon but have app functions.
            installPackage(TEST_APP_B_V1_PATH);

            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_B_PKG);
                        assertThat(mobileApplication).isNotNull();
                        // Its app functions should be indexed.
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        List<String> functionIds =
                                appFunctions.stream()
                                        .map(doc -> doc.getPropertyString(PROPERTY_FUNCTION_ID))
                                        .toList();
                        assertThat(functionIds).containsExactly("com.example.utils#print5");
                    });
        }
    }

    private GenericDocument searchMobileApplicationWithId(String id)
            throws ExecutionException, InterruptedException {
        GlobalSearchSessionShim globalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get();

        SearchResultsShim searchResults =
                globalSearchSession.search(
                        "",
                        new SearchSpec.Builder()
                                .addFilterNamespaces(NAMESPACE_MOBILE_APPLICATION)
                                .addFilterPackageNames(INDEXER_PACKAGE_NAME)
                                .build());
        List<GenericDocument> genericDocuments = collectAllResults(searchResults);
        return genericDocuments.stream()
                .filter(genericDocument -> genericDocument.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private List<GenericDocument> searchAppFunctionsWithPackageName(String packageName)
            throws ExecutionException, InterruptedException {
        GlobalSearchSessionShim globalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get();

        SearchResultsShim searchResults =
                globalSearchSession.search(
                        String.format("packageName:\"%s\"", packageName),
                        new SearchSpec.Builder()
                                .addFilterNamespaces(NAMESPACE_APP_FUNCTIONS)
                                .addFilterPackageNames(INDEXER_PACKAGE_NAME)
                                .setVerbatimSearchEnabled(true)
                                .build());
        return collectAllResults(searchResults);
    }

    private List<GenericDocument> collectAllResults(SearchResultsShim searchResults)
            throws ExecutionException, InterruptedException {
        List<GenericDocument> documents = new ArrayList<>();
        List<SearchResult> results;
        do {
            results = searchResults.getNextPageAsync().get();
            for (SearchResult result : results) {
                documents.add(result.getGenericDocument());
            }
        } while (results.size() > 0);
        return documents;
    }

    private void installPackage(@NonNull String path) {
        assertThat(
                        SystemUtil.runShellCommand(
                                String.format(
                                        "pm install -r -i %s -t -g %s",
                                        mContext.getPackageName(), path)))
                .isEqualTo("Success\n");
    }

    private void uninstallPackage(@NonNull String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    /** Retries an assertion with a delay between attempts. */
    private static void retryAssert(ThrowRunnable runnable) throws Throwable {
        Throwable lastError = null;

        for (int attempt = 0; attempt < RETRY_MAX_INTERVALS; attempt++) {
            try {
                runnable.run();
                return;
            } catch (Throwable e) {
                lastError = e;
                if (attempt < RETRY_MAX_INTERVALS) {
                    Thread.sleep(RETRY_CHECK_INTERVAL_MILLIS);
                }
            }
        }
        throw lastError;
    }

    /** Runnable that throws. */
    public interface ThrowRunnable {
        void run() throws Throwable;
    }
}
