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

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Utility class providing constants and helper methods for AppsIndexer tests. */
public final class AppsIndexerTestUtils {
    private static final String INDEXER_PACKAGE_NAME = "android";
    private static final String TEST_APP_ROOT_FOLDER =
            AppSearchTestEnvironmentFactory.getEnvironmentInstance().getTestAppRootFolder();
    private static final String NAMESPACE_MOBILE_APPLICATION = "apps";

    private static final long RETRY_CHECK_INTERVAL_MILLIS = 500;
    private static final long RETRY_MAX_INTERVALS = 10;

    public static final String TEST_APP_A_V1_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk";
    public static final String TEST_APP_A_V2_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk";

    public static final String TEST_APP_A_PKG = "com.android.cts.appsearch.indexertestapp.a";

    public static final String APP_PROPERTY_DISPLAY_NAME = "displayName";

    /** Queries GlobalSearchSession for a MobileApplication by its document ID. */
    public static GenericDocument searchMobileApplicationWithId(String id)
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
        for (int i = 0; i < genericDocuments.size(); i++) {
            GenericDocument genericDocument = genericDocuments.get(i);
            if (genericDocument.getId().equals(id)) {
                return genericDocument;
            }
        }
        return null;
    }

    /** Installs an APK from a given path and asserts success. */
    public static void installPackage(@NonNull Context context, @NonNull String path) {
        String command =
                String.format(
                        "pm install "
                                + "-r " // -r: Reinstall the app if it already exists.
                                + "-i %s " // -i: Specify the installer's package name.
                                + "-t " // -t: Allow test-only apps to be installed.
                                + "-g " // -g: Grant all permissions listed in the app manifest.
                                + "%s", // %s: The file path of the app.
                        context.getPackageName(), path);
        String shellResult =
                AppSearchTestEnvironmentFactory.getEnvironmentInstance().runShellCommand(command);
        assertThat(shellResult).contains("Success");
    }

    /** Uninstalls an Android package by package name. */
    public static void uninstallPackage(@NonNull String packageName) {
        AppSearchTestEnvironmentFactory.getEnvironmentInstance()
                .runShellCommand("pm uninstall " + packageName);
    }

    /** Retries an assertion with a delay between attempts. */
    public static void retryAssert(ThrowRunnable runnable) throws Throwable {
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

    /** Collects all search results into a list. */
    public static List<GenericDocument> collectAllResults(SearchResultsShim searchResults)
            throws ExecutionException, InterruptedException {
        List<GenericDocument> documents = new ArrayList<>();
        List<SearchResult> results;
        do {
            results = searchResults.getNextPageAsync().get();
            for (SearchResult result : results) {
                documents.add(result.getGenericDocument());
            }
        } while (!results.isEmpty());
        return documents;
    }

    /** Runnable that throws. */
    public interface ThrowRunnable {
        /** Executes the action. */
        void run() throws Throwable;
    }

    private AppsIndexerTestUtils() {}
}
