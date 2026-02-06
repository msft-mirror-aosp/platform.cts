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

package android.os.storage.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.privatecompute.flags.Flags;
import android.content.Context;
import android.os.storage.FilesManager;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
@SmallTest
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class FilesManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private FilesManager mFilesManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mFilesManager = mContext.getSystemService(FilesManager.class);
    }

    @Test
    public void testFilesManagerExists() {
        assertNotNull("FilesManager should be available when flag is enabled", mFilesManager);
    }

    @Test
    public void testConstants() {
        assertNotNull(FilesManager.ACTION_FILE_OPERATION_COMPLETED);
        assertNotNull(FilesManager.EXTRA_REQUEST_ID);
        assertNotNull(FilesManager.EXTRA_RESULT);
    }

    @Test
    public void testEnqueueOperation() throws Exception {
        File dummyFile = new File(mContext.getDataDir(), "test_file.txt");
        if (!dummyFile.exists()) {
            dummyFile.createNewFile();
        }

        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(new AppDataFileSource(dummyFile))
                        .setTarget(new PccTarget())
                        .build();

        try {
            FileOperationEnqueueResult result = mFilesManager.enqueueOperation(request);
            assertNotNull(result);
        } catch (RuntimeException e) {
            // It's possible the service throws if not fully implemented or permission denied,
            // but strictly speaking enqueueOperation should return a result or throw
            // RemoteException (wrapped in RuntimeException).
            // If the service is just a stub or not registered properly in the test environment,
            // this might fail.
            // For now, we assume the service is reachable.
            throw e;
        } finally {
            dummyFile.delete();
        }
    }

    @Test
    public void testFetchResult_unknownId() {
        assertNull(mFilesManager.fetchResult("non_existent_id"));
    }

    @Test
    public void testRegisterAndUnregisterCompletionListener() {
        String requestId = "test_request_id";
        // These calls should not throw exceptions
        mFilesManager.registerCompletionListener(requestId);
        mFilesManager.unregisterCompletionListener(requestId);
    }
}
