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

package android.security.cts.BUG_406243581;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.platform.test.annotations.AsbSecurityTest;
import android.view.View;
import android.view.WindowManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BUG_406243581 extends StsExtraBusinessLogicTestCase {
    private WindowManager mWindowManager;
    private EmptyActivity mActivity;

    private WindowManager.LayoutParams createBaseLayoutParams(int type) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT);
        if (type == TYPE_APPLICATION) {
            params.token = mActivity.getActivityToken();
        }
        params.setTitle("TestWindow");
        return params;
    }

    private void testFlagIsRemoved(int privateFlagToTest) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final View view = new View(instrumentation.getContext());
        final WindowManager.LayoutParams initialParams = createBaseLayoutParams(TYPE_APPLICATION);

        try {
            // Add the view to the window manager without the private flag
            instrumentation.runOnMainSync(() -> {
                mWindowManager.addView(view, initialParams);
            });
            instrumentation.waitForIdleSync();

            // Update the layout to include the private flag
            final WindowManager.LayoutParams updatedParams =
                    (WindowManager.LayoutParams) view.getLayoutParams();
            updatedParams.privateFlags = privateFlagToTest;

            instrumentation.runOnMainSync(() -> {
                mWindowManager.updateViewLayout(view, updatedParams);
            });
            instrumentation.waitForIdleSync();

            WindowManager.LayoutParams actualParams = (WindowManager.LayoutParams) view.getLayoutParams();

            Assert.assertEquals("Flag should have been removed by the system",
                    0, actualParams.privateFlags & privateFlagToTest);
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (view.isAttachedToWindow()) {
                    mWindowManager.removeViewImmediate(view);
                }
            });
        }
    }

    @Before
    public void setUp() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getContext();
        Intent intent = new Intent(context, EmptyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity = (EmptyActivity) instrumentation.startActivitySync(intent);
        mWindowManager = context.getSystemService(WindowManager.class);
    }

    @After
    public void tearDown() {
        if (mActivity != null) {
            mActivity.finish();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_TrustedOverlay() {
        // PRIVATE_FLAG_TRUSTED_OVERLAY requires INTERNAL_SYSTEM_WINDOW
        testFlagIsRemoved(PRIVATE_FLAG_TRUSTED_OVERLAY);
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_RoundedCornersOverlay() {
        // PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY requires INTERNAL_SYSTEM_WINDOW
        testFlagIsRemoved(PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY);
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_InterceptGlobalDragAndDrop() {
        // PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP requires MANAGE_ACTIVITY_TASKS
        testFlagIsRemoved(PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP);
    }
}
