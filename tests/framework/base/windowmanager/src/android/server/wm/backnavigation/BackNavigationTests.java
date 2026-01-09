/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.server.wm.backnavigation;

import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.TouchHelper;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Integration test for back navigation */
@Presubmit
public class BackNavigationTests extends ActivityManagerTestBase {
    private TestActivitySession<BackNavigationActivity> mActivitySession;
    private BackNavigationActivity mActivity;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivitySession = createManagedTestActivitySession();
    }

    @Test
    public void registerCallback_created() {
        try (LifecycleMonitor helper = new LifecycleMonitor()) {
            helper.registerCallbackAtCreate();
            launchTestActivity();
            mWmState.waitAndAssertActivityState(mActivity.getComponentName(), STATE_RESUMED);
            CountDownLatch latch = helper.mRegisterCallbackResult;
            invokeBackAndAssertCallbackIsCalled(latch);
        }
    }

    @Test
    public void registerCallback_resumed() {
        launchTestActivity();
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_dialog() {
        launchTestActivity();
        CountDownLatch backInvokedLatch = new CountDownLatch(1);
        mActivitySession.runOnMainSyncAndWait(
                () -> {
                    Dialog dialog = new Dialog(mActivity, 0);
                    dialog.getOnBackInvokedDispatcher()
                            .registerOnBackInvokedCallback(
                                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                                    backInvokedLatch::countDown);
                    dialog.show();
                });
        invokeBackAndAssertCallbackIsCalled(backInvokedLatch);
    }

    @Test
    public void onBackPressedNotCalled() {
        launchTestActivity();
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
        mInstrumentation.runOnMainSync(
                () ->
                        assertFalse(
                                "Activity.onBackPressed should not be called",
                                mActivity.mOnBackPressedCalled));
    }

    @Test
    public void onUserInteractionCalled() {
        launchTestActivity();
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
        mInstrumentation.runOnMainSync(
                () ->
                        assertTrue(
                                "Activity.onUserInteraction should be called",
                                mActivity.mOnUserInteractionCalled));
    }

    @Test
    public void registerCallback_relaunch() {
        launchTestActivity();
        CountDownLatch latch1 = registerBackCallback();
        CountDownLatch latch2;
        try (LifecycleMonitor helper = new LifecycleMonitor()) {
            mActivitySession.runOnMainSyncAndWait(() -> mActivity.recreate());
            if (!Condition.waitFor(
                    "Wait for activity recreate...", () -> helper.mLaunchedActivity != null)) {
                fail("Test activity did not recreate");
            }
            mWmState.waitAndAssertActivityState(mActivity.getComponentName(), STATE_RESUMED);
            latch2 =
                    registerBackCallback(
                            helper.mLaunchedActivity,
                            true /* unregisterAfterCalled */,
                            false /* inMainThread */);
        }
        invokeBackAndAssertCallbackIsCalled(latch2);
        invokeBackAndAssertCallback(latch1, false);
    }

    private void invokeBackAndAssertCallbackIsCalled(CountDownLatch latch) {
        invokeBackAndAssertCallback(latch, true);
    }

    private void invokeBackAndAssertCallback(CountDownLatch latch, boolean isCalled) {
        try {
            // Make sure the application is idle and input windows is up-to-date.
            mInstrumentation.waitForIdleSync();
            mInstrumentation.getUiAutomation().syncInputTransactions();
            TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
            if (isCalled) {
                assertTrue("OnBackInvokedCallback.onBackInvoked() was not called",
                        latch.await(500, TimeUnit.MILLISECONDS));
            } else {
                assertFalse("OnBackInvokedCallback.onBackInvoked() was called",
                        latch.await(500, TimeUnit.MILLISECONDS));
            }
        } catch (InterruptedException ex) {
            fail("Application died before invoking the callback.\n" + ex.getMessage());
        }
    }

    private void launchTestActivity() {
        mActivitySession.launchTestActivityOnDisplaySync(
                BackNavigationActivity.class, getMainDisplayId());
        mActivity = mActivitySession.getActivity();
    }

    private class LifecycleMonitor implements AutoCloseable {
        final Application mApplication;
        final Application.ActivityLifecycleCallbacks mActivityCallbacks;
        boolean mRegisterCallbackAtCreate;
        CountDownLatch mRegisterCallbackResult;
        Activity mLaunchedActivity;

        LifecycleMonitor() {
            final Context targetContext = mInstrumentation.getTargetContext();
            mApplication = (Application) targetContext.getApplicationContext();
            mActivityCallbacks =
                    new Application.ActivityLifecycleCallbacks() {

                        @Override
                        public void onActivityCreated(
                                @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                            mLaunchedActivity = activity;
                            if (mRegisterCallbackAtCreate) {
                                mRegisterCallbackResult =
                                        registerBackCallback(
                                                activity,
                                                false /* unregisterAfterCalled */,
                                                true /* inMainThread */);
                            }
                        }

                        @Override
                        public void onActivityStarted(@NonNull Activity activity) {}

                        @Override
                        public void onActivityResumed(@NonNull Activity activity) {}

                        @Override
                        public void onActivityPaused(@NonNull Activity activity) {}

                        @Override
                        public void onActivityStopped(@NonNull Activity activity) {}

                        @Override
                        public void onActivitySaveInstanceState(
                                @NonNull Activity activity, @NonNull Bundle outState) {}

                        @Override
                        public void onActivityDestroyed(@NonNull Activity activity) {}
                    };
            mApplication.registerActivityLifecycleCallbacks(mActivityCallbacks);
        }

        void registerCallbackAtCreate() {
            mRegisterCallbackAtCreate = true;
        }

        @Override
        public void close() {
            mApplication.unregisterActivityLifecycleCallbacks(mActivityCallbacks);
        }
    }

    private CountDownLatch registerBackCallback() {
        return registerBackCallback(
                mActivity, false /* unregisterAfterCalled */, false /* inMainThread */);
    }

    private CountDownLatch registerBackCallback(
            Activity activity, boolean unregisterAfterCalled, boolean inMainThread) {
        CountDownLatch backInvokedLatch = new CountDownLatch(1);
        final OnBackInvokedCallback callback =
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        backInvokedLatch.countDown();
                        if (unregisterAfterCalled) {
                            activity.getOnBackInvokedDispatcher()
                                    .unregisterOnBackInvokedCallback(this);
                        }
                    }
                };

        if (inMainThread) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, callback);
        } else {
            mInstrumentation.runOnMainSync(
                    () -> {
                        activity.getOnBackInvokedDispatcher()
                                .registerOnBackInvokedCallback(0, callback);
                    });
        }
        return backInvokedLatch;
    }
}
