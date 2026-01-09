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

package android.provider.cts.media.modern;

import static android.Manifest.permission.WRITE_MEDIA_STORAGE;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.provider.SearchMediaService.BIND_SEARCH_MEDIA_SERVICE_PERMISSION;
import static android.provider.SearchMediaService.EXTRA_NEXT_PAGE_TOKEN;
import static android.provider.SearchMediaService.EXTRA_SEARCH_RESULTS_PAGE_SIZE;
import static android.provider.SearchMediaService.EXTRA_SEARCH_RESULTS_SORT_ORDER;
import static android.provider.SearchMediaService.EXTRA_SORT_BY_RELEVANCE;
import static android.provider.SearchMediaService.EXTRA_SORT_BY_TIME;
import static android.provider.cts.media.modern.AppSearchUtils.CREATE_DOCUMENTS_CALL;
import static android.provider.cts.media.modern.AppSearchUtils.DELETE_DOCUMENTS_CALL;
import static android.provider.cts.media.modern.AppSearchUtils.NAMESPACE;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_DATE_TAKEN;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_FILE_ID;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_ID;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_MEDIA_TYPE;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_METADATA_EXTRACTED;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_NAMESPACE;
import static android.provider.cts.media.modern.AppSearchUtils.PROPERTY_VOLUME_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ISearchMediaService;
import android.provider.MediaStore;
import android.provider.SearchMediaResult;
import android.provider.SearchMediaService;

