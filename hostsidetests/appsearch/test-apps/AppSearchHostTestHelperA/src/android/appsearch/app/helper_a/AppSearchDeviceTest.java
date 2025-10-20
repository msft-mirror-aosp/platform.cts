/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.appsearch.app.helper_a;

import static android.app.appsearch.testutil.AppSearchTestUtils.calculateDigest;
import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.doGet;
import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.expectThrows;

import android.Manifest;
import android.app.UiAutomation;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchBlobHandle;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.EmbeddingPropertyConfig;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.app.appsearch.AppSearchSchema.StringPropertyConfig;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.EmbeddingVector;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.OpenBlobForReadResponse;
import android.app.appsearch.OpenBlobForWriteResponse;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.appsearch.flags.Flags;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppSearchDeviceTest {
    private static final String TAG = "AppSearchDeviceTest";

    private static final String DB_NAME = "";
    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";
    private static final String USER_ID_KEY = "userId";
    private static final AppSearchSchema SCHEMA = new AppSearchSchema.Builder("testSchema")
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            .build();
    private static final AppSearchSchema ALTERNATE_SCHEMA =
            new AppSearchSchema.Builder("testSchema")
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(
                                    AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
                            .setTokenizerType(
                                    AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
                            .build())
                    .build();

    // Schema registration
    private static final AppSearchSchema STRESS_TEST_SCHEMA =
            new AppSearchSchema.Builder("Email")
                    .addProperty(
                            new StringPropertyConfig.Builder("body")
                                    .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                    .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                                    .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                                    .build())
                    .addProperty(
                            new AppSearchSchema.BlobHandlePropertyConfig.Builder("blob")
                                    .setCardinality(
                                            AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                                    .setDescription("this is a blob.")
                                    .build())
                    .addProperty(
                            new EmbeddingPropertyConfig.Builder("embedding1")
                                    .setCardinality(PropertyConfig.CARDINALITY_REPEATED)
                                    .setIndexingType(
                                            EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY)
                                    .build())
                    .addProperty(
                            new EmbeddingPropertyConfig.Builder("embedding2")
                                    .setCardinality(PropertyConfig.CARDINALITY_REPEATED)
                                    .setIndexingType(
                                            EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY)
                                    .build())
                    .build();

    private static final GenericDocument DOCUMENT =
            new GenericDocument.Builder<>(NAMESPACE, ID, SCHEMA.getSchemaType())
                    .setPropertyString("subject", "testPut example1")
                    .setCreationTimestampMillis(12345L)
                    .build();

    private static final String PKG_A = "android.appsearch.app.helper_a";
    private static final String PKG_B = "android.appsearch.app.helper_b";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperA/\
    //   android_common/CtsAppSearchTestHelperA.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_B_CERT_SHA256 = BaseEncoding.base16().decode(
            "3D7A1AAE7AE8B9949BE93E071F3702AA38695B0F99B5FC4B2E8B364AC78FFDB2");

    private static final int STRESS_TEST_MAX_NUM_DOCS = 4000;
    private static final int STRESS_TEST_MAX_NUM_SESSIONS = 10;
    private static final int STRESS_TEST_MAX_NUM_BODY_LENGTH = 5000;
    private static final String STRESS_TEST_NAMESPACE = "stress_test_namespace";

    private AppSearchSessionShim mDb;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mDb = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder(DB_NAME).build()).get();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Test
    public void testRepeatedSetSchema() throws Exception {
        mDb = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder("my_test_db").build()).get();
        // Repeatedly call set schema without waiting for futures to complete to fill up the task
        // queue so we have 30+ seconds of tasks to process after this "test" exits
        for (int i = 1; i <= 500; i++) {
            var unused1 = mDb.setSchemaAsync(
                    new SetSchemaRequest.Builder().addSchemas(SCHEMA).build());
            var unused2 = mDb.setSchemaAsync(new SetSchemaRequest.Builder().setVersion(
                    i).addSchemas(ALTERNATE_SCHEMA).build());
        }
    }

    @Test
    public void testRepeatedSetSchema_finished() throws Exception {
        mDb = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder("my_test_db").build()).get();
        // It is expected that the set schema operations queued by a call to testRepeatedSetSchema
        // before the user restarted have all completed and we verify that here
        assertThat(mDb.getSchemaAsync().get().getVersion()).isEqualTo(500);
    }

    @Test
    public void testReadWriteLockContention() throws Exception {
        // With separate read and write executors, operations queued on the read executor can
        // interleave with operations queued on the write executor. To demonstrate this, we create a
        // backlog of write operations (setSchema calls) and queue up reads afterward. With only one
        // executor, the reads will have to wait for the writes to finish. With separate executors,
        // the reads should be able to finish before the writes finish.
        ExecutorService executor = Executors.newCachedThreadPool();

        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder("my_test_db").build()).get();
        // Repeatedly call set schema without waiting for futures to complete to fill up the task
        // queue so we have 30+ seconds of write operations to process.
        for (int i = 1; i <= 100; i++) {
            var unused = db.setSchemaAsync(
                    new SetSchemaRequest.Builder().addSchemas(SCHEMA).build());
            var unused2 = db.setSchemaAsync(new SetSchemaRequest.Builder().setVersion(
                    i).addSchemas(ALTERNATE_SCHEMA).build());
        }

        List<ListenableFuture<?>> futures = new ArrayList<>();

        long startTimeMs = System.currentTimeMillis();

        for (int i = 0; i < 10; i++) {
            SearchResultsShim shimResults = mDb.search("", new SearchSpec.Builder().build());
            ListenableFuture<Void> future = Futures.transform(shimResults.getNextPageAsync(),
                    results -> {
                        shimResults.close();
                        return null;
                    }, executor);
            futures.add(future);
        }
        List<?> unused = Futures.allAsList(futures).get();
        Log.e("testReadWriteLock", "elapsed " + (System.currentTimeMillis() - startTimeMs) + " ms");

        // We expect setSchema calls to have not finished yet with separate read and write
        // executors enabled
        if (Flags.enableSeparateReadWriteExecutors()) {
            assertThat(db.getSchemaAsync().get().getVersion()).isLessThan(100);
        } else {
            assertThat(db.getSchemaAsync().get().getVersion()).isEqualTo(100);
        }
    }

    @Test
    public void testReadParallelism() throws Exception {
        // Setup
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder("my_other_test_db").build()).get();
        AppSearchSchema schema = new AppSearchSchema.Builder("fooBarSchema")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("prop")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
                        .setTokenizerType(
                                AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_VERBATIM)
                        .build())
                .build();
        db.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(schema).build()).get();

        int suffix = 0;
        for (int i = 0; i < 200; i++) {
            List<GenericDocument> documents = new ArrayList<>();
            for (int j = 0; j < 50; j++) {
                GenericDocument document =
                        new GenericDocument.Builder<>("namespace", "id" + suffix, "fooBarSchema")
                                .setPropertyString("prop",
                                        "mylongprefix" + ((suffix % 1000) + 1000))
                                .build();
                documents.add(document);
                suffix++;
            }
            AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(db.putAsync(
                    new PutDocumentsRequest.Builder().addGenericDocuments(documents).build()));
            assertThat(result.getSuccesses()).hasSize(50);
            assertThat(result.getFailures()).isEmpty();
        }

        // Test read parallelism with a very large query consisting of many OR terms and VERBATIM
        // matching. A single query is estimated to take around ~100 ms.
        ExecutorService executor = Executors.newCachedThreadPool();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\"mylongprefix1000\"");
        for (int i = 1001; i < 2000; i++) {
            stringBuilder.append(" OR \"mylongprefix").append(i).append("\"");
        }
        String query = stringBuilder.toString();
        SearchSpec searchSpec = new SearchSpec.Builder().setListFilterQueryLanguageEnabled(
                true).setVerbatimSearchEnabled(true).build();
        List<ListenableFuture<?>> futures = new ArrayList<>();
        long startTimeMs = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            SearchResultsShim shimResults = db.search(query, searchSpec);
            ListenableFuture<Void> future = Futures.transform(shimResults.getNextPageAsync(),
                    results -> {
                        shimResults.close();
                        return null;
                    }, executor);
            futures.add(future);
        }
        List<?> unused = Futures.allAsList(futures).get();
        Log.e("testReadParallelism",
                "elapsed time " + (System.currentTimeMillis() - startTimeMs) + " ms");

        // Cleanup
        db.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }

    @Test
    public void testPutDocuments() throws Exception {
        // Schema registration
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(SCHEMA)
                .setSchemaTypeVisibilityForPackage(SCHEMA.getSchemaType(), /*visible=*/ true,
                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256)).build()).get();

        // Index a document
        AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder().addGenericDocuments(DOCUMENT).build()));
        assertThat(result.getSuccesses()).containsExactly(ID, /*v0=*/null);
        assertThat(result.getFailures()).isEmpty();
    }

    // Put a large number of docs, with embeddings and blobs, in different sessions.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @Test
    public void setUpStressTestDbs() throws Exception {
        for (int i = 0; i < STRESS_TEST_MAX_NUM_SESSIONS; ++i) {
            AppSearchSessionShim db =
                    AppSearchSessionShimImpl.createSearchSessionAsync(
                                    new AppSearchManager.SearchContext.Builder("my_test_db" + i)
                                            .build())
                            .get();
            populateStressTestData("my_test_db" + i, db);
            db.close();
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @Test
    public void verifyStressTestDbs() throws Exception {
        for (int i = 0; i < STRESS_TEST_MAX_NUM_SESSIONS; ++i) {
            String dbName = "my_test_db" + i;
            Log.i(TAG, "Verifying db " + dbName);
            AppSearchSessionShim db =
                    AppSearchSessionShimImpl.createSearchSessionAsync(
                                    new AppSearchManager.SearchContext.Builder(dbName).build())
                            .get();

            // Normal text search.
            SearchResultsShim shimResults =
                    db.search(
                            "test",
                            new SearchSpec.Builder()
                                    .setResultCountPerPage(STRESS_TEST_MAX_NUM_DOCS)
                                    .build());
            List<SearchResult> searchResults = shimResults.getNextPageAsync().get();
            assertThat(searchResults).hasSize(STRESS_TEST_MAX_NUM_DOCS);

            // Embedding search
            EmbeddingVector searchEmbedding =
                    new EmbeddingVector(new float[] {1, -1, -1, 1, -1}, "my_model_v1");
            SearchSpec embeddingSearchSpec =
                    new SearchSpec.Builder()
                            .setDefaultEmbeddingSearchMetricType(
                                    SearchSpec.EMBEDDING_SEARCH_METRIC_TYPE_DOT_PRODUCT)
                            .addEmbeddingParameters(searchEmbedding)
                            .setRankingStrategy(
                                    "sum(this.matchedSemanticScores(getEmbeddingParameter(0)))")
                            .setListFilterQueryLanguageEnabled(true)
                            .setResultCountPerPage(STRESS_TEST_MAX_NUM_DOCS)
                            .build();
            shimResults =
                    db.search(
                            "semanticSearch(getEmbeddingParameter(0), -1, 1)", embeddingSearchSpec);
            searchResults = shimResults.getNextPageAsync().get();
            assertThat(searchResults).hasSize(STRESS_TEST_MAX_NUM_DOCS);

            // Check blob
            byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
            byte[] digest = calculateDigest(data);
            AppSearchBlobHandle handle =
                    AppSearchBlobHandle.createWithSha256(
                            digest, PKG_A, dbName, STRESS_TEST_NAMESPACE);
            OpenBlobForReadResponse response =
                    db.openBlobForReadAsync(ImmutableSet.of(handle)).get();
            assertThat(response.getResult().getSuccesses()).hasSize(1);

            shimResults.close();
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @Test
    public void clearStressTestDbs() throws Exception {
        for (int i = 0; i < STRESS_TEST_MAX_NUM_SESSIONS; ++i) {
            String dbName = "my_test_db" + i;
            Log.i(TAG, "Clearing db " + dbName);
            AppSearchSessionShim db =
                    AppSearchSessionShimImpl.createSearchSessionAsync(
                                    new AppSearchManager.SearchContext.Builder(dbName).build())
                            .get();
            byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
            byte[] digest = calculateDigest(data);
            AppSearchBlobHandle handle =
                    AppSearchBlobHandle.createWithSha256(
                            digest, PKG_A, dbName, STRESS_TEST_NAMESPACE);
            db.removeBlobAsync(ImmutableSet.of(handle)).get();
            db.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
            db.close();
        }
    }

    private void populateStressTestData(String dbName, AppSearchSessionShim db) throws Exception {
        // schema registration
        db.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(STRESS_TEST_SCHEMA).build())
                .get();

        // Commit a blob
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] digest = calculateDigest(data);
        AppSearchBlobHandle handle =
                AppSearchBlobHandle.createWithSha256(digest, PKG_A, dbName, STRESS_TEST_NAMESPACE);
        try (OpenBlobForWriteResponse writeResponse =
                db.openBlobForWriteAsync(ImmutableSet.of(handle)).get()) {
            AppSearchBatchResult<AppSearchBlobHandle, ParcelFileDescriptor> writeResult =
                    writeResponse.getResult();
            writeResponse.getResult();
            for (Map.Entry<AppSearchBlobHandle, AppSearchResult<ParcelFileDescriptor>> entry :
                    writeResult.getFailures().entrySet()) {
                Log.e(
                        TAG,
                        "Failed to write blob "
                                + entry.getKey()
                                + ": "
                                + entry.getValue().getErrorMessage());
            }
            assertThat(writeResult.isSuccess()).isTrue();

            ParcelFileDescriptor writePfd = writeResult.getSuccesses().get(handle);
            try (OutputStream outputStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(writePfd)) {
                outputStream.write(data);
                outputStream.flush();
            }
        }
        assertThat(db.commitBlobAsync(ImmutableSet.of(handle)).get().getResult().isSuccess())
                .isTrue();

        // Put documents
        char[] bigBodyChars = new char[STRESS_TEST_MAX_NUM_BODY_LENGTH];
        for (int i = 0; i < STRESS_TEST_MAX_NUM_BODY_LENGTH; ++i) {
            bigBodyChars[i] = (char) ('a' + i % 26);
            if (i % 49 == 13) {
                bigBodyChars[i] = ' ';
            }
        }
        String bigBody = new String(bigBodyChars);
        PutDocumentsRequest.Builder putDocumentsRequestBuilder = new PutDocumentsRequest.Builder();
        ArrayList<GenericDocument> docs = new ArrayList<>(STRESS_TEST_MAX_NUM_DOCS);
        for (int i = 0; i < STRESS_TEST_MAX_NUM_DOCS; ++i) {
            GenericDocument doc =
                    new GenericDocument.Builder<>(STRESS_TEST_NAMESPACE, "id" + i, "Email")
                            .setPropertyString("body", bigBody + " test")
                            .setPropertyBlobHandle("blob", handle)
                            .setPropertyEmbedding(
                                    "embedding1",
                                    new EmbeddingVector(
                                            new float[] {-0.1f, 0.2f, -0.3f, -0.4f, 0.5f},
                                            "my_model_v1"))
                            .setPropertyEmbedding(
                                    "embedding2",
                                    new EmbeddingVector(
                                            new float[] {0.6f, 0.7f, -0.8f}, "my_model_v2"))
                            .build();
            docs.add(doc);
        }
        db.putAsync(putDocumentsRequestBuilder.addGenericDocuments(docs).build()).get();
    }

    @Test
    public void testPutDocumentsAsAnotherUser() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));

            // Initialize as other user
            AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                    new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                    userId).get();

            // Schema registration
            db.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(SCHEMA)
                    .setSchemaTypeVisibilityForPackage(SCHEMA.getSchemaType(), /*visible=*/ true,
                            new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256)).build()).get();

            // Index a document
            AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(
                    db.putAsync(new PutDocumentsRequest.Builder().addGenericDocuments(DOCUMENT)
                            .build()));
            assertThat(result.getSuccesses()).containsExactly(ID, /*v0=*/null);
            assertThat(result.getFailures()).isEmpty();

        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetDocuments_exist() throws Exception {
        List<GenericDocument> outDocuments = doGet(mDb, NAMESPACE, ID);
        assertThat(outDocuments).containsExactly(DOCUMENT);
    }

    @Test
    public void testGetDocumentsAsAnotherUser_exist() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));

            // Initialize as other user
            AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                    new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                    userId).get();

            // Get documents
            List<GenericDocument> outDocuments = doGet(db, NAMESPACE, ID);
            assertThat(outDocuments).containsExactly(DOCUMENT);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void closeAndFlush() {
        mDb.close();
    }

    @Test
    public void testGetDocuments_nonexist() throws Exception {
        AppSearchBatchResult<String, GenericDocument> getResult = mDb.getByDocumentIdAsync(
                new GetByDocumentIdRequest.Builder(NAMESPACE).addIds(ID).build()).get();
        assertThat(getResult.getFailures().get(ID).getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
    }

    /**
     * Clear generated data during the test.
     *
     * <p>Device side tests will be a part of host side test. We should clear the test data in the
     * host side tearDown only. Otherwise, it will wipe the data in the middle of a host side test.
     */
    @Test
    public void clearTestData() throws Exception {
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }

    @Test
    public void createSessionInStoppedUser() {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));
            ExecutionException exception = expectThrows(ExecutionException.class, () ->
                    AppSearchSessionShimImpl.createSearchSessionAsync(
                            new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                            userId).get());
            assertThat(exception.getMessage()).contains("is locked or not running.");
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }
}
