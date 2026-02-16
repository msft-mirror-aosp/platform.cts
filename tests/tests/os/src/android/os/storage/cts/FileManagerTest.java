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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.privatecompute.flags.Flags;
import android.content.Context;
import android.os.Parcel;
import android.os.storage.FileManager;
import android.os.storage.operations.FileOperationEnqueueResult;
import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.FileOperationResult;
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
public class FileManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private FileManager mFileManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mFileManager = mContext.getSystemService(FileManager.class);
    }

    @Test
    public void testFileManagerExists() {
        assertNotNull("FileManager should be available when flag is enabled", mFileManager);
    }

    @Test
    public void testMaxReportedFailures() {
        assertEquals(200, FileManager.getMaxReportedFailures());
    }

    @Test
    public void testFileOperationEnqueueResult_success() {
        String requestId = "test_request_id";
        FileOperationEnqueueResult result = new FileOperationEnqueueResult(requestId);

        assertTrue(result.isSuccessful());
        assertEquals(requestId, result.getRequestId());
        assertEquals(FileOperationResult.ERROR_NONE, result.getErrorCode());
    }

    @Test
    public void testFileOperationEnqueueResult_failure() {
        int errorCode = FileOperationResult.ERROR_BUSY;
        FileOperationEnqueueResult result = new FileOperationEnqueueResult(errorCode);

        assertTrue(!result.isSuccessful());
        assertNull(result.getRequestId());
        assertEquals(errorCode, result.getErrorCode());
    }

    @Test
    public void testFileOperationEnqueueResult_parcelable() {
        String requestId = "test_request_id";
        FileOperationEnqueueResult original = new FileOperationEnqueueResult(requestId);

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            FileOperationEnqueueResult fromParcel =
                    FileOperationEnqueueResult.CREATOR.createFromParcel(parcel);

            assertEquals(original.getRequestId(), fromParcel.getRequestId());
            assertEquals(original.getErrorCode(), fromParcel.getErrorCode());
            assertEquals(original.isSuccessful(), fromParcel.isSuccessful());
            assertEquals(0, fromParcel.describeContents());
        } finally {
            parcel.recycle();
        }
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
            FileOperationEnqueueResult enqueueResult = mFileManager.enqueueOperation(request);
            assertNotNull(enqueueResult);
            if (enqueueResult.isSuccessful()) {
                String requestId = enqueueResult.getRequestId();
                assertNotNull(requestId);

                FileOperationResult result = mFileManager.fetchResult(requestId);
                assertNotNull(result);
                assertEquals(requestId, result.getRequestId());
                assertNotNull(result.getSource());
                assertNotNull(result.getTarget());
            }
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
        assertNull(mFileManager.fetchResult("non_existent_id"));
    }

    @Test
    public void testFileOperationRequest_builder() {
        File file = new File(mContext.getDataDir(), "test.txt");
        AppDataFileSource source = new AppDataFileSource(file);
        PccTarget target = new PccTarget("prefix");

        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                        .setSource(source)
                        .setTarget(target)
                        .build();

        assertEquals(FileOperationRequest.OPERATION_MOVE, request.getMode());
        assertEquals(
                file.getAbsolutePath(),
                ((AppDataFileSource) request.getSource()).getFile().getAbsolutePath());
        assertTrue(request.getTarget() instanceof PccTarget);
    }

    @Test
    public void testFileOperationRequest_builder_setCompletionListener() {
        File file = new File(mContext.getDataDir(), "test.txt");
        AppDataFileSource source = new AppDataFileSource(file);
        PccTarget target = new PccTarget("prefix");

        FileOperationRequest request =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(source)
                        .setTarget(target)
                        .setRegisterCompletionListener(true)
                        .build();

        assertNotNull(request);
        assertTrue(request.shouldRegisterCompletionListener());
    }

    @Test
    public void testFileOperationRequest_builder_missingSource() {
        try {
            new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                    .setTarget(new PccTarget())
                    .build();
            fail("Should have thrown IllegalStateException for missing source");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testFileOperationRequest_builder_missingTarget() {
        try {
            new FileOperationRequest.Builder(FileOperationRequest.OPERATION_MOVE)
                    .setSource(new AppDataFileSource(new File("/data/user/0/com.example/test")))
                    .build();
            fail("Should have thrown IllegalStateException for missing target");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testFileOperationRequest_parcelable() {
        File file = new File(mContext.getDataDir(), "test.txt");
        AppDataFileSource source = new AppDataFileSource(file);
        PccTarget target = new PccTarget("prefix");

        FileOperationRequest original =
                new FileOperationRequest.Builder(FileOperationRequest.OPERATION_COPY)
                        .setSource(source)
                        .setTarget(target)
                        .build();

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            FileOperationRequest fromParcel = FileOperationRequest.CREATOR.createFromParcel(parcel);

            assertEquals(original.getMode(), fromParcel.getMode());
            assertEquals(original.getSource().toString(), fromParcel.getSource().toString());
            assertEquals(original.getTarget().toString(), fromParcel.getTarget().toString());
            assertEquals(0, fromParcel.describeContents());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testFileManager_constants() {
        assertNotNull(FileManager.ACTION_FILE_OPERATION_COMPLETED);
        assertNotNull(FileManager.EXTRA_REQUEST_ID);
        assertNotNull(FileManager.EXTRA_RESULT);
    }

    @Test
    public void testFileOperationRequest_constants() {
        assertEquals(1, FileOperationRequest.OPERATION_MOVE);
        assertEquals(2, FileOperationRequest.OPERATION_COPY);
    }

    @Test
    public void testFileOperationResult_constants() {
        assertEquals(0, FileOperationResult.STATUS_UNKNOWN);
        assertEquals(1, FileOperationResult.STATUS_QUEUED);
        assertEquals(2, FileOperationResult.STATUS_IN_PROGRESS);
        assertEquals(3, FileOperationResult.STATUS_FINISHED);
        assertEquals(4, FileOperationResult.STATUS_FAILED);

        assertEquals(-1, FileOperationResult.ERROR_NONE);
        assertEquals(0, FileOperationResult.ERROR_UNKNOWN);
        assertEquals(1, FileOperationResult.ERROR_BUSY);
        assertEquals(2, FileOperationResult.ERROR_INVALID_REQUEST);
        assertEquals(3, FileOperationResult.ERROR_UNSUPPORTED_SOURCE);
        assertEquals(4, FileOperationResult.ERROR_UNSUPPORTED_TARGET);
        assertEquals(5, FileOperationResult.ERROR_PERMISSION_DENIED);
        assertEquals(6, FileOperationResult.ERROR_DISK_FULL);
    }

    @Test
    public void testAppDataFileSource() {
        File file = new File("/data/user/0/com.example/test.txt");
        AppDataFileSource source = new AppDataFileSource(file);

        assertEquals(file.getAbsolutePath(), source.getFile().getAbsolutePath());
        assertTrue(source.toString().contains(file.getAbsolutePath()));
    }

    @Test
    public void testAppDataFileSource_parcelable() {
        File file = new File("/data/user/0/com.example/test.txt");
        AppDataFileSource original = new AppDataFileSource(file);

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            AppDataFileSource fromParcel = AppDataFileSource.CREATOR.createFromParcel(parcel);

            assertEquals(
                    original.getFile().getAbsolutePath(), fromParcel.getFile().getAbsolutePath());
            assertEquals(0, fromParcel.describeContents());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testPccTarget() {
        PccTarget target1 = new PccTarget();
        assertTrue(target1.toString().contains("prefix="));

        String prefix = "my/prefix";
        PccTarget target2 = new PccTarget(prefix);
        assertTrue(target2.toString().contains(prefix));
    }

    @Test
    public void testPccTarget_parcelable() {
        String prefix = "my/prefix";
        PccTarget original = new PccTarget(prefix);

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            PccTarget fromParcel = PccTarget.CREATOR.createFromParcel(parcel);

            assertEquals(original.toString(), fromParcel.toString());
            assertEquals(0, fromParcel.describeContents());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testRegisterAndUnregisterCompletionListener() {
        String requestId = "test_request_id";
        // These calls should not throw exceptions
        mFileManager.registerCompletionListener(requestId);
        mFileManager.unregisterCompletionListener(requestId);
    }
}