import androidx.annotation.NonNull;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_SEARCH)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
public class SearchMediaServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String DEFAULT_SEARCH_SERVICE_PACKAGE_NAME = getMediaProviderPackageName();

    private final CountDownLatch mServiceLatch = new CountDownLatch(1);

    private ISearchMediaService mSearchMediaService;
    private Context mContext;
    private boolean mIsServiceConnected = false;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BIND_SEARCH_MEDIA_SERVICE_PERMISSION);

        assertBindSearchMediaServicePermissionGranted();

        Intent intent = new Intent(SearchMediaService.SERVICE_INTERFACE);
        String packageNameToConnect =
                MediaStore.getPackageForSearchMediaService(mContext.getContentResolver());
        intent.setPackage(packageNameToConnect);

        mIsServiceConnected = mContext.bindService(intent, mServiceConnection, BIND_AUTO_CREATE);

        mServiceLatch.await(10, TimeUnit.SECONDS);
        assumeNotNull(mSearchMediaService);
        deleteDocuments();
    }

    @After
    public void tearDown() throws Exception {
        if (mIsServiceConnected) {
            mContext.unbindService(mServiceConnection);
            mIsServiceConnected = false;
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private final ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    mSearchMediaService = ISearchMediaService.Stub.asInterface(iBinder);
                    mServiceLatch.countDown();
                    mIsServiceConnected = true;
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    mSearchMediaService = null;
                    mIsServiceConnected = false;
                }
            };

    @Test
    public void testSearchWithoutIndexingNewData() throws Exception {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_SEARCH_RESULTS_PAGE_SIZE, 20);
        extras.putString(EXTRA_SEARCH_RESULTS_SORT_ORDER, EXTRA_SORT_BY_TIME);

        SearchMediaCallback callback = new SearchMediaCallback();
        mSearchMediaService.searchMedia(
                /* searchText */ "cat", /* searchId */ "123", extras, callback);
        callback.await(10, TimeUnit.SECONDS);

        List<SearchMediaResult> searchMediaResults =
                callback.getSearchMediaResultPage().getSearchResults();
        assertNotNull(searchMediaResults);
        for (int i = 0; i < searchMediaResults.size() - 1; i++) {
            long dateTaken1 = searchMediaResults.get(i).getDateTaken();
            long dateTaken2 = searchMediaResults.get(i + 1).getDateTaken();
            assertTrue("dateTaken should be in descending order.", dateTaken1 >= dateTaken2);
        }

        callback = new SearchMediaCallback();
        extras.putString(EXTRA_SEARCH_RESULTS_SORT_ORDER, EXTRA_SORT_BY_RELEVANCE);
        mSearchMediaService.searchMedia(
                /* searchText */ "cat", /* searchId */ "123", extras, callback);
        callback.await(10, TimeUnit.SECONDS);

        searchMediaResults = callback.getSearchMediaResultPage().getSearchResults();
        assertNotNull(searchMediaResults);
        for (int i = 0; i < searchMediaResults.size() - 1; i++) {
            double score1 = searchMediaResults.get(i).getScore();
            double score2 = searchMediaResults.get(i + 1).getScore();
            assertTrue("Scores should be in descending order.", score1 >= score2);
        }
    }

    @Test
    public void testDefaultService_searchSortedByRelevance() throws Exception {
        String servicePackageName =
                MediaStore.getPackageForSearchMediaService(mContext.getContentResolver());
        assumeTrue(DEFAULT_SEARCH_SERVICE_PACKAGE_NAME.equals(servicePackageName));

        try {
            indexDocuments();
            Bundle extras = new Bundle();
            extras.putLong(EXTRA_SEARCH_RESULTS_PAGE_SIZE, 20);
            extras.putString(EXTRA_SEARCH_RESULTS_SORT_ORDER, EXTRA_SORT_BY_RELEVANCE);

            List<SearchMediaResult> allSearchMediaResults = new ArrayList<>();

            SearchMediaCallback callback =
                    makeSearchMediaCall(extras, /* expectedSearchResults */ 20);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNotNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            callback = makeSearchMediaCall(extras, /* expectedSearchResults */ 20);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNotNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            callback = makeSearchMediaCall(extras, /* expectedSearchResults */ 10);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            for (int i = 0; i < allSearchMediaResults.size() - 1; i++) {
                double score1 = allSearchMediaResults.get(i).getScore();
                double score2 = allSearchMediaResults.get(i + 1).getScore();
                assertTrue("Scores should be in descending order.", score1 >= score2);
            }
        } finally {
            deleteDocuments();
        }
    }

    @Test
    public void testDefaultService_searchSortedByTime() throws Exception {
        String servicePackageName =
                MediaStore.getPackageForSearchMediaService(mContext.getContentResolver());
        assumeTrue(DEFAULT_SEARCH_SERVICE_PACKAGE_NAME.equals(servicePackageName));

        try {
            indexDocuments();

            Bundle extras = new Bundle();
            extras.putLong(EXTRA_SEARCH_RESULTS_PAGE_SIZE, 20);
            extras.putString(EXTRA_SEARCH_RESULTS_SORT_ORDER, EXTRA_SORT_BY_TIME);

            List<SearchMediaResult> allSearchMediaResults = new ArrayList<>();

            SearchMediaCallback callback =
                    makeSearchMediaCall(extras, /* expectedSearchResults */ 20);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNotNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            callback = makeSearchMediaCall(extras, /* expectedSearchResults */ 20);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNotNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            callback = makeSearchMediaCall(extras, /* expectedSearchResults */ 10);
            allSearchMediaResults.addAll(callback.getSearchMediaResultPage().getSearchResults());
            updateNextPageToken(extras, callback);
            assertNull(extras.getString(EXTRA_NEXT_PAGE_TOKEN));

            for (int i = 0; i < allSearchMediaResults.size() - 1; i++) {
                long dateTaken1 = allSearchMediaResults.get(i).getDateTaken();
                long dateTaken2 = allSearchMediaResults.get(i + 1).getDateTaken();
                assertTrue("dateTaken should be in descending order.", dateTaken1 > dateTaken2);
            }
        } finally {
            deleteDocuments();
        }
    }

    private SearchMediaCallback makeSearchMediaCall(Bundle extras, int expectedSearchResults)
            throws RemoteException, InterruptedException {
        SearchMediaCallback callback = new SearchMediaCallback();
        mSearchMediaService.searchMedia(
                /* searchText */ "cat", /* searchId */ "123", extras, callback);
        callback.await(10, TimeUnit.SECONDS);

        List<SearchMediaResult> searchMediaResults =
                callback.getSearchMediaResultPage().getSearchResults();
        assertNotNull(searchMediaResults);
        assertEquals(expectedSearchResults, searchMediaResults.size());
        return callback;
    }

    private void updateNextPageToken(Bundle extras, SearchMediaCallback callback) {
        String pageToken =
                callback.getSearchMediaResultPage().getExtras().getString(EXTRA_NEXT_PAGE_TOKEN);
        extras.putString(EXTRA_NEXT_PAGE_TOKEN, pageToken);
    }

    private void indexDocuments() {
        ArrayList<Bundle> documentBundles = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            StringBuilder label = new StringBuilder();
            for (int j = 1; j <= 50; j++) {
                if (j <= i) {
                    label.append("cat ");
                } else {
                    label.append("foo ");
                }
            }
            documentBundles.add(createDocumentBundle(label.toString(), i));
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_MEDIA_STORAGE);
        try {
            Bundle extras = new Bundle();
            extras.putParcelableArrayList("media_items", documentBundles);
            mContext.getContentResolver()
                    .call(MediaStore.AUTHORITY, CREATE_DOCUMENTS_CALL, null, extras);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @NonNull
    private Bundle createDocumentBundle(String label, long fileId) {
        Bundle bundle = new Bundle();
        bundle.putString(PROPERTY_NAMESPACE, NAMESPACE);
        bundle.putString(PROPERTY_ID, "doc_" + fileId);
        bundle.putLong(PROPERTY_FILE_ID, fileId);
        bundle.putLong(PROPERTY_MEDIA_TYPE, 1);
        bundle.putLong(PROPERTY_DATE_TAKEN, 10000 + fileId);
        bundle.putString(PROPERTY_METADATA_EXTRACTED, label);
        bundle.putString(PROPERTY_VOLUME_NAME, MediaStore.VOLUME_EXTERNAL_PRIMARY);
        return bundle;
    }

    private void assertBindSearchMediaServicePermissionGranted() {
        int permissionStatus =
                mContext.checkCallingOrSelfPermission(BIND_SEARCH_MEDIA_SERVICE_PERMISSION);
        assertEquals(
                "Failed to adopt BIND_SEARCH_MEDIA_SERVICE_PERMISSION",
                PackageManager.PERMISSION_GRANTED,
                permissionStatus);
    }

    private void deleteDocuments() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_MEDIA_STORAGE);
        try {
            mContext.getContentResolver()
                    .call(MediaStore.AUTHORITY, DELETE_DOCUMENTS_CALL, null, Bundle.EMPTY);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private static String getMediaProviderPackageName() {
        final Instrumentation inst = androidx.test.InstrumentationRegistry.getInstrumentation();
        final PackageManager packageManager = inst.getContext().getPackageManager();
        final ProviderInfo providerInfo =
                packageManager.resolveContentProvider(
                        MediaStore.AUTHORITY, PackageManager.MATCH_ALL);
        return providerInfo.packageName;
    }
}
