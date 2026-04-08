/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.privatecompute.PccClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.os.storage.FileManager;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.pcc.featuretests.services.PccStorageService;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccDataMigrationTest {

    private static final String TAG = "PccDataMigrationTest";

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int BIND_SERVICE_TIMEOUT_SECONDS = 10;
    private static final int DISCONNECT_TIMEOUT_SECONDS = 5;

    private Context mContext;
    private final List<ServiceConnection> mPccConnections = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(),
                "cmd pcc_sandbox enable-trust-instrumented-clients");
    }

    @After
    public void tearDown() throws Exception {
        for (ServiceConnection conn : mPccConnections) {
            mContext.unbindService(conn);
        }
        mPccConnections.clear();
        SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(),
                "cmd pcc_sandbox disable-trust-instrumented-clients");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccService_canStartFileOperationAndReceiveBroadcast() throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ResultReceiver rr =
                new ResultReceiver(new Handler(Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        resultLatch.countDown();
                    }
                };

        sendPccCommand(PccStorageService.COMMAND_START_FILE_OPERATION_AND_LISTEN, null, rr);

        // If the PCC Service successfully receives the broadcast, it calls ResultReceiver.send()
        boolean success = resultLatch.await(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(success).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testHostStartsFileMove_PccListensForCompletion() throws Exception {
        FileManager fileManager = mContext.getSystemService(FileManager.class);
        assertNotNull("FileManager must be available", fileManager);

        File dummyFile = new File(mContext.getFilesDir(), "dummy_move_src.txt");
        try {
            dummyFile.createNewFile();
        } catch (Exception e) {
            fail("Failed to create dummy file to move");
        }

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                            .setSource(new AppDataFileSource(dummyFile))
                            .setTarget(new PccTarget("dummy_dest_from_host"))
                            .build();

            FileOperationEnqueueResult enqueueResult = fileManager.enqueueOperation(request);
            String requestId = enqueueResult.getRequestId();

            Bundle extras = new Bundle();
            extras.putString(PccStorageService.EXTRA_REQUEST_ID, requestId);

            CountDownLatch resultLatch = new CountDownLatch(1);
            ResultReceiver rr =
                    new ResultReceiver(new Handler(Looper.getMainLooper())) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            resultLatch.countDown();
                        }
                    };

            sendPccCommand(PccStorageService.COMMAND_LISTEN_FOR_FILE_OPERATION, extras, rr);

            // Wait for the PCC process to send back result
            boolean success = resultLatch.await(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(success).isTrue();
        } finally {
            deleteIgnoreException(dummyFile);
        }
    }

    private void sendPccCommand(String command, Bundle extras, ResultReceiver rr) throws Exception {
        BlockingQueue<IBinder> binderQueue = new LinkedBlockingQueue<>();

        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        binderQueue.offer(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
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
        if (rr != null) {
            data.putParcelable(PccStorageService.EXTRA_RESULT_RECEIVER, toPlainResultReceiver(rr));
        }
        if (extras != null) {
            data.putAll(extras);
        }

        pccClient.sendData(data);
    }

    /**
     * Converts a ResultReceiver subclass (like an anonymous inner class) into a plain framework
     * ResultReceiver. This is necessary when passing the receiver to PCC sandbox process to avoid
     * custom parcelables in the Bundle.
     */
    private ResultReceiver toPlainResultReceiver(ResultReceiver rr) {
        Parcel parcel = Parcel.obtain();
        rr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ResultReceiver plainRR = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return plainRR;
    }
}
