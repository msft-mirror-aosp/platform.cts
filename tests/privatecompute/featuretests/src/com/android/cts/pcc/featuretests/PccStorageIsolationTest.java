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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.app.privatecompute.PccClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.cts.pcc.featuretests.services.IStorageTestService;
import com.android.cts.pcc.featuretests.services.PccStorageService;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccStorageIsolationTest {

    private static final String TAG = "PccStorageIsolationTest";

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PCC_DIR_SUFFIX = "-pcc";
    private static final int BIND_SERVICE_TIMEOUT_SECONDS = 10;
    private Context mContext;
    private IStorageTestService mNonPccStorageService;
    private ServiceConnection mNonPccConnection;
    private final List<ServiceConnection> mPccConnections = new ArrayList<>();

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
    public void tearDown() {
        // Unbind the non-PCC service if you used it
        if (mNonPccConnection != null) {
            mContext.unbindService(mNonPccConnection);
            mNonPccConnection = null;
        }

        // Unbind ALL PCC connections we made during this test
        for (ServiceConnection conn : mPccConnections) {
            mContext.unbindService(conn);
        }
        mPccConnections.clear();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testGetFilesDir_nonPccService_doesNotContainPcc() throws RemoteException {
        String nonPccStorageServiceFilesDirString = mNonPccStorageService.getFilesDirString();

        assertThat(nonPccStorageServiceFilesDirString).doesNotContain(PCC_DIR_SUFFIX);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccService_cannotOpenNonPccFileByPath() throws Exception {
        File nonPccFile = new File(mContext.getFilesDir(), "test_path_open.txt");
        try {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(nonPccFile)) {
                fos.write("hello".getBytes());
            }

            CountDownLatch disconnectLatch =
                    sendPccCommand(
                            PccStorageService.COMMAND_CAN_OPEN_FILE_BY_PATH,
                            null,
                            nonPccFile.getAbsolutePath());

            // Wait a short bit. If the service reads the file, it will call System.exit(1)
            // and the latch will count down.
            // If SELinux blocks it, it catches the exception and lives.
            boolean didCrash = disconnectLatch.await(2, TimeUnit.SECONDS);

            assertThat(didCrash).isFalse();
        } finally {
            deleteThrowException(nonPccFile);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccService_canReadFilePassedAsFdFromNonPcc() throws Exception {
        File nonPccFile = new File(mContext.getFilesDir(), "test_fd_pass.txt");
        try {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(nonPccFile)) {
                fos.write("hello".getBytes());
            }

            try (ParcelFileDescriptor pfd =
                    ParcelFileDescriptor.open(nonPccFile, ParcelFileDescriptor.MODE_READ_ONLY)) {

                CountDownLatch disconnectLatch =
                        sendPccCommand(PccStorageService.COMMAND_READ_FD, pfd, null);

                // If it fails to read the FD, it will System.exit(1) and trigger the latch.
                boolean didCrash = disconnectLatch.await(2, TimeUnit.SECONDS);

                // Assert it did NOT crash (Meaning it successfully read the FD)
                assertThat(didCrash).isFalse();
            }
        } finally {
            deleteThrowException(nonPccFile);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testHostApp_cannotReadPccFileByPath() throws Exception {
        // Tradefed TargetPreparer already created this file before the test started.
        String hostDataDir = mContext.getApplicationInfo().dataDir;
        File pccFilesDir = new File(hostDataDir + "-pcc", "files");
        File pccFile = new File(pccFilesDir, "test_pcc_file.dat");

        try {
            // Attempt to read the file directly from the Host App
            new FileInputStream(pccFile);

            // If we reach this line, the sandbox failed to protect the PCC data!
            fail("VULNERABILITY: Host app successfully opened the PCC file!");
        } catch (Exception e) {
            // EXPECTED BEHAVIOR!
            // The Host App (UID 10129) was blocked from reading the PCC file by
            // App Data Isolation (Mount Namespaces) and SELinux.
            Log.i(TAG, "Successfully blocked host app from reading PCC file.", e);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
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

    private CountDownLatch sendPccCommand(String command, ParcelFileDescriptor pfd, String filePath)
            throws Exception {
        BlockingQueue<IBinder> binderQueue = new LinkedBlockingQueue<>();
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        binderQueue.offer(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        // This fires if the PCC Service process crashes!
                        disconnectLatch.countDown();
                    }
                };

        // Save the connection so we can unbind it in tearDown()
        mPccConnections.add(connection);

        ComponentName serviceComponent =
                new ComponentName(mContext.getPackageName(), PccStorageService.class.getName());
        mContext.bindService(
                new Intent().setComponent(serviceComponent), connection, Context.BIND_AUTO_CREATE);

        IBinder binder = binderQueue.poll(BIND_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull("Failed to bind to PCC service", binder);

        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        data.putString(PccStorageService.EXTRA_COMMAND, command);
        if (pfd != null) {
            data.putParcelable(PccStorageService.EXTRA_PFD, pfd);
        }
        if (filePath != null) {
            data.putString(PccStorageService.EXTRA_FILE_PATH, filePath);
        }

        pccClient.sendData(data);

        // Simply return the latch. We stay bound!
        return disconnectLatch;
    }
}
