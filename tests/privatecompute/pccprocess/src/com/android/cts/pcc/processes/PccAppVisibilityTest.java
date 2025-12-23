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

package com.android.cts.pcc.processes;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.pcc.checkingvisibilityapp.IVisibilityCheckService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccAppVisibilityTest {

    private static final String QUERIED_PACKAGE = "com.android.cts.pcc.queryablebypackage";
    private static final String INTENT_FILTER_PACKAGE =
            "com.android.cts.pcc.queryablebyintentfilter";
    private static final String PROVIDER_PACKAGE = "com.android.cts.pcc.queryablebyprovider";
    private static final String VISIBILITY_SERVICE_PACKAGE =
            "com.android.cts.pcc.checkingvisibilityapp";

    private static final int TIMEOUT_SECONDS = 5;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageManager = mContext.getPackageManager();
    }

    @Test
    public void testCanSeeManifestQueriedPackages() throws PackageManager.NameNotFoundException {
        assertThat(mPackageManager.getPackageInfo(QUERIED_PACKAGE, 0)).isNotNull();
    }

    @Test
    public void testCanSeeIntentFilterQueriedPackages()
            throws PackageManager.NameNotFoundException {
        assertThat(mPackageManager.getPackageInfo(INTENT_FILTER_PACKAGE, 0)).isNotNull();
    }

    @Test
    public void testCanSeeProviderQueriedPackages() throws PackageManager.NameNotFoundException {
        assertThat(mPackageManager.getPackageInfo(PROVIDER_PACKAGE, 0)).isNotNull();
    }

    @Test
    public void testPccServiceCanSeeCallingPackageViaImplicitGrants() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<IVisibilityCheckService> binderRef = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        binderRef.set(IVisibilityCheckService.Stub.asInterface(binder));
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        VISIBILITY_SERVICE_PACKAGE,
                        VISIBILITY_SERVICE_PACKAGE + ".VisibilityCheckService"));
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            IVisibilityCheckService service = binderRef.get();
            assertThat(service).isNotNull();
            assertThat(service.canSeeCallingPackage()).isTrue();
        } finally {
            mContext.unbindService(connection);
        }
    }
}
