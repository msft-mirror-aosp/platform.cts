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

package com.android.cts.pcc.featuretests.services;

import static com.android.cts.pcc.common.StorageTestUtils.writeFile;

import android.app.privatecompute.PccService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.os.storage.FileManager;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PccStorageService extends PccService {
    private final List<BroadcastReceiver> mRegisteredReceivers = new ArrayList<>();
    private static final String TAG = "PccStorageService";
    public static final String EXTRA_COMMAND = "command";
    public static final String COMMAND_WRITE_FILE = "write_file";
    public static final String COMMAND_WRITE_DE_FILE = "write_de_file";
    public static final String COMMAND_WRITE_CACHE_FILE = "write_cache_file";
    public static final String COMMAND_CLEANUP = "cleanup";
    public static final String COMMAND_READ_FD = "read_fd";
    public static final String COMMAND_CAN_OPEN_FILE_BY_PATH = "can_open_file_by_path";
    public static final String COMMAND_START_FILE_OPERATION_AND_LISTEN =
            "start_file_operation_and_listen";
    public static final String COMMAND_LISTEN_FOR_FILE_OPERATION = "listen_for_file_operation";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_RESULT_RECEIVER = "result_receiver";

    public static final String EXTRA_FILE_SIZE_BYTES = "file_size_bytes";
    public static final String EXTRA_PFD = "pfd";
    public static final String EXTRA_FILE_PATH = "file_path";

    public static final String PCC_FILE = "test_pcc_file.dat";
    private static final String PCC_CACHE_FILE = "test_pcc_cache_file.dat";
    private static final String PCC_DE_FILE = "test_pcc_de_file.dat";

    @Override
    public void onReceiveData(Bundle data, String packageName) {
        String command = data.getString(EXTRA_COMMAND);
        long size = data.getLong(EXTRA_FILE_SIZE_BYTES);

        if (COMMAND_WRITE_FILE.equals(command)) {
            writeFile(getFilesDir(), PCC_FILE, size);
        } else if (COMMAND_WRITE_DE_FILE.equals(command)) {
            writeFile(createDeviceProtectedStorageContext().getFilesDir(), PCC_DE_FILE, size);
        } else if (COMMAND_WRITE_CACHE_FILE.equals(command)) {
            writeFile(getCacheDir(), PCC_CACHE_FILE, size);
        } else if (COMMAND_CLEANUP.equals(command)) {
            cleanup();
        } else if (COMMAND_READ_FD.equals(command)) {
            ParcelFileDescriptor pfd = data.getParcelable(EXTRA_PFD, ParcelFileDescriptor.class);
            if (pfd != null) {
                try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                    int val = fis.read();
                    if (val == -1) {
                        Log.e(TAG, "Failed to read FD: EOF");
                        System.exit(1); // CRASH! Test expects to be able to read this.
                    }
                    Log.i(TAG, "Successfully read file via FD");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read fd", e);
                    System.exit(1); // CRASH! Test expects to be able to read this.
                }
            } else {
                System.exit(1); // CRASH! FD was null
            }

        } else if (COMMAND_CAN_OPEN_FILE_BY_PATH.equals(command)) {
            String path = data.getString(EXTRA_FILE_PATH);
            if (path != null) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    fis.read();
                    // VULNERABILITY! We successfully read an untrusted app file!
                    Log.e(TAG, "VULNERABILITY: Successfully opened file by path: " + path);
                    System.exit(1);
                } catch (IOException | SecurityException e) {
                    // EXPECTED BEHAVIOR! SELinux blocked the read.
                    Log.i(TAG, "Successfully blocked from opening file by path: " + path, e);
                }
            }
        } else if (COMMAND_LISTEN_FOR_FILE_OPERATION.equals(command)) {
            String reqId = data.getString(EXTRA_REQUEST_ID);
            ResultReceiver rr = data.getParcelable(EXTRA_RESULT_RECEIVER, ResultReceiver.class);
            if (reqId != null) {
                FileManager fileManager = getSystemService(FileManager.class);
                if (fileManager != null) {
                    listenForFileOperationCompletion(fileManager, reqId, rr);
                }
            }
        } else if (COMMAND_START_FILE_OPERATION_AND_LISTEN.equals(command)) {
            FileManager fileManager = getSystemService(FileManager.class);
            ResultReceiver rr = data.getParcelable(EXTRA_RESULT_RECEIVER, ResultReceiver.class);
            if (fileManager != null) {
                File dummyFile = new File(getFilesDir(), "dummy.txt");
                try {
                    dummyFile.createNewFile();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create dummy file", e);
                }

                FileOperationRequest request =
                        new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                                .setSource(new AppDataFileSource(dummyFile))
                                .setTarget(new PccTarget("dummy_dest"))
                                .build();

                FileOperationEnqueueResult enqueueResult = fileManager.enqueueOperation(request);
                String requestId = enqueueResult.getRequestId();

                listenForFileOperationCompletion(fileManager, requestId, rr);
            }
        }
    }

    private void listenForFileOperationCompletion(
            FileManager fileManager, String requestId, ResultReceiver rr) {
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String receivedId = intent.getStringExtra(FileManager.EXTRA_REQUEST_ID);
                        if (requestId.equals(receivedId)) {
                            Log.i(
                                    TAG,
                                    "Successfully received broadcast in PCC process for"
                                            + " requestId: "
                                            + requestId);
                            if (rr != null) {
                                rr.send(0, null);
                            }
                        }
                    }
                };

        registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);
        mRegisteredReceivers.add(receiver);

        fileManager.registerCompletionListener(requestId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (BroadcastReceiver receiver : mRegisteredReceivers) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                // Ignore if not registered
            }
        }
        mRegisteredReceivers.clear();
    }

    private void cleanup() {
        try {
            File file = new File(getFilesDir(), PCC_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_FILE, e);
        }
        try {
            File file = new File(createDeviceProtectedStorageContext().getFilesDir(), PCC_DE_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_DE_FILE, e);
        }
        try {
            File file = new File(getCacheDir(), PCC_CACHE_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_CACHE_FILE, e);
        }
    }
}
