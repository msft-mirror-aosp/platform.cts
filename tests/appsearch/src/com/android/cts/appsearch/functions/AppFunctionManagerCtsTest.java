/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.app.appsearch.cts.functions;

import static android.app.appsearch.AppSearchResult.RESULT_INTERNAL_ERROR;
import static android.app.appsearch.AppSearchResult.RESULT_INVALID_ARGUMENT;
import static android.app.appsearch.AppSearchResult.RESULT_NOT_FOUND;
import static android.app.appsearch.AppSearchResult.RESULT_TIMED_OUT;
import static android.app.appsearch.AppSearchResult.RESULT_UNKNOWN_ERROR;
import static android.app.appsearch.functions.ExecuteAppFunctionResponse.PROPERTY_RESULT;
import static android.app.appsearch.testutil.functions.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate;
import static android.app.appsearch.testutil.functions.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.functions.AppFunctionManager;
import android.app.appsearch.functions.ExecuteAppFunctionRequest;
import android.app.appsearch.functions.ExecuteAppFunctionResponse;
import android.app.appsearch.testutil.functions.ActivityCreationSynchronizer;
import android.app.appsearch.testutil.functions.TestAppFunctionServiceLifecycleReceiver;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;

import com.android.appsearch.flags.Flags;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS)
public class AppFunctionManagerCtsTest {
    private static final String TARGET_PACKAGE = "com.android.cts.appsearch";
    private static final long SHORT_TIMEOUT_SECOND = 1;
    private static final long LONG_TIMEOUT_SECOND = 5;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private AppFunctionManager mAppFunctionManager;

    /** This rule establishes a shorter timeout for testing timeout scenarios. */
    @Rule
    public final DeviceConfigStateChangerRule mSetTimeoutRule =
            new DeviceConfigStateChangerRule(
                    mContext, "appsearch", "app_function_call_timeout_millis", "1000");

    @Before
    public void setup() {
        ActivityCreationSynchronizer.reset();
        TestAppFunctionServiceLifecycleReceiver.reset();
        AppSearchManager appSearchManager = mContext.getSystemService(AppSearchManager.class);
        mAppFunctionManager = appSearchManager.getAppFunctionManager();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_success() throws Exception {
        GenericDocument parameters = new GenericDocument.Builder("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build();

        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "add")
                        .setParameters(parameters)
                        .build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultValue().getResult().getPropertyLong(PROPERTY_RESULT))
                .isEqualTo(3);
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_failed_noSuchMethod() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "invalid").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResultCode()).isEqualTo(RESULT_NOT_FOUND);
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_onlyInvokeCallbackOnce() throws Exception {
        GenericDocument parameters = new GenericDocument.Builder("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build();
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "add_invokeCallbackTwice")
                        .setParameters(parameters)
                        .build();
        LinkedBlockingQueue<AppSearchResult<ExecuteAppFunctionResponse>> blockingQueue =
                new LinkedBlockingQueue<>();

        mAppFunctionManager.executeAppFunction(
                request, mContext.getMainExecutor(), blockingQueue::add);

        AppSearchResult<ExecuteAppFunctionResponse> response =
                blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultValue().getResult().getPropertyLong(PROPERTY_RESULT))
                .isEqualTo(3);

        // Each callback can only be invoked once.
        assertThat(blockingQueue.poll(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isNull();
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_otherPackage() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder("other.package", "someMethod").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        // Apps without the permission can only invoke functions from themselves.
        assertThat(response.getResultCode()).isEqualTo(AppSearchResult.RESULT_SECURITY_ERROR);
        assertServiceWasNotCreated();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_startActivity() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "startActivity").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(ActivityCreationSynchronizer.waitForActivityCreated(5, TimeUnit.SECONDS))
                .isTrue();
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_throwsException() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "throwException").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResultCode()).isEqualTo(RESULT_UNKNOWN_ERROR);
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_onRemoteProcessKilled() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "kill").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResultCode()).isEqualTo(RESULT_INTERNAL_ERROR);
        // The process that the service was just crashed. Validate the service is not created again.
        TestAppFunctionServiceLifecycleReceiver.reset();
        assertServiceWasNotCreated();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_timedOut() throws Exception {
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "notInvokeCallback").build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResultCode()).isEqualTo(RESULT_TIMED_OUT);
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_success_async() throws Exception {
        GenericDocument parameters = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build();
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder(TARGET_PACKAGE, "addAsync")
                        .setParameters(parameters)
                        .build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultValue().getResult().getPropertyLong(PROPERTY_RESULT))
                .isEqualTo(3);
        assertServiceDestroyed();
    }

    @ApiTest(apis = {"android.app.appsearch.functions.AppFunctionManager#executeAppFunction"})
    @Test
    public void executeAppFunction_emptyPackage() throws Exception {
        GenericDocument parameters = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build();
        ExecuteAppFunctionRequest request =
                new ExecuteAppFunctionRequest.Builder("", "add")
                        .setParameters(parameters)
                        .build();

        AppSearchResult<ExecuteAppFunctionResponse> response = executeAppFunctionAndWait(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResultCode()).isEqualTo(RESULT_INVALID_ARGUMENT);
        assertServiceWasNotCreated();
    }

    private AppSearchResult<ExecuteAppFunctionResponse> executeAppFunctionAndWait(
            ExecuteAppFunctionRequest request) throws InterruptedException {
        LinkedBlockingQueue<AppSearchResult<ExecuteAppFunctionResponse>> blockingQueue =
                new LinkedBlockingQueue<>();
        mAppFunctionManager.executeAppFunction(
                request, mContext.getMainExecutor(), blockingQueue::add);
        return blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS);
    }

    /**
     * Verifies that the service is unbound by asserting the service was destroyed.
     */
    private void assertServiceDestroyed() throws InterruptedException {
        assertThat(waitForServiceOnDestroy(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Verifies that the service has never been created.
     */
    private void assertServiceWasNotCreated() throws InterruptedException {
        assertThat(waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isFalse();
    }
}
