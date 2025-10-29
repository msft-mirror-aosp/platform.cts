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
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.platform.test.annotations.AsbSecurityTest;
import android.view.IWindowSession;
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class BUG_406243581 extends StsExtraBusinessLogicTestCase {

    private Instrumentation mInstrumentation;
    private Context mContext;
    private WindowManager mWindowManager;
    private View mOverlayView;

    private Class<?> mWindowManagerGlobalClass;
    private Field mWindowSessionField;
    private IWindowSession mOriginalWindowSession;

    private int mFlagToInject;
    private int mPrivateFlagTrustedOverlay;
    private int mPrivateFlagRoundedCornersOverlay;
    private int mPrivateFlagInterceptGlobalDragAndDrop;

    private static final String TEST_WINDOW_TITLE = "StsTestWindow-BUG_406243581";

    private class RelayoutHookHandler implements InvocationHandler {
        private final IWindowSession mOriginal;

        RelayoutHookHandler(IWindowSession original) {
            mOriginal = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Find relayout via reflection like the exploit
            if ("relayout".equals(method.getName())) {
                try {
                    // Find the WindowManager.LayoutParams argument
                    for (Object arg : args) {
                        if (arg instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) arg;

                            // Inject the malicious private flag
                            if (mFlagToInject != 0) {
                                Field privateFlagsField =
                                        WindowManager.LayoutParams.class.getDeclaredField(
                                                "privateFlags");
                                privateFlagsField.setAccessible(true);
                                privateFlagsField.set(attrs, mFlagToInject);
                            }
                        }
                    }
                } catch (Exception e) {
                    fail("Reflection setup for relayout hook failed: " + e.getMessage());
                }
            }

            return method.invoke(mOriginal, args);
        }
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mFlagToInject = 0;

        mPrivateFlagTrustedOverlay =
                WindowManager.LayoutParams.class
                        .getDeclaredField("PRIVATE_FLAG_TRUSTED_OVERLAY")
                        .getInt(null);
        mPrivateFlagRoundedCornersOverlay =
                WindowManager.LayoutParams.class
                        .getDeclaredField("PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY")
                        .getInt(null);
        mPrivateFlagInterceptGlobalDragAndDrop =
                WindowManager.LayoutParams.class
                        .getDeclaredField("PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP")
                        .getInt(null);

        mWindowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal");

        mWindowSessionField = mWindowManagerGlobalClass.getDeclaredField("sWindowSession");
        mWindowSessionField.setAccessible(true);

        Method getSessionMethod = mWindowManagerGlobalClass.getDeclaredMethod("getWindowSession");
        getSessionMethod.setAccessible(true);
        mOriginalWindowSession = (IWindowSession) getSessionMethod.invoke(null);
        assertNotNull("Original Window Session should not be null", mOriginalWindowSession);

        RelayoutHookHandler hookHandler = new RelayoutHookHandler(mOriginalWindowSession);
        IWindowSession proxySession =
                (IWindowSession)
                        Proxy.newProxyInstance(
                                mWindowManagerGlobalClass.getClassLoader(),
                                new Class<?>[] {IWindowSession.class, IBinder.class},
                                hookHandler);

        mWindowSessionField.set(null, proxySession);
    }

    private void assertPrivateFlagIsSanitized(int privateFlagToTest) throws Exception {
        mFlagToInject = privateFlagToTest;

        mOverlayView = new View(mContext);
        mOverlayView.setBackgroundColor(Color.RED);

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        100, 100, TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);

        params.setTitle(TEST_WINDOW_TITLE);

        try {
            mInstrumentation.runOnMainSync(() -> mWindowManager.addView(mOverlayView, params));
            mInstrumentation.waitForIdleSync();

            final String dumpsysOutput = runShellCommand("dumpsys window windows");

            final Pattern windowPattern =
                    Pattern.compile(
                            String.format(
                                    "^(\\s*)Window #\\d+"
                                            + " Window\\{[^:]*%s.*\\}:((?:\\R\\1\\s+.*|\\R\\s*)*)",
                                    Pattern.quote(TEST_WINDOW_TITLE)),
                            Pattern.MULTILINE);

            final Matcher windowMatcher = windowPattern.matcher(dumpsysOutput);

            if (!windowMatcher.find()) {
                Assert.fail("TestWindow with title '" + TEST_WINDOW_TITLE + "' not found.");
            }

            String windowStateDump = windowMatcher.group(2);

            final Pattern privateFlagsPattern = Pattern.compile("\\s*pfl=((?:\\s*[A-Z_]+)+)");
            final Matcher privateFlagsMatcher = privateFlagsPattern.matcher(windowStateDump);

            String serverPrivateFlags = "";
            if (privateFlagsMatcher.find()) {
                serverPrivateFlags = privateFlagsMatcher.group(1);
            }

            String flagString = getPrivateFlagString(privateFlagToTest);

            Assert.assertFalse(
                    "Insecure private flag '"
                            + flagString
                            + "' was found for test window. Server flags: '"
                            + serverPrivateFlags
                            + "'",
                    serverPrivateFlags.contains(flagString));

        } finally {
            mInstrumentation.runOnMainSync(
                    () -> {
                        if (mOverlayView != null && mOverlayView.isAttachedToWindow()) {
                            mWindowManager.removeViewImmediate(mOverlayView);
                        }
                    });
            mOverlayView = null;
            mFlagToInject = 0;
        }
    }

    private String getPrivateFlagString(int privateFlag) {
        if (privateFlag == mPrivateFlagTrustedOverlay) {
            return "TRUSTED_OVERLAY";
        }
        if (privateFlag == mPrivateFlagRoundedCornersOverlay) {
            return "IS_ROUNDED_CORNERS_OVERLAY";
        }
        if (privateFlag == mPrivateFlagInterceptGlobalDragAndDrop) {
            return "INTERCEPT_GLOBAL_DRAG_AND_DROP";
        }
        throw new IllegalArgumentException("Unknown private flag: " + privateFlag);
    }

    @After
    public void tearDown() throws Exception {
        if (mOverlayView != null) {
            mInstrumentation.runOnMainSync(
                    () -> {
                        try {
                            if (mOverlayView.isAttachedToWindow()) {
                                mWindowManager.removeViewImmediate(mOverlayView);
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    });
            mOverlayView = null;
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_TrustedOverlay() throws Exception {
        assertPrivateFlagIsSanitized(mPrivateFlagTrustedOverlay);
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_RoundedCornersOverlay() throws Exception {
        assertPrivateFlagIsSanitized(mPrivateFlagRoundedCornersOverlay);
    }

    @Test
    @AsbSecurityTest(cveBugId = 406243581L)
    public void testPrivateFlagRemoval_InterceptGlobalDragAndDrop() throws Exception {
        assertPrivateFlagIsSanitized(mPrivateFlagInterceptGlobalDragAndDrop);
    }
}
