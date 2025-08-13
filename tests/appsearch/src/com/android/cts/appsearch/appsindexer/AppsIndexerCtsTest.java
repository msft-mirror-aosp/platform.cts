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

import static android.app.appsearch.testutil.AppsIndexerTestUtils.APP_PROPERTY_DISPLAY_NAME;
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
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;

import com.android.appsearch.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RequiresFlagsEnabled(Flags.FLAG_APPS_INDEXER_ENABLED)
public class AppsIndexerCtsTest {

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    @After
    public void uninstallTestApks() throws Throwable {
        uninstallPackage(TEST_APP_A_PKG);

        retryAssert(
                () -> {
                    assertThat(searchMobileApplicationWithId(TEST_APP_A_PKG)).isNull();
                });
    }

    @Test
    public void indexMobileApplications_packageChanges() throws Throwable {
        {
            // Install a new app
            installPackage(mContext, TEST_APP_A_V1_PATH);

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
            installPackage(mContext, TEST_APP_A_V2_PATH);

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
}
