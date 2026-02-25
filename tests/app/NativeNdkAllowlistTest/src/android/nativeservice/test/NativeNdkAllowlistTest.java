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

package android.nativeservice.test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nativeservice.simple.ISimpleNativeService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE,
})
@RunWith(Parameterized.class)
public class NativeNdkAllowlistTest {
    static final long TIMEOUT_MS = 10000;
    static final String TARGET_PACKAGE = "android.nativeservice.allowlist.cts";
    static final String SIMPLE_NATIVE_SERVICE_CLASS_NAME = "android.nativeservice.allowlist.cts.SimpleNativeService";

    private ISimpleNativeService mService;
    private ExecutorService mPipeExecutor;
    private ParcelFileDescriptor mStdoutRead;
    private ParcelFileDescriptor mStderrRead;
    private final String[] mStdio = new String[2];
    private final List<Future<?>> mFutures = new ArrayList<>();

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    static {
        System.loadLibrary("ctsnativendkallowlisttest_jni");
    }

    private static native String[][] getFileContents();

    @Parameters(name = "{0}")
    public static Collection<String[]> getBannedFunctions() {
        List<String[]> bannedFunctions = new ArrayList<>();
        String[][] contents = getFileContents();
        for (String[] file : contents) {
            String symbolFileName = file[0];
            String content = file[1];
            for (String line : content.split("\n")) {
                if (!line.trim().isEmpty()) {
                    bannedFunctions.add(new String[]{line.trim(), symbolFileName});
                }
            }
        }
        return bannedFunctions;
    }

    @Parameter(0)
    public String mBannedFunction;

    @Parameter(1)
    public String mSymbolFileName;

    @Before
    public void setUp() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(TARGET_PACKAGE, SIMPLE_NATIVE_SERVICE_CLASS_NAME));
        IBinder binder = mServiceRule.bindService(intent);
        mService = ISimpleNativeService.Stub.asInterface(binder);

        setupPipes();
    }

    private void setupPipes() throws IOException, RemoteException {
        mPipeExecutor = Executors.newFixedThreadPool(2);

        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor[] pipe2 = ParcelFileDescriptor.createPipe();
        mStdoutRead = pipe[0];
        mStderrRead = pipe2[0];

        try (ParcelFileDescriptor stdoutWrite = pipe[1];
             ParcelFileDescriptor stderrWrite = pipe2[1]) {
            mService.redirectStdio(stdoutWrite, stderrWrite);
        }

        mFutures.add(mPipeExecutor.submit(() -> {
            try (FileInputStream fis = new FileInputStream(mStdoutRead.getFileDescriptor());
                 Scanner scanner = new Scanner(fis)) {
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine());
                }
                mStdio[0] = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        mFutures.add(mPipeExecutor.submit(() -> {
            try (FileInputStream fis = new FileInputStream(mStderrRead.getFileDescriptor());
                 Scanner scanner = new Scanner(fis)) {
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine());
                }
                mStdio[1] = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void waitForPipeShutdown() throws InterruptedException, ExecutionException {
        mPipeExecutor.shutdown();
        assertTrue(mPipeExecutor.awaitTermination(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        for (Future<?> future : mFutures) {
            future.get();
        }
    }

    @After
    public void tearDown() throws IOException {
        if (mPipeExecutor != null) {
            mPipeExecutor.shutdownNow();
        }
        if (mStdoutRead != null) {
            mStdoutRead.close();
        }
        if (mStderrRead != null) {
            mStderrRead.close();
        }
    }

    @Test
    // Test that functions in the generated blocklist are not allowed in artless processes.
    // The test calls a function that is not allowed in artless processes.
    //
    // The expected (passing) behavior is: The function call is denied, and the process crashes
    // with the expected message.
    //
    // If the function call is NOT denied, the test will fail in one of the following ways:
    // a. Hangs, which is detected by a timeout.
    // b. Crashes with an unexpected message, which is detected by the stderr assertion.
    // c. Returns normally, which is detected by assertThrows.
    public void testBannedFunctions() throws Exception {
        if (!mSymbolFileName.isEmpty()) {
            String libName = mSymbolFileName.replace(".map.txt", ".so");
            mService.loadLibrary(libName);
        }
        assertThrows(RemoteException.class, () -> mService.callFunc(mBannedFunction));
        waitForPipeShutdown();
        assertThat(mStdio[0] + mStdio[1]).contains("is not allowed in artless processes");
    }
}
