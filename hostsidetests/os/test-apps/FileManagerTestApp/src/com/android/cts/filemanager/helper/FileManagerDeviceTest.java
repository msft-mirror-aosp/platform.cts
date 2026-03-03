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

package com.android.cts.filemanager.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.privatecompute.PccClient;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.storage.FileManager;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class FileManagerDeviceTest {
    private static final String TAG = "FileManagerDeviceTest";
    private Context mContext;
    private FileManager mFileManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mFileManager = mContext.getSystemService(FileManager.class);
    }

    @Test
    public void testTriggerMoveFile() throws Exception {
        String fileName = "test_file.txt";
        String targetPrefix = "migrated";
        File sourceFile = new File(mContext.getDataDir(), "files/" + fileName);
        sourceFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write("test content".getBytes());
        }

        CountDownLatch latch = new CountDownLatch(1);
        final FileOperationResult[] operationResult = new FileOperationResult[1];

        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (FileManager.ACTION_FILE_OPERATION_COMPLETED.equals(
                                intent.getAction())) {
                            operationResult[0] =
                                    intent.getParcelableExtra(
                                            FileManager.EXTRA_RESULT, FileOperationResult.class);
                            latch.countDown();
                        }
                    }
                };

        mContext.registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                            .setSource(new AppDataFileSource(sourceFile))
                            .setTarget(new PccTarget(targetPrefix))
                            .setRegisterCompletionListener(true)
                            .build();

            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertTrue("Enqueue failed", enqueueResult.isSuccessful());

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Operation timed out", completed);
            assertEquals(
                    "Operation failed: " + operationResult[0].getErrorMessage(),
                    FileOperationResult.ERROR_NONE,
                    operationResult[0].getErrorCode());

            // Trigger PCC verification
            triggerPccVerification(targetPrefix + "/" + fileName);

        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testTriggerMoveFolder() throws Exception {
        String folderName = "test_folder";
        String targetPrefix = "archived";
        File sourceFolder = new File(mContext.getDataDir(), "files/" + folderName);
        sourceFolder.mkdirs();
        File file1 = new File(sourceFolder, "file1.txt");
        try (FileOutputStream fos = new FileOutputStream(file1)) {
            fos.write("content1".getBytes());
        }

        CountDownLatch latch = new CountDownLatch(1);
        final FileOperationResult[] operationResult = new FileOperationResult[1];

        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (FileManager.ACTION_FILE_OPERATION_COMPLETED.equals(
                                intent.getAction())) {
                            operationResult[0] =
                                    intent.getParcelableExtra(
                                            FileManager.EXTRA_RESULT, FileOperationResult.class);
                            latch.countDown();
                        }
                    }
                };

        mContext.registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                            .setSource(new AppDataFileSource(sourceFolder))
                            .setTarget(new PccTarget(targetPrefix))
                            .setRegisterCompletionListener(true)
                            .build();

            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertTrue("Enqueue failed", enqueueResult.isSuccessful());

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Operation timed out", completed);
            assertEquals(
                    "Operation failed: " + operationResult[0].getErrorMessage(),
                    FileOperationResult.ERROR_NONE,
                    operationResult[0].getErrorCode());

            // Trigger PCC verification
            triggerPccVerification(targetPrefix + "/" + folderName + "/file1.txt");

        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testTriggerCopyFile() throws Exception {
        String fileName = "test_copy_file.txt";
        String targetPrefix = "copied";
        File sourceFile = new File(mContext.getDataDir(), "files/" + fileName);
        sourceFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write("test content".getBytes());
        }

        CountDownLatch latch = new CountDownLatch(1);
        final FileOperationResult[] operationResult = new FileOperationResult[1];

        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (FileManager.ACTION_FILE_OPERATION_COMPLETED.equals(
                                intent.getAction())) {
                            operationResult[0] =
                                    intent.getParcelableExtra(
                                            FileManager.EXTRA_RESULT, FileOperationResult.class);
                            latch.countDown();
                        }
                    }
                };

        mContext.registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                            .setSource(new AppDataFileSource(sourceFile))
                            .setTarget(new PccTarget(targetPrefix))
                            .setRegisterCompletionListener(true)
                            .build();

            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertTrue("Enqueue failed", enqueueResult.isSuccessful());

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Operation timed out", completed);
            assertEquals(
                    "Operation failed: " + operationResult[0].getErrorMessage(),
                    FileOperationResult.ERROR_NONE,
                    operationResult[0].getErrorCode());

            // Assert source file still exists
            assertTrue("Source file should still exist after copy", sourceFile.exists());

            // Trigger PCC verification
            triggerPccVerification(targetPrefix + "/" + fileName);

        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testTriggerCopyFolder() throws Exception {
        String folderName = "test_copy_folder";
        String targetPrefix = "copied_folder";
        File sourceFolder = new File(mContext.getDataDir(), "files/" + folderName);
        sourceFolder.mkdirs();
        File file1 = new File(sourceFolder, "file1.txt");
        try (FileOutputStream fos = new FileOutputStream(file1)) {
            fos.write("content1".getBytes());
        }

        CountDownLatch latch = new CountDownLatch(1);
        final FileOperationResult[] operationResult = new FileOperationResult[1];

        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (FileManager.ACTION_FILE_OPERATION_COMPLETED.equals(
                                intent.getAction())) {
                            operationResult[0] =
                                    intent.getParcelableExtra(
                                            FileManager.EXTRA_RESULT, FileOperationResult.class);
                            latch.countDown();
                        }
                    }
                };

        mContext.registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                            .setSource(new AppDataFileSource(sourceFolder))
                            .setTarget(new PccTarget(targetPrefix))
                            .setRegisterCompletionListener(true)
                            .build();

            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertTrue("Enqueue failed", enqueueResult.isSuccessful());

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Operation timed out", completed);
            assertEquals(
                    "Operation failed: " + operationResult[0].getErrorMessage(),
                    FileOperationResult.ERROR_NONE,
                    operationResult[0].getErrorCode());

            // Assert source folder and content still exist
            assertTrue("Source folder should still exist after copy", sourceFolder.exists());
            assertTrue("Source file inside folder should still exist after copy", file1.exists());

            // Trigger PCC verification
            triggerPccVerification(targetPrefix + "/" + folderName + "/file1.txt");

        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testTriggerMoveFileOverwrite() throws Exception {
        String fileName = "test_overwrite.txt";
        String targetPrefix = "overwritten";
        String targetRelativePath = targetPrefix + "/" + fileName;

        // Step 1: Prepare target file with "old content" in PCC
        Bundle prepareData = new Bundle();
        prepareData.putString("file", targetRelativePath);
        prepareData.putString("content", "old content");
        sendActionToPcc("prepare", prepareData);

        // Step 2: Create source file with "new content"
        File sourceFile = new File(mContext.getDataDir(), "files/" + fileName);
        sourceFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write("new content".getBytes());
        }

        CountDownLatch latch = new CountDownLatch(1);
        final FileOperationResult[] operationResult = new FileOperationResult[1];

        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (FileManager.ACTION_FILE_OPERATION_COMPLETED.equals(
                                intent.getAction())) {
                            operationResult[0] =
                                    intent.getParcelableExtra(
                                            FileManager.EXTRA_RESULT, FileOperationResult.class);
                            latch.countDown();
                        }
                    }
                };

        mContext.registerReceiver(
                receiver,
                new IntentFilter(FileManager.ACTION_FILE_OPERATION_COMPLETED),
                Context.RECEIVER_EXPORTED);

        try {
            FileOperationRequest request =
                    new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                            .setSource(new AppDataFileSource(sourceFile))
                            .setTarget(new PccTarget(targetPrefix))
                            .setRegisterCompletionListener(true)
                            .build();

            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertTrue("Enqueue failed", enqueueResult.isSuccessful());

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Operation timed out", completed);
            assertEquals(
                    "Operation failed: " + operationResult[0].getErrorMessage(),
                    FileOperationResult.ERROR_NONE,
                    operationResult[0].getErrorCode());

            // Step 4: Trigger PCC verification and check for "new content"
            triggerPccVerification(targetRelativePath, "new content");

        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private void triggerPccVerification(String expectedFile) throws Exception {
        triggerPccVerification(expectedFile, null);
    }

    private void triggerPccVerification(String expectedFile, String expectedContent)
            throws Exception {
        Bundle data = new Bundle();
        data.putString("expected_file", expectedFile);
        if (expectedContent != null) {
            data.putString("expected_content", expectedContent);
        }
        sendActionToPcc("verify", data);
    }

    private void sendActionToPcc(String action, Bundle data) throws Exception {
        CountDownLatch pccLatch = new CountDownLatch(1);
        Intent bindIntent = new Intent(mContext, PccTestService.class);
        data.putString("action", action);
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        PccClient client = PccClient.createInstance(mContext, service);
                        try {
                            client.sendData(data);
                        } finally {
                            pccLatch.countDown();
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        mContext.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);
        if (!pccLatch.await(5, TimeUnit.SECONDS)) {
            fail("Failed to connect to PCC service for action: " + action);
        }
        mContext.unbindService(connection);
    }
}
