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

package com.android.cts.nativeservice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nativeservice.IGTestNativeService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE
})
public class NativeServiceGTestRunner {
    private final class NativeServiceGTestConnection implements ServiceConnection {
        private final ParcelFileDescriptor mPfd;
        private final File mOutputFile;
        private final List<String> mArgs;
        private volatile String mErrorMessage;

        NativeServiceGTestConnection(File outputFile, ParcelFileDescriptor pfd, List<String> args) {
            mOutputFile = outputFile;
            mPfd = pfd;
            mArgs = args;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IGTestNativeService nativeService = IGTestNativeService.Stub.asInterface(service);
            Log.i(TAG, "Running GTest in native service");
            try (ParcelFileDescriptor dupPfd = mPfd.dup()) {
                nativeService.runGTest(dupPfd, mArgs);
            } catch (RemoteException e) {
                try {
                    mErrorMessage =
                            "GTest failed (RemoteException):\n"
                                    + Files.readString(mOutputFile.toPath());
                } catch (IOException ioException) {
                    mErrorMessage =
                            "GTest failed (RemoteException) and failed to read output file: "
                                    + ioException.getMessage();
                }
            } catch (IOException e) {
                mErrorMessage = "Error with output file: " + e.getMessage();
            } finally {
                Log.i(TAG, "Finished running GTest");
                mConnectionLatch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Service disconnected: " + name);
            mErrorMessage = "Service disconnected: " + name;
            mConnectionLatch.countDown();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "Binding died: " + name);
            mErrorMessage = "Binding died: " + name;
            mConnectionLatch.countDown();
        }
    }

    private static final String TAG = "NativeServiceGTestRunner";

    private static final long BIND_TIMEOUT_MS = 30000;

    private static final String ARG_TARGET_PACKAGE = "native-service-package";
    private static final String ARG_NATIVE_SERVICE_CLASS = "native-service-class";

    private Context mContext;
    private final CountDownLatch mConnectionLatch = new CountDownLatch(1);
    private NativeServiceGTestConnection mConnection;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() {
        if (mConnection != null) {
            mContext.unbindService(mConnection);
            mConnection = null;
        }
    }

    @Test
    public void runGTestInNativeService() throws InterruptedException, IOException {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String targetPackage = arguments.getString(ARG_TARGET_PACKAGE);
        if (targetPackage == null) {
            throw new IllegalArgumentException("Missing required argument: " + ARG_TARGET_PACKAGE);
        }
        String nativeServiceClass =
                arguments.getString(
                        ARG_NATIVE_SERVICE_CLASS, targetPackage + ".GTestNativeService");

        List<String> args = new ArrayList<>();
        for (String key : arguments.keySet()) {
            if (ARG_TARGET_PACKAGE.equals(key) || ARG_NATIVE_SERVICE_CLASS.equals(key)) {
                continue;
            }
            String value = arguments.getString(key);
            if (value != null) {
                args.add("--" + key + "=" + value);
            }
        }

        File outputFile = new File(mContext.getCacheDir(), "gtest_result.xml");
        try (ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(
                        outputFile,
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE)) {

            mConnection = new NativeServiceGTestConnection(outputFile, pfd, args);

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(targetPackage, nativeServiceClass));

            Log.i(TAG, "Binding to native service " + intent.getComponent() + "...");
            boolean success = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            if (!success) {
                throw new RuntimeException("Failed to bind to native service.");
            }

            if (!mConnectionLatch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timed out waiting for native service connection.");
            }

            if (mConnection.mErrorMessage != null) {
                throw new RuntimeException(mConnection.mErrorMessage);
            }
        }
    }
}
