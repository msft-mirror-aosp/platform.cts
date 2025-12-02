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

package com.android.cts.pcc.featuretests;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.common.StorageTestUtils.deleteThrowException;
import static com.android.cts.pcc.featuretests.services.StorageTestServiceStub.ERROR_WRITE_FAILED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.cts.pcc.featuretests.services.IStorageTestService;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccStorageIsolationTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PCC_DIR_SUFFIX = "-pcc";
    private static final int BIND_SERVICE_TIMEOUT_SECONDS = 10;
    private Context mContext;
    private IStorageTestService mNonPccStorageService;

    private ServiceConnection mNonPccConnection;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mNonPccStorageService =
                bindToService(
                        new ComponentName(
                                mContext.getPackageName(),
                                "com.android.cts.pcc.featuretests.services.NonPccStorageService"));
    }

    @After
    public void tearDown() throws Exception {
        if (mNonPccConnection != null) {
            try {
                mContext.unbindService(mNonPccConnection);
            } catch (IllegalArgumentException e) {
                // Ignore if not bound
            }
        }
    }

    @Test
    public void testGetFilesDir_nonPccService_doesNotContainPcc() throws RemoteException {
        String nonPccStorageServiceFilesDirString = mNonPccStorageService.getFilesDirString();

        assertThat(nonPccStorageServiceFilesDirString).doesNotContain(PCC_DIR_SUFFIX);
    }

    @Test
    public void testWritePermissions_nonPccService_sandboxesAreIsolated() throws RemoteException {
        File nonPccDir = mContext.getDataDir();
        File nonPccFilesDir = mContext.getFilesDir();
        File pccFileDir = new File(nonPccDir + PCC_DIR_SUFFIX, "files");
        String fileName = "test.txt";
        File normalFile = new File(nonPccFilesDir, fileName);
        File pccFile = new File(pccFileDir, fileName);

        try {
            ServiceSpecificException e =
                    assertThrows(
                            ServiceSpecificException.class,
                            () -> {
                                mNonPccStorageService.canWriteToFile(pccFile.getAbsolutePath());
                            });
            assertThat(e.errorCode).isEqualTo(ERROR_WRITE_FAILED);

            String normalWrittenPath =
                    mNonPccStorageService.canWriteToFile(normalFile.getAbsolutePath());
            assertThat(normalWrittenPath).isEqualTo(normalFile.getAbsolutePath());
        } finally {
            // PCC file can not be accessed so an exception is expected
            deleteIgnoreException(pccFile);
            deleteThrowException(normalFile);
        }
    }

    private IStorageTestService bindToService(ComponentName component) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] binder = new IBinder[1];
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        binder[0] = service;
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        if (component.getClassName().contains("NonPcc")) {
            mNonPccConnection = connection;
        }

        mContext.bindService(
                new Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE);
        if (!latch.await(BIND_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to bind to service: " + component);
        }
        return IStorageTestService.Stub.asInterface(binder[0]);
    }
}
