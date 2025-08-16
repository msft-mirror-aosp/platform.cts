/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.appsearch.cts.app;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.AppSearchTestUtils;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.appsearch.flags.Flags;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_SCHEMA_MIGRATION_EXECUTOR_DEAD_LOCK_FIX)
public class AppSearchSchemaMigrationSingleThreadCtsTest
        extends AppSearchSchemaMigrationCtsTestBase {

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    @Override
    protected ListenableFuture<AppSearchSessionShim> createSearchSessionAsync(
            @NonNull String dbName) {
        // Use single thread executor for schema migration test to verify it works without callback
        // deadlock.
        Context context = ApplicationProvider.getApplicationContext();
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(dbName).build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return AppSearchSessionShimImpl.createSearchSessionAsync(context, searchContext, executor);
    }
}
