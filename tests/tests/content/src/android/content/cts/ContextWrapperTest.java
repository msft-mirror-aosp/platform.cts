/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test {@link ContextWrapper}.
 *
 * <p>This class inherits most of its test methods from its parent ContextTest. Since ContextWrapper
 * delegates its requests to the Context, the same test cases should pass for both Context and
 * ContextWrapper.
 *
 * <p>There are some tests for ContextWrapper that don't make sense for Context - those are included
 * in this class.
 */
@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(JUnit4.class)
public final class ContextWrapperTest extends ContextTest {
    private static final String ANDROID_SHELL = "com.android.shell";

    /** Returns the ContextWrapper object that's being tested. */
    @Override
    protected Context getContextUnderTest() {
        return new ContextWrapper(super.getContextUnderTest());
    }

    @Test
    public void testConstructor() {
        new ContextWrapper(mContext);

        // null param is allowed
        new ContextWrapper(null);
    }

    @Test
    public void testAccessBaseContext() throws PackageManager.NameNotFoundException {
        Context context = getContext();
        MockContextWrapper testContextWrapper = new MockContextWrapper(context);

        // Test getBaseContext()
        assertThat(testContextWrapper.getBaseContext()).isSameInstanceAs(context);

        Context secondContext =
                testContextWrapper.createPackageContext(
                        ANDROID_SHELL, Context.CONTEXT_IGNORE_SECURITY);
        assertThat(secondContext).isNotNull();
        // Test attachBaseContext
        assertThrows(
                "If base context has already been set, it should throw a IllegalStateException.",
                IllegalStateException.class,
                () -> testContextWrapper.attachBaseContext(secondContext));
    }

    // TODO: this mock seems unnecessary. Remove it, and just use ContextWrapper?
    private static final class MockContextWrapper extends ContextWrapper {
        public MockContextWrapper(Context base) {
            super(base);
        }

        @Override
        public void attachBaseContext(Context base) {
            super.attachBaseContext(base);
        }
    }
}
